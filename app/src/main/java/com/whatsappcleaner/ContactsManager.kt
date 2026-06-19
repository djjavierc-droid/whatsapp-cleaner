package com.whatsappcleaner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

object ContactsManager {

    private const val WHATSAPP_ACCOUNT_TYPE = "com.whatsapp"

    fun getAllContacts(context: Context): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val seen = mutableSetOf<String>()

        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use { c ->
            val nameIdx      = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val normIdx      = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            val rawIdx       = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val contactIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

            while (c.moveToNext()) {
                val name = c.getString(nameIdx)?.trim() ?: continue
                if (name.isEmpty()) continue

                val rawNumber  = c.getString(rawIdx) ?: continue
                val normalized = c.getString(normIdx) ?: normalizeManual(rawNumber)
                val key        = normalized.ifEmpty { normalizeManual(rawNumber) }
                val contactId  = c.getLong(contactIdIdx)

                if (key.isNotEmpty() && !seen.contains(key)) {
                    seen.add(key)
                    contacts.add(Contact(name = name, phone = key, contactId = contactId))
                }
            }
        }

        return contacts
    }

    fun getWhatsAppNumbers(context: Context): Set<String> {
        val whatsappNumbers = mutableSetOf<String>()

        val rawContactIds = mutableSetOf<Long>()
        val rawCursor: Cursor? = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf(WHATSAPP_ACCOUNT_TYPE),
            null
        )
        rawCursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.RawContacts._ID)
            while (c.moveToNext()) rawContactIds.add(c.getLong(idIdx))
        }

        if (rawContactIds.isEmpty()) return emptySet()

        val idList = rawContactIds.joinToString(",")
        val dataCursor: Cursor? = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} IN ($idList)" +
                    " AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
            null
        )

        dataCursor?.use { c ->
            val normIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            val rawIdx  = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val normalized = c.getString(normIdx) ?: ""
                val raw        = c.getString(rawIdx) ?: ""
                val key        = normalized.ifEmpty { normalizeManual(raw) }
                if (key.isNotEmpty()) whatsappNumbers.add(key)
            }
        }

        return whatsappNumbers
    }

    fun deleteContacts(context: Context, contactIds: List<Long>) {
        for (id in contactIds) {
            if (id > 0) {
                val uri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, id
                )
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    fun isWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.whatsapp", 0); true
        } catch (_: Exception) {
            try { context.packageManager.getPackageInfo("com.whatsapp.w4b", 0); true }
            catch (_: Exception) { false }
        }
    }

    private fun normalizeManual(raw: String): String =
        raw.replace(Regex("[^0-9+]"), "")
            .let { if (it.startsWith("+")) it else it.replace(Regex("^0+"), "") }
}
