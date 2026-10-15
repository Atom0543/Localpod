package com.sophonat.localpod

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

// ครอปภาพเป็นสี่เหลี่ยมจัตุรัส: ลากเพื่อเลื่อน บีบนิ้วเพื่อซูม กรอบคือส่วนที่จะได้เป็นปก
@Composable
fun CropDialog(source: Bitmap, onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val viewport = 300.dp
    val v = with(LocalDensity.current) { viewport.toPx() }
    val baseScale = v / minOf(source.width, source.height) // ซูมน้อยสุด = ภาพเต็มกรอบพอดี
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) } // เลื่อนจากกึ่งกลางกรอบ (px)

    // ไม่ให้ลากภาพจนเห็นพื้นที่ว่างในกรอบ
    fun clamp(o: Offset, z: Float): Offset {
        val maxX = (source.width * baseScale * z - v) / 2f
        val maxY = (source.height * baseScale * z - v) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun crop(): Bitmap {
        val scale = baseScale * zoom
        val side = (v / scale).roundToInt().coerceIn(1, minOf(source.width, source.height))
        val cx = source.width / 2f - offset.x / scale
        val cy = source.height / 2f - offset.y / scale
        val left = (cx - side / 2f).roundToInt().coerceIn(0, source.width - side)
        val top = (cy - side / 2f).roundToInt().coerceIn(0, source.height - side)
        val out = Bitmap.createBitmap(source, left, top, side, side)
        return if (out.width > 1024) Bitmap.createScaledBitmap(out, 1024, 1024, true) else out
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("เลือกส่วนที่จะใช้เป็นปก", style = MaterialTheme.typography.titleMedium)
                Text("ลากเพื่อเลื่อน บีบนิ้วเพื่อซูม", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                val image = remember(source) { source.asImageBitmap() }
                Canvas(
                    Modifier.size(viewport)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 6f)
                                offset = clamp(offset + pan, zoom)
                            }
                        }
                ) {
                    val scale = baseScale * zoom
                    val w = source.width * scale
                    val h = source.height * scale
                    drawImage(
                        image,
                        dstOffset = IntOffset((size.width / 2f + offset.x - w / 2f).roundToInt(), (size.height / 2f + offset.y - h / 2f).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt())
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("ยกเลิก") }
                    TextButton({ onDone(crop()) }) { Text("ใช้ภาพนี้") }
                }
            }
        }
    }
}
