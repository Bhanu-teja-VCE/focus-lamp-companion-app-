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
 *   GET http://<IP>/distraction  -> Lamp turns RED (limit exceeded)
 *   GET http://<IP>/warning      -> Lamp turns WHITE (approaching limit)
 *   GET http://<IP>/focus        -> Lamp turns GREEN (within limit)
 *   GET http://<IP>/idle         -> Lamp turns GREEN (ready)
 */
class HttpLampController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS) // Increased for local network discovery
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "HttpLampController"
    }

    private fun formatUrl(espIp: String, path: String): String {
        // Clean the IP address in case the user typed http:// manually
        val cleanIp = espIp.replace("http://", "").replace("https://", "").trim()
        return "http://$cleanIp/$path"
    }

    /**
     * Send a distraction alert to the lamp — makes it turn RED.
     */
    suspend fun sendDistraction(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "distraction"))
    }

    /**
     * Send a focus signal to the lamp — makes it turn GREEN.
     */
    suspend fun sendFocus(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "focus"))
    }

    /**
     * Send a warning signal to the lamp: makes it turn WHITE.
     */
    suspend fun sendWarning(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "warning"))
    }

    /**
     * Tell the lamp to go idle: makes it turn GREEN/ready.
     */
    suspend fun sendIdle(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "idle"))
    }

    /**
     * Ping the ESP32 /status endpoint to test connectivity.
     * Returns a Pair indicating success and an error message if any.
     */
    suspend fun sendStatus(espIp: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = formatUrl(espIp, "status")
        return@withContext try {
            Log.d(TAG, "Sending GET → $url")
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            val msg = response.message
            response.close()
            if (success) Pair(true, "OK") else Pair(false, "HTTP ${response.code}: $msg")
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}")
            Pair(false, e.message ?: "Unknown error")
        }
    }

    /**
     * Generic HTTP GET to the ESP32 (used by focus/distraction/idle).
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
