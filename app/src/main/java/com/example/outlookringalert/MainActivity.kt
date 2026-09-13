package com.example.outlookringalert

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var timePrefs: TimePreferences
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnSelectRingtone: Button
    private lateinit var switchAppEnabled: MaterialSwitch
    private lateinit var tvAppStatusDescription: TextView
    private lateinit var switchAlwaysRing: MaterialSwitch
    private lateinit var tvAlwaysRingModeDescription: TextView
    private lateinit var tvEventLogs: TextView

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                timePrefs.ringtoneUri = uri.toString()
                updateRingtoneButtonText()
            }
        }
    }

    // Reliability Icons
    private lateinit var tvIconNotificationAccess: TextView
    private lateinit var tvIconPostNotifications: TextView
    private lateinit var tvIconChannelStatus: TextView
    private lateinit var tvIconDNDStatus: TextView
    private lateinit var tvIconRingerVolume: TextView
    private lateinit var tvIconBatteryOptimization: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timePrefs = TimePreferences(this)

        // Setup UI references
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnSelectRingtone = findViewById(R.id.btnSelectRingtone)
        switchAppEnabled = findViewById(R.id.switchAppEnabled)
        tvAppStatusDescription = findViewById(R.id.tvAppStatusDescription)
        switchAlwaysRing = findViewById(R.id.switchAlwaysRing)
        tvAlwaysRingModeDescription = findViewById(R.id.tvAlwaysRingModeDescription)
        tvEventLogs = findViewById(R.id.tvEventLogs)

        tvIconNotificationAccess = findViewById(R.id.tvIconNotificationAccess)
        tvIconPostNotifications = findViewById(R.id.tvIconPostNotifications)
        tvIconChannelStatus = findViewById(R.id.tvIconChannelStatus)
        tvIconDNDStatus = findViewById(R.id.tvIconDNDStatus)
        tvIconRingerVolume = findViewById(R.id.tvIconRingerVolume)
        tvIconBatteryOptimization = findViewById(R.id.tvIconBatteryOptimization)

        setupClickListeners()
        
        switchAppEnabled.isChecked = timePrefs.isAppEnabled
        updateAppStatusDescription(timePrefs.isAppEnabled)

        switchAppEnabled.setOnCheckedChangeListener { _, isChecked ->
            timePrefs.isAppEnabled = isChecked
            updateAppStatusDescription(isChecked)
        }

        switchAlwaysRing.isChecked = timePrefs.isAlwaysRingModeEnabled
        updateAlwaysRingModeDescription()

        switchAlwaysRing.setOnCheckedChangeListener { _, isChecked ->
            timePrefs.isAlwaysRingModeEnabled = isChecked
            updateAlwaysRingModeDescription()
        }

        updateTimeButtons()
    }

    private fun setupClickListeners() {
        findViewById<LinearLayout>(R.id.layoutNotificationAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<LinearLayout>(R.id.layoutPostNotifications).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        findViewById<LinearLayout>(R.id.layoutChannelStatus).setOnClickListener {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, OutlookNotificationListener.CHANNEL_ID)
            }
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.layoutDNDStatus).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
        }

        findViewById<LinearLayout>(R.id.layoutRingerVolume).setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            updateReliabilityDashboard()
        }

        findViewById<LinearLayout>(R.id.layoutBatteryOptimization).setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnTestRinging).setOnClickListener {
            val intent = Intent(this, OutlookNotificationListener::class.java).apply {
                action = OutlookNotificationListener.ACTION_TEST_ALERT
            }
            startService(intent)
        }

        btnStartTime.setOnClickListener { showTimePicker(true) }
        btnEndTime.setOnClickListener { showTimePicker(false) }
        btnSelectRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alert Ringtone")
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, 
                timePrefs.ringtoneUri?.let { Uri.parse(it) })
            ringtonePickerLauncher.launch(intent)
        }

        updateTimeButtons()
        updateRingtoneButtonText()
    }

    override fun onResume() {
        super.onResume()
        updateReliabilityDashboard()
    }

    private enum class Status { OK, WARN, BAD }

    private fun setStatusIcon(view: TextView, status: Status) {
        val (bg, fg, symbol) = when (status) {
            Status.OK -> Triple(R.drawable.bg_status_ok, R.color.status_ok_fg, "✓")
            Status.WARN -> Triple(R.drawable.bg_status_warn, R.color.status_warn_fg, "!")
            Status.BAD -> Triple(R.drawable.bg_status_bad, R.color.status_bad_fg, "✕")
        }
        view.setBackgroundResource(bg)
        view.setTextColor(ContextCompat.getColor(this, fg))
        view.text = symbol
    }

    private fun updateReliabilityDashboard() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Notification Listener
        val isListenerEnabled = isNotificationServiceEnabled()
        setStatusIcon(tvIconNotificationAccess, if (isListenerEnabled) Status.OK else Status.BAD)

        // 2. Post Notifications
        val isPostEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        setStatusIcon(tvIconPostNotifications, if (isPostEnabled) Status.OK else Status.BAD)

        // 3. Notification Channel
        val isChannelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(OutlookNotificationListener.CHANNEL_ID)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        } else true
        setStatusIcon(tvIconChannelStatus, if (isChannelEnabled) Status.OK else Status.BAD)

        // 4. Do Not Disturb
        val isDndOff = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        } else true
        setStatusIcon(tvIconDNDStatus, if (isDndOff) Status.OK else Status.BAD)

        // 5. Ringer Volume
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isRingerOn = am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        setStatusIcon(tvIconRingerVolume, if (isRingerOn) Status.OK else Status.WARN)

        // 6. Battery Optimization
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        setStatusIcon(tvIconBatteryOptimization, if (isIgnoringBattery) Status.OK else Status.WARN)

        // Update Logs
        val logs = timePrefs.eventLogs
        tvEventLogs.text = if (logs.isEmpty()) "No recent activity" else logs
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, OutlookNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun showTimePicker(isStart: Boolean) {
        val currentHour = if (isStart) timePrefs.startHour else timePrefs.endHour
        val currentMinute = if (isStart) timePrefs.startMinute else timePrefs.endMinute

        TimePickerDialog(this, { _, hour, minute ->
            if (isStart) {
                timePrefs.startHour = hour
                timePrefs.startMinute = minute
            } else {
                timePrefs.endHour = hour
                timePrefs.endMinute = minute
            }
            updateTimeButtons()
        }, currentHour, currentMinute, false).show()
    }

    private fun updateTimeButtons() {
        btnStartTime.text = String.format(Locale.getDefault(), "From: %02d:%02d %s", 
            if (timePrefs.startHour % 12 == 0) 12 else timePrefs.startHour % 12,
            timePrefs.startMinute,
            if (timePrefs.startHour < 12) "AM" else "PM")

        btnEndTime.text = String.format(Locale.getDefault(), "To: %02d:%02d %s",
            if (timePrefs.endHour % 12 == 0) 12 else timePrefs.endHour % 12,
            timePrefs.endMinute,
            if (timePrefs.endHour < 12) "AM" else "PM")

        updateAlwaysRingModeDescription()
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d %s",
            if (hour % 12 == 0) 12 else hour % 12,
            minute,
            if (hour < 12) "AM" else "PM")
    }

    private fun updateAlwaysRingModeDescription() {
        tvAlwaysRingModeDescription.text = if (timePrefs.isAlwaysRingModeEnabled) {
            "Mode: Ringing for ALL emails 24/7 (Timer Bypassed)"
        } else {
            val startTime = formatTime(timePrefs.startHour, timePrefs.startMinute)
            val endTime = formatTime(timePrefs.endHour, timePrefs.endMinute)
            "Mode: Scheduled (Ringing only between $startTime and $endTime)"
        }
    }

    private fun updateAppStatusDescription(isEnabled: Boolean) {
        tvAppStatusDescription.text = if (isEnabled) {
            "App is currently monitoring Outlook"
        } else {
            "App is disabled"
        }
    }

    private fun updateRingtoneButtonText() {
        val uri = timePrefs.ringtoneUri
        val ringtone = uri?.let { RingtoneManager.getRingtone(this, Uri.parse(it)) }
        btnSelectRingtone.text = if (ringtone != null) {
            "Ringtone: ${ringtone.getTitle(this)}"
        } else {
            "Select Ringtone"
        }
    }
}
