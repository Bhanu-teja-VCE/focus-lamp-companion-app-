package com.focuslamp.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Sends HTTP GET requests to the ESP32 lamp controller.
 *
 * Endpoints:
 *   GET http://<IP>/distraction  → Lamp turns RED (user is distracted)
 *   GET http://<IP>/focus        → Lamp turns GREEN (user is focused)
 *   GET http://<IP>/idle         → Lamp turns OFF (monitoring stopped)
 */
class HttpLampController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "HttpLampController"
    }

    /**
     * Send a distraction alert to the lamp — makes it turn RED.
     */
    suspend fun sendDistraction(espIp: String): Boolean {
        return sendGetRequest("http://$espIp/distraction")
    }

    /**
     * Send a focus signal to the lamp — makes it turn GREEN.
     */
    suspend fun sendFocus(espIp: String): Boolean {
        return sendGetRequest("http://$espIp/focus")
    }

    /**
     * Tell the lamp to go idle — turns it OFF.
     */
    suspend fun sendIdle(espIp: String): Boolean {
        return sendGetRequest("http://$espIp/idle")
    }

    /**
     * Generic HTTP GET to the ESP32.
     */
    private suspend fun sendGetRequest(url: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Sending GET → $url")
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            Log.d(TAG, "Response: ${response.code} - ${response.message}")
            response.close()
            success
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}")
            false
        }
    }
}
