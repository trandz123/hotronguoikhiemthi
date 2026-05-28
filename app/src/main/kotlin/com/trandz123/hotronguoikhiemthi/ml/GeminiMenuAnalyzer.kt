package com.trandz123.hotronguoikhiemthi.ml

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiMenuAnalyzer(private val apiKey: String) {

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun analyze(bitmap: Bitmap): List<MenuItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw IllegalStateException("Gemini API key not configured")

        val base64 = bitmap.toBase64Jpeg(JPEG_QUALITY)
        val body = buildRequestBody(base64)
        val responseJson = postJson(body)
        parseItems(responseJson)
    }

    private fun buildRequestBody(base64Jpeg: String): String {
        val inlineData = JSONObject()
            .put("mime_type", "image/jpeg")
            .put("data", base64Jpeg)
        val parts = JSONArray()
            .put(JSONObject().put("text", PROMPT))
            .put(JSONObject().put("inline_data", inlineData))
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
            readTimeout = 30_000
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

    /** Boc ngoai ```json ... ``` neu Gemini van vo tinh wrap. */
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
        val cleaned = raw.replace(Regex("[^0-9kK]"), "")
        val hasK = cleaned.endsWith("k", ignoreCase = true)
        val digits = cleaned.trimEnd('k', 'K')
        val n = digits.toLongOrNull() ?: return null
        val amount = if (hasK) n * 1_000L else n
        return amount.takeIf { it in 1_000L..10_000_000L }
    }

    private fun Bitmap.toBase64Jpeg(quality: Int): String {
        val baos = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    class GeminiException(message: String) : RuntimeException(message)

    private companion object {
        const val TAG = "GeminiMenuAnalyzer"
        const val API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        const val JPEG_QUALITY = 85
        const val PROMPT = """Đây là ảnh chụp trực tiếp menu nhà hàng. Hãy nhìn kỹ hình ảnh, nhận diện toàn bộ chữ và trích xuất danh sách món ăn cùng giá tiền tương ứng.
Trả về JSON với format chính xác: {"items": [{"name": "tên món", "price": "giá"}]}
Lưu ý: Chỉ trả về chuỗi JSON nguyên bản, không bọc trong ký tự ```json ... ```, không giải thích hoặc thêm bất kỳ văn bản nào khác."""
    }
}
