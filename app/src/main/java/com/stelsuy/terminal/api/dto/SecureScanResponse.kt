package com.stelsuy.terminal.api.dto

data class SecureScanResponse(
    val ok: Boolean,
    val message: String,
    val employee_id: Int?
)
