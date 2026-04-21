package com.example.onepass.service

import android.content.Context
import android.content.SharedPreferences

enum class SpeechEngineMode {
    AUTO,
    SYSTEM,
    BUNDLED_MATCHA
}

data class BundledVoicePackage(
    val voiceName: String,
    val acousticModelPath: String,
    val vocoderPath: String,
    val lexiconPath: String,
    val tokensPath: String,
    val ruleFstPaths: List<String>
)

data class BundledVoiceStatus(
    val isInstalled: Boolean,
    val missingAssets: List<String>,
    val preferredVoice: BundledVoicePackage?
)

class BundledSpeechSupport(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(): SpeechEngineMode {
        val value = prefs.getString(KEY_SPEECH_ENGINE_MODE, SpeechEngineMode.AUTO.name)
        return SpeechEngineMode.entries.firstOrNull { it.name == value } ?: SpeechEngineMode.AUTO
    }

    fun setMode(mode: SpeechEngineMode) {
        prefs.edit().putString(KEY_SPEECH_ENGINE_MODE, mode.name).apply()
    }

    fun getResolvedEngineLabel(): String {
        return when (getMode()) {
            SpeechEngineMode.AUTO -> if (hasBundledVoice()) "内置 Matcha" else "系统 TTS"
            SpeechEngineMode.SYSTEM -> "系统 TTS"
            SpeechEngineMode.BUNDLED_MATCHA ->
                if (hasBundledVoice()) "内置 Matcha" else "内置 Matcha（不可用）"
        }
    }

    fun syncResolvedEngineLabel() {
        setLastActiveEngineLabel(getResolvedEngineLabel())
    }

    fun getBundledVoiceStatus(): BundledVoiceStatus {
        val missing = REQUIRED_FILES.filterNot { fileName ->
            assetExists("$ASSET_DIR/$fileName")
        }
        if (missing.isNotEmpty()) {
            return BundledVoiceStatus(
                isInstalled = false,
                missingAssets = missing.map { "$ASSET_DIR/$it" },
                preferredVoice = null
            )
        }

        return BundledVoiceStatus(
            isInstalled = true,
            missingAssets = emptyList(),
            preferredVoice = BundledVoicePackage(
                voiceName = DEFAULT_MODEL_NAME,
                acousticModelPath = "$ASSET_DIR/model-steps-3.onnx",
                vocoderPath = "$ASSET_DIR/vocos-22khz-univ.onnx",
                lexiconPath = "$ASSET_DIR/lexicon.txt",
                tokensPath = "$ASSET_DIR/tokens.txt",
                ruleFstPaths = listOf(
                    "$ASSET_DIR/date.fst",
                    "$ASSET_DIR/number.fst",
                    "$ASSET_DIR/phone.fst"
                )
            )
        )
    }

    fun hasBundledVoice(): Boolean = getBundledVoiceStatus().isInstalled

    fun getPreferredVoiceDisplayName(): String {
        val voice = getBundledVoiceStatus().preferredVoice ?: return "未检测到内置 Matcha"
        return "${voice.voiceName} / Matcha"
    }

    fun getLastActiveEngineLabel(): String {
        return prefs.getString(KEY_LAST_ACTIVE_ENGINE, "尚未确定") ?: "尚未确定"
    }

    fun setLastActiveEngineLabel(label: String) {
        prefs.edit().putString(KEY_LAST_ACTIVE_ENGINE, label).apply()
    }

    private fun assetExists(path: String): Boolean {
        return try {
            appContext.assets.open(path).close()
            true
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val PREFS_NAME = "OnePassPrefs"
        const val KEY_SPEECH_ENGINE_MODE = "speech_engine_mode"
        const val KEY_LAST_ACTIVE_ENGINE = "last_active_speech_engine"
        private const val ASSET_DIR = "sherpa-vits"
        private const val DEFAULT_MODEL_NAME = "matcha-icefall-zh-baker"

        private val REQUIRED_FILES = listOf(
            "model-steps-3.onnx",
            "vocos-22khz-univ.onnx",
            "lexicon.txt",
            "tokens.txt",
            "date.fst",
            "number.fst",
            "phone.fst"
        )
    }
}
