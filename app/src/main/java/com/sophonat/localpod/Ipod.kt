package com.sophonat.localpod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlin.math.abs
import kotlin.math.atan2

// หนึ่งชั้นของเมนู: key = ชนิดเมนู, arg = ชื่อศิลปิน/อัลบั้ม, sel = แถวที่เลือกอยู่
data class Menu(val key: String, val arg: String = "", val sel: Int = 0)

@Composable
fun IpodScreen(
    songs: List<Song>,
    playlists: List<Playlist>,
    player: MediaController,
    tick: Int, // ส่งต่อให้ IpodDisplay เพื่อให้วาดใหม่เมื่อ player เปลี่ยน (เพลงเปลี่ยนแล้วชื่อต้องเปลี่ยนตาม)
    themeMode: String,
    onTheme: () -> Unit,
    onPlayList: (List<Song>, Int) -> Unit,
    onPlayPause: () -> Unit
) {
    val stack = remember { mutableStateListOf(Menu("main")) }

    // วงล้อปรับ "เสียงของเครื่อง" (STREAM_MUSIC) ไม่ใช่เสียงภายในแอป จึงตรงกับปุ่มข้างเครื่อง
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    DisposableEffect(Unit) {
        // กดปุ่มเพิ่ม/ลดเสียงข้างเครื่อง แถบเสียงในแอปต้องขยับตาม
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val haptic = LocalHapticFeedback.current
    val top = stack.last()

    fun songsOf(m: Menu): List<Song> {
        if (m.key == "artist") {
            return songs.filter { it.artist == m.arg }
        } else if (m.key == "album") {
            return songs.filter { it.album == m.arg }
        } else if (m.key == "playlist") {
            val ids = playlists.find { it.name == m.arg }?.songIds.orEmpty()
            return ids.mapNotNull { id -> songs.find { it.id == id } }
        } else {
            return songs
        }
    }

    fun entries(m: Menu): List<String> {
        if (m.key == "main") {
            return listOf("Music", "Artists", "Playlists", "Now playing", "Shuffle songs", "Settings")
        } else if (m.key == "music") {
            return listOf("Songs", "Artists", "Albums")
        } else if (m.key == "artists") {
            return songs.map { it.artist }.distinct().sorted()
        } else if (m.key == "albums") {
            return songs.map { it.album }.distinct().sorted()
        } else if (m.key == "playlists") {
            return playlists.map { it.name }
        } else if (m.key == "songs" || m.key == "artist" || m.key == "album" || m.key == "playlist") {
            return songsOf(m).map { it.title }
        } else if (m.key == "settings") {
            val shuffle = if (player.shuffleModeEnabled) "On" else "Off"
            val repeat = if (player.repeatMode == Player.REPEAT_MODE_ALL) "All"
            else if (player.repeatMode == Player.REPEAT_MODE_ONE) "One"
            else "Off"
            return listOf("Shuffle: $shuffle", "Repeat: $repeat", "Theme: ${themeLabel(themeMode)}")
        } else {
            return emptyList()
        }
    }

    fun push(key: String, arg: String = "") {
        stack.add(Menu(key, arg))
    }

    // อ่าน stack.last() สดๆ ทุกครั้ง เพราะหมุนเร็วอาจเรียกหลายครั้งก่อนหน้าจอวาดใหม่
    fun setSel(sel: Int) {
        stack[stack.lastIndex] = stack.last().copy(sel = sel)
    }

    fun rotate(step: Int) {
        val now = stack.last()
        if (now.key == "now") {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (step > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0
            )
            vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        } else {
            val n = entries(now).size
            if (n > 0) setSel((now.sel + step).coerceIn(0, n - 1))
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun select() {
        val top = stack.last() // อ่านสดๆ ไม่ใช้ค่าที่จับไว้ตอนวาดครั้งก่อน
        val list = entries(top)
        if (list.isEmpty()) return
        val sel = top.sel.coerceAtMost(list.lastIndex)
        val item = list[sel]

        if (top.key == "main") {
            if (item == "Music") {
                push("music")
            } else if (item == "Playlists") {
                push("playlists")
            } else if (item == "Now playing") {
                push("now")
            } else if (item == "Shuffle songs") {
                if (songs.isNotEmpty()) {
                    player.shuffleModeEnabled = true
                    onPlayList(songs, songs.indices.random())
                    push("now")
                }
            } else if (item == "Artists") {
                push("artists")
            } else {
                push("settings")
            }
        } else if (top.key == "music") {
            if (item == "Songs") {
                push("songs")
            } else if (item == "Artists") {
                push("artists")
            } else {
                push("albums")
            }
        } else if (top.key == "artists") {
            push("artist", item)
        } else if (top.key == "albums") {
            push("album", item)
        } else if (top.key == "playlists") {
            push("playlist", item)
        } else if (top.key == "songs" || top.key == "artist" || top.key == "album" || top.key == "playlist") {
            onPlayList(songsOf(top), sel)
            push("now")
        } else if (top.key == "settings") {
            if (sel == 0) {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            } else if (sel == 2) {
                onTheme()
            } else if (player.repeatMode == Player.REPEAT_MODE_OFF) {
                player.repeatMode = Player.REPEAT_MODE_ALL
            } else if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                player.repeatMode = Player.REPEAT_MODE_ONE
            } else {
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(stack.size > 1) { back() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IpodDisplay(top, entries(top), player, tick, songs.size, vol.toFloat() / maxVol)
        Spacer(Modifier.height(18.dp))
        // ส่งให้วงล้อผ่าน State เสมอ: ถ้าวงล้อถือฟังก์ชันตัวเก่าที่จับ songs/เมนูเก่าไว้ (เช่นตอนเปิดแอปที่รายการเพลงยังว่าง)
        // ปุ่มกดเลือกเพลงจะไม่ทำงาน การเรียกผ่าน State ทำให้ได้ฟังก์ชันล่าสุดทุกครั้ง
        val rotateNow by rememberUpdatedState(::rotate)
        val backNow by rememberUpdatedState(::back)
        val selectNow by rememberUpdatedState(::select)
        val playPauseNow by rememberUpdatedState(onPlayPause)
        val playerNow by rememberUpdatedState(player)
        // กรอบนอกกว้างกว่าวงล้อ 40dp สูงกว่า 14dp ให้ปุ่มโหมดอยู่ในกรอบทั้งปุ่ม
        // (Compose ไม่ส่งการแตะให้ส่วนของลูกที่ล้นกรอบแม่ ปุ่มที่ยื่นออกนอกวงล้อจึงเคยกดไม่ติดตรงส่วนที่ล้น)
        Box(Modifier.width(304.dp).height(278.dp)) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                ClickWheel(
                    onRotate = { rotateNow(it) },
                    onMenu = { backNow() },
                    onPrev = { playerNow.seekToPreviousMediaItem() },
                    onNext = { playerNow.seekToNextMediaItem() },
                    onPlayPause = { playPauseNow() },
                    onSelect = { selectNow() }
                )
            }
            // ปุ่มโหมดเล่น (ไอคอนอย่างเดียว) อยู่มุมขวาบนของวงล้อ
            PlayModeButton(player, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
fun IpodDisplay(top: Menu, list: List<String>, player: MediaController, tick: Int, total: Int, volume: Float) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val title = if (top.key == "main") "LocalPod"
    else if (top.key == "artist" || top.key == "album" || top.key == "playlist") top.arg
    else if (top.key == "now") "Now playing"
    else top.key.replaceFirstChar { it.uppercase() }
    // เมนูที่เลือกแล้วเข้าชั้นถัดไป ถึงจะโชว์ลูกศร
    val hasChildren = top.key in listOf("main", "music", "artists", "albums", "playlists")

    Column(
        Modifier.fillMaxWidth().height(264.dp)
            .clip(shape)
            .border(1.dp, colors.outlineVariant, shape)
            .background(colors.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                if (player.isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause, null,
                Modifier.size(16.dp), tint = if (player.isPlaying) colors.primary else colors.onSurfaceVariant
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))

        if (top.key == "now") {
            val coverUri = player.currentMediaItem?.localConfiguration?.uri
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(coverUri, 76.dp, corner = 12.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            player.mediaMetadata.title?.toString() ?: "-",
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            player.mediaMetadata.artist?.toString() ?: "",
                            fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (player.mediaItemCount > 0) "${player.currentMediaItemIndex + 1} of ${player.mediaItemCount}" else "0 of $total",
                            fontSize = 11.sp, color = colors.onSurfaceVariant
                        )
                    }
                }
                IpodProgress(player, tick, volume)
            }
        } else if (list.isEmpty()) {
            Text("ว่าง", Modifier.padding(14.dp), color = colors.onSurfaceVariant)
        } else {
            val sel = top.sel.coerceAtMost(list.lastIndex)
            // รายการเลื่อนตามแถวที่เลือก (กันแถวล่างสุดตกขอบจอ ไม่ว่าฟอนต์/ภาษาจะสูงแค่ไหน)
            val listState = rememberLazyListState()
            LaunchedEffect(top.key, top.arg, sel) { listState.scrollToItem((sel - 2).coerceAtLeast(0)) }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp), state = listState, userScrollEnabled = false) {
                itemsIndexed(list) { idx, label ->
                    val bg = if (idx == sel) colors.primary else colors.surface
                    val fg = if (idx == sel) colors.onPrimary else colors.onSurface
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, Modifier.weight(1f), color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (hasChildren) Text("›", color = fg)
                    }
                }
            }
        }
    }
}

@Composable
fun ClickWheel(
    onRotate: (Int) -> Unit,
    onMenu: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSelect: () -> Unit
) {
    val rotate by rememberUpdatedState(onRotate)
    val stepDegrees = 15f // หมุนกี่องศาถึงนับ 1 คลิก ปรับความไวตรงนี้
    val colors = MaterialTheme.colorScheme
    val key = colors.onSurfaceVariant

    // พื้นที่กดของแต่ละปุ่มบนวงล้อ ใหญ่พอให้กดง่าย (ไอคอน/ข้อความอยู่ตรงกลาง)
    fun Modifier.zone(onClick: () -> Unit) = this.size(64.dp).clip(CircleShape).clickable { onClick() }

    Box(
        Modifier.size(264.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .pointerInput(Unit) {
                var last = 0f
                fun angle(x: Float, y: Float): Float =
                    Math.toDegrees(atan2(y - size.height / 2f, x - size.width / 2f).toDouble()).toFloat()
                detectDragGestures(
                    onDragStart = { o -> last = angle(o.x, o.y) }
                ) { change, _ ->
                    change.consume()
                    val a = angle(change.position.x, change.position.y)
                    var d = a - last
                    if (d > 180f) {
                        d -= 360f
                    } else if (d < -180f) {
                        d += 360f
                    }
                    if (abs(d) >= stepDegrees) {
                        rotate(if (d > 0) 1 else -1)
                        last = a
                    }
                }
            }
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).zone(onMenu), contentAlignment = Alignment.Center) {
            Text("MENU", color = key, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Box(Modifier.align(Alignment.CenterStart).padding(start = 6.dp).zone(onPrev), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SkipPrevious, "ก่อนหน้า", tint = key)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).zone(onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.SkipNext, "ถัดไป", tint = key)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).zone(onPlayPause), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "เล่น/หยุด", tint = key)
        }
        Box(
            Modifier.align(Alignment.Center).size(104.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.outlineVariant, CircleShape)
                .clickable { onSelect() }
        )
    }
}

// เวลา/แถบเพลงเดินทุก 0.5 วินาที แยกไว้ให้วาดใหม่แค่ส่วนนี้
@Composable
fun IpodProgress(player: MediaController, tick: Int, volume: Float) {
    val colors = MaterialTheme.colorScheme
    val (pos, dur) = rememberPosition(player)
    Column {
        // Slider สูงขั้นต่ำเกือบ 48dp จะดันแถบเสียงตกขอบจอ จึงจำกัดพื้นที่ไว้ 28dp ให้ส่วนที่เกินล้นแบบมองไม่เห็น
                    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                        SeekBar(player, pos, dur, Modifier.fillMaxWidth().wrapContentHeight(unbounded = true))
                    }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(fmtTime(pos), fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(fmtTime(dur), fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text("เสียงเครื่อง ${(volume * 100).toInt()}%  (หมุนวงล้อ)", fontSize = 11.sp, color = colors.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { volume },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            trackColor = colors.surfaceVariant, gapSize = 0.dp, drawStopIndicator = {}
        )
    }
}


// โหมดเล่น: 0 = ตามคิวปกติ (ลูกศรวน), 1 = วนเพลงนี้เพลงเดียว (ลูกศรวนมีเลข 1), 2 = สุ่ม (ลูกศรไขว้)
// ถือสถานะของตัวเองและฟัง player ตรงๆ ไม่พึ่ง tick จากข้างบน (tick อาจค้างตอนหน้าจอถูกวาดด้วยค่าเก่า ปุ่มเลยไม่ขยับ)
// กดแล้วเปลี่ยนทันที ถ้า player ส่งสถานะจริงมาทีหลังก็แก้ตามนั้น
@Composable
fun PlayModeButton(player: MediaController, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    fun read() = if (player.repeatMode == Player.REPEAT_MODE_ONE) 1 else if (player.shuffleModeEnabled) 2 else 0
    var mode by remember(player) { mutableIntStateOf(read()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) { mode = read() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { mode = read() }
        }
        player.addListener(listener)
        mode = read()
        onDispose { player.removeListener(listener) }
    }
    val bg by animateColorAsState(if (mode == 0) colors.surfaceVariant else colors.primary, label = "bg")
    val fg by animateColorAsState(if (mode == 0) colors.onSurfaceVariant else colors.onPrimary, label = "fg")
    val icon = when (mode) { 1 -> Icons.Filled.RepeatOne; 2 -> Icons.Filled.Shuffle; else -> Icons.Filled.Repeat }
    Box(
        modifier.size(44.dp).clip(CircleShape).background(bg)
            .clickable {
                // เปลี่ยนทีละครั้ง เคลียร์อีกโหมดให้ไม่ซ้อนกัน
                val next = (mode + 1) % 3
                mode = next
                player.repeatMode = if (next == 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = next == 2
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, when (mode) { 1 -> "วนเพลงนี้"; 2 -> "สุ่ม"; else -> "ตามคิว" }, Modifier.size(22.dp), tint = fg)
    }
}
