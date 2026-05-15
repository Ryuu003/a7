package com.focuslock

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ForegroundBlockerService : Service() {

    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPkg = ""
    private var blockNotifShowing = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                if (prefs.isActive) checkForegroundApp()
                else cancelBlockNotif()
            } catch (e: Exception) {}
            handler.postDelayed(this, 400)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildStatusNotif())
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
        return START_STICKY
    }

    private fun checkForegroundApp() {
        val pkg = getActualForegroundApp() ?: return
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg
        if (pkg == packageName) { cancelBlockNotif(); return }

        if (prefs.isBlocked(pkg)) {
            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) { pkg }
            showFullScreenBlockNotif(label)
        } else {
            cancelBlockNotif()
        }
    }

    private fun getActualForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 5000, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = event.packageName
            }
        }
        return last
    }

    private fun showFullScreenBlockNotif(label: String) {
        // Intent que abre BlockedActivity al tocar la notificación
        val fullScreenIntent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedActivity.EXTRA_LABEL, label)
        }
        val fullScreenPI = PendingIntent.getActivity(
            this, 2, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, BLOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("⊘  $label bloqueada")
            .setContentText("Esta app está bloqueada. Toca para ver.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPI, true)  // ← Esto abre la pantalla completa automáticamente
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        NotificationManagerCompat.from(this).notify(BLOCK_NOTIF_ID, notif)
        blockNotifShowing = true
    }

    private fun cancelBlockNotif() {
        if (blockNotifShowing) {
            NotificationManagerCompat.from(this).cancel(BLOCK_NOTIF_ID)
            blockNotifShowing = false
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val pi = PendingIntent.getService(
            applicationContext, 1,
            Intent(applicationContext, ForegroundBlockerService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.ELAPSED_REALTIME, 1000, pi)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelBlockNotif()
        handler.removeCallbacks(checkRunnable)
        startService(Intent(this, ForegroundBlockerService::class.java))
    }

    private fun buildStatusNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusLock activo")
            .setContentText("Protegiendo tu enfoque")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "FocusLock", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(BLOCK_CHANNEL_ID, "Bloqueo de apps", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Alerta cuando se abre una app bloqueada"
                    setShowBadge(true)
                }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1001
        const val BLOCK_NOTIF_ID = 1002
        const val CHANNEL_ID = "focuslock_channel"
        const val BLOCK_CHANNEL_ID = "focuslock_block"
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, ForegroundBlockerService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, ForegroundBlockerService::class.java))
    }
}
