package com.voiceassistant.data

internal enum class ProcessingMode {
    QUEUE,
    PARALLEL,
}

internal fun ProcessingMode.maxParallel(
    deviceProfile: DeviceProfile? = null,
    autoStrategy: Boolean = false,
): Int {
    val base = if (this == ProcessingMode.PARALLEL) 2 else 1
    if (!autoStrategy || deviceProfile == null) return base
    if (deviceProfile.memoryClassMb < 512 || deviceProfile.totalRamGb < 8) return 1
    return base
}
