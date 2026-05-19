package com.drosocode.myphone.service

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val address = messages[0].displayOriginatingAddress
            var body = ""
            for (message in messages) {
                body += message.displayMessageBody
            }

            Log.d("SmsReceiver", "Received SMS from $address: $body")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Default SMS app is responsible for writing the message to the provider
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                        put(Telephony.Sms.READ, 0)
                    }
                    
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    
                    // Show Notification
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val channelId = "sms_channel_id"

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val channel = android.app.NotificationChannel(
                            channelId,
                            "SMS Messages",
                            android.app.NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Incoming SMS notifications"
                            enableVibration(true)
                        }
                        notificationManager.createNotificationChannel(channel)
                    }

                    // Wake up screen if locked
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    if (!powerManager.isInteractive) {
                        @Suppress("DEPRECATION")
                        val wakeLock = powerManager.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "myPhone:SmsWakeLock"
                        )
                        wakeLock.acquire(3000) // wake for 3 seconds
                    }

                    val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
                    val clickIntent = Intent(context, com.drosocode.myphone.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("start_destination", "conversation/$threadId/$address")
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context, address.hashCode(), clickIntent, 
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )

                    val contactName = com.drosocode.myphone.data.SmsRepository(context).getContactName(context, address)
                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.stat_notify_chat) // Using default system icon for simplicity
                        .setContentTitle(contactName ?: address)
                        .setContentText(body)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE) // Hides content on lock screen
                        .build()

                    notificationManager.notify(address.hashCode(), notification)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
