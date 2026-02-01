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

    fun getTerminalId(): String = sp.getString("terminal_id", "ENTRANCE_1")!!
    fun setTerminalId(v: String) = sp.edit().putString("terminal_id", v.trim()).apply()

    fun getDirection(): String = sp.getString("direction", "IN")!!
    fun setDirection(v: String) = sp.edit().putString("direction", v.trim()).apply()

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