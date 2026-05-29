package com.trandz123.hotronguoikhiemthi.ml

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Goi Groq Vision API (Llama 4 Scout) de PARSE anh menu thanh danh sach mon + gia.
 *
 * Pipeline v0.6 (Vision-based):
 *   Bitmap -> resize 1024px JPEG -> base64 -> Groq Llama 4 Scout Vision -> JSON {items}
 *
 * Tai sao Vision: OCR ML Kit flatten text mat layout cot -> LLM khong match dish-price duoc.
 * Model Vision nhin truc tiep anh menu -> match spatially -> chinh xac hon nhieu.
 *
 * Free tier Groq Vision: ~1000 RPD model llama-4-scout, qua du cho demo.
 */
class GroqMenuAnalyzer(private val apiKey: String) {

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * Parse menu tu Bitmap su dung Vision model.
     * Method chinh thay the parseMenuText() -- bo qua buoc OCR.
     */
    suspend fun parseMenuImage(bitmap: Bitmap): List<MenuItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IllegalStateException("Groq API key not configured")

        val base64Image = encodeBitmap(bitmap)
        Log.d(TAG, "Image base64 size: ${base64Image.length / 1024} KB")
        val body = buildVisionRequestBody(base64Image)

        var attempt = 0
        var lastError: GroqException? = null
        while (attempt < MAX_RETRIES) {
            try {
                val responseJson = postJson(body)
                return@withContext parseItems(responseJson)
            } catch (e: GroqException) {
                lastError = e
                val msg = e.message.orEmpty()
                val transient = msg.contains("429") || msg.contains("503") || msg.contains("rate_limit")
                if (!transient || attempt == MAX_RETRIES - 1) throw e
                val backoffMs = 2_000L shl attempt
                Log.w(TAG, "Transient error '$msg', retry attempt ${attempt + 1} after ${backoffMs}ms")
                delay(backoffMs)
                attempt++
            }
        }
        throw lastError ?: GroqException("Unknown error after retries")
    }

    /** Resize bitmap ve max 1024px, JPEG quality 85, encode base64. */
    private fun encodeBitmap(bitmap: Bitmap): String {
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildVisionRequestBody(base64Image: String): String {
        val textPart = JSONObject()
            .put("type", "text")
            .put("text", VISION_PROMPT)
        val imagePart = JSONObject()
            .put("type", "image_url")
            .put(
                "image_url",
                JSONObject().put("url", "data:image/jpeg;base64,$base64Image"),
            )
        val content = JSONArray().put(textPart).put(imagePart)
        val userMsg = JSONObject()
            .put("role", "user")
            .put("content", content)
        val messages = JSONArray().put(userMsg)
        return JSONObject()
            .put("model", VISION_MODEL)
            .put("messages", messages)
            .put("temperature", 0.1)
            .put("max_tokens", 4096)
            .toString()
    }

    private fun postJson(body: String): String {
        val url = URL(API_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 45_000
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.w(TAG, "Groq HTTP $code: $err")
                throw GroqException("Groq API returned HTTP $code: ${err.orEmpty().take(200)}")
            }
            return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                .use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseItems(responseJson: String): List<MenuItem> {
        val root = JSONObject(responseJson)
        val choices = root.optJSONArray("choices")
            ?: throw GroqException("No choices in response")
        if (choices.length() == 0) throw GroqException("Empty choices array")
        val message = choices.getJSONObject(0).optJSONObject("message")
            ?: throw GroqException("No message in choice")
        val content = message.optString("content").trim()
        if (content.isBlank()) throw GroqException("Empty content in response")
        Log.d(TAG, "Groq vision raw response: ${content.take(1000)}")

        val cleaned = stripCodeFence(content)
        val payload = JSONObject(extractJsonObject(cleaned))
        val items = payload.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<MenuItem>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isBlank()) continue
            val priceRaw = obj.optString("price").trim()
            out += MenuItem(
                rawText = "$name${if (priceRaw.isNotBlank()) " — $priceRaw" else ""}",
                name = name,
                priceVnd = parseVndAmount(priceRaw),
            )
        }
        return out
    }

    /** Vision model doi khi tra ve "Day la menu:\n{...json...}". Trich JSON object dau tien. */
    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    private fun stripCodeFence(text: String): String {
        var s = text.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
            val endFence = s.lastIndexOf("```")
            if (endFence >= 0) s = s.substring(0, endFence).trim()
        }
        return s
    }

    private fun parseVndAmount(raw: String): Long? {
        if (raw.isBlank()) return null
        val s = raw.lowercase().trim()
        val cleaned = s
            .replace("vnd", "").replace("vnđ", "").replace("đồng", "").replace("dong", "")
            .replace("₫", "").replace(" ", "")
        val hasTr = cleaned.contains("tr") || cleaned.contains("triệu") || cleaned.contains("trieu")
        val hasK = cleaned.contains("k") || cleaned.contains("nghìn") || cleaned.contains("nghin")
        val numStr = cleaned.replace(Regex("[^0-9.,]"), "").replace(",", ".")
        if (numStr.isBlank()) return null
        val n = if (numStr.contains(".")) {
            val parts = numStr.split(".")
            if (parts.size > 2 || (parts.size == 2 && parts[1].length == 3 && !hasK && !hasTr)) {
                numStr.replace(".", "").toLongOrNull()
            } else {
                numStr.toDoubleOrNull()?.toLong()
            }
        } else {
            numStr.toLongOrNull()
        } ?: return null
        val amount = when {
            hasTr -> n * 1_000_000L
            hasK && n < 1_000 -> n * 1_000L
            n in 1L..999L -> n * 1_000L
            else -> n
        }
        return amount.takeIf { it in 1_000L..10_000_000L }
    }

    class GroqException(message: String) : RuntimeException(message)

    private companion object {
        const val TAG = "GroqMenuAnalyzer"
        const val MAX_RETRIES = 3
        const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

        const val VISION_PROMPT = """Đây là ảnh chụp menu một nhà hàng Việt Nam.

NHIỆM VỤ: Trích xuất TẤT CẢ món ăn và giá tiền từ menu, trả về JSON.

QUY TẮC:
1. **GIỮ NGUYÊN TÊN MÓN ĐẦY ĐỦ** — không cắt bớt, bao gồm mô tả như "sốt bơ tỏi", "kèm rau", "rim mật ong".
2. **Tên tiếng Việt CÓ DẤU ĐẦY ĐỦ** — "Phở Bò", "Bún Chả", "Sườn Nướng Mật Ong"...
3. **GHÉP CHÍNH XÁC giá với món** — dùng vị trí trong ảnh để match (giá thường cùng dòng hoặc cột bên phải).
4. **CHUẨN HÓA giá về số nguyên VND** (string chỉ chữ số):
   - "50k" / "50.000" / "50N" → "50000"
   - "200.000đ" → "200000"
   - "1.5tr" / "1.500.000" → "1500000"
5. Nếu món thật sự không có giá → price = "" (không bịa).
6. Bỏ qua: tên cửa hàng, địa chỉ, SĐT, header section, slogan, ghi chú.

OUTPUT (CHỈ JSON, không bọc markdown, không giải thích):
{"items":[{"name":"Tên món đầy đủ có dấu","price":"50000"}]}"""
    }
}
