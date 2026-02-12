package com.voiceassistant.ui

internal data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val date: String,
    val notes: List<String>,
)

internal object AppInfo {
    val releases = listOf(
        AppRelease(
            versionName = "3.2",
            versionCode = 32,
            date = "2026-02-12",
            notes = listOf(
                "修复：紧凑 UI 模式下核心录音操作被误折叠的问题",
                "优化：紧凑模式与高级参数折叠逻辑，确保核心流程可用",
            ),
        ),
        AppRelease(
            versionName = "3.1",
            versionCode = 31,
            date = "2026-02-11",
            notes = listOf(
                "修复：紧凑 UI 切换后高级参数默认折叠，切换效果可感知",
                "优化：紧凑模式下间距与卡片内边距同步收敛，减少滚动负担",
            ),
        ),
        AppRelease(
            versionName = "3.0",
            versionCode = 30,
            date = "2026-02-06",
            notes = listOf(
                "版本规范：主版本从 2.9 正式递增到 3.0（修正此前 2.10 表述）",
                "新增：录音前质检（RMS/峰值/削波）与是否建议重录提示",
                "新增：场景预设（会议/户外/实验室）一键切换引擎+线程+模式",
                "新增：结果对比视图（同一音频快速 vs 准确并排对比）",
                "新增：失败自动重试策略（按错误类型自动降级重试一次）",
                "新增：转写质量评分（A/B/C/D 标签）",
                "新增：紧凑 UI 模式（默认核心操作，高级参数可折叠）",
            ),
        ),
        AppRelease(
            versionName = "2.9",
            versionCode = 29,
            date = "2026-02-06",
            notes = listOf(
                "新增：转写任务健康度看板（最近20次成功率/失败率/平均耗时）",
                "优化：低成功率场景给出参数调优建议（排队+准确/关闭加速）",
            ),
        ),
        AppRelease(
            versionName = "2.8",
            versionCode = 28,
            date = "2026-02-06",
            notes = listOf(
                "新增：文档同步加密脚本与版本一致性校验脚本（工程维护）",
                "维护：风险修复版本重新打包与版本号同步",
            ),
        ),
        AppRelease(
            versionName = "2.7",
            versionCode = 27,
            date = "2026-02-05",
            notes = listOf(
                "重新打包：版本号同步（无功能改动）",
            ),
        ),
        AppRelease(
            versionName = "2.6",
            versionCode = 26,
            date = "2026-01-22",
            notes = listOf(
                "优化：录音预热与尾音补齐，转写更稳定",
                "新增：玻璃透明度调节",
                "更新：项目书与版本号同步",
            ),
        ),
        AppRelease(
            versionName = "2.5",
            versionCode = 25,
            date = "2026-01-21",
            notes = listOf(
                "维护：构建内存参数收敛，打包更稳定",
                "更新：项目书与版本号同步",
            ),
        ),
        AppRelease(
            versionName = "2.4",
            versionCode = 24,
            date = "2026-01-21",
            notes = listOf(
                "适配：状态栏沉浸与系统栏边距",
                "修复：设置页手势返回不再直接退出",
                "修复：项目书弹窗/文档背景过透明",
            ),
        ),
        AppRelease(
            versionName = "2.3",
            versionCode = 23,
            date = "2026-01-20",
            notes = listOf(
                "新增：高刷新率屏幕适配",
                "优化：玻璃卡片渲染开销与多处稳定性细节",
                "优化：转写结果展示空值处理",
            ),
        ),
        AppRelease(
            versionName = "2.2",
            versionCode = 22,
            date = "2026-01-20",
            notes = listOf(
                "优化：玻璃拟态配色与柔和渐变背景细化",
                "优化：毛玻璃/高斯模糊开关覆盖主界面/接入页/设置页",
                "更新：项目书与内置文档同步",
            ),
        ),
        AppRelease(
            versionName = "2.1",
            versionCode = 21,
            date = "2026-01-15",
            notes = listOf(
                "新增：全局 UI 玻璃拟态 + 柔和渐变背景",
                "新增：视觉效果开关（毛玻璃/高斯模糊）",
                "优化：设置/接入页卡片与对话框视觉一致性",
            ),
        ),
        AppRelease(
            versionName = "2.0",
            versionCode = 20,
            date = "2026-01-13",
            notes = listOf(
                "新增：Sherpa 流式模型可选（zipformer 中文 / zipformer bilingual）",
                "新增：Sherpa 高精度模型可选（sense-voice / paraformer 中文）",
                "新增：接入与后台转写可下发 Sherpa 模型选择",
            ),
        ),
        AppRelease(
            versionName = "1.9",
            versionCode = 19,
            date = "2026-01-13",
            notes = listOf(
                "调整：Whisper 模型仅保留 small-q8_0（移除 q5）",
                "优化：zipformer 解码路径增强（准确档位更稳）",
                "修复：sense-voice 准确模式自动降级以避免闪退",
                "新增：后台转写支持按请求覆盖模型与加速参数",
            ),
        ),
        AppRelease(
            versionName = "1.8",
            versionCode = 18,
            date = "2026-01-13",
            notes = listOf(
                "修复：Sherpa 模型加载路径，提升稳定性",
                "新增：流式模型实时转写展示",
            ),
        ),
        AppRelease(
            versionName = "1.7",
            versionCode = 17,
            date = "2026-01-13",
            notes = listOf(
                "新增：Sherpa 引擎可选（zipformer 流式 / sense-voice 高精度）",
                "新增：NNAPI 加速开关（失败自动回退 CPU）",
                "新增：接入页支持清除音频缓存",
                "调整：更新语音助手应用图标",
            ),
        ),
        AppRelease(
            versionName = "1.6",
            versionCode = 16,
            date = "2026-01-13",
            notes = listOf(
                "新增：项目书资源加密（本地密码解密后显示）",
            ),
        ),
        AppRelease(
            versionName = "1.5",
            versionCode = 15,
            date = "2026-01-13",
            notes = listOf(
                "新增：设置页（项目书/更新日志）",
                "新增：项目书访问密码保护",
            ),
        ),
        AppRelease(
            versionName = "1.4",
            versionCode = 14,
            date = "2026-01-13",
            notes = listOf(
                "新增：音频缓存一键清除（录音/导入/解码/分段）",
                "调整：更新应用图标",
            ),
        ),
        AppRelease(
            versionName = "1.3",
            versionCode = 13,
            date = "2026-01-12",
            notes = listOf(
                "修复：后台转写服务补充 dataSync 权限声明",
                "新增：后台转写支持取消（主 App 可下发取消）",
            ),
        ),
        AppRelease(
            versionName = "1.2",
            versionCode = 12,
            date = "2026-01-12",
            notes = listOf(
                "新增：多线程线程数可选（自动/1/2/4/6/8）",
                "新增：录音格式可选（M4A/WAV），分析前自动解码",
                "新增：转写结果一键清除",
                "优化：转写中仅做轻量更新，完成后再补标点与转简体",
                "修复：标点稀疏时也会补标点",
            ),
        ),
        AppRelease(
            versionName = "1.1",
            versionCode = 11,
            date = "2026-01-12",
            notes = listOf(
                "修复：开始分析按钮在小屏下不可见",
                "优化：取消转写按钮常驻（无任务时禁用）",
                "优化：录音/导入按钮布局更清晰",
            ),
        ),
        AppRelease(
            versionName = "1.0",
            versionCode = 10,
            date = "2026-01-12",
            notes = listOf(
                "新增：后台转写服务（ACTION_TRANSCRIBE_AUDIO）可被主 App 直接调用",
                "新增：转写任务可取消，结果按任务分卡展示",
                "新增：录音支持暂停/继续，状态与时长更清晰",
                "优化：转写结果自动补标点并转为简体中文",
                "调整：模型档位更新为 small/medium（q5_0/q8_0）",
            ),
        ),
        AppRelease(
            versionName = "0.9",
            versionCode = 9,
            date = "2026-01-12",
            notes = listOf(
                "新增多线程开关（可切单线程降低资源占用）",
                "接入请求默认回传转写文本（可关闭）",
            ),
        ),
        AppRelease(
            versionName = "0.8",
            versionCode = 8,
            date = "2026-01-12",
            notes = listOf(
                "新增快速/准确识别模式与自动策略",
                "内置量化模型（base/small：q5_1、q8_0）",
                "长音频 VAD 分段转写与流式更新",
                "转写前统一降采样到 16kHz 单声道",
                "GPU 失败自动回退 CPU，并限制长音频启用",
            ),
        ),
        AppRelease(
            versionName = "0.7",
            versionCode = 7,
            date = "2026-01-11",
            notes = listOf(
                "新增 Vulkan GPU 加速开关（失败自动回退 CPU）",
                "GPU 使用状态持久化保存",
            ),
        ),
        AppRelease(
            versionName = "0.6",
            versionCode = 6,
            date = "2026-01-11",
            notes = listOf(
                "转写性能优化（best_of=1/线程上限提升）",
                "长音频自动启用并行分段转写",
                "关闭时间戳输出以提升速度",
            ),
        ),
        AppRelease(
            versionName = "0.5",
            versionCode = 5,
            date = "2026-01-11",
            notes = listOf(
                "新增并发/排队二选一开关",
                "队列与并发任务进度可视化",
                "转写输出自动转简体中文",
            ),
        ),
        AppRelease(
            versionName = "0.4",
            versionCode = 4,
            date = "2026-01-11",
            notes = listOf(
                "新增分析进度条与队列",
                "录音/导入不再自动分析",
                "新增“保存模型设置”和“开始分析”按钮",
                "移除按住说话入口",
                "修复音频导入解码异常",
            ),
        ),
        AppRelease(
            versionName = "0.3",
            versionCode = 3,
            date = "2026-01-11",
            notes = listOf(
                "内置 base/small 模型",
                "首次使用自动复制模型到本地",
            ),
        ),
        AppRelease(
            versionName = "0.2",
            versionCode = 2,
            date = "2026-01-11",
            notes = listOf(
                "接入 whisper.cpp 本地转写（中英文/自动）",
                "新增点击录音、导入音频分析",
                "录音完成自动转写并显示结果",
                "回传音频路径（content://）",
                "新增录音动效提示",
            ),
        ),
        AppRelease(
            versionName = "0.1",
            versionCode = 1,
            date = "2026-01-11",
            notes = listOf(
                "初始化项目结构",
                "接入悬浮窗与授权白名单",
            ),
        ),
    )
}
