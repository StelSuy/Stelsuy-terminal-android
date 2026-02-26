package com.stelsuy.terminal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.stelsuy.terminal.api.ApiClient
import com.stelsuy.terminal.util.Prefs
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * First-launch setup: scan QR code from admin panel to configure
 * server URL, API key, and terminal ID.
 *
 * QR payload (JSON):
 * {
 *   "server": "http://192.168.0.101:8000",
 *   "api_key": "abc123...",
 *   "terminal_id": 1,
 *   "terminal_name": "Вхід_1"
 * }
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var manualBtn: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // AtomicBoolean замість @Volatile — гарантує що два потоки camera executor
    // не пройдуть через перевірку одночасно (compareAndSet є атомарним)
    private val scanned = AtomicBoolean(false)

    companion object {
        private const val CAMERA_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        prefs = Prefs(this)
        previewView = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.setupStatus)
        manualBtn = findViewById(R.id.setupManualBtn)

        manualBtn.setOnClickListener { showManualDialog() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                statusText.text = "Камера заблокована. Введіть дані вручну."
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.getSurfaceProvider()) // замість surfaceProvider
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val scanner = BarcodeScanning.getClient()

            analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !scanned.get()) {
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage, imageProxy.imageInfo.rotationDegrees
                    )

                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                if (barcode.valueType == Barcode.TYPE_TEXT ||
                                    barcode.valueType == Barcode.TYPE_UNKNOWN
                                ) {
                                    val raw = barcode.rawValue ?: continue
                                    if (raw.contains("api_key") && raw.contains("server")) {
                                        if (!scanned.compareAndSet(false, true)) break
                                        runOnUiThread { handleQrPayload(raw) }
                                        break
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleQrPayload(raw: String) {
        try {
            val json = JSONObject(raw)
            val server = json.getString("server")
            val apiKey = json.getString("api_key")
            val terminalId = json.getInt("terminal_id")
            val terminalName = json.optString("terminal_name", "Terminal $terminalId")

            // Show confirmation
            AlertDialog.Builder(this)
                .setTitle("Підтвердіть налаштування")
                .setMessage(
                    "Сервер: $server\n" +
                    "Термінал: $terminalName (ID: $terminalId)\n" +
                    "API-ключ: ${apiKey.take(12)}…"
                )
                .setPositiveButton("Застосувати") { _, _ ->
                    applyConfig(server, apiKey, terminalId)
                }
                .setNegativeButton("Скасувати") { _, _ ->
                    scanned.set(false)  // allow re-scan
                }
                .setCancelable(false)
                .show()

        } catch (e: Exception) {
            statusText.text = "Невірний QR-код ❌\nОчікується JSON з полями server, api_key, terminal_id"
            scanned.set(false)
        }
    }

    private fun applyConfig(server: String, apiKey: String, terminalId: Int) {
        prefs.setBaseUrl(server)
        prefs.setApiKey(apiKey)
        prefs.setTerminalId(terminalId)
        prefs.setTerminalIdStr("T$terminalId")
        prefs.setSetupDone(true)

        ApiClient.reload(this)

        Toast.makeText(this, "Термінал налаштовано ✅", Toast.LENGTH_LONG).show()

        // Go to main
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showManualDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        val labelUrl = TextView(this).apply { text = "Адреса сервера:"; setTextColor(0xFFEAF0FF.toInt()) }
        val inputUrl = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getBaseUrl())
            hint = "192.168.0.101:8000"
        }

        val labelKey = TextView(this).apply { text = "\nAPI-ключ:"; setTextColor(0xFFEAF0FF.toInt()) }
        val inputKey = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getApiKey())
            hint = "Вставте ключ з адмін-панелі"
        }

        val labelId = TextView(this).apply { text = "\nTerminal ID:"; setTextColor(0xFFEAF0FF.toInt()) }
        val inputId = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getTerminalId().toString())
        }

        layout.addView(labelUrl); layout.addView(inputUrl)
        layout.addView(labelKey); layout.addView(inputKey)
        layout.addView(labelId); layout.addView(inputId)

        AlertDialog.Builder(this)
            .setTitle("Ручне налаштування")
            .setView(layout)
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Зберегти") { _, _ ->
                val url = inputUrl.text.toString()
                val key = inputKey.text.toString()
                val tid = inputId.text.toString().toIntOrNull() ?: 1
                if (key.isBlank()) {
                    Toast.makeText(this, "API-ключ обов'язковий!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (url.isBlank()) {
                    Toast.makeText(this, "Адреса сервера обов'язкова!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                applyConfig(url, key, tid)
            }
            .show()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
