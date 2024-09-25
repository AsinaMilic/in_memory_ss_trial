package com.example.in_memory_ss_trial

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

class GroqApiClient(private val apiKey: String) {
    private val client = OkHttpClient()

    fun sendPrompt(prompt: String, callback: (String?) -> Unit) {
        val json = JSONObject()
        json.put("model", "llama3-8b-8192")
        val messages = JSONArray()
        val message = JSONObject()
        message.put("role", "user")
        message.put("content", prompt)
        messages.put(message)
        json.put("messages", messages)

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        Log.d("GroqApiClient", "Sending request to Groq API")
        Log.d("GroqApiClient", "Request body: $json")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GroqApiClient", "Request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("GroqApiClient", "Received response. Status: ${response.code}")
                Log.d("GroqApiClient", "Response body: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val content = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        Log.d("GroqApiClient", "Extracted content: $content")
                        callback(content)
                    } catch (e: Exception) {
                        Log.e("GroqApiClient", "Error parsing JSON response", e)
                        callback(null)
                    }
                } else {
                    Log.e("GroqApiClient", "Unsuccessful response: ${response.code}")
                    callback(null)
                }
            }
        })
    }
}