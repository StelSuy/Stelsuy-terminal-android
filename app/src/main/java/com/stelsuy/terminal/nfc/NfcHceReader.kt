package com.stelsuy.terminal.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Base64
import java.nio.charset.Charset

object NfcHceReader {

    data class ReadResult(val ok: Boolean, val value: String? = null, val error: String? = null)

    private const val AID = "F0010203040506"

    private val swOk = byteArrayOf(0x90.toByte(), 0x00.toByte())

    fun readEmp(tag: Tag): ReadResult = transceiveText(tag, cmd = hex("00CA000000"))
    fun readPub(tag: Tag): ReadResult = transceiveText(tag, cmd = hex("00CC000000"))

    fun signChallenge(tag: Tag, challenge: ByteArray): ReadResult {
        val cmd = byteArrayOf(0x00, 0xCB.toByte()) + challenge
        return transceiveText(tag, cmd)
    }

    fun toB64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    // URL_SAFE потрібен бо сервер використовує secrets.token_urlsafe() (символи - і _)
    fun fromB64(s: String): ByteArray = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP)

    private fun transceiveText(tag: Tag, cmd: ByteArray): ReadResult {
        val iso = IsoDep.get(tag) ?: return ReadResult(false, error = "IsoDep not supported")
        return try {
            iso.connect()
            iso.timeout = 7000

            // SELECT AID
            val select = buildSelectApdu(AID)
            val r1 = iso.transceive(select)
            if (!endsWith9000(r1)) return ReadResult(false, error = "SELECT failed: ${toHex(r1)}")

            // CMD
            val r2 = iso.transceive(cmd)
            if (!endsWith9000(r2)) return ReadResult(false, error = "CMD failed: ${toHex(r2)}")

            val payload = r2.copyOfRange(0, r2.size - 2)
            val text = payload.toString(Charset.forName("UTF-8"))
            ReadResult(true, value = text)
        } catch (e: Exception) {
            ReadResult(false, error = e.message ?: "Unknown")
        } finally {
            try { iso.close() } catch (_: Exception) {}
        }
    }

    private fun buildSelectApdu(aidHex: String): ByteArray {
        val aid = hex(aidHex)
        val header = hex("00A40400")
        val lc = byteArrayOf(aid.size.toByte())
        val le = hex("00")
        return header + lc + aid + le
    }

    private fun endsWith9000(resp: ByteArray): Boolean {
        if (resp.size < 2) return false
        return resp[resp.size - 2] == swOk[0] && resp[resp.size - 1] == swOk[1]
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val idx = i * 2
            out[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
        }
        return out
    }

    private fun toHex(b: ByteArray): String = b.joinToString("") { "%02X".format(it) }
}
