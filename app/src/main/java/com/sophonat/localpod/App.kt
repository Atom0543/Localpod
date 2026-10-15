package com.sophonat.localpod

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay

// รายการเมนูของแถวเพลง (ปุ่มจุด 3 จุด): ชื่อ + สิ่งที่ทำ
typealias RowAction = Pair<String, () -> Unit>

@Composable
fun App(player: MediaController, themeMode: String, onTheme: () -> Unit) {
    val context = LocalContext.current
    var scanned by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var renames by remember { mutableStateOf(UserData.loadRenames(context)) }
    var artists by remember { mutableStateOf(UserData.loadArtists(context)) }
    var hidden by remember { mutableStateOf(Library.hiddenIds(context)) }
    var playlists by remember { mutableStateOf(UserData.loadPlaylists(context)) }
    // คำนวณใหม่เฉพาะตอนข้อมูลเปลี่ยนจริง ไม่ใช่ทุกครั้งที่ player ขยับ
    val songs = remember(scanned, renames, artists, hidden) {
        scanned.filter { it.id !in hidden }.map { s -> s.copy(title = renames[s.id] ?: s.title, artist = artists[s.id] ?: s.artist) }
    }
    val hiddenSongs = remember(scanned, renames, artists, hidden) {
        scanned.filter { it.id in hidden }.map { s -> s.copy(title = renames[s.id] ?: s.title, artist = artists[s.id] ?: s.artist) }
    }

    // สแกนเพลงนอกเธรดหลัก และสแกนใหม่เองเมื่อกลับเข้าแอป หรือมีไฟล์เสียงเปลี่ยนในเครื่อง
    LaunchedEffect(refresh) {
        if (refresh > 0) delay(800) // รอให้ระบบสแกนไฟล์ใหม่เสร็จ/รวมการเปลี่ยนแปลงถี่ๆ
        scanned = withContext(Dispatchers.IO) { Library.scan(context) }
        scanning = false
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var first = true // ครั้งแรกสแกนไปแล้วด้านบน
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                if (first) first = false else refresh++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh++
            }
        }
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val tick = rememberPlayerTick(player)
    var screen by remember { mutableStateOf("player") } // player / hidden / playlists / tidy
    val pager = rememberPagerState { 2 }

    var renameTarget by remember { mutableStateOf<Song?>(null) }
    var addTarget by remember { mutableStateOf<Song?>(null) }
    var creatingFor by remember { mutableStateOf<Song?>(null) }

    // เลือกรูปจากเครื่องมาเป็นปกของเพลงที่กำลังตั้ง
    var coverTarget by remember { mutableStateOf<Song?>(null) }
    var cropping by remember { mutableStateOf<Pair<Song, android.graphics.Bitmap>?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
        val target = coverTarget
        coverTarget = null
        if (picked != null && target != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { Covers.decode(context, picked) }
                if (bmp == null) Toast.makeText(context, "เปิดรูปไม่สำเร็จ", Toast.LENGTH_SHORT).show() else cropping = target to bmp
            }
        }
    }

    // ฟังก์ชันด้านล่างอาจถูกส่งต่อให้หน้าจออื่นแล้วค้างเป็นตัวเก่า (ถ้าถือ player ตัวเก่าที่ถูกปล่อยไปหลังต่อ service ใหม่ คำสั่งจะเงียบหาย)
    // จึงเรียก player ผ่าน State ที่ชี้ตัวล่าสุดเสมอ
    val livePlayer by rememberUpdatedState(player)

    fun playList(list: List<Song>, index: Int) {
        livePlayer.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        livePlayer.prepare()
        livePlayer.play()
    }

    fun hide(song: Song) {
        hidden = hidden + song.id
        Library.saveHidden(context, hidden)
        // ถ้าเพลงนี้อยู่ในคิว ให้เอาออกจากคิวด้วย
        for (i in 0 until livePlayer.mediaItemCount) {
            if (livePlayer.getMediaItemAt(i).mediaId == song.id.toString()) {
                livePlayer.removeMediaItem(i)
                break
            }
        }
    }

    fun unhide(song: Song) {
        hidden = hidden - song.id
        Library.saveHidden(context, hidden)
    }

    // ว่าง หรือเท่าชื่อเดิม = กลับไปใช้ชื่อเดิม
    fun rename(song: Song, name: String) {
        val original = scanned.firstOrNull { it.id == song.id }?.title ?: song.title
        renames = if (name.isBlank() || name == original) renames - song.id else renames + (song.id to name)
        UserData.saveRenames(context, renames)
    }

    // ใช้ผลจากหน้า "จัดชื่อเพลง": เก็บเป็นชื่อที่ทับไว้ (ถ้าเท่าชื่อเดิมจากไฟล์ก็ลบการทับออก)
    fun applyTidy(list: List<Tidy>) {
        var r = renames
        var ar = artists
        for (t in list) {
            val orig = scanned.firstOrNull { it.id == t.song.id } ?: continue
            r = if (t.title.isBlank() || t.title == orig.title) r - orig.id else r + (orig.id to t.title)
            ar = if (t.artist.isBlank() || t.artist == orig.artist) ar - orig.id else ar + (orig.id to t.artist)
        }
        renames = r
        artists = ar
        UserData.saveRenames(context, r)
        UserData.saveArtists(context, ar)
    }

    fun resetNames() {
        renames = emptyMap()
        artists = emptyMap()
        UserData.saveRenames(context, renames)
        UserData.saveArtists(context, artists)
    }

    fun setPlaylists(list: List<Playlist>) {
        playlists = list
        UserData.savePlaylists(context, list)
    }

    fun createPlaylist(name: String, first: Song? = null) {
        if (name.isBlank()) return
        setPlaylists(playlists + Playlist(System.currentTimeMillis(), name, listOfNotNull(first?.id)))
    }

    fun addToPlaylist(p: Playlist, s: Song) {
        if (s.id in p.songIds) return
        setPlaylists(playlists.map { if (it.id == p.id) it.copy(songIds = it.songIds + s.id) else it })
    }

    fun playPause() {
        if (livePlayer.mediaItemCount == 0 && songs.isNotEmpty()) {
            playList(songs, 0)
        } else if (livePlayer.isPlaying) {
            livePlayer.pause()
        } else {
            livePlayer.play()
        }
    }

    fun actionsFor(s: Song): List<RowAction> = buildList {
        add("เพิ่มเข้า Playlist" to { addTarget = s })
        add("เปลี่ยนชื่อ" to { renameTarget = s })
        add("ตั้งภาพปก" to { coverTarget = s; picker.launch("image/*") })
        if (Covers.hasCustom(context, s.uri)) add("ลบภาพปกที่ตั้งเอง" to { Covers.removeCustom(context, s.uri) })
        add("ซ่อน" to { hide(s) })
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            if (page == 1) {
                IpodScreen(songs, playlists, livePlayer, tick, themeMode, onTheme, ::playList, ::playPause)
            } else if (screen == "hidden") {
                BackHandler { screen = "player" }
                HiddenScreen(hiddenSongs, ::unhide) { screen = "player" }
            } else if (screen == "tidy") {
                TidyScreen(songs, ::applyTidy, ::resetNames) { screen = "player" }
            } else if (screen == "playlists") {
                PlaylistsScreen(
                    playlists, songs, player, tick, ::playList,
                    onCreate = { createPlaylist(it) },
                    onRename = { p, name -> if (name.isNotBlank()) setPlaylists(playlists.map { if (it.id == p.id) it.copy(name = name) else it }) },
                    onDelete = { p -> setPlaylists(playlists.filter { it.id != p.id }) },
                    onAddSongs = { p, list -> setPlaylists(playlists.map { if (it.id == p.id) it.copy(songIds = it.songIds + list.map { s -> s.id }.filter { id -> id !in it.songIds }) else it }) },
                    onRemove = { p, s -> setPlaylists(playlists.map { if (it.id == p.id) it.copy(songIds = it.songIds - s.id) else it }) },
                    onBack = { screen = "player" }
                )
            } else {
                NormalScreen(
                    songs, scanning, player, tick, themeMode, onTheme, ::playList, ::actionsFor, ::playPause,
                    onOpenHidden = { screen = "hidden" }, onOpenPlaylists = { screen = "playlists" }, onOpenTidy = { screen = "tidy" }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0..1) {
                val on = pager.currentPage == i
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }

    cropping?.let { (song, bmp) ->
        CropDialog(bmp, { cropping = null }) { out ->
            cropping = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Covers.saveCustom(context, song.uri, out) }
                if (!ok) Toast.makeText(context, "ตั้งภาพปกไม่สำเร็จ", Toast.LENGTH_SHORT).show()
            }
        }
    }
    renameTarget?.let { s ->
        TextDialog("เปลี่ยนชื่อเพลง", s.title, "เว้นว่าง = ใช้ชื่อเดิม (เปลี่ยนเฉพาะชื่อที่แสดงในแอป)", { renameTarget = null }) {
            rename(s, it)
            renameTarget = null
        }
    }
    creatingFor?.let { s ->
        TextDialog("สร้าง Playlist ใหม่", "", null, { creatingFor = null }) {
            createPlaylist(it, s)
            creatingFor = null
        }
    }
    addTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { addTarget = null },
            title = { Text("เพิ่มเข้า Playlist") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().clickable { creatingFor = s; addTarget = null }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  สร้าง Playlist ใหม่", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                    for (p in playlists) {
                        val has = s.id in p.songIds
                        Row(
                            Modifier.fillMaxWidth().clickable { addToPlaylist(p, s); addTarget = null }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (has) "อยู่แล้ว" else "${p.songIds.size} เพลง", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ addTarget = null }) { Text("ปิด") } }
        )
    }
}

@Composable
fun TextDialog(title: String, initial: String, hint: String?, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = true, shape = RoundedCornerShape(12.dp),
                supportingText = hint?.let { { Text(it) } }
            )
        },
        confirmButton = { TextButton({ onOk(text.trim()) }) { Text("ตกลง") } },
        dismissButton = { TextButton(onDismiss) { Text("ยกเลิก") } }
    )
}

// ตัวนับที่เพิ่มทุกครั้งที่ player เปลี่ยนสถานะ ใช้สั่งให้หน้าจอวาดใหม่
@Composable
fun rememberPlayerTick(player: Player): Int {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                tick++
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return tick
}

fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

fun themeIcon(mode: String) = when (mode) {
    "light" -> Icons.Filled.LightMode
    "dark" -> Icons.Filled.DarkMode
    else -> Icons.Filled.BrightnessAuto
}

fun themeLabel(mode: String) = when (mode) {
    "light" -> "สว่าง"
    "dark" -> "มืด"
    else -> "ตามระบบ"
}

// เล่นถึงไหนแล้ว (ms) ถามจาก player ทุก 500ms
@Composable
fun rememberPosition(player: Player): Pair<Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    return pos to dur
}

// ตัวเลขเวลาเดินทุก 0.5 วินาที แยกเป็นคอมโพสเซเบิลของตัวเอง จะได้วาดใหม่แค่การ์ดนี้ ไม่ลากทั้งรายการเพลงไปด้วย
@Composable
fun NowPlayingCard(player: MediaController, tick: Int, onPlayPause: () -> Unit) {
    val (pos, dur) = rememberPosition(player)
    val meta = player.mediaMetadata
    val coverUri = player.currentMediaItem?.localConfiguration?.uri
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(coverUri, 96.dp, corner = 16.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    meta.title?.toString() ?: "ยังไม่ได้เลือกเพลง",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    meta.artist?.toString() ?: "แตะเพลงในรายการเพื่อเล่น",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SeekBar(player, pos, dur, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(fmtTime(pos), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(fmtTime(dur), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekToPreviousMediaItem() }, Modifier.size(52.dp)) { Icon(Icons.Filled.SkipPrevious, "ก่อนหน้า", Modifier.size(28.dp)) }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(onClick = onPlayPause, Modifier.size(60.dp)) {
                Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.isPlaying) "หยุด" else "เล่น", Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { player.seekToNextMediaItem() }, Modifier.size(52.dp)) { Icon(Icons.Filled.SkipNext, "ถัดไป", Modifier.size(28.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalScreen(
    songs: List<Song>,
    scanning: Boolean,
    player: MediaController,
    tick: Int, // ไม่ได้อ่านตรงๆ แต่ต้องรับไว้ เพื่อให้หน้าจอวาดใหม่เมื่อ player เปลี่ยน
    themeMode: String,
    onTheme: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    actionsFor: (Song) -> List<RowAction>,
    onPlayPause: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenTidy: () -> Unit
) {
    val currentId = player.currentMediaItem?.mediaId
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val shown = remember(songs, q) {
        if (q.isEmpty()) songs else songs.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) || it.album.contains(q, ignoreCase = true)
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // เลื่อนผ่านการ์ดกำลังเล่นไปแล้ว (เหลือเห็นไม่ถึงครึ่ง) ให้มินิเพลเยอร์โผล่ด้านล่างแทน
    val cardGonePx = with(LocalDensity.current) { 180.dp.toPx() }
    val showMini by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > cardGonePx } }

    Box(Modifier.fillMaxSize()) {
    // ทั้งหน้าเลื่อนไปพร้อมรายการ เหลือช่องค้นหาติดอยู่ด้านบนเสมอ
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "top") {
            Column {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("LocalPod", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onTheme) { Icon(themeIcon(themeMode), "ธีม: ${themeLabel(themeMode)}") }
                    IconButton(onClick = onOpenTidy) { Icon(Icons.Filled.AutoFixHigh, "จัดชื่อเพลง") }
                    IconButton(onClick = onOpenPlaylists) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Playlist") }
                    IconButton(onClick = onOpenHidden) { Icon(Icons.Outlined.VisibilityOff, "ที่ซ่อนไว้") }
                }
                Spacer(Modifier.height(12.dp))
                NowPlayingCard(player, tick, onPlayPause)
                Spacer(Modifier.height(12.dp))
            }
        }

        stickyHeader(key = "search") {
            Column(Modifier.fillMaxWidth().background(colors.background).padding(vertical = 6.dp)) {
                OutlinedTextField(
                    query, { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ค้นหาเพลง ศิลปิน อัลบั้ม") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton({ query = "" }) { Icon(Icons.Filled.Close, "ล้างคำค้น") } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Text(
                    if (q.isEmpty()) "${songs.size} เพลง" else "พบ ${shown.size} จาก ${songs.size} เพลง",
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (songs.isEmpty()) (if (scanning) "กำลังสแกนเพลง..." else "ไม่พบไฟล์เพลงในเครื่อง") else "ไม่พบเพลงที่ตรงกับ \"$q\"",
                    Modifier.padding(16.dp), color = colors.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                SongRow(s, s.id.toString() == currentId, { onPlay(shown, i) }, { actionsFor(s) })
            }
        }
    }
    AnimatedVisibility(
        showMini,
        Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp),
        enter = slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180))
    ) {
        MiniPlayer(player, tick, onPlayPause) { scope.launch { listState.animateScrollToItem(0) } }
    }
    }
}

// แถบเล็กๆ ลอยด้านล่าง: เพลงที่เล่นอยู่ + ปุ่มควบคุม แตะที่ตัวแถบเพื่อกลับขึ้นไปบนสุด
@Composable
fun MiniPlayer(player: MediaController, tick: Int, onPlayPause: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (pos, dur) = rememberPosition(player)
    val meta = player.mediaMetadata
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable { onOpen() }
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(player.currentMediaItem?.localConfiguration?.uri, 44.dp, corner = 10.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(meta.title?.toString() ?: "ยังไม่ได้เลือกเพลง", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta.artist?.toString() ?: "กดเล่นเพื่อเริ่มฟัง", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ player.seekToPreviousMediaItem() }) { Icon(Icons.Filled.SkipPrevious, "ก่อนหน้า") }
            FilledIconButton(onPlayPause, Modifier.size(44.dp)) {
                Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.isPlaying) "หยุด" else "เล่น")
            }
            IconButton({ player.seekToNextMediaItem() }) { Icon(Icons.Filled.SkipNext, "ถัดไป") }
        }
        LinearProgressIndicator(
            progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            trackColor = colors.surfaceVariant, gapSize = 0.dp, drawStopIndicator = {}
        )
    }
}

// แถวเพลง: ปุ่มจุด 3 จุดท้ายแถว กดแล้วจึงเลือกทำอะไรกับเพลงนั้น
@Composable
fun SongRow(s: Song, current: Boolean, onPlay: () -> Unit, actions: () -> List<RowAction>) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onPlay() }.padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(s.uri, 48.dp, corner = 10.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    s.title, color = if (current) colors.primary else colors.onSurface,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(s.artist, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "ตัวเลือกเพลง", tint = colors.onSurfaceVariant) }
                DropdownMenu(open, { open = false }) {
                    for ((label, run) in actions()) {
                        DropdownMenuItem(text = { Text(label) }, onClick = { open = false; run() })
                    }
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    songs: List<Song>,
    player: MediaController,
    tick: Int, // ไม่ได้อ่านตรงๆ แต่ต้องรับไว้ เพื่อให้หน้าจอวาดใหม่เมื่อ player เปลี่ยน
    onPlay: (List<Song>, Int) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Playlist, String) -> Unit,
    onDelete: (Playlist) -> Unit,
    onRemove: (Playlist, Song) -> Unit,
    onAddSongs: (Playlist, List<Song>) -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var openId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    val open = playlists.find { it.id == openId }
    val currentId = player.currentMediaItem?.mediaId

    fun back() {
        if (open != null) openId = null else onBack()
    }
    BackHandler { back() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") }
            Text(
                open?.name ?: "Playlist", Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (open == null) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "สร้าง Playlist") }
            } else {
                IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, "เพิ่มเพลงเข้า Playlist") }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (open == null) {
            if (playlists.isEmpty()) {
                Text(
                    "ยังไม่มี Playlist กด + เพื่อสร้าง หรือกดจุด 3 จุดที่เพลงแล้วเลือก \"เพิ่มเข้า Playlist\"",
                    Modifier.padding(vertical = 16.dp), color = colors.onSurfaceVariant
                )
            }
            LazyColumn {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                    var menu by remember { mutableStateOf(false) }
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { openId = p.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = colors.onPrimaryContainer)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.songIds.size} เพลง", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "ตัวเลือก Playlist", tint = colors.onSurfaceVariant) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text("เปลี่ยนชื่อ") }, onClick = { menu = false; renaming = p })
                                    DropdownMenuItem(text = { Text("ลบ Playlist") }, onClick = { menu = false; onDelete(p) })
                                }
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
            }
        } else {
            // เพลงที่ถูกซ่อนหรือหายจากเครื่องจะไม่แสดงใน Playlist
            val list = open.songIds.mapNotNull { id -> songs.find { it.id == id } }
            if (list.isEmpty()) {
                Text("Playlist นี้ยังว่าง กด + ด้านบนเพื่อเลือกเพลงมาใส่", Modifier.padding(vertical = 16.dp), color = colors.onSurfaceVariant)
            } else {
                TextButton(onClick = { onPlay(list, 0) }) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Text("  เล่นทั้งหมด (${list.size})")
                }
            }
            LazyColumn {
                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, s.id.toString() == currentId, { onPlay(list, i) }, { listOf("เอาออกจาก Playlist" to { onRemove(open, s) }) })
                }
            }
        }
    }

    if (picking && open != null) {
        SongPickerDialog(songs, open.songIds.toSet(), { picking = false }) {
            onAddSongs(open, it)
            picking = false
        }
    }
    if (creating) {
        TextDialog("สร้าง Playlist ใหม่", "", null, { creating = false }) {
            onCreate(it)
            creating = false
        }
    }
    renaming?.let { p ->
        TextDialog("เปลี่ยนชื่อ Playlist", p.name, null, { renaming = null }) {
            onRename(p, it)
            renaming = null
        }
    }
}

@Composable
fun HiddenScreen(hiddenSongs: List<Song>, onUnhide: (Song) -> Unit, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") }
            Text("ที่ซ่อนไว้", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text("ไฟล์ยังอยู่ในเครื่อง แค่ไม่แสดงในแอป", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (hiddenSongs.isEmpty()) {
            Text("ยังไม่มีไฟล์ที่ซ่อน", Modifier.padding(vertical = 16.dp), color = colors.onSurfaceVariant)
        } else {
            LazyColumn {
                itemsIndexed(hiddenSongs, key = { _, s -> s.id }) { _, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { onUnhide(s) }) {
                            Icon(Icons.Filled.Visibility, null, Modifier.size(18.dp))
                            Text("  แสดง")
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }
}

// เลือกเพลงหลายเพลงมาใส่ Playlist: ติ๊กที่เพลง ค้นหาได้ เพลงที่อยู่ใน Playlist แล้วจะติ๊กค้างและกดเปลี่ยนไม่ได้
@Composable
fun SongPickerDialog(songs: List<Song>, already: Set<Long>, onDismiss: () -> Unit, onAdd: (List<Song>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val shown = remember(songs, q) {
        if (q.isEmpty()) songs else songs.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) || it.album.contains(q, ignoreCase = true)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(Icons.Filled.Close, "ปิด") }
                    Text("เลือกเพลง", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onAdd(songs.filter { it.id in picked }) }, enabled = picked.isNotEmpty()) {
                        Text("เพิ่ม (${picked.size})")
                    }
                }
                OutlinedTextField(
                    query, { query = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    placeholder = { Text("ค้นหาเพลง ศิลปิน อัลบั้ม") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton({ query = "" }) { Icon(Icons.Filled.Close, "ล้างคำค้น") } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(shown, key = { _, s -> s.id }) { _, s ->
                        val have = s.id in already
                        val on = have || s.id in picked
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !have) { picked = if (s.id in picked) picked - s.id else picked + s.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(on, onCheckedChange = null, enabled = !have, modifier = Modifier.padding(end = 8.dp))
                            Cover(s.uri, 40.dp, corner = 8.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (have) colors.onSurfaceVariant else colors.onSurface)
                                Text(
                                    if (have) "อยู่ใน Playlist แล้ว" else s.artist,
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
            }
        }
    }
}

// แถบเวลาที่ลากเพื่อเลื่อนไปช่วงอื่นของเพลงได้ ปล่อยนิ้วแล้วค่อยกระโดดไปตำแหน่งนั้น
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(player: Player, pos: Long, dur: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var drag by remember { mutableStateOf<Float?>(null) }
    var settle by remember { mutableStateOf<Float?>(null) } // ค้างค่าที่เพิ่งเลื่อนไว้สั้นๆ จนตัวเลขเวลาตามทัน จะได้ไม่กระตุกกลับ
    LaunchedEffect(settle) {
        if (settle != null) {
            delay(700)
            settle = null
        }
    }
    val value = drag ?: settle ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surfaceVariant,
        disabledThumbColor = colors.outlineVariant, disabledActiveTrackColor = colors.outlineVariant, disabledInactiveTrackColor = colors.surfaceVariant
    )
    Slider(
        value, { drag = it },
        modifier,
        enabled = dur > 0,
        onValueChangeFinished = {
            drag?.let {
                player.seekTo((it * dur).toLong())
                settle = it
            }
            drag = null
        },
        colors = sliderColors,
        thumb = { Box(Modifier.size(14.dp).background(if (dur > 0) colors.primary else colors.outlineVariant, CircleShape)) },
        track = { st ->
            SliderDefaults.Track(st, Modifier.height(4.dp), enabled = dur > 0, colors = sliderColors, thumbTrackGapSize = 0.dp, trackInsideCornerSize = 0.dp, drawStopIndicator = null)
        }
    )
}
