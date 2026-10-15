package com.sophonat.localpod

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(val id: Long, val title: String, val artist: String, val album: String, val uri: Uri, val albumId: Long) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            // ไม่ใส่ artworkUri: ปกส่งเป็นข้อมูลเองใน PlaybackService.refreshArt ไม่งั้นหน้าล็อกบางเครื่องเลือกโหลดปกเดิมจาก URI แทน
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()
        )
        .build()
}

object Library {
    // สแกนไฟล์เสียงทุกไฟล์ในเครื่อง (ไม่กรองเอง ให้ผู้ใช้ซ่อนไฟล์ที่ไม่ใช่เพลงเอง)
    fun scan(context: Context): List<Song> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val list = mutableListOf<Song>()
        context.contentResolver.query(uri, cols, null, null, MediaStore.Audio.Media.TITLE + " ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                list.add(
                    Song(
                        id,
                        c.getString(1) ?: "ไม่มีชื่อ",
                        c.getString(2) ?: "ไม่ทราบศิลปิน",
                        c.getString(3) ?: "ไม่ทราบอัลบั้ม",
                        ContentUris.withAppendedId(uri, id),
                        c.getLong(4)
                    )
                )
            }
        }
        return list
    }

    // เพลงที่เล่นได้จริง: ตัดที่ซ่อนออก และใช้ชื่อที่ผู้ใช้ตั้งเอง (ใช้ตอนต้องสุ่มเพลงเองนอกหน้าจอแอป เช่นปุ่มบน widget)
    fun playable(context: Context): List<Song> {
        val hidden = hiddenIds(context)
        val renames = UserData.loadRenames(context)
        val artists = UserData.loadArtists(context)
        return scan(context).filter { it.id !in hidden }.map { s -> s.copy(title = renames[s.id] ?: s.title, artist = artists[s.id] ?: s.artist) }
    }

    // "ลบ" = แค่จด id ไว้ว่าซ่อน ไฟล์จริงไม่ถูกแตะ
    private fun prefs(context: Context) = context.getSharedPreferences("hidden", Context.MODE_PRIVATE)

    fun hiddenIds(context: Context): Set<Long> =
        prefs(context).getStringSet("ids", emptySet()).orEmpty().map { it.toLong() }.toSet()

    fun saveHidden(context: Context, ids: Set<Long>) =
        prefs(context).edit().putStringSet("ids", ids.map { it.toString() }.toSet()).apply()
}

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

// ชื่อเพลงที่ผู้ใช้ตั้งเอง (แค่ชื่อที่แสดงในแอป ไฟล์จริงไม่ถูกแตะ) และ playlist เก็บเป็น JSON ใน SharedPreferences
object UserData {
    private fun prefs(context: Context) = context.getSharedPreferences("userdata", Context.MODE_PRIVATE)

    fun loadRenames(context: Context): Map<Long, String> {
        val o = org.json.JSONObject(prefs(context).getString("renames", "{}") ?: "{}")
        return o.keys().asSequence().associate { it.toLong() to o.getString(it) }
    }

    fun saveRenames(context: Context, m: Map<Long, String>) {
        val o = org.json.JSONObject()
        m.forEach { (id, name) -> o.put(id.toString(), name) }
        prefs(context).edit().putString("renames", o.toString()).apply()
    }

    // ชื่อศิลปินที่ผู้ใช้/ระบบจัดชื่อตั้งให้ (แค่ชื่อที่แสดง ไฟล์จริงไม่ถูกแตะ)
    fun loadArtists(context: Context): Map<Long, String> {
        val o = org.json.JSONObject(prefs(context).getString("artists", "{}") ?: "{}")
        return o.keys().asSequence().associate { it.toLong() to o.getString(it) }
    }

    fun saveArtists(context: Context, m: Map<Long, String>) {
        val o = org.json.JSONObject()
        m.forEach { (id, name) -> o.put(id.toString(), name) }
        prefs(context).edit().putString("artists", o.toString()).apply()
    }

    fun loadPlaylists(context: Context): List<Playlist> {
        val a = org.json.JSONArray(prefs(context).getString("playlists", "[]") ?: "[]")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val ids = o.getJSONArray("songs")
            Playlist(o.getLong("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
        }
    }

    fun savePlaylists(context: Context, list: List<Playlist>) {
        val a = org.json.JSONArray()
        list.forEach { p ->
            a.put(org.json.JSONObject().put("id", p.id).put("name", p.name).put("songs", org.json.JSONArray(p.songIds)))
        }
        prefs(context).edit().putString("playlists", a.toString()).apply()
    }
}
