package com.example.onepass.service

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import kotlin.math.abs

class BundledSpeechEngine(private val context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val audioPlayer = PcmAudioPlayer()

    private var tts: OfflineTts? = null
    private var currentVoice: BundledVoicePackage? = null
    private var statusReason: String? = null
    private var lastFailureReason: String? = null

    fun initialize() {
        if (tts != null) {
            return
        }

        val voice = BundledSpeechSupport(appContext).getBundledVoiceStatus().preferredVoice
        requireNotNull(voice) { "未检测到内置 Matcha 模型文件" }

        try {
            val matchaConfig = OfflineTtsMatchaModelConfig(
                voice.acousticModelPath,
                voice.vocoderPath,
                voice.lexiconPath,
                voice.tokensPath,
                "",
                "",
                DEFAULT_NOISE_SCALE,
                DEFAULT_LENGTH_SCALE
            )

            val modelConfig = OfflineTtsModelConfig(
                OfflineTtsVitsModelConfig(),
                matchaConfig,
                OfflineTtsKokoroModelConfig(),
                OfflineTtsZipVoiceModelConfig(),
                OfflineTtsKittenModelConfig(),
                OfflineTtsPocketModelConfig(),
                OfflineTtsSupertonicModelConfig(),
                DEFAULT_NUM_THREADS,
                false,
                "cpu"
            )

            val config = OfflineTtsConfig(
                modelConfig,
                voice.ruleFstPaths.joinToString(","),
                "",
                DEFAULT_MAX_NUM_SENTENCES,
                DEFAULT_SILENCE_SCALE
            )

            tts = OfflineTts(appContext.assets, config)
            currentVoice = voice
            statusReason = null
            lastFailureReason = null
        } catch (t: Throwable) {
            statusReason = t.message ?: t.javaClass.simpleName
            lastFailureReason = statusReason
            Log.e(TAG, "Failed to initialize Matcha engine", t)
            close()
        }
    }

    fun isReady(): Boolean = tts != null

    fun getStatusReason(): String? = statusReason

    fun getLastFailureReason(): String? = lastFailureReason

    fun getVoiceLabel(): String = currentVoice?.voiceName ?: "matcha-icefall-zh-baker"

    fun canSynthesize(text: String): Boolean = preprocessText(text).isNotBlank()

    fun speak(text: String, speechRate: Float, volume: Float): Boolean {
        val activeTts = tts ?: return false
        val preparedText = preprocessText(text)
        if (preparedText.isBlank()) {
            lastFailureReason = "文本为空"
            return false
        }

        return runCatching {
            audioPlayer.stop()
            val speed = mapSpeechRate(speechRate)
            val generated = activeTts.generate(preparedText, 0, speed)
            if (!isAudioUsable(generated)) {
                lastFailureReason = "内置语音没有生成可播放的 PCM 音频"
                false
            } else {
                val pcm = floatToPcm16(generated.samples)
                audioPlayer.playMono16Blocking(pcm, generated.sampleRate, volume)
                lastFailureReason = null
                true
            }
        }.getOrElse { error ->
            lastFailureReason = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "Failed to synthesize bundled speech", error)
            false
        }
    }

    fun stop() {
        audioPlayer.stop()
    }

    override fun close() {
        audioPlayer.stop()
        runCatching { tts?.release() }
        tts = null
        currentVoice = null
    }

    private fun preprocessText(text: String): String {
        return normalizeNumbersForSpeech(
            text.trim()
                .replace(Regex("(\\d+(?:\\.\\d+)?)%")) { match ->
                    "\u767e\u5206\u4e4b${numberToChinese(match.groupValues[1])}"
                }
                .replace("\u00b0C", "\u6444\u6c0f\u5ea6")
                .replace("%", "\u767e\u5206\u4e4b")
                .replace(">=", "\u5927\u4e8e\u7b49\u4e8e")
                .replace("<=", "\u5c0f\u4e8e\u7b49\u4e8e")
                .replace(">", "\u5927\u4e8e")
                .replace("<", "\u5c0f\u4e8e")
                .replace("|", "\uff0c")
                .replace("/", "\uff0c")
                .replace("\n", "\uff0c")
                .replace("\r", "\uff0c")
                .replace("\t", "\uff0c")
                .replace(Regex("\\s+"), "")
        )
    }

    private fun normalizeNumbersForSpeech(text: String): String {
        var normalized = text

        normalized = Regex("(?<!\\d)((?:19|20)\\d{2})(?!\\d)").replace(normalized) { match ->
            match.groupValues[1].map(::digitToChinese).joinToString("")
        }

        normalized = Regex("\\u767e\\u5206\\u4e4b(\\d+(?:\\.\\d+)?)").replace(normalized) { match ->
            "\u767e\u5206\u4e4b${numberToChinese(match.groupValues[1])}"
        }

        normalized = Regex("(\\d+(?:\\.\\d+)?)\\u767e\\u5206\\u4e4b").replace(normalized) { match ->
            "\u767e\u5206\u4e4b${numberToChinese(match.groupValues[1])}"
        }

        normalized = Regex("(-?\\d+(?:\\.\\d+)?)(\\u6444\\u6c0f\\u5ea6|\\u5ea6|\\u6708|\\u65e5|\\u53f7|\\u7ea7)").replace(normalized) { match ->
            "${numberToChinese(match.groupValues[1])}${match.groupValues[2]}"
        }

        normalized = Regex("(?<![A-Za-z])(\\d+(?:\\.\\d+)?)").replace(normalized) { match ->
            numberToChinese(match.groupValues[1])
        }

        return normalized
    }

    private fun numberToChinese(number: String): String {
        if (number.isBlank()) {
            return number
        }

        val normalized = number.trim()
        val negative = normalized.startsWith("-")
        val unsigned = if (negative) normalized.substring(1) else normalized
        val parts = unsigned.split(".", limit = 2)
        val integerPart = parts[0]
        val decimalPart = parts.getOrNull(1)

        val integerSpoken = integerToChinese(integerPart)
        val decimalSpoken = decimalPart
            ?.takeIf { it.isNotEmpty() }
            ?.map(::digitToChinese)
            ?.joinToString("") { it.toString() }

        return buildString {
            if (negative) append("负")
            append(integerSpoken)
            if (decimalSpoken != null) {
                append("点")
                append(decimalSpoken)
            }
        }
    }

    private fun integerToChinese(digits: String): String {
        val cleaned = digits.trimStart('0')
        if (cleaned.isEmpty()) {
            return "零"
        }

        val value = cleaned.toLongOrNull()
        if (value == null) {
            return digits.map(::digitToChinese).joinToString("")
        }

        if (value < 10) {
            return digitToChinese(cleaned[0]).toString()
        }

        val smallUnits = arrayOf("", "十", "百", "千")
        val bigUnits = arrayOf("", "万", "亿", "兆")
        val groups = mutableListOf<Int>()
        var remaining = value
        while (remaining > 0) {
            groups += (remaining % 10000).toInt()
            remaining /= 10000
        }

        val result = StringBuilder()
        var needZero = false
        for (index in groups.indices.reversed()) {
            val group = groups[index]
            if (group == 0) {
                needZero = result.isNotEmpty()
                continue
            }

            if (needZero || (result.isNotEmpty() && group < 1000)) {
                if (!result.endsWith("零")) {
                    result.append("零")
                }
            }

            result.append(convertGroup(group, smallUnits))
            result.append(bigUnits[index])
            needZero = false
        }

        return result.toString()
            .replace(Regex("零+"), "零")
            .removeSuffix("零")
            .replace(Regex("^一十"), "十")
    }

    private fun convertGroup(group: Int, smallUnits: Array<String>): String {
        if (group == 0) {
            return ""
        }

        val digits = group.toString().padStart(4, '0')
        val result = StringBuilder()
        var zeroPending = false
        for (index in digits.indices) {
            val digit = digits[index] - '0'
            val unitIndex = digits.length - 1 - index
            if (digit == 0) {
                zeroPending = result.isNotEmpty()
                continue
            }

            if (zeroPending) {
                result.append("零")
                zeroPending = false
            }

            result.append(digitToChinese(digits[index]))
            result.append(smallUnits[unitIndex])
        }

        return result.toString()
    }

    private fun digitToChinese(char: Char): Char {
        return when (char) {
            '0' -> '零'
            '1' -> '一'
            '2' -> '二'
            '3' -> '三'
            '4' -> '四'
            '5' -> '五'
            '6' -> '六'
            '7' -> '七'
            '8' -> '八'
            '9' -> '九'
            else -> char
        }
    }

    private fun mapSpeechRate(rate: Float): Float {
        return (0.9f + ((rate - 1.0f) * 0.15f)).coerceIn(0.7f, 1.3f)
    }

    private fun isAudioUsable(audio: GeneratedAudio): Boolean {
        val peak = audio.samples.maxOfOrNull { abs(it) } ?: 0f
        return audio.samples.isNotEmpty() && peak > MIN_USABLE_PEAK
    }

    private fun floatToPcm16(samples: FloatArray): ShortArray {
        return ShortArray(samples.size) { index ->
            val clipped = samples[index].coerceIn(-1f, 1f)
            (clipped * Short.MAX_VALUE).toInt().toShort()
        }
    }

    companion object {
        private const val TAG = "BundledSpeechEngine"
        private const val DEFAULT_NUM_THREADS = 2
        private const val DEFAULT_MAX_NUM_SENTENCES = 1
        private const val DEFAULT_SILENCE_SCALE = 0.6f
        private const val DEFAULT_NOISE_SCALE = 0.667f
        private const val DEFAULT_LENGTH_SCALE = 1.0f
        private const val MIN_USABLE_PEAK = 0.003f
    }
}



