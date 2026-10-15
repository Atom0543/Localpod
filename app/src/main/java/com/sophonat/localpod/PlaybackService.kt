package com.sophonat.localpod

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// เล่นเพลงเบื้องหลัง + โชว์ปุ่มบน notification/หน้าล็อกให้อัตโนมัติ (Media3 ทำให้) และอัปเดต widget หน้าจอหลัก
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var player: Player? = null

    private fun snapshot(p: Player): WidgetState {
        val meta = p.mediaMetadata
        val dur = p.duration
        val progress = if (dur > 0) (p.currentPosition * 1000 / dur).toInt().coerceIn(0, 1000) else 0
        return WidgetState(
            meta.title?.toString() ?: "LocalPod",
            meta.artist?.toString() ?: "แตะเพื่อเปิดแอป",
            p.currentMediaItem?.localConfiguration?.uri,
            p.isPlaying,
            progress
        )
    }

    // ระหว่างเล่น อัปเดตแถบความคืบหน้าบน widget ทุก 3 วินาที
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            WidgetRender.partial(this@PlaybackService, snapshot(p))
            if (p.isPlaying) handler.postDelayed(this, 3000)
        }
    }

    private var artKey = ""

    // ใส่ปกที่ตั้งเอง/ตัดแถบแล้วให้เพลงที่กำลังเล่น เพื่อให้ notification และหน้าล็อกใช้ปกเดียวกับในแอป
    private fun refreshArt() {
        val exo = player as? ExoPlayer ?: return
        val item = exo.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val key = "${item.mediaId}@${Covers.version}"
        if (key == artKey && item.mediaMetadata.artworkData != null) return // คิวใหม่ที่เพลงเดิมไม่มีข้อมูลปก ต้องใส่ใหม่
        artKey = key
        Thread {
            val bytes = Covers.artBytes(this, uri) ?: return@Thread
            handler.post {
                val p = player as? ExoPlayer ?: return@post
                val cur = p.currentMediaItem ?: return@post
                if (cur.mediaId != item.mediaId) return@post
                val meta = cur.mediaMetadata.buildUpon().setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
                p.replaceMediaItem(p.currentMediaItemIndex, cur.buildUpon().setMediaMetadata(meta).build())
            }
        }.start()
    }

    override fun onCreate() {
        super.onCreate()
        Covers.onChange = { handler.post { refreshArt() } }
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo
        exo.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) refreshArt()
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_TIMELINE_CHANGED)) {
                    WidgetRender.full(this@PlaybackService, snapshot(p))
                } else if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                    WidgetRender.partial(this@PlaybackService, snapshot(p))
                }
                handler.removeCallbacks(tick)
                if (p.isPlaying) handler.postDelayed(tick, 3000)
            }
        })
        session = MediaSession.Builder(this, RandomNextPlayer(exo)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        Covers.onChange = null
        session?.player?.release()
        session?.release()
        session = null
        player = null
        super.onDestroy()
    }

    // ถ้าไม่มีเพลงถัดไปในคิว (ไม่ได้เล่นจาก playlist/คิวหมดแล้ว/ยังไม่มีคิว) ให้สุ่มเพลงจากทั้งเครื่องมาเล่นต่อ
    // ใช้ได้กับทุกที่ที่สั่งถัดไป: ปุ่มใน widget, แจ้งเตือน, หน้าล็อก และในแอป
    private inner class RandomNextPlayer(base: Player) : ForwardingPlayer(base) {
        private fun randomQueue() {
            val songs = Library.playable(this@PlaybackService)
            if (songs.isEmpty()) return
            val current = wrappedPlayer.currentMediaItem?.mediaId
            val queue = songs.filter { it.id.toString() != current }.shuffled() + songs.filter { it.id.toString() == current }
            wrappedPlayer.setMediaItems(queue.map { it.toMediaItem() }, 0, 0L)
            wrappedPlayer.prepare()
            wrappedPlayer.play()
        }

        override fun seekToNext() {
            if (hasNextMediaItem()) super.seekToNext() else randomQueue()
        }

        override fun seekToNextMediaItem() {
            if (hasNextMediaItem()) super.seekToNextMediaItem() else randomQueue()
        }

        override fun play() {
            if (mediaItemCount == 0) randomQueue() else super.play()
        }

        // ให้ปุ่มถัดไปกดได้เสมอ แม้ไม่มีเพลงถัดไปในคิว (ไม่งั้น session จะปฏิเสธคำสั่งก่อนถึงโค้ดข้างบน)
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_PLAY_PAUSE)
                .build()

        override fun isCommandAvailable(command: Int): Boolean =
            command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                command == Player.COMMAND_PLAY_PAUSE || super.isCommandAvailable(command)
    }
}
