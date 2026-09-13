package com.example.outlookringalert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

class OutlookNotificationListener : NotificationListenerService() {

    companion object {
        const val OUTLOOK_PACKAGE_NAME = "com.microsoft.office.outlook"
        const val CHANNEL_ID = "outlook_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TEST_ALERT = "com.example.outlookringalert.ACTION_TEST_ALERT"

        var activeRingtone: Ringtone? = null
        var activeVibrator: Vibrator? = null

        fun stopAlert(context: Context) {
            activeRingtone?.stop()
            activeRingtone = null

            activeVibrator?.cancel()
            activeVibrator = null

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    override fun onDestroy() {
        stopAlert(this)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            if (it.packageName == OUTLOOK_PACKAGE_NAME) {
                val timePrefs = TimePreferences(this)
                
                // Ignore group summary notifications
                val isSummary = (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
                if (isSummary) {
                    timePrefs.addLog("Ignored (Group Summary)")
                    return
                }

                if (!timePrefs.isAppEnabled) {
                    timePrefs.addLog("Ignored (App Disabled)")
                    return
                }

                if (timePrefs.isAlwaysRingModeEnabled) {
                    timePrefs.addLog("✅ Alert Triggered (24/7 Always Ring Mode)")
                    triggerCallAlert()
                    return
                }

                if (!timePrefs.isCurrentlyInWindow()) {
                    timePrefs.addLog("Ignored (Outside Scheduled Hours)")
                    return
                }

                timePrefs.addLog("✅ Alert Triggered (Scheduled Window)")
                triggerCallAlert()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST_ALERT) {
            TimePreferences(this).addLog("✅ Test Alert Triggered")
            triggerCallAlert()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun triggerCallAlert() {
        stopAlert(this)

        val timePrefs = TimePreferences(this)
        val ringtoneUri = timePrefs.ringtoneUri?.let { Uri.parse(it) } 
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
        activeRingtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ?: RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))

        activeRingtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        activeRingtone?.play()

        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            activeVibrator = vibratorManager.defaultVibrator
            activeVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            activeVibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            activeVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }

        showStopNotification()
    }

    private fun showStopNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Outlook Mail Alert",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, StopAlertReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Outlook Email Received")
            .setContentText("Ringing like a phone call. Tap stop to silence.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Ringing", stopPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}