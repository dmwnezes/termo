package com.dmwnezes.palavreiro

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.dmwnezes.palavreiro.data.Store
import com.dmwnezes.palavreiro.game.Mark
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sons curtos (gerados no próprio app, sem arquivos) e vibração.
 * Os dois podem ser desligados no Perfil > Configurações.
 */
class Feedback(context: Context, private val store: Store) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    private val audio = Executors.newSingleThreadExecutor()

    fun type() {
        tone(listOf(660.0 to 28), volume = 0.10)
        vibrate(longArrayOf(0, 8))
    }

    fun reveal(index: Int, mark: Mark) {
        val base = when (mark) { Mark.CORRECT -> 587.0; Mark.PRESENT -> 494.0; Mark.ABSENT -> 330.0 }
        tone(listOf(base * (1 + index * 0.06) to 70), volume = 0.12)
        // Vibração por cor: verde = um toque, amarelo = dois toques, cinza = nada.
        when (mark) {
            Mark.CORRECT -> vibrate(longArrayOf(0, 35))
            Mark.PRESENT -> vibrate(longArrayOf(0, 25, 70, 25))
            Mark.ABSENT -> {}
        }
    }

    fun invalid() {
        tone(listOf(196.0 to 90, 165.0 to 120), volume = 0.14)
        vibrate(longArrayOf(0, 40, 60, 40))
    }

    fun win() {
        tone(listOf(523.25 to 110, 659.25 to 110, 783.99 to 110, 1046.5 to 260), volume = 0.16)
        vibrate(longArrayOf(0, 30, 70, 30, 70, 90))
    }

    fun lose() {
        tone(listOf(392.0 to 160, 311.1 to 160, 261.6 to 320), volume = 0.14)
        vibrate(longArrayOf(0, 180))
    }

    /** Bomba-Relógio: passou a ser a sua vez (um toque curto). */
    fun turn() {
        tone(listOf(880.0 to 40), volume = 0.10)
        vibrate(longArrayOf(0, 30))
    }

    /** Bomba-Relógio: a bomba explodiu (vibração longa). */
    fun boom() {
        tone(listOf(110.0 to 260, 82.4 to 420), volume = 0.2)
        vibrate(longArrayOf(0, 650))
    }

    /** Partida com amigo: chegou uma reação (só uma vibração curtinha). */
    fun react() {
        vibrate(longArrayOf(0, 22))
    }

    private fun vibrate(pattern: LongArray) {
        if (!store.vibration) return
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    /** Toca uma sequência de notas (frequência em Hz to duração em ms) com envelope suave. */
    private fun tone(notes: List<Pair<Double, Int>>, volume: Double) {
        if (!store.sound) return
        audio.execute {
            runCatching {
                val rate = 44100
                val samples = ArrayList<Short>()
                for ((freq, ms) in notes) {
                    val n = rate * ms / 1000
                    for (i in 0 until n) {
                        val t = i.toDouble() / rate
                        val attack = (i / (rate * 0.004)).coerceAtMost(1.0)
                        val decay = exp(-4.0 * i / n)
                        val v = sin(2 * PI * freq * t) * attack * decay * volume
                        samples += (v * Short.MAX_VALUE).toInt().toShort()
                    }
                }
                val data = samples.toShortArray()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(data.size * 2)
                    .build()
                track.write(data, 0, data.size)
                track.play()
                Thread.sleep(notes.sumOf { it.second }.toLong() + 50)
                track.release()
            }
        }
    }
}
