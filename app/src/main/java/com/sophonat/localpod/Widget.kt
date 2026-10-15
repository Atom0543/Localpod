package com.sophonat.localpod

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import android.content.BroadcastReceiver
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executors

// วาดแผ่นเสียง: จานดำมีร่อง + ภาพปกเป็นฉลากวงกลมตรงกลาง หมุนได้ตามมุมที่ส่งมา
object Vinyl {
    fun draw(cover: Bitmap?, size: Int, degrees: Float): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFF111114.toInt()
        c.drawCircle(r, r, r, paint)

        // ร่องแผ่นเสียง
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0x1FFFFFFF
        var g = r * 0.68f
        while (g < r * 0.97f) {
            c.drawCircle(r, r, g, paint)
            g += r * 0.06f
        }

        // แสงสะท้อนสองแฉก อยู่กับที่ ไม่หมุนตามจาน จะได้ดูเหมือนจานกำลังหมุนอยู่ใต้แสง
        paint.style = Paint.Style.FILL
        paint.shader = SweepGradient(
            r, r,
            intArrayOf(0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.12f, 0.25f, 0.5f, 0.62f, 0.75f, 1f)
        )
        c.drawCircle(r, r, r, paint)
        paint.shader = null

        // ภาพปกเป็นฉลาก (ครอปกลางภาพเป็นวงกลม) หมุนตามองศา
        val label = r * 0.66f
        c.save()
        c.rotate(degrees, r, r)
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val m = Matrix()
            m.postTranslate(-(cover.width - side) / 2f, -(cover.height - side) / 2f)
            val s = label * 2f / side
            m.postScale(s, s)
            m.postTranslate(r - label, r - label)
            val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(m)
            paint.shader = shader
            c.drawCircle(r, r, label, paint)
            paint.shader = null
        } else {
            paint.color = 0xFF2A2A2F.toInt()
            c.drawCircle(r, r, label, paint)
            paint.color = 0xFFC93A27.toInt()
            c.drawCircle(r, r, label * 0.35f, paint)
        }
        c.restore()

        // ขอบฉลาก + รูตรงกลาง (เจาะทะลุให้เห็นพื้น widget)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0xFF000000.toInt()
        c.drawCircle(r, r, label, paint)
        paint.style = Paint.Style.FILL
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        c.drawCircle(r, r, r * 0.07f, paint)
        return out
    }
}

class WidgetState(
    val title: String,
    val artist: String,
    val coverUri: Uri?,
    val playing: Boolean,
    val progress: Int // 0..1000
)

object WidgetRender {
    private const val SIZE = 256
    private const val FRAMES = 18 // 18 เฟรม เฟรมละ 100ms = หมุนรอบละ 1.8 วินาที (ราว 33 รอบ/นาที)
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile
    var last = WidgetState("LocalPod", "แตะเพื่อเปิดแอป", null, false, 0)

    // widget มี 2 ขนาด (4x2 และ 4x1) ใช้ id ของ view ชุดเดียวกัน
    private val kinds = listOf(
        PlayerWidget::class.java to R.layout.widget,
        PlayerWidgetSmall::class.java to R.layout.widget_small
    )

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    // ปุ่มบน widget ส่งคำสั่งเข้า WidgetActionReceiver (action ต่างกันต่อปุ่ม)
    private fun key(context: Context, action: String, request: Int): PendingIntent {
        val intent = Intent(action).setClass(context, WidgetActionReceiver::class.java)
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun applyBasics(v: RemoteViews, context: Context, st: WidgetState) {
        v.setProgressBar(R.id.widget_progress, 1000, st.progress, false)
        v.setImageViewResource(R.id.widget_play, if (st.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        // เล่นอยู่ = จานหมุน (ViewFlipper) / หยุด = โชว์ภาพนิ่ง
        v.setViewVisibility(R.id.widget_spin, if (st.playing) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_still, if (st.playing) View.GONE else View.VISIBLE)
    }

    // อัปเดตทั้งหมด (เปลี่ยนเพลง): สร้างเฟรมแผ่นเสียงใหม่ ทำนอกเธรดหลัก
    fun full(context: Context, st: WidgetState) {
        last = st
        val app = context.applicationContext
        exec.execute {
            val targets = kinds.map { (cls, layout) -> Triple(cls, layout, ids(app, cls)) }.filter { it.third.isNotEmpty() }
            if (targets.isEmpty()) return@execute
            val cover = st.coverUri?.let { Covers.load(app, it, SIZE) }
            val frames = (0 until FRAMES).map { Vinyl.draw(cover, SIZE, 360f * it / FRAMES) }

            val open = PendingIntent.getActivity(
                app, 4, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for ((_, layout, ids) in targets) {
                val v = RemoteViews(app.packageName, layout)
                v.setTextViewText(R.id.widget_title, st.title)
                v.setTextViewText(R.id.widget_artist, st.artist)
                v.removeAllViews(R.id.widget_spin)
                for (bmp in frames) {
                    val frame = RemoteViews(app.packageName, R.layout.widget_frame)
                    frame.setImageViewBitmap(R.id.widget_frame_image, bmp)
                    v.addView(R.id.widget_spin, frame)
                }
                v.setImageViewBitmap(R.id.widget_still, frames[0])
                applyBasics(v, app, st)

                v.setOnClickPendingIntent(R.id.widget_prev, key(app, WidgetActionReceiver.PREV, 1))
                v.setOnClickPendingIntent(R.id.widget_play, key(app, WidgetActionReceiver.PLAY_PAUSE, 2))
                v.setOnClickPendingIntent(R.id.widget_next, key(app, WidgetActionReceiver.NEXT, 3))
                v.setOnClickPendingIntent(R.id.widget_root, open)
                v.setOnClickPendingIntent(R.id.widget_title, open)
                v.setOnClickPendingIntent(R.id.widget_artist, open)
                v.setOnClickPendingIntent(R.id.widget_still, open)
                v.setOnClickPendingIntent(R.id.widget_spin, open)
                AppWidgetManager.getInstance(app).updateAppWidget(ids, v)
            }
        }
    }

    // อัปเดตเฉพาะแถบความคืบหน้า + ปุ่มเล่น/หยุด + จานหมุน/นิ่ง (เบา ไม่ส่งภาพซ้ำ)
    fun partial(context: Context, st: WidgetState) {
        last = st
        val app = context.applicationContext
        for ((cls, layout) in kinds) {
            val ids = ids(app, cls)
            if (ids.isEmpty()) continue
            val v = RemoteViews(app.packageName, layout)
            applyBasics(v, app, st)
            AppWidgetManager.getInstance(app).partiallyUpdateAppWidget(ids, v)
        }
    }
}

class PlayerWidget : AppWidgetProvider() {
    // วางวิดเจ็ตใหม่ / ระบบขอวาดใหม่: ใช้สถานะล่าสุดที่รู้
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRender.full(context, WidgetRender.last)
    }
}

class PlayerWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRender.full(context, WidgetRender.last)
    }
}

// รับการกดปุ่มบน widget: ต่อเข้ากับ PlaybackService ผ่าน MediaController (ปลุกตัวเล่นเพลงให้เองถ้ายังไม่ทำงาน) แล้วสั่งตรงๆ
class WidgetActionReceiver : BroadcastReceiver() {
    companion object {
        const val PLAY_PAUSE = "com.sophonat.localpod.WIDGET_PLAY_PAUSE"
        const val NEXT = "com.sophonat.localpod.WIDGET_NEXT"
        const val PREV = "com.sophonat.localpod.WIDGET_PREV"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        val app = context.applicationContext
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                when (action) {
                    PLAY_PAUSE -> if (c.mediaItemCount > 0 && c.isPlaying) c.pause() else c.play()
                    NEXT -> if (c.hasNextMediaItem()) {
                        c.seekToNextMediaItem()
                    } else {
                        // ไม่มีเพลงถัดไปในคิว (ไม่ได้เล่นจาก playlist / คิวหมด / ยังไม่มีคิว) = โหลดคิวสุ่มจากทั้งเครื่อง
                        val songs = Library.playable(app)
                        if (songs.isNotEmpty()) {
                            val now = c.currentMediaItem?.mediaId
                            val queue = songs.filter { it.id.toString() != now }.shuffled() + songs.filter { it.id.toString() == now }
                            c.setMediaItems(queue.map { it.toMediaItem() }, 0, 0L)
                            c.prepare()
                            c.play()
                        }
                    }
                    PREV -> c.seekToPreviousMediaItem()
                }
                // หน่วงก่อนปล่อย controller ให้คำสั่งส่งถึงตัวเล่นแน่ๆ
                Handler(Looper.getMainLooper()).postDelayed({
                    c.release()
                    pending.finish()
                }, 1000)
            } catch (e: Exception) {
                pending.finish()
            }
        }, ContextCompat.getMainExecutor(app))
    }
}
