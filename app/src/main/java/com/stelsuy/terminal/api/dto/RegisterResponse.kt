package com.stelsuy.terminal.api.dto

data class RegisterResponse(
    val ok: Boolean,
    val message: String?,
    val employee_id: Int?
)
