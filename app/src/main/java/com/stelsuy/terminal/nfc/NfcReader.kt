package com.stelsuy.terminal.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag

/**
 * Утиліти для роботи з NFC-тегами.
 * HCE-специфічна логіка знаходиться в NfcHceReader.
 */
object NfcReader {

    fun extractUidHex(intent: Intent): String? {
        val action = intent.action ?: return null
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) return null

        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return null
        val id = tag.id ?: return null
        return id.joinToString(separator = "") { b -> "%02X".format(b) }
    }
}
