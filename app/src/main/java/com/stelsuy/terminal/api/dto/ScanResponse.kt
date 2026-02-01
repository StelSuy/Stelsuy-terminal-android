package com.stelsuy.terminal.api.dto

data class ScanResponse(
    val ok: Boolean,
    val message: String?,
    val employee_id: Int?
)
