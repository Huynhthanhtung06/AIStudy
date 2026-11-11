package com.aistudy.aistudy.ask

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
            conn.connectTimeout = 60000
            conn.readTimeout = 60000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            conn.outputStream.use { os ->
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            Log.d(TAG, "response httpCode=$responseCode")

            val stream = if (responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream
            }

            val responseBody = BufferedReader(InputStreamReader(stream, "UTF-8")).use { br ->
                val sb = StringBuilder()
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                sb.toString()
            }

            if (responseCode in 200..299) {
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return parts.getJSONObject(0).getString("text")
                    }
                }
                throw Exception("No text content returned from Gemini response")
            } else {
                Log.e(TAG, "request failed httpCode=$responseCode: $responseBody")
                throw Exception("HTTP $responseCode: $responseBody")
            }
        } finally {
            conn.disconnect()
        }
    }
}
