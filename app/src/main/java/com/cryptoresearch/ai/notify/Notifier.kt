package com.cryptoresearch.ai.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cryptoresearch.ai.MainActivity
import com.cryptoresearch.ai.R

object Notifier {
    const val CH_MOVES = "moves"
    const val CH_REPORTS = "reports"
    const val CH_NEWS = "news"
    const val CH_ERRORS = "errors"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun ch(id: String, name: String, imp: Int) {
            nm.createNotificationChannel(NotificationChannel(id, name, imp))
        }
        ch(CH_MOVES, "Significant market movements", NotificationManager.IMPORTANCE_DEFAULT)
        ch(CH_REPORTS, "Research report completion", NotificationManager.IMPORTANCE_LOW)
        ch(CH_NEWS, "Important news updates", NotificationManager.IMPORTANCE_LOW)
        ch(CH_ERRORS, "Data / API errors", NotificationManager.IMPORTANCE_LOW)
    }

    fun hasPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun show(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (!hasPermission(ctx)) return
        try {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // permission revoked while running: ignore
        } catch (e: Exception) {
            // never crash because of a notification
        }
    }
}
