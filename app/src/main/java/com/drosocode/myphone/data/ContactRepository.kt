package com.drosocode.myphone.data

import android.content.ContentProviderOperation
import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import com.drosocode.myphone.data.model.Contact

class ContactRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("contact_bin", Context.MODE_PRIVATE)

    companion object {
        private var cachedContacts: List<Contact>? = null
        private var lastFetchTime: Long = 0
        private const val CACHE_DURATION = 5000L // 5 seconds cache
    }

    fun fetchContacts(forceRefresh: Boolean = false): List<Contact> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedContacts != null && (now - lastFetchTime) < CACHE_DURATION) {
            return cachedContacts!!
        }

        val binnedIds = getBinnedIds()
        val contacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    if (id in binnedIds) continue
                    
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val number = it.getString(numberIndex) ?: ""
                    contacts.add(Contact(id, name, number))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val result = contacts.distinctBy { it.phoneNumber.filter { char -> char.isDigit() } }
        cachedContacts = result
        lastFetchTime = now
        return result
    }

    fun addToBin(contactId: String) {
        val binned = getBinnedIds().toMutableSet()
        binned.add(contactId)
        prefs.edit().putStringSet("binned_ids", binned).apply()
        cachedContacts = null // Invalidate cache
    }

    private fun getBinnedIds(): Set<String> {
        return prefs.getStringSet("binned_ids", emptySet()) ?: emptySet()
    }

    fun updateContact(contactId: String, newName: String, newNumber: String): Boolean {
        val ops = ArrayList<ContentProviderOperation>()

        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            )
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
            .build())

        ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection(
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            )
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newNumber)
            .build())

        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            cachedContacts = null // Invalidate cache
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun addContact(name: String, number: String): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        val rawContactInsertIndex = ops.size

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build())

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build())

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            .build())

        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            cachedContacts = null // Invalidate cache
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteContact(contactId: String): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection("${ContactsContract.RawContacts.CONTACT_ID}=?", arrayOf(contactId))
            .build())

        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            cachedContacts = null // Invalidate cache
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
