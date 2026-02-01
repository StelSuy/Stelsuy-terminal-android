package com.stelsuy.terminal.api.dto

data class RegisterRequest(
    val uid: String,
    val code: String,
    val first_name: String,
    val last_name: String,
    val created_by_terminal_id: String
)
