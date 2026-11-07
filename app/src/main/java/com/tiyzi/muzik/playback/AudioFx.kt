package com.tiyzi.muzik.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EqBand(val index: Int, val label: String, val level: Int)

data class FxState(
    val enabled: Boolean,
    val bands: List<EqBand>,
    val minLevel: Int,
    val maxLevel: Int,
    val presets: List<String>,
    val preset: Int,
    val bassSupported: Boolean,
    val bass: Int,
)

/** Ekolayzır ve bas güçlendirici. Ayarlar kaydedilir. */
object AudioFx {
    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow<FxState?>(null)
    val state: StateFlow<FxState?> = _state

    fun attach(context: Context, sessionId: Int) {
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        val p = context.applicationContext.getSharedPreferences("fx", Context.MODE_PRIVATE)
        prefs = p
        val on = p.getBoolean("enabled", false)
        try {
            val e = Equalizer(0, sessionId)
            for (i in 0 until e.numberOfBands.toInt()) {
                if (p.contains("band_$i")) e.setBandLevel(i.toShort(), p.getInt("band_$i", 0).toShort())
            }
            e.enabled = on
            eq = e
        } catch (ex: Exception) {
            eq = null
        }
        try {
            val b = BassBoost(0, sessionId)
            val strength = p.getInt("bass", 0)
            if (b.strengthSupported) b.setStrength(strength.toShort())
            b.enabled = on && strength > 0
            bassBoost = b
        } catch (ex: Exception) {
            bassBoost = null
        }
        publish()
    }

    private fun publish() {
        val e = eq
        if (e == null) {
            _state.value = null
            return
        }
        try {
            val range = e.bandLevelRange
            val bands = (0 until e.numberOfBands.toInt()).map { i ->
                val hz = e.getCenterFreq(i.toShort()) / 1000
                EqBand(
                    index = i,
                    label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz",
                    level = e.getBandLevel(i.toShort()).toInt(),
                )
            }
            val presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            _state.value = FxState(
                enabled = e.enabled,
                bands = bands,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                presets = presets,
                preset = prefs?.getInt("preset", -1) ?: -1,
                bassSupported = bassBoost?.strengthSupported == true,
                bass = prefs?.getInt("bass", 0) ?: 0,
            )
        } catch (ex: Exception) {
            _state.value = null
        }
    }

    fun setEnabled(on: Boolean) {
        try {
            eq?.enabled = on
            bassBoost?.enabled = on && (prefs?.getInt("bass", 0) ?: 0) > 0
        } catch (_: Exception) {
        }
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
        publish()
    }

    fun setBand(index: Int, level: Int) {
        val e = eq ?: return
        try {
            e.setBandLevel(index.toShort(), level.toShort())
        } catch (_: Exception) {
        }
        prefs?.edit()?.putInt("band_$index", level)?.putInt("preset", -1)?.apply()
        publish()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
            val ed = prefs?.edit()
            for (b in 0 until e.numberOfBands.toInt()) {
                ed?.putInt("band_$b", e.getBandLevel(b.toShort()).toInt())
            }
            ed?.putInt("preset", index)?.apply()
        } catch (_: Exception) {
        }
        publish()
    }

    fun setBass(strength: Int) {
        val b = bassBoost ?: return
        try {
            b.setStrength(strength.toShort())
            b.enabled = prefs?.getBoolean("enabled", false) == true && strength > 0
        } catch (_: Exception) {
        }
        prefs?.edit()?.putInt("bass", strength)?.apply()
        publish()
    }

    fun release() {
        try { eq?.release() } catch (_: Exception) { }
        try { bassBoost?.release() } catch (_: Exception) { }
        eq = null
        bassBoost = null
        _state.value = null
    }
}
