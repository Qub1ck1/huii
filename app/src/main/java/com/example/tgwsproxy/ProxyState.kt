package com.example.tgwsproxy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object ProxyState {
    private const val PREFS_NAME = "tg_ws_proxy_prefs"
    private const val KEY_PORT = "port"
    private const val KEY_SLEEP_ON_SCREEN_OFF = "sleep_screen_off"
    private const val KEY_AUTOSTART = "autostart"

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _activeTunnels = MutableStateFlow(0)
    val activeTunnels = _activeTunnels.asStateFlow()

    private val _bytesUp = MutableStateFlow(0L)
    val bytesUp = _bytesUp.asStateFlow()

    private val _bytesDown = MutableStateFlow(0L)
    val bytesDown = _bytesDown.asStateFlow()

    private val upCounter = AtomicLong(0)
    private val downCounter = AtomicLong(0)

    fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) {
            _activeTunnels.value = 0
        }
    }

    fun setActiveTunnelsCount(count: Int) {
        _activeTunnels.value = count
    }

    fun addBytesUp(bytes: Long) {
        _bytesUp.value = upCounter.addAndGet(bytes)
    }

    fun addBytesDown(bytes: Long) {
        _bytesDown.value = downCounter.addAndGet(bytes)
    }

    fun resetStats() {
        upCounter.set(0)
        downCounter.set(0)
        _bytesUp.value = 0L
        _bytesDown.value = 0L
    }

    fun getPort(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PORT, 1080)

    fun setPort(context: Context, port: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PORT, port).apply()
    }

    fun isSleepOnScreenOff(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SLEEP_ON_SCREEN_OFF, true)

    fun setSleepOnScreenOff(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SLEEP_ON_SCREEN_OFF, enabled).apply()
    }

    fun isAutostart(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART, true)

    fun setAutostart(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTOSTART, enabled).apply()
    }
}
