package com.voiceassistant.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale

internal enum class SocFamily {
    SNAPDRAGON,
    MEDIATEK,
    OTHER,
}

internal data class DeviceProfile(
    val totalRamMb: Long,
    val memoryClassMb: Int,
    val socModel: String,
    val socFamily: SocFamily,
    val localeLanguage: String,
) {
    val totalRamGb: Double = totalRamMb / 1024.0

    val isGpuStable: Boolean = socFamily == SocFamily.SNAPDRAGON

    companion object {
        fun from(context: Context): DeviceProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memoryInfo)
            val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
            val memoryClassMb = am.memoryClass
            val rawSoc = Build.SOC_MODEL
            val socModel = (if (rawSoc.isNotBlank()) rawSoc else Build.HARDWARE).lowercase(Locale.getDefault())
            val socFamily = when {
                socModel.contains("snapdragon") || socModel.contains("qcom") -> SocFamily.SNAPDRAGON
                socModel.contains("mediatek") || socModel.contains("dimensity") || socModel.contains("mt") -> SocFamily.MEDIATEK
                else -> SocFamily.OTHER
            }
            val localeLanguage = Locale.getDefault().language.lowercase(Locale.getDefault())
            return DeviceProfile(
                totalRamMb = totalRamMb,
                memoryClassMb = memoryClassMb,
                socModel = socModel,
                socFamily = socFamily,
                localeLanguage = localeLanguage,
            )
        }
    }
}
