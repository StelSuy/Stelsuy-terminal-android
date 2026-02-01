package com.stelsuy.terminal.util

import com.stelsuy.terminal.R

enum class VoiceMessage(val resId: Int) {
    SCAN_SUCCESS(R.raw.voice_scan_success),
    ACCESS_GRANTED(R.raw.voice_access_granted),

    SCAN_ERROR(R.raw.voice_scan_error),
    ACCESS_DENIED(R.raw.voice_access_denied),

    PLEASE_WAIT(R.raw.voice_please_wait),
    NO_CONNECTION(R.raw.voice_no_connection)
}