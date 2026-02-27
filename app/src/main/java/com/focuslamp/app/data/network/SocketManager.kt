package com.focuslamp.app.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Raw TCP socket connection to the ESP32.
 * Protocol:
 *   App → ESP32:  "START:<MINUTES>"  |  "STOP"
 *   ESP32 → App:  "STATUS:ACTIVE"  |  "STATUS:IDLE"  |  "COMPLETE"
 */
class SocketManager {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    companion object {
        private const val TAG = "SocketManager"
        private const val TIMEOUT = 5000 // 5 seconds
    }

    suspend fun connect(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            close()
            Log.d(TAG, "Connecting to $ip:$port")
            socket = Socket()
            socket?.connect(InetSocketAddress(ip, port), TIMEOUT)

            writer = PrintWriter(socket!!.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            Log.d(TAG, "Connected successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            false
        }
    }

    suspend fun sendCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (socket == null || socket!!.isClosed) return@withContext false
            Log.d(TAG, "Sending: $command")
            writer?.println(command)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            false
        }
    }

    suspend fun readResponse(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (socket == null || socket!!.isClosed) return@withContext null
            val response = reader?.readLine()
            Log.d(TAG, "Received: $response")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Read failed: ${e.message}")
            null
        }
    }

    fun isConnected(): Boolean {
        return socket != null && !socket!!.isClosed && socket!!.isConnected
    }

    fun close() {
        try {
            writer?.close()
            reader?.close()
            socket?.close()
            socket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
    }
}
