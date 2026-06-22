package com.whatsappcleaner

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ScanViewModel"

data class ScanState(
    val status: ScanStatus = ScanStatus.IDLE,
    val totalContacts: Int = 0,
    val processedContacts: Int = 0,
    val noWhatsAppContacts: List<Contact> = emptyList(),
    val errorMessage: String? = null
)

enum class ScanStatus { IDLE, SCANNING, DONE, CANCELLED, ERROR }

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    private var scanJob: Job? = null

    fun startScan() {
        if (_state.value.status == ScanStatus.SCANNING) return

        scanJob = viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            if (!ContactsManager.isWhatsAppInstalled(context)) {
                _state.value = ScanState(
                    status = ScanStatus.ERROR,
                    errorMessage = context.getString(R.string.status_no_whatsapp)
                )
                return@launch
            }

            val allContacts = withContext(Dispatchers.IO) {
                ContactsManager.getAllContacts(context)
            }

            if (allContacts.isEmpty()) {
                _state.value = ScanState(
                    status = ScanStatus.ERROR,
                    errorMessage = context.getString(R.string.status_no_contacts)
                )
                return@launch
            }

            // Obtener los CONTACT_IDs que WhatsApp tiene sincronizados.
            // Comparar por ID es 100% preciso — no depende del formato del número.
            val whatsappIds: Set<Long> = withContext(Dispatchers.IO) {
                ContactsManager.getWhatsAppContactIds(context)
            }

            Log.d(TAG, "Total agenda: ${allContacts.size} | Con WhatsApp: ${whatsappIds.size}")

            _state.value = ScanState(
                status = ScanStatus.SCANNING,
                totalContacts = allContacts.size,
                processedContacts = 0,
                noWhatsAppContacts = emptyList()
            )

            // Filtrar en hilo de CPU — O(1) por contacto gracias al HashSet
            val noWaList: List<Contact> = withContext(Dispatchers.Default) {
                val result = mutableListOf<Contact>()
                for ((index, contact) in allContacts.withIndex()) {
                    if (!isActive) break

                    if (!whatsappIds.contains(contact.contactId)) {
                        result.add(contact)
                    }

                    // Actualizar UI cada 50 contactos para no saturar el hilo principal
                    if ((index + 1) % 50 == 0 || index == allContacts.size - 1) {
                        val snapshot = result.toList()
                        withContext(Dispatchers.Main) {
                            _state.value = _state.value.copy(
                                processedContacts = index + 1,
                                noWhatsAppContacts = snapshot
                            )
                        }
                    }
                }
                result.toList()
            }

            if (isActive) {
                _state.value = _state.value.copy(
                    status = ScanStatus.DONE,
                    noWhatsAppContacts = noWaList
                )
                Log.d(TAG, "Completado. Sin WhatsApp: ${noWaList.size}")
            } else {
                _state.value = _state.value.copy(status = ScanStatus.CANCELLED)
            }
        }
    }

    fun deleteContacts(contactIds: List<Long>, phones: Set<String>) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            withContext(Dispatchers.IO) {
                ContactsManager.deleteContacts(context, contactIds)
            }
            val updated = _state.value.noWhatsAppContacts.filter { it.phone !in phones }
            _state.value = _state.value.copy(
                noWhatsAppContacts = updated,
                totalContacts = _state.value.totalContacts - contactIds.size
            )
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.value = _state.value.copy(status = ScanStatus.CANCELLED)
    }

    fun reset() {
        scanJob?.cancel()
        _state.value = ScanState()
    }
}
