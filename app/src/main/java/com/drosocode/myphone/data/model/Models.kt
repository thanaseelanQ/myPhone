package com.drosocode.myphone.data.model

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val initial: String = if (name.isNotEmpty()) name[0].uppercase() else "?"
)

data class CallLogEntry(
    val id: String,
    val number: String,
    val name: String?,
    val type: Int, // Incoming, Outgoing, Missed
    val date: Long,
    val duration: String
)
