package com.voiceassistant.ui

internal enum class VoiceEntryMode(val label: String, val description: String) {
    QUICK_SEND("快速发送", "转写完成后优先使用结果，仍由接收 App 的预览确认写入"),
    REVIEW_BEFORE_SEND("核对后发送", "先编辑文本和查看任务结果，再发送给接收 App"),
}
