package com.spenditos.android.data.capture

import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Small recording seam so capture state can be exercised with a plain fake. */
interface AudioRecorder {
    fun start(): File
    fun maxAmplitude(): Int
    fun stop(): File?
    fun cancel()
}

/** AAC in an MP4 container, mono 22.05 kHz / 64 kbps, matching the iOS fallback profile. */
class MediaRecorderAudioRecorder @Inject constructor(@ApplicationContext private val context: Context) : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    @Suppress("DEPRECATION")
    override fun start(): File {
        cancel()
        val directory = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File.createTempFile("capture-voice-", ".m4a", directory)
        val active = MediaRecorder()
        try {
            active.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioChannels(1)
                setAudioEncodingBitRate(BIT_RATE)
                setMaxDuration(MAX_DURATION_MILLIS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = active
            output = file
            return file
        } catch (error: Throwable) {
            runCatching { active.release() }
            file.delete()
            cancel()
            throw error
        }
    }

    override fun maxAmplitude(): Int = recorder?.maxAmplitude ?: 0

    override fun stop(): File? {
        val file = output
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            file?.delete()
            return null
        } finally {
            recorder?.release()
            recorder = null
            output = null
        }
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    override fun cancel() {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // Stopping before the first encoded frame can fail; the file is removed below.
        } finally {
            recorder?.release()
            recorder = null
            output?.delete()
            output = null
        }
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val BIT_RATE = 64_000
        const val MAX_DURATION_MILLIS = 60_000
    }
}
