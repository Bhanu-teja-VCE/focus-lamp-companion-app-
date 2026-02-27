package com.focuslamp.app.data.repository

import com.focuslamp.app.data.network.HttpLampController
import com.focuslamp.app.data.network.SocketManager
import com.focuslamp.app.utils.Resource

/**
 * Central repository that coordinates communication with the ESP32 lamp.
 * Supports both HTTP (OkHttp) and TCP (Socket) modes.
 */
class SessionRepository(
    private val socketManager: SocketManager,
    private val httpController: HttpLampController
) {

    private var currentIp = "192.168.4.1"
    private var port = 8080
    private var useHttp = true

    fun updateConnectionDetails(ip: String, port: Int, httpMode: Boolean) {
        this.currentIp = ip
        this.port = port
        this.useHttp = httpMode
    }

    // ========== HTTP Mode ==========

    /** Send distraction alert via HTTP GET */
    suspend fun sendDistraction(): Resource<Boolean> {
        return if (useHttp) {
            val success = httpController.sendDistraction(currentIp)
            if (success) Resource.Success(true) else Resource.Error("HTTP distraction failed")
        } else {
            val sent = socketManager.sendCommand("DISTRACTION")
            if (sent) Resource.Success(true) else Resource.Error("TCP distraction failed")
        }
    }

    /** Send focus signal via HTTP GET */
    suspend fun sendFocus(): Resource<Boolean> {
        return if (useHttp) {
            val success = httpController.sendFocus(currentIp)
            if (success) Resource.Success(true) else Resource.Error("HTTP focus failed")
        } else {
            val sent = socketManager.sendCommand("FOCUS")
            if (sent) Resource.Success(true) else Resource.Error("TCP focus failed")
        }
    }

    /** Send idle signal */
    suspend fun sendIdle(): Resource<Boolean> {
        return if (useHttp) {
            val success = httpController.sendIdle(currentIp)
            if (success) Resource.Success(true) else Resource.Error("HTTP idle failed")
        } else {
            val sent = socketManager.sendCommand("STOP")
            if (sent) Resource.Success(true) else Resource.Error("TCP idle failed")
        }
    }

    // ========== TCP Mode (for timer-based sessions) ==========

    suspend fun connectToLamp(): Resource<Boolean> {
        val success = socketManager.connect(currentIp, port)
        return if (success) Resource.Success(true) else Resource.Error("Connection failed")
    }

    suspend fun startSession(durationMinutes: Int): Resource<Boolean> {
        val command = "START:$durationMinutes"
        val sent = socketManager.sendCommand(command)
        return if (sent) Resource.Success(true) else Resource.Error("Failed to send START")
    }

    suspend fun stopSession(): Resource<Boolean> {
        val sent = socketManager.sendCommand("STOP")
        return if (sent) Resource.Success(true) else Resource.Error("Failed to send STOP")
    }
}
