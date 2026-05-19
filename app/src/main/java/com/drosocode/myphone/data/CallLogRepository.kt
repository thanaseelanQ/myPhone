package com.drosocode.myphone.data

import android.content.Context
import android.provider.CallLog
import com.drosocode.myphone.data.model.CallLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {
    
    companion object {
        private var cachedCallLogs: List<CallLogEntry>? = null
        private var lastFetchTime: Long = 0
        private const val CACHE_DURATION = 3000L // 3 seconds cache
    }

    suspend fun fetchCallLogs(forceRefresh: Boolean = false): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCallLogs != null && (now - lastFetchTime) < CACHE_DURATION) {
            return@withContext cachedCallLogs!!
        }

        val callLogs = mutableListOf<CallLogEntry>()
        val contentResolver = context.contentResolver
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val number = it.getString(numberIndex)
                    val name = it.getString(nameIndex)
                    val type = it.getInt(typeIndex)
                    val date = it.getLong(dateIndex)
                    val duration = it.getString(durationIndex)
                    
                    callLogs.add(CallLogEntry(id, number, name, type, date, duration))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cachedCallLogs = callLogs
        lastFetchTime = now
        callLogs
    }

    fun formatDuration(seconds: String): String {
        val s = seconds.toIntOrNull() ?: 0
        return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getDateCategory(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000L
        
        val todayStart = now - (now % dayInMillis)
        val yesterdayStart = todayStart - dayInMillis

        return when {
            timestamp >= todayStart -> "Today"
            timestamp >= yesterdayStart -> "Yesterday"
            else -> "Older"
        }
    }

    suspend fun deleteCallLogs(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val contentResolver = context.contentResolver
        val placeholders = ids.joinToString(",") { "?" }
        val selection = "${CallLog.Calls._ID} IN ($placeholders)"
        val selectionArgs = ids.toTypedArray()
        
        try {
            contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
            cachedCallLogs = null // Invalidate cache
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
