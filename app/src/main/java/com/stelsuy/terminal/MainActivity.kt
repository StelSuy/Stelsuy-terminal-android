package com.stelsuy.terminal

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.stelsuy.terminal.api.ApiClient
import com.stelsuy.terminal.api.dto.FirstScanRequest
import com.stelsuy.terminal.api.dto.SecureScanRequest
import com.stelsuy.terminal.nfc.NfcHceReader
import com.stelsuy.terminal.util.Prefs
import com.stelsuy.terminal.util.VoiceMessage
import com.stelsuy.terminal.util.VoicePlayer
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusText: TextView
    private lateinit var registerModeSwitch: Switch
    private lateinit var titleView: TextView

    private lateinit var voicePlayer: VoicePlayer
    private lateinit var prefs: Prefs

    private var lastReadAt = 0L
    private val READ_COOLDOWN_MS = 1500L

    // Вібрація: коротка для успіху, подвійна для помилки
    private fun vibrateSuccess() = vibrate(longArrayOf(0, 100))
    private fun vibrateError() = vibrate(longArrayOf(0, 80, 100, 80))

    private fun vibrate(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }

    // ===== Auto clear logs after idle =====
    private val CLEAR_LOG_DELAY_MS = 15_000L
    private val clearLogHandler = Handler(Looper.getMainLooper())
    private val clearLogRunnable = Runnable {
        statusText.text = "Очікування сканування…"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Якщо setup не пройдено — перенаправляємо на QR-сканер
        val prefsCheck = Prefs(this)
        if (!prefsCheck.isSetupDone()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        registerModeSwitch = findViewById(R.id.registerModeSwitch)
        titleView = findViewById(R.id.title)

        prefs = Prefs(this)
        voicePlayer = VoicePlayer(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Long tap по заголовку = налаштування API
        titleView.setOnLongClickListener {
            showApiDialog()
            true
        }

        logUi("Термінал запущено ✅")
        logUi("API: ${ApiClient.currentBaseUrl(this)}")
        logUi("Режим: ${modeNameUa()}")

        registerModeSwitch.setOnCheckedChangeListener { _, _ ->
            logUi("Режим: ${modeNameUa()}")
        }

        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()

        // повертаємо автоочистку, якщо вже є текст
        clearLogHandler.removeCallbacks(clearLogRunnable)
        clearLogHandler.postDelayed(clearLogRunnable, CLEAR_LOG_DELAY_MS)
    }

    override fun onPause() {
        // зупиняємо автоочистку
        clearLogHandler.removeCallbacks(clearLogRunnable)
        disableForegroundDispatch()
        super.onPause()
    }

    override fun onDestroy() {
        clearLogHandler.removeCallbacks(clearLogRunnable)
        try { voicePlayer.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        val action = intent.action ?: return
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) return

        val now = System.currentTimeMillis()
        if (now - lastReadAt < READ_COOLDOWN_MS) {
            voicePlayer.play(VoiceMessage.PLEASE_WAIT)
            logUi("Зачекайте, будь ласка…")
            return
        }
        lastReadAt = now

        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag == null) {
            voicePlayer.play(VoiceMessage.SCAN_ERROR)
            logUi("Помилка сканування ❌")
            return
        }

        if (IsoDep.get(tag) == null) {
            voicePlayer.play(VoiceMessage.SCAN_ERROR)
            logUi("Непідтримувана NFC-мітка ❌")
            return
        }

        // 1) EMP UID
        val empRes = NfcHceReader.readEmp(tag)
        if (!empRes.ok) {
            voicePlayer.play(VoiceMessage.SCAN_ERROR)
            logUi("Помилка сканування ❌")
            return
        }

        val empRaw = empRes.value ?: ""
        if (empRaw == "DISABLED") {
            voicePlayer.play(VoiceMessage.ACCESS_DENIED)
            logUi("Доступ заборонено ⛔")
            return
        }

        val employeeUid = empRaw.removePrefix("EMP:").trim()
        if (employeeUid.isBlank()) {
            voicePlayer.play(VoiceMessage.SCAN_ERROR)
            logUi("Помилка сканування ❌")
            return
        }

        // === РЕЄСТРАЦІЯ (перший скан) ===
        if (isRegisterMode()) {
            val pubRes = NfcHceReader.readPub(tag)
            if (!pubRes.ok) {
                voicePlayer.play(VoiceMessage.SCAN_ERROR)
                logUi("Помилка сканування ❌")
                return
            }

            val pubRaw = pubRes.value ?: ""
            if (pubRaw == "DISABLED") {
                voicePlayer.play(VoiceMessage.ACCESS_DENIED)
                logUi("Доступ заборонено ⛔")
                return
            }

            if (!pubRaw.startsWith("PUB:")) {
                voicePlayer.play(VoiceMessage.SCAN_ERROR)
                logUi("Помилка сканування ❌")
                return
            }

            val publicKeyB64 = pubRaw.removePrefix("PUB:").trim()
            if (publicKeyB64.isBlank()) {
                voicePlayer.play(VoiceMessage.SCAN_ERROR)
                logUi("Помилка сканування ❌")
                return
            }

            lifecycleScope.launch {
                try {
                    ApiClient.api(this@MainActivity).firstScan(
                        FirstScanRequest(
                            employee_uid = employeeUid,
                            terminal_id = prefs.getTerminalIdStr(),
                            public_key_b64 = publicKeyB64
                        )
                    )

                    vibrateSuccess()
                    voicePlayer.play(VoiceMessage.SCAN_SUCCESS, VoiceMessage.ACCESS_GRANTED)
                    logUi("Скан успішний ✅ (реєстрація)")

                } catch (_: Exception) {
                    vibrateError()
                    voicePlayer.play(VoiceMessage.NO_CONNECTION)
                    logUi("Немає зʼєднання з сервером ❌")
                }
            }
            return
        }

        // === ЗАХИЩЕНИЙ СКАН ===
        val direction = prefs.getDirection()
        val ts = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                // 1) Отримуємо server-side challenge
                val challengeResp = ApiClient.api(this@MainActivity).getChallenge(
                    com.stelsuy.terminal.api.dto.ChallengeRequest(
                        terminal_id = prefs.getTerminalId()
                    )
                )
                val challengeB64 = challengeResp.challenge_b64
                val challenge = NfcHceReader.fromB64(challengeB64)

                // 2) Підписуємо challenge телефоном через NFC
                val sigRes = NfcHceReader.signChallenge(tag, challenge)
                if (!sigRes.ok) {
                    voicePlayer.play(VoiceMessage.SCAN_ERROR)
                    logUi("Помилка підпису ❌")
                    return@launch
                }

                val signatureB64 = (sigRes.value ?: "").trim()
                if (signatureB64 == "DISABLED") {
                    voicePlayer.play(VoiceMessage.ACCESS_DENIED)
                    logUi("Доступ заборонено ⛔")
                    return@launch
                }

                // 3) Відправляємо secure-scan
                val resp = ApiClient.api(this@MainActivity).secureScan(
                    SecureScanRequest(
                        employee_uid = employeeUid,
                        terminal_id = prefs.getTerminalId(),
                        direction = direction,
                        ts = ts,
                        challenge_b64 = challengeB64,
                        signature_b64 = signatureB64
                    )
                )

                val ok = (resp.ok == true)
                val msgLower = (resp.message ?: "").lowercase()

                val isCooldown = msgLower.contains("cooldown") ||
                        msgLower.contains("too fast") ||
                        msgLower.contains("зачекайте") ||
                        msgLower.contains("wait")

                if (isCooldown) {
                    voicePlayer.play(VoiceMessage.PLEASE_WAIT)
                    logUi("Зачекайте, будь ласка…")
                    return@launch
                }

                if (ok) {
                    vibrateSuccess()
                    voicePlayer.play(VoiceMessage.SCAN_SUCCESS, VoiceMessage.ACCESS_GRANTED)
                    val name = resp.employee_name ?: ""
                    val dir = if (resp.direction == "IN") "⬆️ ВХІД" else "⬇️ ВИХІД"
                    logUi("Скан успішний ✅")
                    if (name.isNotBlank()) logUi("$dir — $name")
                } else {
                    vibrateError()
                    voicePlayer.play(VoiceMessage.SCAN_ERROR, VoiceMessage.ACCESS_DENIED)
                    logUi("Доступ заборонено ⛔")
                }

            } catch (_: Exception) {
                vibrateError()
                voicePlayer.play(VoiceMessage.NO_CONNECTION)
                logUi("Немає зʼєднання з сервером ❌")
            }
        }
    }

    private fun enableForegroundDispatch() {
        val adapter = nfcAdapter ?: return

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )

        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
        val techLists = arrayOf(arrayOf(IsoDep::class.java.name))

        adapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)
    }

    private fun disableForegroundDispatch() {
        try { nfcAdapter?.disableForegroundDispatch(this) } catch (_: Exception) {}
    }

    private fun isRegisterMode(): Boolean = registerModeSwitch.isChecked

    private fun modeNameUa(): String =
        if (isRegisterMode()) "РЕЄСТРАЦІЯ" else "СКАНУВАННЯ"

    // ===== Мінімальний лог + автоочистка через 15 сек =====
    private fun logUi(line: String) {
        clearLogHandler.removeCallbacks(clearLogRunnable)

        val prevLines = (statusText.text?.toString() ?: "")
            .split("\n")
            .filter { it.isNotBlank() && it != "Очікування сканування…" }
            .takeLast(9)

        statusText.text = (prevLines + line).joinToString("\n")

        clearLogHandler.postDelayed(clearLogRunnable, CLEAR_LOG_DELAY_MS)
    }

    // ===== Діалог налаштувань =====
    private fun showApiDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        val labelUrl = TextView(this).apply { text = "Адреса сервера:" }
        val inputUrl = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getBaseUrl())
            hint = "192.168.0.101:8000"
        }

        val labelKey = TextView(this).apply { text = "\nAPI-ключ терміналу (X-Terminal-Key):" }
        val inputKey = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getApiKey())
            hint = "Вставте ключ з адмін-панелі"
        }

        val labelId = TextView(this).apply { text = "\nTerminal ID (число):" }
        val inputId = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getTerminalId().toString())
        }

        layout.addView(labelUrl)
        layout.addView(inputUrl)
        layout.addView(labelKey)
        layout.addView(inputKey)
        layout.addView(labelId)
        layout.addView(inputId)

        AlertDialog.Builder(this)
            .setTitle("Налаштування терміналу")
            .setView(layout)
            .setNegativeButton("Скасувати", null)
            .setNeutralButton("Скинути конфігурацію") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Скинути конфігурацію?")
                    .setMessage("Термінал повернеться до екрану QR-сканування.")
                    .setPositiveButton("Так") { _, _ ->
                        prefs.setSetupDone(false)
                        startActivity(Intent(this, SetupActivity::class.java))
                        finish()
                    }
                    .setNegativeButton("Ні", null)
                    .show()
            }
            .setPositiveButton("Зберегти") { _, _ ->
                prefs.setBaseUrl(inputUrl.text.toString())
                prefs.setApiKey(inputKey.text.toString())
                val tid = inputId.text.toString().toIntOrNull() ?: 1
                prefs.setTerminalId(tid)
                prefs.setTerminalIdStr("T$tid")
                ApiClient.reload(this)
                logUi("API: ${ApiClient.currentBaseUrl(this)}")
                logUi("Terminal ID: $tid")
                logUi("API-ключ: ${if (prefs.getApiKey().isNotBlank()) "встановлено ✅" else "не задано ⚠️"}")
                Toast.makeText(this, "Збережено ✅", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}