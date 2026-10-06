package com.focuslamp.app.data.lamp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTP GET to the ESP32 on the local network. Same endpoints and protocol as before:
 *   /focus /warning /distraction /idle /status
 * Timeouts are shorter (4 s instead of 15 s) so a missing lamp doesn't freeze anything.
 */
class HttpLampController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class Status(val mode: LampState?, val ip: String?)

    suspend fun send(host: String, state: LampState): Boolean = get(host, state.endpoint) != null

    suspend fun status(host: String): Status? {
        val body = get(host, "status") ?: return null
        return runCatching {
            val o = JSONObject(body)
            Status(LampState.fromMode(o.optString("mode")), o.optString("ip").ifEmpty { null })
        }.getOrElse { Status(null, null) }
    }

    private suspend fun get(host: String, path: String): String? = withContext(Dispatchers.IO) {
        val clean = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (clean.isEmpty()) return@withContext null
        try {
            client.newCall(Request.Builder().url("http://$clean/$path").get().build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() ?: "" else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "GET $path failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "HttpLamp"
    }
}
