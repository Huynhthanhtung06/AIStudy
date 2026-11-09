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
    private const val MODEL = "gemini-1.5-flash"

    fun ask(apiKey: String, prompt: String): String {
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.readTimeout = 60000
        conn.connectTimeout = 60000

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
        }

        val inputBytes = requestJson.toString().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { os ->
            os.write(inputBytes)
        }

        val code = conn.responseCode
        Log.d(TAG, "response httpCode=$code")

        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseBody = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

        if (code !in 200..299) {
            Log.e(TAG, "request failed httpCode=$code response=$responseBody")
            throw Exception("HTTP $code: $responseBody")
        }

        val json = JSONObject(responseBody)
        val candidates = json.getJSONArray("candidates")
        if (candidates.length() == 0) {
            throw Exception("No candidates returned from Gemini API")
        }
        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        if (parts.length() == 0) {
            throw Exception("No content parts returned from Gemini API")
        }
        return parts.getJSONObject(0).getString("text")
    }
}
