package com.solisium.core.platform

import java.security.MessageDigest
import java.util.UUID

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
