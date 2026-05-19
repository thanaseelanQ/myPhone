package com.drosocode.myphone.data

import android.content.Context
import android.provider.Telephony
import com.drosocode.myphone.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

data class TransactionDetail(
    val id: String,
    val date: Long,
    val address: String,
    val amount: Double,
    val isCredit: Boolean,
    val description: String,
    val category: String,
    val fullMessage: String,
    val isDeleted: Boolean = false
)

data class MonthlyAnalysis(
    val monthName: String,
    val year: Int,
    val monthInt: Int,
    val totalSpent: Double,
    val totalCredited: Double,
    val transactions: List<TransactionDetail>
)

class SpendRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("spend_analysis_prefs", Context.MODE_PRIVATE)

    fun getDeletedTransactionIds(): Set<String> {
        val rawSet = prefs.getStringSet("deleted_transaction_ids", null) ?: emptySet()
        return HashSet(rawSet)
    }

    fun restoreTransaction(messageId: String) {
        val current = getDeletedTransactionIds().toMutableSet()
        current.remove(messageId)
        prefs.edit().putStringSet("deleted_transaction_ids", current).apply()
    }

    fun clearDeletedTransactions() {
        prefs.edit().remove("deleted_transaction_ids").apply()
    }

    private val amountRegex = """(?i)(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""".toRegex()
    private val amountRegex2 = """([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹)""".toRegex()

    suspend fun getAllMessages(): List<Message> = withContext(Dispatchers.IO) {
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
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
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
        messages
    }

    fun parseTransaction(message: Message): TransactionDetail? {
        val body = message.body
        val lowerBody = body.lowercase(Locale.ROOT)

        // Skip informational "we have received" type notifications
        if (lowerBody.contains("we have received") || lowerBody.contains("we've received") ||
            lowerBody.contains("received your payment") || lowerBody.contains("received payment")) {
            return null
        }

        // Skip "you requested to stop SIP" type messages or other SIP stop alerts
        if (lowerBody.contains("stop sip") || lowerBody.contains("stop your sip") ||
            lowerBody.contains("requested to stop") || lowerBody.contains("request to stop") ||
            lowerBody.contains("sip stop") || lowerBody.contains("stop my sip")) {
            return null
        }

        // Check if message belongs to EPFO/provident fund
        val isEpfo = lowerBody.contains("epfo") || lowerBody.contains("provident fund") ||
                     (lowerBody.contains(" epf") && !lowerBody.contains("stop"))

        // Evaluate debit check first to ensure debited/spent/paid/payment/debited by are marked as expense
        val isCredit = when {
            lowerBody.contains("debited") || lowerBody.contains("debited by") ||
            lowerBody.contains("spent") || lowerBody.contains("paid") ||
            lowerBody.contains("payment") || lowerBody.contains("sent to") ||
            lowerBody.contains("withdrawn") || lowerBody.contains("charged") ||
            lowerBody.contains("transferred") || lowerBody.contains("txn") ||
            lowerBody.contains("transaction") || lowerBody.contains("purchase") ||
            lowerBody.contains("shopping") || lowerBody.contains("bought") ||
            lowerBody.contains("transfer") || lowerBody.contains(" dr") ||
            lowerBody.contains("dr.") || lowerBody.contains("sent") ||
            lowerBody.contains("towards") || lowerBody.contains("transfer to") ||
            lowerBody.contains("atm") || lowerBody.contains("withdrawal") ||
            lowerBody.contains("wdl") || lowerBody.contains("withdrew") ||
            lowerBody.contains("cash withdrawal") -> false

            lowerBody.contains("credited") || lowerBody.contains("received") ||
            lowerBody.contains("deposited") || lowerBody.contains("added to") ||
            lowerBody.contains("refunded") || lowerBody.contains("salary") ||
            lowerBody.contains(" cr") || lowerBody.contains("cr.") ||
            isEpfo -> true

            else -> return null
        }

        // Amount parsing
        var amount = 0.0
        val match = amountRegex.find(body) ?: amountRegex2.find(body)
        if (match != null) {
            val amountStr = match.groupValues[1].replace(",", "")
            amount = amountStr.toDoubleOrNull() ?: 0.0
        }

        if (amount <= 0.0) return null

        // Categorize transactions
        val category = when {
            isEpfo || lowerBody.contains("mutual fund") || lowerBody.contains("sip ") ||
            lowerBody.contains("investment") || lowerBody.contains("stock") || lowerBody.contains("groww") ||
            lowerBody.contains("zerodha") -> "Investment"

            isCredit && (lowerBody.contains("salary") || lowerBody.contains("wages")) -> "Salary"
            lowerBody.contains("zomato") || lowerBody.contains("swiggy") || lowerBody.contains("food") || 
            lowerBody.contains("restaurant") || lowerBody.contains("cafe") || lowerBody.contains("dining") -> "Food & Dining"
            lowerBody.contains("amazon") || lowerBody.contains("flipkart") || lowerBody.contains("meesho") || 
            lowerBody.contains("myntra") || lowerBody.contains("shopping") || lowerBody.contains("store") ||
            lowerBody.contains("purchase") -> "Shopping"
            lowerBody.contains("recharge") || lowerBody.contains("jio") || lowerBody.contains("airtel") || 
            lowerBody.contains("vi ") || lowerBody.contains("bill") || lowerBody.contains("electricity") || 
            lowerBody.contains("netflix") || lowerBody.contains("spotify") || lowerBody.contains("subscription") ||
            lowerBody.contains("broadband") || lowerBody.contains("dth") -> "Bills & Utilities"
            lowerBody.contains("uber") || lowerBody.contains("ola") || lowerBody.contains("rapido") || 
            lowerBody.contains("metro") || lowerBody.contains("travel") || lowerBody.contains("cab") || 
            lowerBody.contains("train") || lowerBody.contains("flight") || lowerBody.contains("fuel") ||
            lowerBody.contains("petrol") -> "Travel & Fuel"
            else -> if (isCredit) "Income" else "Other Spend"
        }

        // Extract clean description
        var rawDescription = when {
            lowerBody.contains("paid to") -> {
                val idx = lowerBody.indexOf("paid to")
                body.substring(idx)
            }
            lowerBody.contains("sent to") -> {
                val idx = lowerBody.indexOf("sent to")
                body.substring(idx)
            }
            lowerBody.contains("debited at") -> {
                val idx = lowerBody.indexOf("debited at")
                body.substring(idx)
            }
            lowerBody.contains("credited from") -> {
                val idx = lowerBody.indexOf("credited from")
                body.substring(idx)
            }
            lowerBody.contains("received from") -> {
                val idx = lowerBody.indexOf("received from")
                body.substring(idx)
            }
            lowerBody.contains("debited for") -> {
                val idx = lowerBody.indexOf("debited for")
                body.substring(idx)
            }
            else -> {
                val words = body.split(" ").filter { it.isNotBlank() }
                if (words.size > 5) words.take(5).joinToString(" ") else body
            }
        }

        // Clean raw description
        var cleanDescription = rawDescription.trim()
            .replace(amountRegex, "")
            .replace(amountRegex2, "")
            .replace(Regex("(?i)at\\s+\\d{2}:\\d{2}\\s*(?:am|pm)?"), "")
            .replace(Regex("(?i)on\\s+\\d{2}-\\d{2}-\\d{4}"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(".")
            .removeSuffix(",")
            .removeSuffix("-")
            .trim()

        if (cleanDescription.length > 40) {
            cleanDescription = cleanDescription.take(37) + "..."
        }

        if (cleanDescription.isEmpty()) {
            cleanDescription = if (isCredit) "Received Money" else "Spent Money"
        }

        // Format description nicely: capitalize first letter
        cleanDescription = cleanDescription.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        return TransactionDetail(
            id = message.id,
            date = message.date,
            address = message.address,
            amount = amount,
            isCredit = isCredit,
            description = cleanDescription,
            category = category,
            fullMessage = message.body
        )
    }

    suspend fun getSpendAnalytics(): List<MonthlyAnalysis> {
        val messages = getAllMessages()
        val deletedIds = getDeletedTransactionIds()
        val rawTransactions = messages.mapNotNull { msg ->
            parseTransaction(msg)?.let { tx ->
                tx.copy(isDeleted = deletedIds.contains(tx.id))
            }
        }

        // Remove duplicate transactions (e.g. duplicate bank & UPI notifications within 30 seconds)
        val transactions = mutableListOf<TransactionDetail>()
        for (tx in rawTransactions) {
            val isDuplicate = transactions.any { existing ->
                val sameAmount = Math.abs(existing.amount - tx.amount) < 0.01
                val sameType = existing.isCredit == tx.isCredit
                val closeTime = Math.abs(existing.date - tx.date) < 30000L // 30 seconds
                sameAmount && sameType && closeTime
            }
            if (!isDuplicate) {
                transactions.add(tx)
            }
        }

        val calendar = Calendar.getInstance()
        val grouped = transactions.groupBy { transaction ->
            calendar.timeInMillis = transaction.date
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            Pair(year, month)
        }

        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        return grouped.map { (key, txs) ->
            val (year, monthInt) = key
            val totalSpent = txs.filter { !it.isDeleted && !it.isCredit }.sumOf { it.amount }
            val totalCredited = txs.filter { !it.isDeleted && it.isCredit }.sumOf { it.amount }
            MonthlyAnalysis(
                monthName = "${monthNames[monthInt]} $year",
                year = year,
                monthInt = monthInt,
                totalSpent = totalSpent,
                totalCredited = totalCredited,
                transactions = txs.sortedByDescending { it.date }
            )
        }.sortedWith(compareByDescending<MonthlyAnalysis> { it.year }.thenByDescending { it.monthInt })
    }

    fun deleteTransaction(messageId: String) {
        val current = getDeletedTransactionIds().toMutableSet()
        current.add(messageId)
        prefs.edit().putStringSet("deleted_transaction_ids", current).apply()
    }
}
