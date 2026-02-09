package com.stelsuy.terminal.api

import android.content.Context
import com.stelsuy.terminal.util.Prefs
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var apiInstance: TerminalApi? = null
    @Volatile private var lastBaseUrl: String? = null
    @Volatile private var lastApiKey: String? = null

    private fun build(ctx: Context) {
        val prefs = Prefs(ctx)
        val base = prefs.getBaseUrl()
        val apiKey = prefs.getApiKey()

        if (apiInstance != null && retrofit != null && lastBaseUrl == base && lastApiKey == apiKey) return

        // Interceptor додає X-Terminal-Key до кожного запиту
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val key = Prefs(ctx).getApiKey()
            if (key.isNotBlank()) {
                val request = original.newBuilder()
                    .header("X-Terminal-Key", key)
                    .build()
                chain.proceed(request)
            } else {
                chain.proceed(original)
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
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
        lastApiKey = apiKey
    }

    fun api(ctx: Context): TerminalApi {
        build(ctx)
        return apiInstance!!
    }

    fun reload(ctx: Context) {
        retrofit = null
        apiInstance = null
        lastBaseUrl = null
        lastApiKey = null
        build(ctx)
    }

    fun currentBaseUrl(ctx: Context): String {
        build(ctx)
        return lastBaseUrl ?: Prefs(ctx).getBaseUrl()
    }
}
