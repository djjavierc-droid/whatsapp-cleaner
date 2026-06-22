package com.whatsappcleaner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

object ContactsManager {

    private const val WHATSAPP_ACCOUNT_TYPE = "com.whatsapp"

    /**
     * Devuelve todos los contactos de la agenda con su CONTACT_ID de Android.
     */
    fun getAllContacts(context: Context): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val seenIds  = mutableSetOf<Long>()

        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use { c ->
            val nameIdx      = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx       = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val contactIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

            while (c.moveToNext()) {
                val name      = c.getString(nameIdx)?.trim() ?: continue
                val phone     = c.getString(numIdx)  ?: continue
                val contactId = c.getLong(contactIdIdx)

                if (name.isEmpty() || contactId <= 0) continue

                // Un contacto puede tener varios números — guardamos uno por contactId
                if (!seenIds.contains(contactId)) {
                    seenIds.add(contactId)
                    contacts.add(Contact(name = name, phone = phone, contactId = contactId))
                }
            }
        }

        return contacts
    }

    /**
     * Devuelve el conjunto de CONTACT_IDs que WhatsApp tiene sincronizados.
     *
     * WhatsApp crea un RawContact con account_type = "com.whatsapp" para cada
     * contacto que tiene cuenta. Android une ese RawContact con el contacto de
     * la agenda bajo el mismo CONTACT_ID. No hace falta comparar números.
     */
    fun getWhatsAppContactIds(context: Context): Set<Long> {
        val ids = mutableSetOf<Long>()

        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf(WHATSAPP_ACCOUNT_TYPE),
            null
        )

        cursor?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
            while (c.moveToNext()) {
                val id = c.getLong(idx)
                if (id > 0) ids.add(id)
            }
        }

        return ids
    }

    /**
     * Elimina contactos de la agenda por su CONTACT_ID.
     */
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
}
