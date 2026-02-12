package com.plankton.one102.ui

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val DOC_MAGIC = "DOCENC1"
private const val DOC_MAGIC_SIZE = 7
private const val SALT_SIZE = 16
private const val IV_SIZE = 12
private const val TAG_SIZE = 16
private const val PBKDF2_ITERATIONS = 120000
private const val KEY_BITS = 256

private fun deriveKey(password: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    return factory.generateSecret(spec).encoded
}

fun decryptDocument(payload: ByteArray, password: String): String? {
    if (payload.size <= DOC_MAGIC_SIZE + SALT_SIZE + IV_SIZE + TAG_SIZE) return null
    val magic = String(payload, 0, DOC_MAGIC_SIZE, Charsets.US_ASCII)
    if (magic != DOC_MAGIC) return null
    val saltStart = DOC_MAGIC_SIZE
    val ivStart = saltStart + SALT_SIZE
    val tagStart = ivStart + IV_SIZE
    val dataStart = tagStart + TAG_SIZE
    if (payload.size <= dataStart) return null
    val salt = payload.copyOfRange(saltStart, ivStart)
    val iv = payload.copyOfRange(ivStart, tagStart)
    val tag = payload.copyOfRange(tagStart, dataStart)
    val cipherText = payload.copyOfRange(dataStart, payload.size)
    return runCatching {
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        val combined = ByteArray(cipherText.size + tag.size)
        System.arraycopy(cipherText, 0, combined, 0, cipherText.size)
        System.arraycopy(tag, 0, combined, cipherText.size, tag.size)
        val plain = cipher.doFinal(combined)
        String(plain, Charsets.UTF_8)
    }.getOrNull()
}

suspend fun readEncryptedAsset(context: Context, path: String, password: String): String? {
    return runCatching {
        context.assets.open(path).use { input ->
            val bytes = input.readBytes()
            decryptDocument(bytes, password)
        }
    }.getOrNull()
}

fun buildEncryptedPayload(password: String, plain: ByteArray): ByteArray {
    val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
    val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
    val key = deriveKey(password, salt)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val spec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
    val encrypted = cipher.doFinal(plain)
    val cipherText = encrypted.copyOfRange(0, encrypted.size - TAG_SIZE)
    val tag = encrypted.copyOfRange(encrypted.size - TAG_SIZE, encrypted.size)
    val magic = DOC_MAGIC.toByteArray(Charsets.US_ASCII)
    val output = ByteArray(magic.size + salt.size + iv.size + tag.size + cipherText.size)
    var offset = 0
    System.arraycopy(magic, 0, output, offset, magic.size)
    offset += magic.size
    System.arraycopy(salt, 0, output, offset, salt.size)
    offset += salt.size
    System.arraycopy(iv, 0, output, offset, iv.size)
    offset += iv.size
    System.arraycopy(tag, 0, output, offset, tag.size)
    offset += tag.size
    System.arraycopy(cipherText, 0, output, offset, cipherText.size)
    return output
}
