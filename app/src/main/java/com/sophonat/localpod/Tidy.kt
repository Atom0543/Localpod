package com.sophonat.localpod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ข้อเสนอชื่อใหม่ของเพลงหนึ่งเพลง
// online = ได้จากการตรวจกับฐานข้อมูลออนไลน์
// confident = มั่นใจพอให้ติ๊กไว้ล่วงหน้า (รายการปกติ); false = เดาจากชื่อเพลงอย่างเดียว ให้ผู้ใช้ตรวจเอง
// reason = เหตุที่ขึ้นมาในรายการ (ถ้าน่าสงสัย) / options = ทางเลือก (ชื่อเพลง, ศิลปิน) เมื่อระบบไม่แน่ใจว่าฝั่งไหนคือศิลปิน ให้ผู้ใช้เลือก
data class Tidy(
    val song: Song,
    val title: String,
    val artist: String,
    val online: Boolean = false,
    val confident: Boolean = true,
    val reason: String = "",
    val options: List<Pair<String, String>> = emptyList()
) {
    val changed: Boolean get() = title != song.title || artist != song.artist
}

// ข้อมูลทั้งคลังเพลง ใช้ช่วยตัดสินว่าชื่อฝั่งไหนคือศิลปิน (รู้จักศิลปินจากแท็กเพลงอื่น / ชื่อที่โผล่ซ้ำหลายเพลง)
class TidyContext(songs: List<Song>) {
    val knownArtists: Set<String>
    val partCount: Map<String, Int> // นับทั้งคลัง
    val groupCount: Map<String, Int> // นับเฉพาะเพลงที่แท็กศิลปินเดียวกัน (คีย์ "แท็ก|ชื่อ")

    init {
        val known = HashSet<String>()
        val counts = HashMap<String, Int>()
        val groups = HashMap<String, Int>()
        for (s in songs) {
            val a = NameTidy.cleanArtistTag(s.artist)
            if (a.isNotEmpty() && !NameTidy.isChannel(a)) known.add(NameTidy.norm(a))
            val parts = NameTidy.twoParts(NameTidy.pre(s.title)) ?: continue
            val tag = NameTidy.norm(a)
            for (p in setOf(NameTidy.norm(parts.first), NameTidy.norm(parts.second))) {
                if (p.isEmpty()) continue
                counts[p] = (counts[p] ?: 0) + 1
                if (tag.isNotEmpty()) groups["$tag|$p"] = (groups["$tag|$p"] ?: 0) + 1
            }
        }
        knownArtists = known
        partCount = counts
        groupCount = groups
    }
}

class Analysis(
    val title: String,
    val artist: String, // ว่าง = ไม่ทราบ
    val reason: String,
    val options: List<Pair<String, String>>
)

// จัดชื่อเพลง: ล้างขยะในชื่อ แยกชื่อศิลปินออกจากชื่อเพลง และ (ถ้าสั่ง) ตรวจชื่อ/ศิลปินกับ Deezer ผ่านอินเทอร์เน็ต
object NameTidy {
    private val unknownArtists = setOf("", "unknown", "unknown artist", "<unknown>", "ไม่ทราบศิลปิน", "ไม่ทราบ", "various artists")

    // คำที่ถ้าอยู่ในวงเล็บล้วนๆ ถือเป็นขยะ (ถ้ามีคำอื่นปนอยู่ เช่น Remix / Live / Acoustic จะเก็บไว้)
    private val junkWords = setOf(
        "official", "video", "mv", "m", "v", "lyric", "lyrics", "audio", "visualizer", "visualiser", "hd", "hq", "4k", "8k",
        "1080p", "720p", "480p", "320kbps", "256kbps", "192kbps", "128kbps", "kbps", "download", "free", "full", "music",
        "song", "clip", "new", "เนื้อเพลง", "เนื้อร้อง", "ซับไทย", "เพลงใหม่", "ฟังเพลง", "ต้นฉบับ"
    )

    // คำที่ซ้ำกันหลายเพลงแต่ไม่ใช่ชื่อศิลปิน
    private val notArtist = setOf("live", "remix", "acoustic", "cover", "version", "ver", "instrumental", "demo", "official", "mv", "session", "unplugged")

    private val bracket = Regex("""[(\[{【「『<][^)\]}】」』>]*[)\]}】」』>]""")
    private val splitWords = Regex("""[\s,/|&+\-_.]+""")
    // คำขยะลอยๆ ท้ายชื่อ เช่น "Carefree HD" "Song Official Video" (ไม่รวมคำที่เป็นชื่อเพลงได้ เช่น free/new/music)
    private val trailingTag = Regex("""(\s+(official|video|audio|lyrics?|mv|m/v|hd|hq|4k|8k|1080p|720p|480p|visuali[sz]er))+\s*$""", RegexOption.IGNORE_CASE)
    private val topicSuffix = Regex("""\s*-\s*topic\s*$""", RegexOption.IGNORE_CASE) // ช่อง YouTube อัตโนมัติ "ศิลปิน - Topic"
    private val url = Regex("""https?://\S+|www\.\S+|\b[\w-]+\.(com|net|org|co\.th|in\.th)\b""", RegexOption.IGNORE_CASE)
    private val hashtag = Regex("""#\S+""")
    private val fakeSeparator = Regex("""\s*(\.{2,}|…+|~+|_{2,})\s*""") // "ชื่อเพลง....ศิลปิน" ใช้จุด/ขีดล่างหลายตัวคั่น
    // คำที่บอกว่าข้อความนั้นเป็นรายชื่อศิลปิน (ร่วมร้อง): x / × / feat / ft / with / vs
    private val strongCollab = Regex("""(\s+x\s+|\s*×\s*|\bfeat\.?(\s|$)|\bft\.?(\s|$)|\bfeaturing\b|\bwith\b|\bvs\.?(\s|$))""", RegexOption.IGNORE_CASE)
    private val weakCollab = Regex("""(\s&\s|,\s*|\s\+\s)""")
    private val featSplit = Regex("""\s+(feat\.?|ft\.?|featuring)\s+""", RegexOption.IGNORE_CASE) // ศิลปินหลัก feat. ศิลปินรับเชิญ
    private val dashes = Regex("""\s+[–—−]\s+""")
    private val leadingNumber = Regex("""^\d{1,3}\s*[-._)]\s+""") // "01 - ชื่อเพลง" แต่ไม่แตะ "7 นาที"
    private val tailGroup = Regex("""^(.*?)\s*([(\[{][^)\]}]*[)\]}])$""")
    // ชื่อที่ดูเหมือนช่อง YouTube / ค่ายเพลง ไม่ใช่ตัวศิลปิน
    private val channelLike = Regex("""(records?|recordings?|music|studios?|entertainment|labs?|channel|media|productions?|network|vevo|official)$|ค่าย|เรคคอร์ด|เร็คคอร์ด|มิวสิค|เอ็นเตอร์เทน""", RegexOption.IGNORE_CASE)

    fun norm(s: String): String = buildString { s.lowercase().forEach { if (it.isLetterOrDigit()) append(it) } }

    fun isUnknown(artist: String) = artist.trim().lowercase() in unknownArtists

    fun isChannel(artist: String) = channelLike.containsMatchIn(artist.trim())

    private fun junkBracket(m: MatchResult): String {
        val inner = m.value.substring(1, m.value.length - 1)
        val tokens = inner.lowercase().split(splitWords).filter { it.isNotEmpty() }
        val junk = tokens.isNotEmpty() && (tokens.all { it in junkWords } || tokens.first().startsWith("prod"))
        return if (junk) " " else m.value
    }

    private fun tidyEnds(s: String): String =
        s.replace(Regex("""\s+"""), " ").trim().trim('-', '_', '.', '~', '|', '–', '—', ' ').trim()

    fun cleanArtistTag(a: String): String {
        var r = a.replace(Regex("""\s*-\s*topic\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*vevo\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+official(\s+channel)?\s*$""", RegexOption.IGNORE_CASE), "")
        r = tidyEnds(r)
        return if (isUnknown(r)) "" else r
    }

    // ล้างขยะในชื่อเพลง (ลิงก์ แฮชแท็ก วงเล็บขยะ ฯลฯ) ยังไม่แยกศิลปิน
    fun pre(title: String): String {
        var t = title
        t = url.replace(t, " ")
        t = hashtag.replace(t, " ")
        t = bracket.replace(t) { junkBracket(it) }
        t = t.replace(Regex("""\s*[|｜].*$"""), "") // หลัง | มักเป็นชื่อช่อง/แฮชแท็ก
        t = fakeSeparator.replace(t, " - ")
        t = dashes.replace(t, " - ")
        t = t.replace('_', ' ')
        t = tidyEnds(topicSuffix.replace(tidyEnds(t), ""))
        t = trailingTag.replace(t, "")
        t = leadingNumber.replace(tidyEnds(t), "")
        t = tidyEnds(t)
        return if (t.isBlank()) tidyEnds(title) else t
    }

    // "A - B [วงเล็บท้าย]" -> (A, B, วงเล็บท้าย) วงเล็บท้ายสุดถือเป็นของชื่อเพลง (เช่น [Live Session])
    fun twoParts(t: String): Triple<String, String, String>? {
        val m = tailGroup.find(t)
        val body = m?.groupValues?.get(1)?.takeIf { it.contains(" - ") }
        val tail = if (body != null && m != null) m.groupValues[2] else ""
        val parts = (body ?: t).split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size == 2) Triple(parts[0], parts[1], tail) else null
    }

    // ดูจากคำร่วมร้อง (x / feat. / ft. ...) ว่าฝั่งไหนคือศิลปิน: 0 / 1 / -1 (ไม่รู้)
    private fun markerIdx(p0: String, p1: String): Int {
        val s0 = strongCollab.containsMatchIn(p0)
        val s1 = strongCollab.containsMatchIn(p1)
        val w0 = weakCollab.containsMatchIn(p0)
        val w1 = weakCollab.containsMatchIn(p1)
        return when {
            s0 != s1 -> if (s0) 0 else 1
            !s0 && !s1 && w0 != w1 -> if (w0) 0 else 1
            else -> -1
        }
    }

    // ดูจากทั้งคลัง: ชื่อที่ตรงกับศิลปินในแท็กเพลงอื่น หรือชื่อที่โผล่ซ้ำหลายเพลง (อีกฝั่งไม่ซ้ำ) คือศิลปิน
    private fun ctxIdx(p0: String, p1: String, tag: String, ctx: TidyContext): Int {
        val n0 = norm(p0)
        val n1 = norm(p1)
        val k0 = n0 in ctx.knownArtists
        val k1 = n1 in ctx.knownArtists
        if (k0 != k1) return if (k0) 0 else 1
        // แท็กเป็นชื่อช่อง/ค่าย: นับเฉพาะเพลงของช่องเดียวกัน (ศิลปินคนเดียวกันมักโผล่ซ้ำหลายเพลงในช่องนั้น) ไม่งั้นนับทั้งคลัง
        val g = norm(tag)
        val c0 = if (g.isNotEmpty()) ctx.groupCount["$g|$n0"] ?: 0 else ctx.partCount[n0] ?: 0
        val c1 = if (g.isNotEmpty()) ctx.groupCount["$g|$n1"] ?: 0 else ctx.partCount[n1] ?: 0
        fun ok(n: String) = n.length >= 3 && n !in notArtist
        if (c0 >= 2 && c1 < 2 && ok(n0)) return 0
        if (c1 >= 2 && c0 < 2 && ok(n1)) return 1
        return -1
    }

    fun analyze(title: String, artist: String, ctx: TidyContext?): Analysis {
        val cleanedArtist = cleanArtistTag(artist)
        var t = pre(title)
        var outArtist = cleanedArtist
        var reason = ""
        var options = emptyList<Pair<String, String>>()
        val untrusted = cleanedArtist.isEmpty() || isChannel(cleanedArtist)

        // ถ้ามีชื่อศิลปินติดอยู่ในชื่อเพลง (ไม่ว่าหน้าหรือหลัง "-") ให้ตัดออก
        var matched = false
        if (cleanedArtist.isNotEmpty()) {
            val a = norm(cleanedArtist)
            val parts = t.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val keep = parts.filterNot {
                    val p = norm(it)
                    p.isNotEmpty() && (p == a || (p.length >= 3 && a.contains(p)))
                }
                if (keep.isNotEmpty() && keep.size < parts.size) {
                    t = keep.joinToString(" - ")
                    matched = true
                }
            }
        }

        // ช่องศิลปินเป็นชื่อช่อง/ค่ายเพลง หรือไม่ทราบ และชื่อเป็น "ศิลปิน - เพลง" หรือ "เพลง - ศิลปิน"
        if (!matched) {
            val tp = twoParts(t)
            if (tp != null) {
                val (p0, p1, tail) = tp
                val parts = listOf(p0, p1)
                fun withTail(x: String) = if (tail.isNotEmpty()) "$x $tail" else x
                var idx = markerIdx(p0, p1)
                if (idx < 0 && ctx != null) idx = ctxIdx(p0, p1, cleanedArtist, ctx)
                // แท็กเป็นศิลปินจริงอยู่แล้ว จะแทนที่ก็ต่อเมื่อฝั่งนั้นมีชื่อในแท็กอยู่ข้างใน (เช่น แท็ก F.HERO / ชื่อ F.HERO x Tilly Birds)
                val allowed = idx >= 0 && (untrusted || norm(parts[idx]).contains(norm(cleanedArtist)))
                if (allowed) {
                    t = withTail(parts[1 - idx])
                    outArtist = tidyEnds(parts[idx])
                } else if (untrusted) {
                    // ไม่แน่ใจ ให้ผู้ใช้เลือกว่าฝั่งไหนคือศิลปิน
                    options = listOf(withTail(p1) to p0, withTail(p0) to p1)
                    reason = "ไม่แน่ใจว่าฝั่งไหนคือศิลปิน"
                }
            }
        }

        if (reason.isEmpty()) {
            if (outArtist.isEmpty()) {
                reason = "ไม่ทราบศิลปิน"
            } else if (outArtist == cleanedArtist && isChannel(cleanedArtist)) {
                reason = "ศิลปินดูเหมือนชื่อช่อง/ค่ายเพลง"
            }
        }

        // ศิลปินให้เหลือแค่ศิลปินหลัก ส่วนผู้ร่วมร้องแบบ feat./ft. ย้ายไปไว้ท้ายชื่อเพลง (ไม่ทิ้งข้อมูล)
        val feat = featSplit.find(outArtist)
        if (feat != null) {
            val primary = outArtist.substring(0, feat.range.first).trim()
            val featured = outArtist.substring(feat.range.last + 1).trim()
            if (primary.isNotEmpty() && featured.isNotEmpty()) {
                outArtist = primary
                if (!norm(t).contains(norm(featured))) t = "$t (feat. $featured)"
            }
        }
        return Analysis(t, outArtist, reason, options)
    }

    // รายการที่ควรแก้หรือควรตรวจ: เปลี่ยนจริง หรือน่าสงสัย (ไม่ทราบศิลปิน / ศิลปินเหมือนชื่อช่อง / ไม่แน่ใจ) คืน null ถ้าเรียบร้อยดี
    fun suggest(song: Song, ctx: TidyContext?): Tidy? {
        val a = analyze(song.title, song.artist, ctx)
        val artist = if (a.artist.isEmpty()) song.artist else a.artist
        val t = Tidy(song, a.title, artist, false, true, a.reason, a.options)
        return if (t.changed || a.reason.isNotEmpty()) t else null
    }

    // ความคล้ายของสองชื่อ 0..1 (เทียบแบบไม่สนตัวพิมพ์/วรรคตอน)
    private fun sim(a: String, b: String): Double {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val (short, long) = if (x.length <= y.length) x to y else y to x
        if (long.contains(short)) return 0.85 + 0.15 * short.length / long.length
        fun bigrams(s: String) = (0 until s.length - 1).map { s.substring(it, it + 2) }
        val bx = bigrams(x)
        val by = bigrams(y).toMutableList()
        if (bx.isEmpty() || by.isEmpty()) return 0.0
        var hit = 0
        for (g in bx) if (by.remove(g)) hit++
        return 2.0 * hit / (bx.size + bigrams(y).size)
    }

    private fun artistSim(a: String, b: String): Double {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return if (x.contains(y) || y.contains(x)) 1.0 else sim(a, b)
    }

    private class Hit(val short: String, val full: String, val artist: String)

    // null = ติดต่ออินเทอร์เน็ต/เซิร์ฟเวอร์ไม่ได้
    private fun search(q: String): List<Hit>? {
        try {
            val c = URL("https://api.deezer.com/search?limit=5&q=" + URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "LocalPod/1.0")
            if (c.responseCode != 200) return null
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (json.has("error")) return null
            val data = json.optJSONArray("data") ?: return emptyList()
            return (0 until data.length()).map {
                val o = data.getJSONObject(it)
                Hit(o.optString("title_short"), o.optString("title"), o.optJSONObject("artist")?.optString("name").orEmpty())
            }
        } catch (e: Exception) {
            return null
        }
    }

    // ตรวจรายการหนึ่งกับ Deezer (ตรวจเฉพาะรายการที่ควรแก้/ควรตรวจ) คืน null ถ้าไม่เจอที่ตรงพอ
    // - มีทางเลือก (ไม่แน่ใจฝั่งไหนคือศิลปิน): เลือกทางที่ทั้งชื่อเพลงและศิลปินตรงกับฐานข้อมูล
    // - แท็กเป็นศิลปินจริง: เชื่อเฉพาะผลที่ศิลปินตรงกัน (กันชื่อเพลงทั่วไปไปแทนที่ศิลปินที่ถูกอยู่แล้ว)
    // - ไม่ทราบศิลปิน/เป็นชื่อช่อง: เดาจากชื่อเพลงอย่างเดียว ติ๊กไว้ให้ไม่ได้ (confident = false)
    fun lookup(item: Tidy, failed: BooleanArray): Tidy? {
        val song = item.song
        val trusted = item.artist.isNotBlank() && !isUnknown(item.artist) && !isChannel(item.artist)
        val query = when {
            item.options.isNotEmpty() -> "${item.options[0].second} ${item.options[0].first}"
            trusted -> "${item.title} ${item.artist}"
            else -> item.title
        }
        val hits = search(query)
        if (hits == null) {
            failed[0] = true
            return null
        }
        fun score(h: Hit, x: String) = maxOf(sim(h.short, x), sim(h.full, x))
        var best: Tidy? = null
        var bestScore = 0.0
        for (h in hits) {
            if (h.artist.isBlank()) continue
            if (item.options.isNotEmpty()) {
                for ((ot, oa) in item.options) {
                    val st = score(h, ot)
                    val sa = artistSim(oa, h.artist)
                    if (st >= 0.9 && sa >= 0.6 && st + sa > bestScore) {
                        best = Tidy(song, ot, if (norm(oa) == norm(h.artist)) h.artist else oa, true, true)
                        bestScore = st + sa
                    }
                }
            } else {
                if (trusted && artistSim(item.artist, h.artist) < 0.6) continue
                val s = score(h, item.title)
                if (s >= 0.9 && s > bestScore) {
                    val artist = if (trusted) (if (norm(item.artist) == norm(h.artist)) h.artist else item.artist) else h.artist
                    best = Tidy(song, item.title, artist, true, trusted)
                    bestScore = s
                }
            }
        }
        val r = best ?: return null
        return if (r.changed) r else null
    }
}

@Composable
fun TidyScreen(
    songs: List<Song>,
    onApply: (List<Tidy>) -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    val ctx = remember(songs) { TidyContext(songs) }
    val local = remember(songs, ctx) { songs.mapNotNull { NameTidy.suggest(it, ctx) }.associateBy { it.song.id } }
    var online by remember { mutableStateOf<Map<Long, Tidy>>(emptyMap()) }
    var manual by remember { mutableStateOf<Map<Long, Tidy>>(emptyMap()) } // ที่ผู้ใช้เลือกทางเลือก/แก้เอง
    var toggled by remember { mutableStateOf(setOf<Long>()) } // เพลงที่ผู้ใช้กลับค่าติ๊กจากค่าเริ่มต้น (ค่าเริ่มต้น = ติ๊กเมื่อ confident)
    var progress by remember { mutableIntStateOf(-1) } // -1 = ไม่ได้ตรวจอยู่
    var total by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Tidy?>(null) }
    val scope = rememberCoroutineScope()

    // ลำดับความสำคัญ: ที่ผู้ใช้ตั้งเอง > ผลออนไลน์ > ที่วิเคราะห์ในเครื่อง (ผูกกับเพลงปัจจุบันเสมอ เผื่อชื่อเพิ่งถูกเปลี่ยนไปแล้ว)
    val items = remember(songs, local, online, manual) {
        songs.mapNotNull { s ->
            manual[s.id]?.copy(song = s)?.takeIf { it.changed }
                ?: online[s.id]?.copy(song = s)?.takeIf { it.changed }
                ?: local[s.id]
        }
    }
    val eligible = items.filter { it.changed } // มีข้อเสนอให้เปลี่ยนจริง (ที่เหลือแค่น่าสงสัย ต้องเลือกทางเลือก/แก้เอง)
    fun isOn(t: Tidy) = t.confident != (t.song.id in toggled)
    val picked = eligible.filter { isOn(it) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ") }
            Text("จัดชื่อเพลง", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text(
            "แสดงเพลงที่ควรแก้หรือน่าสงสัย (ล้างขยะในชื่อ แยกศิลปินออกจากชื่อเพลง ศิลปินเป็นชื่อช่อง/ไม่ทราบ) " +
                "ไม่แตะไฟล์จริง ย้อนกลับได้ ถ้าไม่แน่ใจจะให้คุณเลือกเอง หรือกดไอคอนดินสอเพื่อแก้เอง",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        if (progress >= 0) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else progress.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = colors.surfaceVariant, gapSize = 0.dp, drawStopIndicator = {}
            )
            Text("กำลังตรวจออนไลน์ $progress / $total", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = items.isNotEmpty(), onClick = {
                    message = null
                    // ตรวจเฉพาะรายการที่ควรแก้/น่าสงสัย และที่ผู้ใช้ยังไม่ได้ตั้งเอง
                    val targets = items.filter { manual[it.song.id] == null }
                    total = targets.size
                    scope.launch {
                        var fails = 0
                        var found = 0
                        for ((i, item) in targets.withIndex()) {
                            progress = i
                            val failed = booleanArrayOf(false)
                            val r = withContext(Dispatchers.IO) { NameTidy.lookup(item, failed) }
                            if (failed[0]) {
                                fails++
                                if (fails >= 3) {
                                    message = "ติดต่ออินเทอร์เน็ตไม่ได้ ตรวจเน็ตแล้วลองใหม่"
                                    break
                                }
                            } else {
                                fails = 0
                            }
                            if (r != null) {
                                // การเดาศิลปินจากชื่อเพลงอย่างเดียว ไม่ไปทับรายการที่ล้างขยะแล้วและมั่นใจ แต่เสนอเป็นปุ่มตัวเลือกให้เลือกเอง
                                val keep = !r.confident && item.changed
                                online = online + (item.song.id to if (keep) {
                                    item.copy(options = (item.options + (item.title to r.artist)).distinct(), reason = "เดาศิลปินจากชื่อเพลง: ${r.artist} (ตรวจก่อน)")
                                } else {
                                    r
                                })
                                found++
                            }
                            delay(200)
                        }
                        progress = -1
                        if (message == null) message = "ตรวจเสร็จ: พบข้อมูลตรงกัน $found จาก ${targets.size} รายการ"
                    }
                }) { Text("ตรวจออนไลน์ (${items.size})") }
                TextButton(onClick = onResetAll) { Text("คืนชื่อเดิมทั้งหมด") }
            }
        }
        message?.let { Text(it, color = colors.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }

        Text(
            "พบ ${items.size} เพลง (มีข้อเสนอแก้ ${eligible.size} · ติ๊กไว้ ${picked.size})",
            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            // เลือกทั้งหมด = ติ๊กทุกรายการที่มีข้อเสนอ / เฉพาะรายการปกติ = ติ๊กเฉพาะที่มั่นใจ (ไม่รวมที่เดาจากชื่อเพลงอย่างเดียว)
            TextButton(onClick = { toggled = eligible.filter { !it.confident }.map { it.song.id }.toSet() }) { Text("เลือกทั้งหมด") }
            TextButton(onClick = { toggled = emptySet() }) { Text("เฉพาะรายการปกติ") }
            TextButton(onClick = { toggled = eligible.filter { it.confident }.map { it.song.id }.toSet() }) { Text("ไม่เลือก") }
        }
        HorizontalDivider(color = colors.outlineVariant)

        if (items.isEmpty()) {
            Text("ชื่อเพลงเรียบร้อยดีทุกเพลงแล้ว", Modifier.padding(vertical = 16.dp), color = colors.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(items, key = { _, t -> t.song.id }) { _, t ->
                val on = isOn(t)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = t.changed) { toggled = if (t.song.id in toggled) toggled - t.song.id else toggled + t.song.id }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (t.changed) {
                        Checkbox(on, onCheckedChange = null, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
                    } else {
                        Icon(Icons.Filled.Warning, null, Modifier.padding(end = 12.dp, top = 2.dp, start = 2.dp).size(20.dp), tint = colors.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            t.artist + if (!t.online) "" else if (t.confident) "  · ออนไลน์" else "  · เดาจากชื่อเพลง ตรวจก่อน",
                            style = MaterialTheme.typography.bodySmall, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (t.reason.isNotEmpty()) {
                            Text(t.reason, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                        if (t.changed) {
                            Text(
                                "เดิม: ${t.song.title} — ${t.song.artist}",
                                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                "${t.song.title} — ${t.song.artist}",
                                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        for ((ot, oa) in t.options) {
                            OutlinedButton(
                                onClick = { manual = manual + (t.song.id to Tidy(t.song, ot, oa, false, true, "เลือกเอง")) },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) { Text("ศิลปินคือ $oa", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    IconButton(onClick = { editing = t }) { Icon(Icons.Filled.Edit, "แก้ชื่อเอง", tint = colors.onSurfaceVariant) }
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
        Button(
            onClick = { onApply(picked) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(48.dp)
        ) { Text("ใช้ที่เลือก (${picked.size})") }
    }

    editing?.let { t ->
        ManualDialog(t, { editing = null }) { title, artist ->
            manual = manual + (t.song.id to Tidy(t.song, title.ifBlank { t.song.title }, artist.ifBlank { t.song.artist }, false, true, "แก้เอง"))
            toggled = toggled - t.song.id
            editing = null
        }
    }
}

@Composable
fun ManualDialog(t: Tidy, onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var title by remember { mutableStateOf(t.title) }
    var artist by remember { mutableStateOf(if (NameTidy.isUnknown(t.artist)) "" else t.artist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("แก้ชื่อเอง") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("ชื่อเพลง") }, maxLines = 3, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(artist, { artist = it }, label = { Text("ศิลปิน") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { TextButton({ onOk(title.trim(), artist.trim()) }) { Text("ตกลง") } },
        dismissButton = { TextButton(onDismiss) { Text("ยกเลิก") } }
    )
}
