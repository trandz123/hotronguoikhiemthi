package com.trandz123.hotronguoikhiemthi.ml

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Goi Gemini 1.5 Flash API de PARSE raw text OCR thanh danh sach mon + gia.
 *
 * Pipeline (v0.5):
 *   Bitmap -> ML Kit OCR (on-device) -> raw text -> Gemini Flash -> JSON {items}
 *
 * KHONG gui anh len cloud: tiet kiem bandwidth + bao mat hon + chi phi token thap hon.
 */
class GeminiMenuAnalyzer(private val apiKey: String) {

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun parseMenuText(rawText: String): List<MenuItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IllegalStateException("Gemini API key not configured")
        if (rawText.isBlank()) return@withContext emptyList()

        val body = buildRequestBody(rawText)
        // Retry 3 lan voi backoff 2s -> 4s -> 8s khi gap 429/503 (rate limit / overload).
        var attempt = 0
        var lastError: GeminiException? = null
        while (attempt < MAX_RETRIES) {
            try {
                val responseJson = postJson(body)
                return@withContext parseItems(responseJson)
            } catch (e: GeminiException) {
                lastError = e
                val msg = e.message.orEmpty()
                val transient = msg.contains("429") || msg.contains("503") || msg.contains("RESOURCE_EXHAUSTED")
                if (!transient || attempt == MAX_RETRIES - 1) throw e
                val backoffMs = 2_000L shl attempt
                Log.w(TAG, "Transient error '$msg', retry attempt ${attempt + 1} after ${backoffMs}ms")
                delay(backoffMs)
                attempt++
            }
        }
        throw lastError ?: GeminiException("Unknown error after retries")
    }

    private fun buildRequestBody(rawText: String): String {
        val fullPrompt = PROMPT_TEMPLATE + "\n\nRAW_TEXT:\n<<<\n" + rawText + "\n>>>"
        val parts = JSONArray().put(JSONObject().put("text", fullPrompt))
        val contents = JSONArray().put(JSONObject().put("parts", parts))
        val generationConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("temperature", 0.1)
        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
            .toString()
    }

    private fun postJson(body: String): String {
        val url = URL("$API_URL?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.w(TAG, "Gemini HTTP $code: $err")
                throw GeminiException("Gemini API returned HTTP $code")
            }
            return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                .use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseItems(responseJson: String): List<MenuItem> {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates")
            ?: throw GeminiException("No candidates in response")
        if (candidates.length() == 0) throw GeminiException("Empty candidates array")
        val content = candidates.getJSONObject(0).optJSONObject("content")
            ?: throw GeminiException("No content in candidate")
        val parts = content.optJSONArray("parts")
            ?: throw GeminiException("No parts in content")
        if (parts.length() == 0) throw GeminiException("Empty parts array")

        val rawText = parts.getJSONObject(0).optString("text").trim()
        if (rawText.isBlank()) throw GeminiException("Empty text in response")

        val cleaned = stripCodeFence(rawText)
        val payload = JSONObject(cleaned)
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
        val s = raw.lowercase()
        val hasK = s.contains("k") || s.contains("nghin") || s.contains("nghìn")
        val digits = raw.replace(Regex("[^0-9]"), "")
        val n = digits.toLongOrNull() ?: return null
        val amount = if (hasK && n < 1_000) n * 1_000L else n
        return amount.takeIf { it in 1_000L..10_000_000L }
    }

    class GeminiException(message: String) : RuntimeException(message)

    private companion object {
        const val TAG = "GeminiMenuAnalyzer"
        const val MAX_RETRIES = 3
        const val API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent"

        /**
         * Prompt cho Gemini Flash: input la TEXT THO tu ML Kit OCR.
         * Output: JSON nguyen ban {"items":[{"name","price"}]}.
         */
        const val PROMPT_TEMPLATE = """Bạn được cung cấp RAW TEXT trích xuất từ ảnh chụp menu nhà hàng Việt Nam (OCR có thể bị sai chính tả, mất dấu, layout cột bị xáo trộn).

Nhiệm vụ:
1. Phân tích raw text bên dưới, ghép cặp tên món với giá tiền tương ứng.
2. Bỏ qua header/section như "MENU", "ĐỒ ĂN", "ĐỒ UỐNG", địa chỉ, số điện thoại, hashtag, lời chào.
3. Giá có thể ghi: "50.000", "50000", "50k", "50 nghìn", "50N" — chuẩn hóa về dạng đầy đủ "50.000".
4. Nếu một dòng có tên món nhưng không có giá kèm, vẫn đưa vào (price="").
5. Trả về JSON với format CHÍNH XÁC: {"items":[{"name":"tên món","price":"giá"}]}

Lưu ý: chỉ trả về JSON nguyên bản, KHÔNG bọc trong ```json ... ```, KHÔNG giải thích, KHÔNG thêm văn bản ngoài JSON."""
    }
}
