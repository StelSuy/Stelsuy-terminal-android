package com.stelsuy.terminal.api

import android.content.Context
import com.stelsuy.terminal.util.Prefs
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var apiInstance: TerminalApi? = null
    @Volatile private var lastBaseUrl: String? = null

    private fun build(ctx: Context) {
        val base = Prefs(ctx).getBaseUrl()
        if (apiInstance != null && retrofit != null && lastBaseUrl == base) return

        val client = OkHttpClient.Builder()
            .build()

        val r = Retrofit.Builder()
            // Retrofit вимагає "/" у кінці
            .baseUrl("$base/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit = r
        apiInstance = r.create(TerminalApi::class.java)
        lastBaseUrl = base
    }

    fun api(ctx: Context): TerminalApi {
        build(ctx)
        return apiInstance!!
    }

    fun reload(ctx: Context) {
        retrofit = null
        apiInstance = null
        lastBaseUrl = null
        build(ctx)
    }

    fun currentBaseUrl(ctx: Context): String {
        build(ctx)
        return lastBaseUrl ?: Prefs(ctx).getBaseUrl()
    }
}