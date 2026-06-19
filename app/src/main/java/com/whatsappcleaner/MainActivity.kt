package com.whatsappcleaner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: ScanViewModel by viewModels()

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressText: TextView
    private lateinit var btnScan: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var cardResults: CardView
    private lateinit var tvResultCount: TextView
    private lateinit var rvContacts: RecyclerView
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnDeleteSelected: MaterialButton
    private lateinit var btnDeleteAll: MaterialButton

    private val adapter = ContactAdapter { selectedCount ->
        updateSelectionButtons(selectedCount)
    }

    private val requestReadContacts =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.startScan()
            else Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }

    private val requestWriteContacts =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingDeleteAction?.invoke()
            } else {
                Toast.makeText(this, "Permiso de escritura denegado. No se pueden eliminar contactos.", Toast.LENGTH_LONG).show()
            }
            pendingDeleteAction = null
        }

    private var pendingDeleteAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun bindViews() {
        tvStatus          = findViewById(R.id.tvStatus)
        progressBar       = findViewById(R.id.progressBar)
        tvProgressText    = findViewById(R.id.tvProgressText)
        btnScan           = findViewById(R.id.btnScan)
        btnCancel         = findViewById(R.id.btnCancel)
        cardResults       = findViewById(R.id.cardResults)
        tvResultCount     = findViewById(R.id.tvResultCount)
        rvContacts        = findViewById(R.id.rvContacts)
        btnSelectAll      = findViewById(R.id.btnSelectAll)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        btnDeleteAll      = findViewById(R.id.btnDeleteAll)
    }

    private fun setupRecyclerView() {
        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = adapter
    }

    private fun setupClickListeners() {
        btnScan.setOnClickListener { onScanClicked() }
        btnCancel.setOnClickListener { viewModel.cancelScan() }

        btnSelectAll.setOnClickListener {
            if (adapter.getSelectedCount() == adapter.itemCount) {
                adapter.deselectAll()
                btnSelectAll.text = "Marcar todos"
            } else {
                adapter.selectAll()
                btnSelectAll.text = "Desmarcar todos"
            }
        }

        btnDeleteSelected.setOnClickListener {
            val selected = adapter.getSelectedContacts()
            if (selected.isEmpty()) return@setOnClickListener
            showDeleteDialog(
                message = "¿Eliminar ${selected.size} contacto(s) seleccionado(s) de tu agenda?",
                onConfirm = {
                    val ids    = selected.map { it.contactId }
                    val phones = selected.map { it.phone }.toSet()
                    withWritePermission {
                        viewModel.deleteContacts(ids, phones)
                        adapter.clearSelectionFor(phones)
                        btnSelectAll.text = "Marcar todos"
                    }
                }
            )
        }

        btnDeleteAll.setOnClickListener {
            val count = adapter.itemCount
            if (count == 0) return@setOnClickListener
            showDeleteDialog(
                message = "¿Eliminar los $count contactos sin WhatsApp de tu agenda?",
                onConfirm = {
                    val all    = (0 until adapter.itemCount).map { adapter.currentList[it] }
                    val ids    = all.map { it.contactId }
                    val phones = all.map { it.phone }.toSet()
                    withWritePermission {
                        viewModel.deleteContacts(ids, phones)
                        adapter.deselectAll()
                        btnSelectAll.text = "Marcar todos"
                    }
                }
            )
        }
    }

    private fun onScanClicked() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED -> viewModel.startScan()

            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.app_name))
                    .setMessage(getString(R.string.permission_rationale))
                    .setPositiveButton("Continuar") { _, _ ->
                        requestReadContacts.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

            else -> requestReadContacts.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun withWritePermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingDeleteAction = action
            requestWriteContacts.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    private fun showDeleteDialog(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage(message)
            .setPositiveButton("Eliminar") { _, _ -> onConfirm() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateSelectionButtons(selectedCount: Int) {
        if (selectedCount == 0) {
            btnDeleteSelected.isEnabled = false
            btnDeleteSelected.alpha = 0.5f
            btnDeleteSelected.text = "Eliminar (0)"
        } else {
            btnDeleteSelected.isEnabled = true
            btnDeleteSelected.alpha = 1f
            btnDeleteSelected.text = "Eliminar ($selectedCount)"
        }
        if (adapter.itemCount > 0 && selectedCount == adapter.itemCount) {
            btnSelectAll.text = "Desmarcar todos"
        } else {
            btnSelectAll.text = "Marcar todos"
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collect { state -> updateUi(state) }
        }
    }

    private fun updateUi(state: ScanState) {
        when (state.status) {

            ScanStatus.IDLE -> {
                tvStatus.text             = getString(R.string.status_ready)
                progressBar.visibility    = View.GONE
                tvProgressText.visibility = View.GONE
                btnScan.visibility        = View.VISIBLE
                btnScan.text              = getString(R.string.btn_start_scan)
                btnCancel.visibility      = View.GONE
                cardResults.visibility    = View.GONE
            }

            ScanStatus.SCANNING -> {
                val pct = if (state.totalContacts > 0)
                    (state.processedContacts * 100) / state.totalContacts else 0

                tvStatus.text             = getString(R.string.status_scanning)
                progressBar.visibility    = View.VISIBLE
                progressBar.progress      = pct
                tvProgressText.visibility = View.VISIBLE
                tvProgressText.text       = getString(R.string.progress_text,
                    state.processedContacts, state.totalContacts)
                btnScan.visibility        = View.GONE
                btnCancel.visibility      = View.VISIBLE

                if (state.noWhatsAppContacts.isNotEmpty()) {
                    cardResults.visibility = View.VISIBLE
                    tvResultCount.text     = state.noWhatsAppContacts.size.toString()
                    adapter.submitList(state.noWhatsAppContacts.toList())
                }
            }

            ScanStatus.DONE -> {
                tvStatus.text             = getString(R.string.status_done)
                progressBar.visibility    = View.VISIBLE
                progressBar.progress      = 100
                tvProgressText.visibility = View.VISIBLE
                tvProgressText.text       = getString(R.string.progress_text,
                    state.totalContacts, state.totalContacts)
                btnScan.visibility        = View.VISIBLE
                btnScan.text              = getString(R.string.btn_new_scan)
                btnCancel.visibility      = View.GONE

                if (state.noWhatsAppContacts.isEmpty()) {
                    cardResults.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.empty_results), Toast.LENGTH_LONG).show()
                } else {
                    cardResults.visibility = View.VISIBLE
                    tvResultCount.text     = state.noWhatsAppContacts.size.toString()
                    adapter.submitList(state.noWhatsAppContacts.toList())
                }

                btnScan.setOnClickListener {
                    viewModel.reset()
                    btnScan.setOnClickListener { onScanClicked() }
                    onScanClicked()
                }
            }

            ScanStatus.CANCELLED -> {
                tvStatus.text             = getString(R.string.status_cancelled)
                progressBar.visibility    = View.VISIBLE
                tvProgressText.visibility = View.VISIBLE
                tvProgressText.text       = getString(R.string.progress_text,
                    state.processedContacts, state.totalContacts)
                btnScan.visibility        = View.VISIBLE
                btnScan.text              = getString(R.string.btn_start_scan)
                btnCancel.visibility      = View.GONE

                if (state.noWhatsAppContacts.isNotEmpty()) {
                    cardResults.visibility = View.VISIBLE
                    tvResultCount.text     = state.noWhatsAppContacts.size.toString()
                    adapter.submitList(state.noWhatsAppContacts.toList())
                }

                btnScan.setOnClickListener { onScanClicked() }
            }

            ScanStatus.ERROR -> {
                tvStatus.text             = state.errorMessage ?: "Error desconocido"
                progressBar.visibility    = View.GONE
                tvProgressText.visibility = View.GONE
                btnScan.visibility        = View.VISIBLE
                btnScan.text              = getString(R.string.btn_start_scan)
                btnCancel.visibility      = View.GONE
                cardResults.visibility    = View.GONE
                btnScan.setOnClickListener { onScanClicked() }
            }
        }
    }
}
