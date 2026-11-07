package com.tiyzi.muzik.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Müziği arka planda çalan servis. Uygulama kapatılsa da, ekran kilitlense de çalmaya devam eder;
 * bildirimde ve kilit ekranında oynat/duraklat/ileri/geri düğmeleri çıkar.
 */
class PlaybackService : MediaSessionService() {

    companion object {
        /** Ekolayzır ve uyku zamanlayıcısı için (aynı uygulama içinden erişilir) */
        @Volatile
        var exo: ExoPlayer? = null
            private set

        const val EXTRA_MIME = "mime"
    }

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // kulaklık çıkınca duraklat
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo = player

        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    SleepTimer.onPausedAtEndOfTrack()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(this@PlaybackService, audioSessionId)
            }
        })
        AudioFx.attach(this, player.audioSessionId)

        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pending = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val items = mediaItems.map { item ->
                        item.buildUpon()
                            .setUri(item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)
                            .setMimeType(
                                item.requestMetadata.extras?.getString(EXTRA_MIME)
                                    ?: item.localConfiguration?.mimeType
                            )
                            .build()
                    }.toMutableList()
                    return Futures.immediateFuture(items)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        SleepTimer.cancel()
        AudioFx.release()
        exo = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
