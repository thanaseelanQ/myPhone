package com.drosocode.myphone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import com.drosocode.myphone.MainActivity
import kotlinx.coroutines.*

class CallService : InCallService() {

    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d("CallService", "Call added: ${call.state}")
        
        currentCall = call
        currentCallState = call.state
        callDuration = 0 // Reset duration for new call
        
        acquireProximityWakeLock()
        
        if (call.state == Call.STATE_RINGING) {
            showIncomingCallNotification(call)
        } else {
            showActiveCallNotification(call)
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("EXTRA_CALL_ACTIVE", true)
            }
            startActivity(intent)
        }

        call.registerCallback(object : Call.Callback() {
            private var wasAnswered = false
            private var startTimeMillis = 0L
            private var timerJob: kotlinx.coroutines.Job? = null

            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                currentCallState = state
                
                if (state == Call.STATE_ACTIVE) {
                    if (!wasAnswered) {
                        wasAnswered = true
                        startTimeMillis = System.currentTimeMillis()
                        showActiveCallNotification(call)
                        
                        val intent = Intent(this@CallService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        startActivity(intent)
                    }
                    
                    // Start timer if not already running
                    if (timerJob == null) {
                        timerJob = CoroutineScope(Dispatchers.Main).launch {
                            while (currentCallState == Call.STATE_ACTIVE) {
                                callDuration = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                                delay(1000)
                            }
                        }
                    }
                }

                if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
                    showActiveCallNotification(call)
                }

                if (state == Call.STATE_DISCONNECTED) {
                    timerJob?.cancel()
                    timerJob = null
                    val duration = if (wasAnswered) (System.currentTimeMillis() - startTimeMillis) / 1000 else 0
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
                    notificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID)

                    if (!wasAnswered) {
                        showMissedCallNotification(call)
                    } else {
                        showCallMetricsNotification(call, duration)
                    }
                    
                    currentCall = null
                    currentCallState = null
                }
            }
        })
    }

    private fun showIncomingCallNotification(call: Call) {
        val channelId = "incoming_calls"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_CALL_ACTIVE", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Call")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .build()

        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
    }

    private fun showActiveCallNotification(call: Call) {
        val channelId = "active_calls"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Active Calls", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_CALL_ACTIVE", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Active Call")
            .setContentText(number)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

        notificationManager.notify(ACTIVE_CALL_NOTIFICATION_ID, notification)
    }

    private fun showMissedCallNotification(call: Call) {
        val channelId = "missed_calls"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Missed Calls", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Missed Call")
            .setContentText("From: $number")
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showCallMetricsNotification(call: Call, durationSeconds: Long) {
        val channelId = "call_metrics"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Call Metrics", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val durationText = if (durationSeconds < 60) "${durationSeconds}s" else "${durationSeconds / 60}m ${durationSeconds % 60}s"

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Call Ended")
            .setContentText("$number | Duration: $durationText")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID)
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
        releaseProximityWakeLock()
        currentCall = null
        currentCallState = null
        callDuration = 0
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        currentAudioState = audioState
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximityWakeLock()
        instance = null
        currentCall = null
        currentCallState = null
    }

    private fun acquireProximityWakeLock() {
        if (proximityWakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "myPhone:InCallProximity"
            )
        }
        if (proximityWakeLock?.isHeld == false) {
            proximityWakeLock?.acquire()
        }
    }

    private fun releaseProximityWakeLock() {
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release()
        }
        proximityWakeLock = null
    }

    companion object {
        private const val INCOMING_CALL_NOTIFICATION_ID = 1001
        private const val ACTIVE_CALL_NOTIFICATION_ID = 1002
        var instance: CallService? = null

        var currentCall by mutableStateOf<Call?>(null)
        var currentCallState by mutableStateOf<Int?>(null)
        var currentAudioState by mutableStateOf<CallAudioState?>(null)
        var callDuration by mutableStateOf(0)

        fun mute(shouldMute: Boolean) {
            instance?.setMuted(shouldMute)
        }

        fun toggleSpeaker(useSpeaker: Boolean) {
            val route = if (useSpeaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            instance?.setAudioRoute(route)
        }
    }
}
