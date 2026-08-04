package com.focuslamp.app.data.network

import android.util.Log
import com.focuslamp.app.data.model.LampState
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
 *   GET http://<IP>/blue         -> Lamp turns BLUE (rest/recovery)
 *   GET http://<IP>/purple       -> Lamp turns PURPLE (deep work session)
 *   GET http://<IP>/amber        -> Lamp turns AMBER (extension granted)
 *   GET http://<IP>/pulse_slow   -> Lamp pulses SLOW (mindful nudge)
 *   GET http://<IP>/pulse_fast   -> Lamp pulses FAST (restricted alert)
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
        val cleanIp = espIp.replace("http://", "").replace("https://", "").trim()
        return "http://$cleanIp/$path"
    }

    suspend fun sendState(espIp: String, state: LampState): Boolean {
        return sendGetRequest(formatUrl(espIp, state.endpoint))
    }

    suspend fun sendDistraction(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "distraction"))
    }

    suspend fun sendFocus(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "focus"))
    }

    suspend fun sendWarning(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "warning"))
    }

    suspend fun sendIdle(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "idle"))
    }

    suspend fun sendBlue(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "blue"))
    }

    suspend fun sendPurple(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "purple"))
    }

    suspend fun sendAmber(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "amber"))
    }

    suspend fun sendSlowPulse(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "pulse_slow"))
    }

    suspend fun sendFastPulse(espIp: String): Boolean {
        return sendGetRequest(formatUrl(espIp, "pulse_fast"))
    }

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
