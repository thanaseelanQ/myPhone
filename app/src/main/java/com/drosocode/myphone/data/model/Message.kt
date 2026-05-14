package com.drosocode.myphone.data.model

data class Message(
    val id: String,
    val threadId: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 = Inbox, 2 = Sent
    val read: Boolean
)

data class Conversation(
    val threadId: String,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val category: MessageCategory
)

enum class MessageCategory {
    ALL,
    PERSONAL,
    TRANSACTIONS,
    OTPS,
    OFFERS
}
