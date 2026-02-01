package com.stelsuy.terminal.api.dto

data class FirstScanResponse(
    val ok: Boolean,
    val status: String,
    val employee_id: Int?
)
