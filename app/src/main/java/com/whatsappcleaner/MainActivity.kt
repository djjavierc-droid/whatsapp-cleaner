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
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // ─── ViewModel ───────────────────────────────────────────────────────────
    private val viewModel: ScanViewModel by viewModels()

    // ─── Vistas ──────────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressText: TextView
    private lateinit var btnScan: Button
    private lateinit var btnCancel: Button
    private lateinit var cardResults: CardView
    private lateinit var tvResultCount: TextView
    private lateinit var rvContacts: RecyclerView

    // ─── Adapter ─────────────────────────────────────────────────────────────
    private val adapter = ContactAdapter()

    // ─── Permiso READ_CONTACTS ────────────────────────────────────────────────
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startScan()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    // ─── Inicialización ───────────────────────────────────────────────────────

    private fun bindViews() {
        tvStatus      = findViewById(R.id.tvStatus)
        progressBar   = findViewById(R.id.progressBar)
        tvProgressText = findViewById(R.id.tvProgressText)
        btnScan       = findViewById(R.id.btnScan)
        btnCancel     = findViewById(R.id.btnCancel)
        cardResults   = findViewById(R.id.cardResults)
        tvResultCount = findViewById(R.id.tvResultCount)
        rvContacts    = findViewById(R.id.rvContacts)
    }

    private fun setupRecyclerView() {
        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = adapter
    }

    private fun setupClickListeners() {
        btnScan.setOnClickListener   { onScanClicked() }
        btnCancel.setOnClickListener { viewModel.cancelScan() }
    }

    // ─── Lógica de permisos y escaneo ─────────────────────────────────────────

    private fun onScanClicked() {
        when {
            // Permiso ya concedido → iniciar escaneo directamente
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED -> {
                viewModel.startScan()
            }
            // Mostrar diálogo explicativo antes de pedir permiso
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.app_name))
                    .setMessage(getString(R.string.permission_rationale))
                    .setPositiveButton("Continuar") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            // Solicitar permiso directamente
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    // ─── Observar cambios del ViewModel ──────────────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: ScanState) {
        when (state.status) {

            ScanStatus.IDLE -> {
                tvStatus.text       = getString(R.string.status_ready)
                progressBar.visibility   = View.GONE
                tvProgressText.visibility = View.GONE
                btnScan.visibility   = View.VISIBLE
                btnScan.text         = getString(R.string.btn_start_scan)
                btnCancel.visibility = View.GONE
                cardResults.visibility = View.GONE
            }

            ScanStatus.SCANNING -> {
                val pct = if (state.totalContacts > 0)
                    (state.processedContacts * 100) / state.totalContacts
                else 0

                tvStatus.text = getString(R.string.status_scanning)

                progressBar.visibility = View.VISIBLE
                progressBar.progress   = pct

                tvProgressText.visibility = View.VISIBLE
                tvProgressText.text = getString(
                    R.string.progress_text,
                    state.processedContacts,
                    state.totalContacts
                )

                btnScan.visibility   = View.GONE
                btnCancel.visibility = View.VISIBLE

                // Mostrar resultados parciales en tiempo real
                if (state.noWhatsAppContacts.isNotEmpty()) {
                    cardResults.visibility = View.VISIBLE
                    tvResultCount.text     = state.noWhatsAppContacts.size.toString()
                    adapter.submitList(state.noWhatsAppContacts.toList())
                }
            }

            ScanStatus.DONE -> {
                tvStatus.text = getString(R.string.status_done)

                progressBar.visibility    = View.VISIBLE
                progressBar.progress      = 100
                tvProgressText.visibility = View.VISIBLE
                tvProgressText.text       = getString(
                    R.string.progress_text,
                    state.totalContacts,
                    state.totalContacts
                )

                btnScan.visibility   = View.VISIBLE
                btnScan.text         = getString(R.string.btn_new_scan)
                btnCancel.visibility = View.GONE

                if (state.noWhatsAppContacts.isEmpty()) {
                    cardResults.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.empty_results), Toast.LENGTH_LONG).show()
                } else {
                    cardResults.visibility = View.VISIBLE
                    tvResultCount.text     = state.noWhatsAppContacts.size.toString()
                    adapter.submitList(state.noWhatsAppContacts.toList())
                }

                // Nuevo escaneo
                btnScan.setOnClickListener {
                    viewModel.reset()
                    btnScan.setOnClickListener { onScanClicked() }
                    onScanClicked()
                }
            }

            ScanStatus.CANCELLED -> {
                tvStatus.text = getString(R.string.status_cancelled)

                progressBar.visibility    = View.VISIBLE
                tvProgressText.visibility = View.VISIBLE
                tvProgressText.text       = getString(
                    R.string.progress_text,
                    state.processedContacts,
                    state.totalContacts
                )

                btnScan.visibility   = View.VISIBLE
                btnScan.text         = getString(R.string.btn_start_scan)
                btnCancel.visibility = View.GONE

                if (state.noWhatsAppContacts.isNotEmpty()) {
                    cardResults.visibility = View.VISIBLE
                    tvResultCount.text     = state.noWhatsAppContacts.size.toString()
                    adapter.submitList(state.noWhatsAppContacts.toList())
                }

                btnScan.setOnClickListener { onScanClicked() }
            }

            ScanStatus.ERROR -> {
                tvStatus.text = state.errorMessage ?: "Error desconocido"

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
