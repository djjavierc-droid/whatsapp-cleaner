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

            // Cargar datos en hilo de IO
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

            val whatsappNumbers = withContext(Dispatchers.IO) {
                ContactsManager.getWhatsAppNumbers(context)
            }

            Log.d(TAG, "Total: ${allContacts.size}, WhatsApp: ${whatsappNumbers.size}")

            _state.value = ScanState(
                status = ScanStatus.SCANNING,
                totalContacts = allContacts.size,
                processedContacts = 0,
                noWhatsAppContacts = emptyList()
            )

            // Procesar en hilo de CPU para no bloquear la UI
            val noWaList = withContext(Dispatchers.Default) {
                val result = mutableListOf<Contact>()
                for ((index, contact) in allContacts.withIndex()) {
                    if (!isActive) break

                    val hasWhatsApp = whatsappNumbers.any { waNumber ->
                        numbersMatch(contact.phone, waNumber)
                    }
                    if (!hasWhatsApp) result.add(contact)

                    // Actualizar UI cada 30 contactos (no en cada iteración)
                    if ((index + 1) % 30 == 0 || index == allContacts.size - 1) {
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

    private fun numbersMatch(a: String, b: String): Boolean {
        if (a == b) return true
        val cleanA = a.replace(Regex("[^0-9]"), "")
        val cleanB = b.replace(Regex("[^0-9]"), "")
        if (cleanA == cleanB) return true
        val suffixLen = 9
        if (cleanA.length >= suffixLen && cleanB.length >= suffixLen) {
            return cleanA.takeLast(suffixLen) == cleanB.takeLast(suffixLen)
        }
        return false
    }
}
