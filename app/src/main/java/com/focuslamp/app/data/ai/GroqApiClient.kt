package com.focuslamp.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Groq chat completions (OpenAI-compatible API). */
class GroqApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** [messages] is (role, content) in order, ending with the newest user message. */
    suspend fun complete(apiKey: String, system: String, messages: List<Pair<String, String>>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "Add a Groq API key to use the coach." }
                val arr = JSONArray().put(JSONObject().put("role", "system").put("content", system))
                messages.forEach { (role, content) -> arr.put(JSONObject().put("role", role).put("content", content)) }
                val body = JSONObject()
                    .put("model", MODEL)
                    .put("messages", arr)
                    .put("temperature", 0.6)
                    .put("max_tokens", 600)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(URL)
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                        throw IOException(
                            when (r.code) {
                                401 -> "The Groq API key was rejected. Check it in the coach settings."
                                429 -> "The coach is getting too many requests. Try again in a minute."
                                else -> msg ?: "The coach couldn't answer (HTTP ${r.code})."
                            }
                        )
                    }
                    val choices = JSONObject(text).getJSONArray("choices")
                    if (choices.length() == 0) throw IOException("The coach returned an empty answer.")
                    choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                }
            }.recoverCatching { e ->
                throw if (e is java.net.UnknownHostException) IOException("No internet connection.") else e
            }
        }

    suspend fun validate(apiKey: String): Result<Unit> =
        complete(apiKey, "Reply with OK.", listOf("user" to "ping")).map { }

    companion object {
        private const val URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b-versatile"
    }
}
