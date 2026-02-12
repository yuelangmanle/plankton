package com.voiceassistant

import java.security.MessageDigest

private const val DOC_PASSWORD_HASH = "ed0e753f7aacdfbf291c46562c36a712c5dbcc5db28740a26dee2e3b5046618b"

object DocAccessCache {
    @Volatile
    var password: String? = null
        private set

    fun unlock(password: String) {
        this.password = password
    }

    fun clear() {
        password = null
    }
}

fun isDocPasswordValid(input: String): Boolean {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    val hex = buildString(bytes.size * 2) {
        bytes.forEach { append("%02x".format(it)) }
    }
    return hex == DOC_PASSWORD_HASH
}
