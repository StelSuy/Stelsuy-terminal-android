package com.stelsuy.terminal.api.dto

data class FirstScanRequest(
    val employee_uid: String,
    val terminal_id: String,
    val public_key_b64: String
)
