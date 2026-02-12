package com.voiceassistant.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

internal fun readSignatureSha256(context: Context, packageName: String): String? {
    val pm = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }
    val info = runCatching { pm.getPackageInfo(packageName, flags) }.getOrNull() ?: return null
    val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        info.signatures
    } ?: return null
    val sig = signatures.firstOrNull() ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
    return digest.joinToString(":") { b -> "%02X".format(b) }
}
