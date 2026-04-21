package com.example.onepass.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class PcmAudioPlayer {
    @Volatile
    private var currentTrack: AudioTrack? = null

    fun playMono16Blocking(samples: ShortArray, sampleRate: Int, volume: Float) {
        stop()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(samples.size * 2)

        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        currentTrack = audioTrack
        audioTrack.setVolume(volume.coerceIn(0f, 1f))
        audioTrack.play()
        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

        // WRITE_BLOCKING 只保证写入缓冲区完成，不保证已经真正播放完。
        while (currentTrack === audioTrack &&
            audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING &&
            audioTrack.playbackHeadPosition < samples.size
        ) {
            Thread.sleep(20)
        }

        if (currentTrack === audioTrack) {
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            currentTrack = null
        }
    }

    fun stop() {
        val track = currentTrack ?: return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
        currentTrack = null
    }
}
