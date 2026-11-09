package com.aistudy.aistudy.ask

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-1.5-flash"

    fun ask(apiKey: String, prompt: String): String {
        return ask(apiKey, prompt, null, null)
    }

    fun ask(apiKey: String, prompt: String, imageBase64: String?, mimeType: String?): String {
        val hasImage = !imageBase64.isNullOrEmpty()
        Log.d(TAG, "request start model=$MODEL promptLength=${prompt.length} hasImage=$hasImage")
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 90000

        val parts = JSONArray()

        val textPart = JSONObject()
        textPart.put("text", prompt)
        parts.put(textPart)

        if (hasImage) {
            val mime = if (mimeType.isNullOrEmpty()) "image/jpeg" else mimeType
            val inlineData = JSONObject()
            inlineData.put("mime_type", mime)
            inlineData.put("data", imageBase64)
            val imagePart = JSONObject()
            imagePart.put("inline_data", inlineData)
            parts.put(imagePart)
        }

        val content = JSONObject()
        content.put("parts", parts)

        val body = JSONObject()
        body.put("contents", JSONArray().put(content))

        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        Log.d(TAG, "request body bytes=${bytes.size}")
        conn.outputStream.use { os ->
            os.write(bytes)
        }

        val code = conn.responseCode
        Log.d(TAG, "response httpCode=$code")
        val reader = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
        } else {
            BufferedReader(InputStreamReader(conn.errorStream, StandardCharsets.UTF_8))
        }

        val sb = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            sb.append(line)
            line = reader.readLine()
        }
        reader.close()

        val responseBody = sb.toString()
        if (code < 200 || code >= 300) {
            Log.e(TAG, "request failed httpCode=$code body=${truncate(responseBody, 500)}")
            throw Exception("HTTP $code: $responseBody")
        }

        Log.d(TAG, "response body length=${responseBody.length}")
        val json = JSONObject(responseBody)
        val text = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        Log.d(TAG, "parsed text length=${text.length} preview=${truncate(text, 200)}")
        return text
    }

    private fun truncate(s: String?, max: Int): String {
        if (s == null) return "null"
        if (s.length <= max) return s
        return s.substring(0, max) + "..."
    }
}
