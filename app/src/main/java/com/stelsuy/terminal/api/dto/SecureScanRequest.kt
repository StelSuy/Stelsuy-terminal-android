package com.stelsuy.terminal.api.dto

data class SecureScanRequest(
    val employee_uid: String,
    val terminal_id: Int,
    val direction: String,
    val ts: Long,
    val challenge_b64: String,
    val signature_b64: String
)
