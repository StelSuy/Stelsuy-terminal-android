package com.stelsuy.terminal.api

import com.stelsuy.terminal.api.dto.*
import retrofit2.http.Body
import retrofit2.http.POST

interface TerminalApi {

    @POST("api/register/first-scan")
    suspend fun firstScan(@Body body: FirstScanRequest): FirstScanResponse

    @POST("api/terminal/secure-scan")
    suspend fun secureScan(@Body body: SecureScanRequest): SecureScanResponse
}
