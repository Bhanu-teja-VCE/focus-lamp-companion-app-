package com.focuslamp.app.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.focuslamp.app.data.local.AppDatabase
import com.focuslamp.app.data.local.SessionEntity
import com.focuslamp.app.data.network.HttpLampController
import com.focuslamp.app.data.network.SocketManager
import com.focuslamp.app.data.repository.SessionRepository
import com.focuslamp.app.data.tracking.DistractingAppsManager
import com.focuslamp.app.data.tracking.ScreenTimeTracker
import com.focuslamp.app.service.FocusMonitorService
import com.focuslamp.app.utils.SettingsManager
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the Focus Lamp app.
 * Uses AndroidViewModel so it has access to Application context.
 */
class FocusViewModel(application: Application) : AndroidViewModel(application) {

    // === Dependencies ===
    private val settingsManager = SettingsManager(application)
    private val screenTimeTracker = ScreenTimeTracker(application)
    private val distractingAppsManager = DistractingAppsManager(application)
    private val sessionDao = AppDatabase.getInstance(application).sessionDao()
    private val repository = SessionRepository(SocketManager(), HttpLampController())

    // === Timer State ===
    private val _timerText = MutableLiveData("25:00")
    val timerText: LiveData<String> = _timerText

    private val _isSessionActive = MutableLiveData(false)
    val isSessionActive: LiveData<Boolean> = _isSessionActive

    private var timer: CountDownTimer? = null
    var selectedDurationMinutes: Int = 25

    // === Connection State ===
    private val _connectionStatus = MutableLiveData("Not Connected")
    val connectionStatus: LiveData<String> = _connectionStatus

    // === Screen Time State ===
    private val _distractionMinutes = MutableLiveData(0L)
    val distractionMinutes: LiveData<Long> = _distractionMinutes

    private val _timeLimitMinutes = MutableLiveData(settingsManager.timeLimitMinutes)
    val timeLimitMinutes: LiveData<Int> = _timeLimitMinutes

    private val _isLimitExceeded = MutableLiveData(false)
    val isLimitExceeded: LiveData<Boolean> = _isLimitExceeded

    // === Monitoring State ===
    private val _isMonitoring = MutableLiveData(settingsManager.isMonitoringActive)
    val isMonitoring: LiveData<Boolean> = _isMonitoring

    // === Statistics ===
    private val _totalFocusMinutes = MutableLiveData(0L)
    val totalFocusMinutes: LiveData<Long> = _totalFocusMinutes

    private val _completedSessions = MutableLiveData(0)
    val completedSessions: LiveData<Int> = _completedSessions

    private val _sessionHistory = MutableLiveData<List<SessionEntity>>(emptyList())
    val sessionHistory: LiveData<List<SessionEntity>> = _sessionHistory

    // === ESP32 IP ===
    private val _espIp = MutableLiveData(settingsManager.espIp)
    val espIp: LiveData<String> = _espIp

    init {
        refreshScreenTime()
        loadStats()
        syncRepositorySettings()
    }

    // ===========================
    // Screen Time Monitoring
    // ===========================

    fun refreshScreenTime() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val minutes = screenTimeTracker.getTotalScreenTimeToday()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _distractionMinutes.value = minutes
                _isLimitExceeded.value = minutes >= settingsManager.timeLimitMinutes
            }
        }
    }

    fun startMonitoringService() {
        val context = getApplication<Application>()
        val intent = Intent(context, FocusMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _isMonitoring.value = true
        settingsManager.isMonitoringActive = true
    }

    fun stopMonitoringService() {
        val context = getApplication<Application>()
        val intent = Intent(context, FocusMonitorService::class.java)
        context.stopService(intent)
        _isMonitoring.value = false
        settingsManager.isMonitoringActive = false
    }

    // ===========================
    // Focus Timer Session
    // ===========================

    fun startFocusSession() {
        viewModelScope.launch {
            repository.sendFocus()
            _isSessionActive.value = true
            startLocalTimer(selectedDurationMinutes * 60 * 1000L)
        }
    }

    fun stopSession() {
        timer?.cancel()
        _isSessionActive.value = false
        _timerText.value = "00:00"

        viewModelScope.launch {
            repository.sendIdle()
            sessionDao.insertSession(
                SessionEntity(
                    timestamp = System.currentTimeMillis(),
                    durationMinutes = selectedDurationMinutes,
                    isCompleted = false
                )
            )
            loadStats()
        }
    }

    private fun startLocalTimer(durationMillis: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                _timerText.value = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                _isSessionActive.value = false
                _timerText.value = "00:00"
                saveCompletedSession()
            }
        }.start()
    }

    private fun saveCompletedSession() {
        viewModelScope.launch {
            repository.sendIdle()
            sessionDao.insertSession(
                SessionEntity(
                    timestamp = System.currentTimeMillis(),
                    durationMinutes = selectedDurationMinutes,
                    isCompleted = true,
                    distractionMinutes = _distractionMinutes.value ?: 0
                )
            )
            loadStats()
        }
    }

    // ===========================
    // Settings
    // ===========================

    fun updateEspIp(ip: String) {
        settingsManager.espIp = ip
        _espIp.value = ip
        syncRepositorySettings()
    }

    fun updateTimeLimit(minutes: Int) {
        settingsManager.timeLimitMinutes = minutes
        _timeLimitMinutes.value = minutes
    }

    fun hasUsagePermission(): Boolean {
        return screenTimeTracker.hasUsagePermission()
    }

    private fun syncRepositorySettings() {
        repository.updateConnectionDetails(
            settingsManager.espIp,
            settingsManager.espPort,
            settingsManager.useHttpMode
        )
    }

    // ===========================
    // Statistics
    // ===========================

    // Note: Detailed App Usage and Weekly Usage were removed due to device limitations.
    // The UI will now display Total Screen Time instead.

    fun loadDetailedStats() {
        // Just trigger a refresh of the total screen time so the UI gets the latest number
        refreshScreenTime()
    }

    fun loadStats() {
        viewModelScope.launch {
            _totalFocusMinutes.value = sessionDao.getTotalFocusMinutes() ?: 0
            _completedSessions.value = sessionDao.getCompletedSessionCount()
            _sessionHistory.value = sessionDao.getRecentSessions(20)
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            _connectionStatus.value = "Connecting..."
            val result = repository.connectToLamp()
            _connectionStatus.value = if (result.data == true) "Connected" else "Connection Failed"
        }
    }
}
