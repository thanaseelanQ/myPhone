package com.drosocode.myphone.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.os.Build
import com.drosocode.myphone.data.model.Conversation
import com.drosocode.myphone.data.model.Message
import com.drosocode.myphone.data.model.MessageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    companion object {
        private var cachedConversations: List<Conversation>? = null
        private var lastFetchTime: Long = 0
        private const val CACHE_DURATION = 30000L // 30 seconds cache
    }

    suspend fun getConversations(forceRefresh: Boolean = false): List<Conversation> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedConversations != null && (now - lastFetchTime) < CACHE_DURATION) {
            return@withContext cachedConversations!!
        }

        val conversations = mutableListOf<Conversation>()
        val contactNameCache = mutableMapOf<String, String?>()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                Telephony.Sms.DEFAULT_SORT_ORDER
            )

            val seenThreads = mutableSetOf<String>()

            cursor?.use {
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val snippetIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val threadId = it.getString(threadIdIdx) ?: continue
                    if (seenThreads.contains(threadId)) continue
                    seenThreads.add(threadId)

                    val address = it.getString(addressIdx) ?: "Unknown"
                    val snippet = it.getString(snippetIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val read = it.getInt(readIdx) == 1
                    
                    val contactName = contactNameCache.getOrPut(address) {
                        getContactName(context, address)
                    }
                    val category = categorizeMessage(address, snippet)

                    conversations.add(
                        Conversation(threadId, address, contactName, snippet, date, read, category)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        cachedConversations = conversations
        lastFetchTime = now
        conversations
    }

    suspend fun getMessagesForThread(threadId: String): List<Message> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<Message>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId)

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                Telephony.Sms.DEFAULT_SORT_ORDER
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    messages.add(
                        Message(
                            id = it.getString(idIdx),
                            threadId = it.getString(threadIdIdx),
                            address = it.getString(addressIdx) ?: "Unknown",
                            body = it.getString(bodyIdx) ?: "",
                            date = it.getLong(dateIdx),
                            type = it.getInt(typeIdx),
                            read = it.getInt(readIdx) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        messages.reversed()
    }

    suspend fun sendMessage(address: String, body: String) = withContext(Dispatchers.IO) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            smsManager.sendTextMessage(address, null, body, null, null)
            
            if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.READ, 1)
                }
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            }
            cachedConversations = null // Invalidate cache
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markThreadAsRead(threadId: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId)
        )
        cachedConversations = null // Invalidate cache
    }

    private fun categorizeMessage(address: String, body: String): MessageCategory {
        val lowerBody = body.lowercase()
        val isNumericAddress = address.replace("+", "").all { it.isDigit() }

        if (lowerBody.contains("otp") || lowerBody.contains("verification code") || lowerBody.contains("is your code")) {
            return MessageCategory.OTPS
        }

        if (isNumericAddress && address.length > 6) {
            return MessageCategory.PERSONAL
        }

        if (lowerBody.contains("offer") || lowerBody.contains("discount") || lowerBody.contains("% off") || lowerBody.contains("cashback")) {
            return MessageCategory.OFFERS
        }

        if (lowerBody.contains("debited") || lowerBody.contains("credited") || lowerBody.contains("txn") || lowerBody.contains("ac") || lowerBody.contains("order")) {
            return MessageCategory.TRANSACTIONS
        }

        return if (!isNumericAddress) MessageCategory.TRANSACTIONS else MessageCategory.PERSONAL
    }

    suspend fun getContactName(context: Context, phoneNumber: String): String? = withContext(Dispatchers.IO) {
        val uri = Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
        var name: String? = null
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        name
    }

    suspend fun performCleanup() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val otpCutoff = now - (30 * 24 * 60 * 60 * 1000L)
        val offerCutoff = now - (10 * 24 * 60 * 60 * 1000L)
        val transactionCutoff = now - (108L * 24 * 60 * 60 * 1000L)

        val idsToDelete = mutableListOf<String>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val date = it.getLong(dateIdx)
                    val address = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: ""

                    val category = categorizeMessage(address, body)

                    val shouldDelete = when (category) {
                        MessageCategory.OTPS -> date < otpCutoff
                        MessageCategory.OFFERS -> date < offerCutoff
                        MessageCategory.TRANSACTIONS -> date < transactionCutoff
                        else -> false
                    }

                    if (shouldDelete) {
                        idsToDelete.add(it.getString(idIdx))
                    }
                }
            }

            idsToDelete.chunked(100).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                val selection = "${Telephony.Sms._ID} IN ($placeholders)"
                val selectionArgs = batch.toTypedArray()
                
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    selection,
                    selectionArgs
                )
            }
            if (idsToDelete.isNotEmpty()) {
                cachedConversations = null // Invalidate cache
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    suspend fun deleteThreads(threadIds: List<String>) = withContext(Dispatchers.IO) {
        if (threadIds.isEmpty()) return@withContext
        try {
            val placeholders = threadIds.joinToString(",") { "?" }
            val selection = "${Telephony.Sms.THREAD_ID} IN ($placeholders)"
            val selectionArgs = threadIds.toTypedArray()
            
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                selection,
                selectionArgs
            )
            cachedConversations = null // Invalidate cache
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
