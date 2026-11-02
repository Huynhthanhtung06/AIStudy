package com.aistudy.aistudy.ask

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-3.5-flash"

    fun ask(apiKey: String, prompt: String): String {
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
            }

            val bytes = jsonBody.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { os ->
                os.write(bytes)
            }

            val code = conn.responseCode
            val responseStream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = BufferedReader(InputStreamReader(responseStream, Charsets.UTF_8)).use { it.readText() }

            Log.d(TAG, "response httpCode=$code")
            if (code !in 200..299) {
                throw Exception("HTTP $code: $responseBody")
            }

            val root = JSONObject(responseBody)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
            throw Exception("No text found in Gemini response")
        } finally {
            conn.disconnect()
        }
    }
}
