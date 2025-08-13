package com.example.palm_app.util

import android.app.*
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.palm_app.R

object NotificationUtils {
    const val CHANNEL_ID = "ble_peripheral"
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "BLE Peripheral", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }
    fun foreground(ctx: Context): Notification {
        ensureChannel(ctx)
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name) // any small icon
            .setContentTitle("Palm BLE active")
            .setContentText("Advertising & GATT server running")
            .setOngoing(true)
            .build()
    }
}
