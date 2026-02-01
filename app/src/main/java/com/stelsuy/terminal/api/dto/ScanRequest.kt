package com.stelsuy.terminal.api.dto

data class ScanRequest(
    val uid: String,
    val terminal_id: String,
    val direction: String,
    val ts: String
)
