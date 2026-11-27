package com.mmplayer.music

import android.Manifest
import android.media.audiofx.Equalizer
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long, val quran: Boolean, val album: Long, val size: Long, val added: Long, val path: String) {
    val cover: Uri? get() = if (album > 0) Uri.parse("content://media/external/audio/albumart/" + album) else null
}
data class AppTheme(val name: String, val accent: Color, val bg: Color, val panel: Color, val second: Color)
private val appThemes = listOf(
    AppTheme("Midnight Mint", Color(0xFF72E0C0), Color(0xFF10131A), Color(0xFF202733), Color(0xFFB6A0FF)),
    AppTheme("Ocean Blue", Color(0xFF63B8FF), Color(0xFF081522), Color(0xFF14283A), Color(0xFF8BD8FF)),
    AppTheme("Royal Purple", Color(0xFFC3A0FF), Color(0xFF140F20), Color(0xFF261C38), Color(0xFF8D79FF)),
    AppTheme("Sunset", Color(0xFFFFA36C), Color(0xFF1C1112), Color(0xFF302020), Color(0xFFFFD08A)),
    AppTheme("Emerald", Color(0xFF64D99B), Color(0xFF0C1914), Color(0xFF1A3025), Color(0xFFA2F0C2)),
    AppTheme("Rose", Color(0xFFFF8FAE), Color(0xFF1D1018), Color(0xFF321D2A), Color(0xFFFFC1D2)),
    AppTheme("Graphite", Color(0xFFE0E0E0), Color(0xFF111111), Color(0xFF252525), Color(0xFFAAAAAA)),
    AppTheme("Amber", Color(0xFFFFC45C), Color(0xFF1B160B), Color(0xFF302714), Color(0xFFFFE0A0)),
    AppTheme("Arctic", Color(0xFF8DE8E8), Color(0xFF0B171A), Color(0xFF193034), Color(0xFFB6F5F5)),
    AppTheme("Neon", Color(0xFFB6FF4A), Color(0xFF10140A), Color(0xFF242D16), Color(0xFFE0FF9C))
)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var tracks by mutableStateOf(listOf<Track>())
    private var current by mutableStateOf<Track?>(null)
    private var playing by mutableStateOf(false)
    private var activeQueue by mutableStateOf(listOf<Track>())
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var shuffleEnabled by mutableStateOf(false)
    private var equalizer: Equalizer? = null
    private var equalizerLevels by mutableStateOf(listOf<Int>())
    private var equalizerEnabled by mutableStateOf(false)
    private var equalizerRange by mutableStateOf(-1500..1500)
    private var libraryMessage by mutableStateOf("")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) scan() else {
            tracks = emptyList()
            libraryMessage = "يجب السماح بالوصول إلى ملفات الصوت حتى تظهر الموسيقى"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            MMApp(tracks, current, playing, activeQueue, { track, queue -> play(track, queue) }, { toggle() }, { step(1) }, { step(-1) }, { refreshLibrary() }, libraryMessage, repeatMode, { cycleRepeat() }, shuffleEnabled, { toggleShuffle() }, equalizerEnabled, equalizerLevels, equalizerRange, { setEqualizerEnabled(it) }, { band, level -> setEqualizerBand(band, level) })
        }
        refreshLibrary()
    }
    private fun refreshLibrary() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) scan() else permission.launch(p)
    }
    private fun scan() {
        val u = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATA)
        val found = mutableListOf<Track>()
        try {
        contentResolver.query(u, cols, null, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { c: Cursor ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                val artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown artist"
                val q = (title + " " + artist).contains(Regex("quran|قرآن|قران|سورة|سوره|تلاوة|تلاوه|عبدالباسط|مشاري|السديس|العفاسي", RegexOption.IGNORE_CASE))
                found.add(Track(id, title, artist, ContentUris.withAppendedId(u, id), c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)), q, c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)), c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)), c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)), c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""))
            }
        }
        tracks = found
        libraryMessage = if (found.isEmpty()) "لم يتم العثور على ملفات صوتية. تأكد من وجود ملفات موسيقى في الهاتف." else "تم العثور على " + found.size + " ملف صوتي"
        } catch (e: SecurityException) {
            tracks = emptyList()
            libraryMessage = "لا توجد صلاحية للوصول إلى ملفات الصوت. اضغط تحديث واسمح بالوصول."
        } catch (e: Exception) {
            libraryMessage = "تعذر فحص ملفات الصوت: " + (e.localizedMessage ?: "خطأ غير معروف")
        }
    }
    private fun play(t: Track, requestedQueue: List<Track>) {
        val queue = requestedQueue.distinctBy { it.id }.ifEmpty { listOf(t) }
        val index = queue.indexOfFirst { it.id == t.id }.coerceAtLeast(0)
        activeQueue = queue
        player.setMediaItems(queue.map { MediaItem.fromUri(it.uri) }, index, 0L)
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffleEnabled
        player.prepare()
        player.play()
        current = t
        playing = true
        setupEqualizer()
    }
    private fun toggle() { if (player.isPlaying) player.pause() else if (current != null) player.play() }
    private fun step(d: Int) {
        if (player.mediaItemCount > 0) {
            if (d > 0) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
        }
    }
    private fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = repeatMode
    }
    private fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        player.shuffleModeEnabled = shuffleEnabled
    }
    private fun setupEqualizer() {
        if (equalizer != null || player.audioSessionId == 0) return
        try {
            val eq = Equalizer(0, player.audioSessionId)
            val range = eq.bandLevelRange
            equalizerRange = range[0].toInt()..range[1].toInt()
            equalizerLevels = (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()).toInt() }
            eq.enabled = equalizerEnabled
            equalizer = eq
        } catch (_: Exception) { equalizer = null }
    }
    private fun setEqualizerEnabled(enabled: Boolean) {
        setupEqualizer()
        equalizerEnabled = enabled
        try { equalizer?.enabled = enabled } catch (_: Exception) { }
    }
    private fun setEqualizerBand(band: Int, level: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), level.toShort())
            equalizerLevels = equalizerLevels.toMutableList().also { if (band in it.indices) it[band] = level }
        } catch (_: Exception) { }
    }
    override fun onDestroy() { equalizer?.release(); player.release(); super.onDestroy() }
}
private fun time(ms: Long) = String.format(Locale.getDefault(), "%d:%02d", ms / 60000, (ms / 1000) % 60)
private fun sizeText(n: Long) = if (n >= 1048576) String.format(Locale.US, "%.1f MB", n / 1048576.0) else String.format(Locale.US, "%.0f KB", n / 1024.0)
private fun getLists(c: Context): Map<String, List<Long>> = try {
    val j = JSONObject(c.getSharedPreferences("mmplayer", 0).getString("lists", "{}") ?: "{}")
    buildMap { j.keys().forEach { k -> val a = j.getJSONArray(k); put(k, (0 until a.length()).map { a.getLong(it) }) } }
} catch (_: Exception) { emptyMap() }
private fun putLists(c: Context, m: Map<String, List<Long>>) { val j = JSONObject(); m.forEach { (k, v) -> j.put(k, JSONArray(v)) }; c.getSharedPreferences("mmplayer", 0).edit().putString("lists", j.toString()).apply() }

@Composable
private fun MMApp(tracks: List<Track>, current: Track?, playing: Boolean, playerQueue: List<Track>, play: (Track, List<Track>) -> Unit, toggle: () -> Unit, next: () -> Unit, previous: () -> Unit, refresh: () -> Unit, libraryMessage: String, repeatMode: Int, cycleRepeat: () -> Unit, shuffleEnabled: Boolean, toggleShuffle: () -> Unit, equalizerEnabled: Boolean, equalizerLevels: List<Int>, equalizerRange: IntRange, setEqualizerEnabled: (Boolean) -> Unit, setEqualizerBand: (Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mmplayer", 0) }
    var lang by remember { mutableStateOf(prefs.getString("lang", "ar") ?: "ar") }
    var themeNo by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, 9)) }
    var tab by remember { mutableStateOf("home") }
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("manual") }
    var lists by remember { mutableStateOf(getLists(ctx)) }
    var activeList by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Track?>(null) }
    var newName by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var equalizerDialog by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(runCatching { val a = JSONArray(prefs.getString("manual_order", "[]") ?: "[]"); (0 until a.length()).map { a.getLong(it) } }.getOrDefault(emptyList())) }
    val th = appThemes[themeNo]
    val ar = lang == "ar"
    fun tr(a: String, e: String) = if (ar) a else e

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val raw = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val nm = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported"
            // Preserve M3U item order exactly as listed in the source file.
            val entries = raw.lineSequence().map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotBlank() && !it.startsWith("#") }.toList()
            val ids = entries.mapNotNull { entry ->
                val path = if (entry.startsWith("file://", true)) Uri.parse(entry).path ?: entry else entry
                val title = path.substringAfterLast('/').substringBeforeLast('.', path.substringAfterLast('/'))
                tracks.firstOrNull { it.path.equals(path, true) }?.id ?: tracks.firstOrNull { it.title.equals(title, true) }?.id
            }
            lists = lists + (nm to ids); putLists(ctx, lists)
            Toast.makeText(ctx, tr("تم استيراد القائمة", "Playlist imported") + " (" + ids.size + ")", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(ctx, e.message ?: "Import error", Toast.LENGTH_LONG).show() }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) try {
            val text = buildString {
                append("#EXTM3U\n")
                lists[activeList].orEmpty().mapNotNull { id -> tracks.firstOrNull { it.id == id } }.forEach { t ->
                    append("#EXTINF:").append(t.duration / 1000).append(",").append(t.artist).append(" - ").append(t.title).append("\n")
                    append(if (t.path.isBlank()) t.uri.toString() else t.path).append("\n")
                }
            }
            ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            Toast.makeText(ctx, tr("تم تصدير القائمة", "Playlist exported"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(ctx, e.message ?: "Export error", Toast.LENGTH_LONG).show() }
    }

    val base = when (tab) {
        "quran" -> tracks.filter { it.quran }
        "music" -> tracks.filter { !it.quran }
        "playlist" -> lists[activeList].orEmpty().mapNotNull { id -> tracks.firstOrNull { it.id == id } }
        else -> tracks.filter { !it.quran }
    }
    val filtered = base.filter { it.title.contains(search, true) || it.artist.contains(search, true) }
    val shown = when (sort) {
        "name" -> filtered.sortedBy { it.title.lowercase() }
        "date" -> filtered.sortedByDescending { it.added }
        "size" -> filtered.sortedByDescending { it.size }
        else -> if (manual.isEmpty()) filtered else filtered.sortedBy { manual.indexOf(it.id).let { n -> if (n < 0) Int.MAX_VALUE else n } }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = th.accent, secondary = th.second, background = th.bg, surface = th.panel)) {
        if (expanded && current != null) {
            FullPlayer(current, playerQueue, playing, th, ar, { expanded = false }, toggle, next, previous, { play(it, playerQueue) }, repeatMode, cycleRepeat, shuffleEnabled, toggleShuffle, { equalizerDialog = true })
        } else {
            Scaffold(containerColor = th.bg, bottomBar = {
                Column {
                    AnimatedVisibility(visible = current != null, enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }, exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 }) {
                    if (current != null) Row(Modifier.fillMaxWidth().animateContentSize().padding(8.dp).clip(RoundedCornerShape(15.dp)).background(th.panel).clickable { expanded = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Art(current, Modifier.size(42.dp), th)
                        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text(current.title, color = Color.White, maxLines = 1); Text(current.artist, color = Color.LightGray, fontSize = 11.sp) }
                        IconButton(onClick = toggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = th.accent) }
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White) }
                    }
                    }
                    NavigationBar(containerColor = th.panel) {
                        listOf(Triple("home", Icons.Rounded.Home, tr("الرئيسية", "Home")), Triple("music", Icons.Rounded.MusicNote, tr("الموسيقى", "Music")), Triple("quran", Icons.Rounded.MenuBook, tr("القرآن", "Quran")), Triple("lists", Icons.Rounded.QueueMusic, tr("قوائمي", "Playlists")), Triple("settings", Icons.Rounded.Settings, tr("الإعدادات", "Settings"))).forEach { item ->
                            NavigationBarItem(selected = tab == item.first, onClick = { tab = item.first }, icon = { AnimatedContent(targetState = tab == item.first, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "nav-icon") { active -> Icon(item.second, null, tint = if (active) th.accent else Color.LightGray) } }, label = { Text(item.third, fontSize = 9.sp) })
                        }
                    }
                }
            }) { pad ->
                Column(Modifier.fillMaxSize().animateContentSize(tween(260)).padding(pad).padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("MMplayer", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text(when (tab) { "settings" -> tr("الإعدادات", "Settings"); "quran" -> tr("مكتبة القرآن", "Quran library"); "music" -> tr("مكتبة الموسيقى", "Music library"); "lists" -> tr("قوائم التشغيل", "Playlists"); else -> tr("مساحتك الصوتية", "Your audio space") }, color = th.accent) }
                        IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, null, tint = Color.White) }
                    }
                    when (tab) {
                        "settings" -> {
                            Text(tr("اللغة", "Language"), color = Color.White, fontWeight = FontWeight.Bold)
                            Row { FilterChip(selected = ar, onClick = { lang = "ar"; prefs.edit().putString("lang", lang).apply() }, label = { Text("العربية") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = !ar, onClick = { lang = "en"; prefs.edit().putString("lang", lang).apply() }, label = { Text("English") }) }
                            Text(tr("الثيمات — 10 تصاميم", "Themes — 10 designs"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                            appThemes.forEachIndexed { i, item -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(13.dp)).background(th.panel).clickable { themeNo = i; prefs.edit().putInt("theme", i).apply() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(28.dp).clip(CircleShape).background(item.accent)); Text(item.name, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)); if (themeNo == i) Icon(Icons.Rounded.Check, null, tint = th.accent) } }
                        }
                        "lists" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("قوائم التشغيل المحفوظة", "Saved playlists"), color = Color.White, modifier = Modifier.weight(1f))
                                IconButton(onClick = { dialog = "new" }) { Icon(Icons.Rounded.Add, null, tint = th.accent) }
                                IconButton(onClick = { importFile.launch(arrayOf("text/*", "audio/x-mpegurl", "application/octet-stream")) }) { Icon(Icons.Rounded.FileOpen, null, tint = th.accent) }
                            }
                            lists.keys.forEach { name -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(th.panel).clickable { activeList = name; tab = "playlist" }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.QueueMusic, null, tint = th.accent); Text(name, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 10.dp)); Text(lists[name].orEmpty().size.toString(), color = Color.LightGray); IconButton(onClick = { activeList = name; exportFile.launch(name + ".m3u") }) { Icon(Icons.Rounded.FileDownload, null, tint = th.accent) }; IconButton(onClick = { lists = lists - name; putLists(ctx, lists) }) { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF8888)) } } }
                            if (lists.isEmpty()) Text(tr("أنشئ قائمة أو استورد ملف M3U", "Create a playlist or import an M3U file"), color = Color.LightGray)
                        }
                        "home" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Tile(tr("الموسيقى", "Music"), tracks.count { !it.quran }.toString(), Icons.Rounded.MusicNote, Modifier.weight(1f), th) { tab = "music" }
                                Tile(tr("القرآن", "Quran"), tracks.count { it.quran }.toString(), Icons.Rounded.MenuBook, Modifier.weight(1f), th) { tab = "quran" }
                            }
                            Text(tr("أُضيفت مؤخراً", "Recently added"), color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(vertical = 12.dp))
                            TrackRows(tracks.sortedByDescending { it.added }.take(12), current, th, { play(it, tracks.filter { q -> q.quran == it.quran }) }, { selected = it }, { _, _ -> })
                        }
                        else -> {
                            if (tab == "playlist") Row(verticalAlignment = Alignment.CenterVertically) { Text(activeList, color = Color.White, modifier = Modifier.weight(1f)); IconButton(onClick = { exportFile.launch(activeList + ".m3u") }) { Icon(Icons.Rounded.FileDownload, null, tint = th.accent) } }
                            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text(tr("ابحث عن ملف صوتي", "Search audio")) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
                            var sortMenu by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(shown.size.toString() + " " + tr("ملف", "files"), color = Color.LightGray, modifier = Modifier.weight(1f))
                                Box { TextButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, null); Text(tr("ترتيب", "Sort")) }; DropdownMenu(sortMenu, { sortMenu = false }) {
                                    listOf("manual" to tr("يدوي", "Manual"), "name" to tr("الاسم", "Name"), "date" to tr("التاريخ", "Date"), "size" to tr("الحجم", "Size")).forEach { (v, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { sort = v; sortMenu = false }) }
                                } }
                            }
                            if (tracks.isEmpty() || libraryMessage.isNotBlank()) Text(libraryMessage, color = if (tracks.isEmpty()) th.second else Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                            TrackRows(shown, current, th, { play(it, base) }, { selected = it }, { a, b -> val ids = shown.map { it.id }.toMutableList(); if (a in ids.indices && b in ids.indices) { val v = ids.removeAt(a); ids.add(b, v); manual = ids; prefs.edit().putString("manual_order", JSONArray(ids).toString()).apply(); sort = "manual" } })
                        }
                    }
                }
            }
        }
    }
    if (dialog == "new") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(tr("قائمة جديدة", "New playlist")) }, text = { OutlinedTextField(newName, { newName = it }, label = { Text(tr("اسم القائمة", "Playlist name")) }) }, confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) { lists = lists + (newName.trim() to emptyList()); putLists(ctx, lists); activeList = newName.trim() }; newName = ""; dialog = "" }) { Text(tr("إنشاء", "Create")) } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text(tr("إلغاء", "Cancel")) } })
    if (dialog == "add") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(tr("إضافة إلى قائمة", "Add to playlist")) }, text = { Column { lists.keys.forEach { n -> TextButton(onClick = { val t = selected; if (t != null) { lists = lists + (n to (lists[n].orEmpty() + t.id).distinct()); putLists(ctx, lists) }; selected = null; dialog = "" }) { Text(n) } } } }, confirmButton = { TextButton(onClick = { dialog = ""; selected = null }) { Text(tr("إغلاق", "Close")) } })
    selected?.let { t ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(t.title, maxLines = 1) }, text = { Column {
            TextButton(onClick = { dialog = "add" }) { Text(tr("إضافة إلى قائمة تشغيل", "Add to playlist")) }
            TextButton(onClick = { dialog = "new"; newName = "" }) { Text(tr("إنشاء قائمة جديدة", "Create new playlist")) }
            TextButton(onClick = { dialog = "details" }) { Text(tr("معلومات الملف وموقعه وحجمه", "File details, path and size")) }
            TextButton(onClick = { lists = lists.mapValues { it.value - t.id }; putLists(ctx, lists); selected = null; Toast.makeText(ctx, tr("أزيل من القوائم فقط ولم يُحذف من الجهاز", "Removed from playlists only; file retained"), Toast.LENGTH_LONG).show() }) { Text(tr("إزالة من القوائم", "Remove from playlists")) }
            TextButton(onClick = { Toast.makeText(ctx, tr("تغيير اسم الملف الأصلي يحتاج إذن Android خاصاً؛ لم يتم تغيير الملف", "Renaming the original file requires Android permission; file unchanged"), Toast.LENGTH_LONG).show() }) { Text(tr("تعديل الاسم / الخصائص", "Edit name / properties")) }
        } }, confirmButton = { TextButton(onClick = { selected = null }) { Text(tr("إغلاق", "Close")) } })
        if (dialog == "details") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(tr("تفاصيل الملف", "File details")) }, text = { Column { Text(tr("الفنان", "Artist") + ": " + t.artist); Text(tr("المدة", "Duration") + ": " + time(t.duration)); Text(tr("الحجم", "Size") + ": " + sizeText(t.size)); Text(tr("الموقع", "Location") + ": " + t.path) } }, confirmButton = { TextButton(onClick = { dialog = ""; selected = null }) { Text("OK") } })
    }
}

@Composable
private fun FullPlayer(track: Track, queue: List<Track>, playing: Boolean, th: AppTheme, ar: Boolean, close: () -> Unit, toggle: () -> Unit, next: () -> Unit, previous: () -> Unit, play: (Track) -> Unit, repeatMode: Int, cycleRepeat: () -> Unit, shuffleEnabled: Boolean, toggleShuffle: () -> Unit, openEqualizer: () -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }; var dy by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().background(th.bg).pointerInput(track.id) {
        detectDragGestures(onDragEnd = { if (dy > 85) close() else if (dx < -60) next() else if (dx > 60) previous(); dx = 0f; dy = 0f }) { change, d -> dx += d.x; dy += d.y; change.consume() }
    }.padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(Color.Gray)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White) }; Text(if (ar) "قيد التشغيل" else "Now playing", color = Color.White, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center); Icon(Icons.Rounded.MoreHoriz, null, tint = Color.White) }
        Spacer(Modifier.weight(0.3f))
        AnimatedContent(targetState = track, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) }, label = "now-playing-track") { animatedTrack ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Art(animatedTrack, Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 18.dp), th)
                Spacer(Modifier.height(24.dp))
                Text(animatedTrack.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(animatedTrack.artist, color = Color.LightGray, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = previous) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
            IconButton(onClick = toggle, modifier = Modifier.padding(horizontal = 22.dp).size(66.dp).background(th.accent, CircleShape)) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = th.bg, modifier = Modifier.size(36.dp)) }
            IconButton(onClick = next) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = toggleShuffle) {
                Icon(Icons.Rounded.Shuffle, null, tint = if (shuffleEnabled) th.accent else Color.LightGray)
                Text(if (ar) "عشوائي" else "Shuffle", color = if (shuffleEnabled) th.accent else Color.LightGray)
            }
            TextButton(onClick = cycleRepeat) {
                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, tint = if (repeatMode != Player.REPEAT_MODE_OFF) th.accent else Color.LightGray)
                Text(when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> if (ar) "تكرار ملف" else "Repeat one"
                    Player.REPEAT_MODE_ALL -> if (ar) "تكرار الكل" else "Repeat all"
                    else -> if (ar) "بدون تكرار" else "Repeat off"
                }, color = if (repeatMode != Player.REPEAT_MODE_OFF) th.accent else Color.LightGray)
            }
        }
        Spacer(Modifier.weight(0.2f))
        Text(if (ar) "قائمة التشغيل الحالية" else "Current playlist", color = th.accent, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(queue, key = { it.id }) { item -> Column(Modifier.width(88.dp).clip(RoundedCornerShape(12.dp)).background(if (item.id == track.id) th.accent.copy(alpha = 0.20f) else th.panel).border(if (item.id == track.id) 2.dp else 1.dp, if (item.id == track.id) th.accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable { play(item) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Art(item, Modifier.size(72.dp), th); Text(item.title, color = if (item.id == track.id) th.accent else Color.White, fontSize = 10.sp, fontWeight = if (item.id == track.id) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
    }
}
@Composable
private fun Art(t: Track, m: Modifier, th: AppTheme) {
    Box(m.clip(RoundedCornerShape(13.dp)).background(th.panel), contentAlignment = Alignment.Center) {
        if (t.cover != null) AsyncImage(t.cover, t.title, Modifier.fillMaxSize()) else Icon(if (t.quran) Icons.Rounded.MenuBook else Icons.Rounded.MusicNote, null, tint = th.accent, modifier = Modifier.size(30.dp))
    }
}
@Composable
private fun Tile(title: String, count: String, icon: androidx.compose.ui.graphics.vector.ImageVector, m: Modifier, th: AppTheme, click: () -> Unit) {
    Card(m.clickable { click() }, colors = CardDefaults.cardColors(containerColor = th.panel), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(15.dp)) { Icon(icon, null, tint = th.accent); Text(count, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(title, color = Color.LightGray) } }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRows(rows: List<Track>, current: Track?, th: AppTheme, play: (Track) -> Unit, menu: (Track) -> Unit, move: (Int, Int) -> Unit) {
    var draggingId by remember { mutableStateOf<Long?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(rows, key = { it.id }) { t ->
            val i = rows.indexOfFirst { it.id == t.id }
            var delta by remember(t.id) { mutableFloatStateOf(0f) }
            var dragIndex by remember(t.id) { mutableIntStateOf(i) }
            val isDragging = draggingId == t.id
            Row(
                Modifier.fillMaxWidth()
                    .animateItemPlacement()
                    .animateContentSize()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isDragging -> th.accent.copy(alpha = 0.32f)
                            current?.id == t.id -> th.accent.copy(alpha = 0.16f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        if (isDragging || current?.id == t.id) 2.dp else 0.dp,
                        if (isDragging) th.accent else if (current?.id == t.id) th.accent else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f).pointerInput(t.id) {
                    detectTapGestures(onTap = { play(t) }, onLongPress = { menu(t) })
                }, verticalAlignment = Alignment.CenterVertically) {
                    Art(t, Modifier.size(46.dp), th)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(t.title, color = if (current?.id == t.id) th.accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist + " • " + time(t.duration), color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
                    }
                }
                IconButton(onClick = { menu(t) }) { Icon(Icons.Rounded.MoreVert, null, tint = Color.LightGray) }
                Icon(
                    Icons.Rounded.DragHandle,
                    null,
                    tint = if (isDragging) Color.White else th.accent,
                    modifier = Modifier.padding(horizontal = 8.dp)
                        .pointerInput(t.id, rows.size) {
                            detectDragGestures(
                                onDragStart = { draggingId = t.id; dragIndex = rows.indexOfFirst { it.id == t.id }; delta = 0f },
                                onDragEnd = { draggingId = null; delta = 0f },
                                onDragCancel = { draggingId = null; delta = 0f }
                            ) { change, dragAmount ->
                                delta += dragAmount.y
                                while (delta >= 20f && dragIndex < rows.lastIndex) {
                                    move(dragIndex, dragIndex + 1)
                                    dragIndex++
                                    delta -= 20f
                                }
                                while (delta <= -20f && dragIndex > 0) {
                                    move(dragIndex, dragIndex - 1)
                                    dragIndex--
                                    delta += 20f
                                }
                                change.consume()
                            }
                        }
                )
            }
        }
    }
}
