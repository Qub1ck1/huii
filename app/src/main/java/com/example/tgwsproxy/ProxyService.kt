package com.example.tgwsproxy

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

class ProxyService : Service() {

    companion object {
        const val CHANNEL_ID = "tg_ws_proxy_channel"
        const val ACTION_STOP = "com.example.tgwsproxy.ACTION_STOP"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private val activeTunnels = ConcurrentHashMap.newKeySet<Closeable>()
    private var screenOffJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (ProxyState.isSleepOnScreenOff(this@ProxyService)) {
                        screenOffJob?.cancel()
                        screenOffJob = scope.launch {
                            delay(10_000L)
                            closeAllTunnels()
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> screenOffJob?.cancel()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!ProxyState.isRunning.value) {
            val port = ProxyState.getPort(this)
            startForegroundCompat(buildNotification(port))
            ProxyState.setRunning(true)
            startSocksListener(port)
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(101, notification)
        }
    }

    private fun startSocksListener(port: Int) {
        scope.launch {
            try {
                val server = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = server
                while (isActive && !server.isClosed) {
                    val client = server.accept()
                    launch {
                        TelegramWsTunnel(client, activeTunnels) {
                            ProxyState.setActiveTunnelsCount(activeTunnels.size)
                        }.run()
                    }
                }
            } catch (_: Exception) {
                stopSelf()
            }
        }
    }

    private fun closeAllTunnels() {
        activeTunnels.toList().forEach { it.close() }
        activeTunnels.clear()
        ProxyState.setActiveTunnelsCount(0)
    }

    private fun buildNotification(port: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ProxyService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TG WS Proxy • 127.0.0.1:$port")
            .setContentText("Режим энергосбережения активен")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent)
            .addAction(Notification.Action.Builder(null, "Выключить", stopPending).build())
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Локальный WS-прокси Telegram",
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        ProxyState.setRunning(false)
        screenOffJob?.cancel()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        closeAllTunnels()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
