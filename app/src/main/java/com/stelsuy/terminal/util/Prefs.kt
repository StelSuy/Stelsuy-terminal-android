package com.stelsuy.terminal.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)

    private val DEFAULT_BASE_URL = "http://192.168.0.101:8000"

    fun getBaseUrl(): String = normalizeBaseUrl(
        sp.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    )

    fun setBaseUrl(v: String) {
        sp.edit().putString("base_url", normalizeBaseUrl(v)).apply()
    }

    fun getTerminalId(): Int = sp.getInt("terminal_id_int", 1)
    fun setTerminalId(v: Int) = sp.edit().putInt("terminal_id_int", v).apply()

    fun getTerminalIdStr(): String = sp.getString("terminal_id_str", "T1")!!
    fun setTerminalIdStr(v: String) = sp.edit().putString("terminal_id_str", v.trim()).apply()

    fun getApiKey(): String = sp.getString("api_key", "")!!
    fun setApiKey(v: String) = sp.edit().putString("api_key", v.trim()).apply()

    fun getDirection(): String = sp.getString("direction", "IN")!!
    fun setDirection(v: String) = sp.edit().putString("direction", v.trim()).apply()

    fun isSetupDone(): Boolean = sp.getBoolean("setup_done", false)
    fun setSetupDone(v: Boolean) = sp.edit().putBoolean("setup_done", v).apply()

    private fun normalizeBaseUrl(input: String): String {
        var s = input.trim()

        if (s.isEmpty()) s = DEFAULT_BASE_URL

        // якщо ввели лише IP/host — додаємо http://
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://$s"
        }

        // прибираємо всі "/" в кінці
        while (s.endsWith("/")) s = s.dropLast(1)

        return s
    }
}