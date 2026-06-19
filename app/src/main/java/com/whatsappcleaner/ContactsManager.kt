package com.whatsappcleaner

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

/**
 * ContactsManager
 *
 * Lee todos los contactos de la agenda del dispositivo y determina cuáles
 * NO tienen cuenta de WhatsApp.
 *
 * Estrategia de detección (sin enviar mensajes):
 * ─────────────────────────────────────────────
 * Cuando WhatsApp está instalado, sincroniza tu agenda y marca los contactos
 * que SÍ tienen cuenta de WhatsApp creando entradas de tipo RawContact con
 * account_type = "com.whatsapp".
 *
 * El proceso:
 *   1. Leer TODOS los números de la agenda (ContactsContract.CommonDataKinds.Phone).
 *   2. Obtener el conjunto de números normalizados que WhatsApp conoce
 *      (RawContacts con account_type = "com.whatsapp").
 *   3. La diferencia entre los dos conjuntos = contactos sin WhatsApp.
 */
object ContactsManager {

    /** Cuenta de sincronización que usa WhatsApp en Android */
    private const val WHATSAPP_ACCOUNT_TYPE = "com.whatsapp"

    /**
     * Lee todos los contactos de la agenda.
     * Devuelve una lista de [Contact] (nombre + número limpio).
     */
    fun getAllContacts(context: Context): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val seen = mutableSetOf<String>() // evitar duplicados por mismo número

        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val normIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            val rawIdx  = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (c.moveToNext()) {
                val name = c.getString(nameIdx)?.trim() ?: continue
                if (name.isEmpty()) continue

                // Preferir número normalizado (E.164), caer en el número raw
                val rawNumber = c.getString(rawIdx) ?: continue
                val normalized = c.getString(normIdx) ?: normalizeManual(rawNumber)
                val key = normalized.ifEmpty { normalizeManual(rawNumber) }

                if (key.isNotEmpty() && !seen.contains(key)) {
                    seen.add(key)
                    contacts.add(Contact(name = name, phone = key))
                }
            }
        }

        return contacts
    }

    /**
     * Devuelve el conjunto de números normalizados que WhatsApp tiene
     * sincronizados en este dispositivo.
     *
     * Consulta RawContacts filtrados por account_type = "com.whatsapp" y
     * luego resuelve el número a través de Data/Phone.
     */
    fun getWhatsAppNumbers(context: Context): Set<String> {
        val whatsappNumbers = mutableSetOf<String>()

        // Paso 1: IDs de raw_contacts que pertenecen a la cuenta de WhatsApp
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
            while (c.moveToNext()) {
                rawContactIds.add(c.getLong(idIdx))
            }
        }

        if (rawContactIds.isEmpty()) return emptySet()

        // Paso 2: Para cada raw_contact de WhatsApp, obtener su número
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
                val raw = c.getString(rawIdx) ?: ""
                val key = normalized.ifEmpty { normalizeManual(raw) }
                if (key.isNotEmpty()) {
                    whatsappNumbers.add(key)
                }
            }
        }

        return whatsappNumbers
    }

    /**
     * Verifica si WhatsApp está instalado en el dispositivo.
     * Si no está instalado no hay datos de sincronización y el análisis
     * no tiene sentido.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.whatsapp", 0)
            true
        } catch (_: Exception) {
            try {
                context.packageManager.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Normalización manual cuando el sistema no provee E.164 */
    private fun normalizeManual(raw: String): String {
        return raw.replace(Regex("[^0-9+]"), "")
            .let { if (it.startsWith("+")) it else it.replace(Regex("^0+"), "") }
    }
}
