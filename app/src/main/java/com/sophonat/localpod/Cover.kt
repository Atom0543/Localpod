package com.sophonat.localpod

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ดึงภาพปกจากไฟล์เพลง (ภาพที่ฝังในไฟล์ / thumbnail ของ MediaStore) แคชไว้ในหน่วยความจำ
object Covers {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val misses = HashSet<String>() // เพลงที่ไม่มีภาพปก จะได้ไม่ลองซ้ำ

    // เพิ่มค่าทุกครั้งที่ตั้ง/ลบปกเอง ให้ Cover ที่แสดงอยู่โหลดใหม่
    var version by mutableIntStateOf(0)
    var onChange: (() -> Unit)? = null // service ใช้ฟังเพื่ออัปเดตปกบนหน้าล็อก

    // ปกที่ผู้ใช้ตั้งเอง เก็บเป็นไฟล์ในโฟลเดอร์ของแอป ชื่อไฟล์ = id เพลง (ท้าย uri)
    private fun customFile(context: Context, uri: Uri) = File(File(context.filesDir, "covers"), "${uri.lastPathSegment}.jpg")

    fun hasCustom(context: Context, uri: Uri) = customFile(context, uri).exists()

    fun removeCustom(context: Context, uri: Uri) {
        customFile(context, uri).delete()
        version++
        onChange?.invoke()
    }

    // อ่านรูปที่ผู้ใช้เลือก (ย่อให้ด้านยาวไม่เกิน ~2048px และหมุนตามที่ถ่ายมา) เพื่อเอาไปครอป
    fun decode(context: Context, source: Uri): Bitmap? {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > 2048) sample *= 2
            val bmp = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val orientation = context.contentResolver.openInputStream(source)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
            } ?: 1
            val degrees = when (orientation) {
                6 -> 90f
                3 -> 180f
                8 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bmp
            val m = android.graphics.Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (e: Exception) {
            return null
        }
    }

    fun saveCustom(context: Context, uri: Uri, bmp: Bitmap): Boolean {
        try {
            val f = customFile(context, uri)
            f.parentFile?.mkdirs()
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            version++
            onChange?.invoke()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // key รวมเวลาแก้ไขไฟล์ปกที่ตั้งเอง ตั้งใหม่แล้วจะไม่ได้ภาพเก่าจากแคช
    private fun key(context: Context, uri: Uri, px: Int) = "$uri@$px@${customFile(context, uri).lastModified()}"

    // ภาพปกสำหรับ notification/หน้าล็อก (ใช้ปกที่ตั้งเอง + ตัดแถบแล้ว)
    fun artBytes(context: Context, uri: Uri): ByteArray? {
        val b = load(context, uri, 512) ?: return null
        return java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    fun cached(context: Context, uri: Uri, px: Int): Bitmap? = cache.get(key(context, uri, px))

    fun load(context: Context, uri: Uri, px: Int): Bitmap? {
        val k = key(context, uri, px)
        cache.get(k)?.let { return it }
        if (synchronized(misses) { k in misses }) return null

        var bmp: Bitmap? = null
        val custom = customFile(context, uri)
        if (custom.exists()) {
            bmp = BitmapFactory.decodeFile(custom.path)?.let {
                val side = minOf(it.width, it.height) // ตัดกลางภาพเป็นสี่เหลี่ยมจัตุรัส ไม่ให้ภาพเบี้ยว
                val square = Bitmap.createBitmap(it, (it.width - side) / 2, (it.height - side) / 2, side, side)
                Bitmap.createScaledBitmap(square, px, px, true)
            }
        }
        if (bmp == null && Build.VERSION.SDK_INT >= 29) {
            bmp = try {
                context.contentResolver.loadThumbnail(uri, Size(px, px), null)
            } catch (e: Exception) {
                null
            }
        }
        if (bmp == null) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                r.embeddedPicture?.let { bmp = BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (e: Exception) {
                // ไม่มีภาพปก
            } finally {
                r.release()
            }
        }
        // ปกที่ผู้ใช้ตั้งเองครอปมาแล้ว ไม่ต้องตัดขอบซ้ำ
        val out = bmp?.let { if (custom.exists()) it else trimBars(it) }
        if (out != null) cache.put(k, out) else synchronized(misses) { misses.add(k) }
        return out
    }

    // ปกที่ถูกยัดมาในกรอบ (แถบดำบนล่าง + แถบสีข้างๆ) -> ตัดเหลือแต่ปกตรงกลาง
    // ตัดเฉพาะเมื่อแถบเรียบสีเดียวชัดเจน ไม่เจอแพทเทิร์นก็คืนภาพเดิม
    private fun trimBars(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 64 || h < 64) return src
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }

        // เส้น (แถว/คอลัมน์) ถือว่าเป็นแถบเมื่อสีเกือบเท่ากันตลอดเส้น
        fun uniform(start: Int, step: Int, n: Int, from: Int, to: Int): Boolean {
            var r = 0L; var g = 0L; var b = 0L; var c = 0
            var i = from
            while (i < to) { val p = px[start + i * step]; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; c++; i += 2 }
            r /= c; g /= c; b /= c
            var dev = 0L
            i = from
            while (i < to) { val p = px[start + i * step]; dev += kotlin.math.abs(((p shr 16) and 255) - r) + kotlin.math.abs(((p shr 8) and 255) - g) + kotlin.math.abs((p and 255) - b); i += 2 }
            return dev / c < 36 // เฉลี่ยคลาดเคลื่อนรวม 3 ช่อง < 36 (รับ noise ของ JPEG)
        }
        fun row(y: Int) = uniform(y * w, 1, w, 0, w)

        var top = 0
        while (top < h && row(top)) top++
        if (top >= h) return src // ทั้งภาพสีเดียว
        var bottom = h - 1
        while (bottom > top && row(bottom)) bottom--
        fun col(x: Int) = uniform(top * w + x, w, bottom - top + 1, 0, bottom - top + 1)
        var left = 0
        while (left < w && col(left)) left++
        var right = w - 1
        while (right > left && col(right)) right--

        val cw = right - left + 1
        val ch = bottom - top + 1
        val removed = 1f - cw.toFloat() * ch / (w * h)
        // ต้องตัดออกอย่างน้อย 8% และเหลือปกอย่างน้อยครึ่งนึงของแต่ละด้าน ไม่งั้นถือว่าไม่ใช่แพทเทิร์นนี้
        if (removed < 0.08f || cw < w / 2 || ch < h / 2) return src
        return Bitmap.createBitmap(src, left, top, cw, ch)
    }
}

@Composable
fun Cover(uri: Uri?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState<ImageBitmap?>(null, uri, px, Covers.version) {
        value = null
        if (uri != null) {
            value = (Covers.cached(context, uri, px) ?: withContext(Dispatchers.IO) { Covers.load(context, uri, px) })
                ?.asImageBitmap()
        }
    }
    Box(
        modifier.size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(size / 2.5f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
