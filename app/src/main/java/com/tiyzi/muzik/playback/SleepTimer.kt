package com.tiyzi.muzik.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Uyku zamanlayıcısı: süre dolunca sesi yavaşça kısıp müziği durdurur. */
object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _endsAt = MutableStateFlow<Long?>(null)
    val endsAt: StateFlow<Long?> = _endsAt

    private val _endOfTrack = MutableStateFlow(false)
    val endOfTrack: StateFlow<Boolean> = _endOfTrack

    fun start(minutes: Int) {
        cancel()
        val ms = minutes * 60_000L
        _endsAt.value = System.currentTimeMillis() + ms
        job = scope.launch {
            delay(ms)
            fadeOutAndPause()
            _endsAt.value = null
        }
    }

    fun stopAtEndOfTrack() {
        cancel()
        _endOfTrack.value = true
        PlaybackService.exo?.pauseAtEndOfMediaItems = true
    }

    fun cancel() {
        job?.cancel()
        job = null
        _endsAt.value = null
        if (_endOfTrack.value) {
            _endOfTrack.value = false
            PlaybackService.exo?.pauseAtEndOfMediaItems = false
        }
    }

    internal fun onPausedAtEndOfTrack() {
        if (!_endOfTrack.value) return
        _endOfTrack.value = false
        PlaybackService.exo?.pauseAtEndOfMediaItems = false
    }

    private suspend fun fadeOutAndPause() {
        val p = PlaybackService.exo ?: return
        val start = p.volume
        try {
            for (i in 10 downTo 1) {
                p.volume = start * i / 10f
                delay(800)
            }
            p.pause()
        } finally {
            p.volume = start
        }
    }
}
