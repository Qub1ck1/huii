package com.example.tgwsproxy

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ProxyTileService : TileService() {

    override fun onStartListening() {
        updateState(ProxyState.isRunning.value)
    }

    override fun onClick() {
        val currentlyRunning = ProxyState.isRunning.value
        if (currentlyRunning) {
            stopService(Intent(this, ProxyService::class.java))
        } else {
            startForegroundService(Intent(this, ProxyService::class.java))
        }
        updateState(!currentlyRunning)
    }

    private fun updateState(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (active) "127.0.0.1:${ProxyState.getPort(this@ProxyTileService)}" else "Выкл"
            }
            updateTile()
        }
    }
}
