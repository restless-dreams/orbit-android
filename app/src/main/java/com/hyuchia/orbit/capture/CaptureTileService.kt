package com.hyuchia.orbit.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * "Add an idea" in Quick Settings: the sheet to catch ideas into a map
 * opens over whatever is on screen, the panel folding away.
 */
class CaptureTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()

        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = captureIntent(this)

        val open = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }

        // On a locked phone, the idea waits for the phone to be unlocked.
        if (isLocked) {
            unlockAndRun(open)
        } else {
            open()
        }
    }
}
