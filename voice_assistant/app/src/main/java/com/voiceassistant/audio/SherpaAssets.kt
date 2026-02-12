package com.voiceassistant.audio

import android.content.Context
import com.voiceassistant.data.SherpaOfflineModel
import com.voiceassistant.data.SherpaStreamingModel

internal data class SherpaTransducerPaths(
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val tokens: String,
)

internal data class SherpaOfflinePaths(
    val model: String,
    val tokens: String,
)

internal data class AssetEnsureResult<T>(
    val value: T? = null,
    val error: String? = null,
)

internal object SherpaAssets {
    const val ZipformerAssetDir = "sherpa/zipformer"
    const val ZipformerBilingualAssetDir = "sherpa/zipformer_bilingual"
    const val SenseVoiceAssetDir = "sherpa/sensevoice"
    const val ParaformerAssetDir = "sherpa/paraformer"

    fun streamingAssetDir(model: SherpaStreamingModel): String {
        return when (model) {
            SherpaStreamingModel.ZIPFORMER_ZH -> ZipformerAssetDir
            SherpaStreamingModel.ZIPFORMER_BILINGUAL -> ZipformerBilingualAssetDir
        }
    }

    fun offlineAssetDir(model: SherpaOfflineModel): String {
        return when (model) {
            SherpaOfflineModel.SENSE_VOICE -> SenseVoiceAssetDir
            SherpaOfflineModel.PARAFORMER_ZH -> ParaformerAssetDir
        }
    }

    fun resolveStreamingModel(context: Context, model: SherpaStreamingModel): SherpaTransducerPaths? {
        val dir = streamingAssetDir(model)
        val paths = SherpaTransducerPaths(
            encoder = "$dir/encoder-epoch-99-avg-1.int8.onnx",
            decoder = "$dir/decoder-epoch-99-avg-1.int8.onnx",
            joiner = "$dir/joiner-epoch-99-avg-1.int8.onnx",
            tokens = "$dir/tokens.txt",
        )
        return if (assetExists(context, paths.encoder) &&
            assetExists(context, paths.decoder) &&
            assetExists(context, paths.joiner) &&
            assetExists(context, paths.tokens)
        ) {
            paths
        } else {
            null
        }
    }

    fun resolveOfflineModel(context: Context, model: SherpaOfflineModel): SherpaOfflinePaths? {
        val dir = offlineAssetDir(model)
        val paths = SherpaOfflinePaths(
            model = "$dir/model.int8.onnx",
            tokens = "$dir/tokens.txt",
        )
        return if (assetExists(context, paths.model) && assetExists(context, paths.tokens)) {
            paths
        } else {
            null
        }
    }

    fun ensureStreamingModel(context: Context, model: SherpaStreamingModel): AssetEnsureResult<SherpaTransducerPaths> {
        val paths = resolveStreamingModel(context, model)
        return if (paths != null) {
            AssetEnsureResult(value = paths)
        } else {
            AssetEnsureResult(error = "模型资源缺失，请重新安装")
        }
    }

    fun ensureOfflineModel(context: Context, model: SherpaOfflineModel): AssetEnsureResult<SherpaOfflinePaths> {
        val paths = resolveOfflineModel(context, model)
        return if (paths != null) {
            AssetEnsureResult(value = paths)
        } else {
            AssetEnsureResult(error = "模型资源缺失，请重新安装")
        }
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).close()
            true
        }.getOrDefault(false)
    }
}
