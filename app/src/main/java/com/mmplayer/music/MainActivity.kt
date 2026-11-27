package com.mmplayer.music

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.media.audiofx.Equalizer
import android.content.ContentUris
import android.content.ComponentName
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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import java.io.File
import java.util.concurrent.Executors
import coil.ImageLoader
import coil.Coil
import coil.disk.DiskCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long, val quran: Boolean, val album: Long, val size: Long, val added: Long, val path: String) {
    val cover: Uri? get() = if (album > 0) Uri.parse("content://media/external/audio/albumart/" + album) else null
}
data class AppTheme(val name: String, val accent: Color, val bg: Color, val panel: Color, val second: Color) { val primaryText: Color get() = if (bg == Color(0xFFF3F5F8)) Color(0xFF17202A) else Color.White; val secondaryText: Color get() = if (bg == Color(0xFFF3F5F8)) Color(0xFF5D6875) else Color(0xFFB8C0CC) }
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
    private lateinit var player: MediaController
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var tracks by mutableStateOf(listOf<Track>())
    private var current by mutableStateOf<Track?>(null)
    private var playing by mutableStateOf(false)
    private var activeQueue by mutableStateOf(listOf<Track>())
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var shuffleEnabled by mutableStateOf(false)
    private var equalizer: Equalizer? = null
    private val currentPositionState = mutableLongStateOf(0L)
    private var currentPositionMs by currentPositionState
    private var equalizerLevels by mutableStateOf(listOf<Int>())
    private var equalizerEnabled by mutableStateOf(false)
    private var equalizerRange by mutableStateOf(-1500..1500)
    private var libraryMessage by mutableStateOf("")
    private val libraryExecutor = Executors.newSingleThreadExecutor()
    private lateinit var artworkLoader: ImageLoader
    private var deleteResultCallback: ((Boolean) -> Unit)? = null
    private var legacyDeleteItems: List<Track> = emptyList()
    private var legacyDeleteIndex = 0
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        if (legacyDeleteItems.isNotEmpty()) {
            if (approved) { legacyDeleteIndex++; deleteLegacyNext() } else finishLegacyDelete(false)
        } else {
            if (approved) scan()
            deleteResultCallback?.invoke(approved)
            deleteResultCallback = null
        }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) scan() else {
            tracks = emptyList()
            libraryMessage = "يجب السماح بالوصول إلى ملفات الصوت حتى تظهر الموسيقى"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tracks = loadCachedTracks()
        if (tracks.isNotEmpty()) libraryMessage = "تم تحميل " + tracks.size + " ملف من الذاكرة المحفوظة"
        artworkLoader = ImageLoader.Builder(applicationContext)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(filesDir, "mmplayer_artwork_cache"))
                    .maxSizeBytes(96L * 1024L * 1024L)
                    .build()
            }
            .build()
        Coil.setImageLoader(artworkLoader)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            player = future.get()
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    current = activeQueue.getOrNull(player.currentMediaItemIndex)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            })
            setContent {
                MMApp(tracks, current, playing, activeQueue, { track, queue -> play(track, queue) }, { toggle() }, { step(1) }, { step(-1) }, { refreshLibrary() }, libraryMessage, repeatMode, { cycleRepeat() }, shuffleEnabled, { toggleShuffle() }, equalizerEnabled, equalizerLevels, equalizerRange, { updateEqualizerEnabled(it) }, { band, level -> setEqualizerBand(band, level) }, currentPositionState, { position -> if (player.mediaItemCount > 0) player.seekTo(position.coerceIn(0L, player.duration.coerceAtLeast(0L))) }, { items, done -> requestDeleteTracks(items, done) })
            }
            refreshLibrary()
            val ticker = android.os.Handler(mainLooper)
            val tick = object : Runnable {
                override fun run() {
                    if (!isFinishing && !isDestroyed) {
                        currentPositionMs = if (player.mediaItemCount > 0) player.currentPosition else currentPositionMs
                        ticker.postDelayed(this, 500)
                    }
                }
            }
            ticker.post(tick)
        }, ContextCompat.getMainExecutor(this))
    }
    private fun requestDeleteTracks(items: List<Track>, onResult: (Boolean) -> Unit) {
        val targets = items.distinctBy { it.id }
        if (targets.isEmpty()) { onResult(false); return }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val request = MediaStore.createDeleteRequest(contentResolver, targets.map { it.uri })
                deleteResultCallback = onResult
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(this, e.localizedMessage ?: "تعذر طلب حذف الملفات", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        } else {
            legacyDeleteItems = targets
            legacyDeleteIndex = 0
            deleteResultCallback = onResult
            deleteLegacyNext()
        }
    }

    private fun deleteLegacyNext() {
        while (legacyDeleteIndex < legacyDeleteItems.size) {
            try {
                contentResolver.delete(legacyDeleteItems[legacyDeleteIndex].uri, null, null)
                legacyDeleteIndex++
            } catch (e: RecoverableSecurityException) {
                deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                return
            } catch (e: Exception) {
                Toast.makeText(this, e.localizedMessage ?: "تعذر حذف الملف من الجهاز", Toast.LENGTH_LONG).show()
                finishLegacyDelete(false)
                return
            }
        }
        finishLegacyDelete(true)
    }

    private fun finishLegacyDelete(success: Boolean) {
        legacyDeleteItems = emptyList()
        legacyDeleteIndex = 0
        if (success) scan()
        deleteResultCallback?.invoke(success)
        deleteResultCallback = null
    }

    private fun refreshLibrary() {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) scan() else permission.launch(p)
    }
    private fun loadCachedTracks(): List<Track> {
        val raw = getSharedPreferences("mmplayer", MODE_PRIVATE).getString("library_cache_v1", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.getLong("id")
                    add(Track(
                        id, item.optString("title", "Unknown"), item.optString("artist", "Unknown artist"),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        item.optLong("duration"), item.optBoolean("quran"),
                        item.optLong("album"), item.optLong("size"), item.optLong("added"), item.optString("path")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCachedTracks(items: List<Track>) {
        val array = JSONArray()
        items.forEach { t ->
            array.put(JSONObject().apply {
                put("id", t.id); put("title", t.title); put("artist", t.artist)
                put("duration", t.duration); put("quran", t.quran); put("album", t.album)
                put("size", t.size); put("added", t.added); put("path", t.path)
            })
        }
        getSharedPreferences("mmplayer", MODE_PRIVATE).edit().putString("library_cache_v1", array.toString()).apply()
    }

    private fun preloadArtwork(items: List<Track>) {
        if (!::artworkLoader.isInitialized) return
        items.asSequence().mapNotNull { it.cover }.distinct().forEach { cover ->
            artworkLoader.enqueue(
                ImageRequest.Builder(applicationContext)
                    .data(cover)
                    .size(256, 256)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }

    private fun scan() {
        libraryExecutor.execute {
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
                saveCachedTracks(found)
                preloadArtwork(found)
                runOnUiThread {
                    tracks = found
                    libraryMessage = if (found.isEmpty()) "لم يتم العثور على ملفات صوتية. تأكد من وجود ملفات موسيقى في الهاتف." else "تم فحص وحفظ " + found.size + " ملف"
                }
            } catch (e: SecurityException) {
                runOnUiThread {
                    if (tracks.isEmpty()) tracks = emptyList()
                    libraryMessage = "لا توجد صلاحية للوصول إلى ملفات الصوت. اضغط تحديث واسمح بالوصول."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    libraryMessage = "تعذر فحص ملفات الصوت: " + (e.localizedMessage ?: "خطأ غير معروف")
                }
            }
        }
    }
    private fun play(t: Track, requestedQueue: List<Track>) {
        val queue = requestedQueue.distinctBy { it.id }.ifEmpty { listOf(t) }
        val index = queue.indexOfFirst { it.id == t.id }.coerceAtLeast(0)
        activeQueue = queue
        player.setMediaItems(queue.map { track -> MediaItem.Builder().setUri(track.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).apply { track.cover?.let { setArtworkUri(it) } }.build()).build() }, index, 0L)
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
        if (equalizer != null || PlaybackService.audioSessionId == 0) return
        try {
            val eq = Equalizer(0, PlaybackService.audioSessionId)
            val range = eq.bandLevelRange
            equalizerRange = range[0].toInt()..range[1].toInt()
            equalizerLevels = (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()).toInt() }
            eq.enabled = equalizerEnabled
            equalizer = eq
        } catch (_: Exception) { equalizer = null }
    }
    private fun updateEqualizerEnabled(enabled: Boolean) {
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
    override fun onPause() { super.onPause(); if (::player.isInitialized) getSharedPreferences("mmplayer", 0).edit().putLong("last_track_id", current?.id ?: -1L).putLong("last_position_ms", player.currentPosition).apply() }
    override fun onDestroy() { equalizer?.release(); controllerFuture?.let { MediaController.releaseFuture(it) }; controllerFuture = null; super.onDestroy() }
}
private fun time(ms: Long) = String.format(Locale.getDefault(), "%d:%02d", ms / 60000, (ms / 1000) % 60)
private fun sizeText(n: Long) = if (n >= 1048576) String.format(Locale.US, "%.1f MB", n / 1048576.0) else String.format(Locale.US, "%.0f KB", n / 1024.0)
private fun getLists(c: Context): Map<String, List<Long>> = try {
    val j = JSONObject(c.getSharedPreferences("mmplayer", 0).getString("lists", "{}") ?: "{}")
    buildMap { j.keys().forEach { k -> val a = j.getJSONArray(k); put(k, (0 until a.length()).map { a.getLong(it) }) } }
} catch (_: Exception) { emptyMap() }
private fun putLists(c: Context, m: Map<String, List<Long>>) { val j = JSONObject(); m.forEach { (k, v) -> j.put(k, JSONArray(v)) }; c.getSharedPreferences("mmplayer", 0).edit().putString("lists", j.toString()).apply() }

@Composable
private fun MMApp(tracks: List<Track>, current: Track?, playing: Boolean, playerQueue: List<Track>, play: (Track, List<Track>) -> Unit, toggle: () -> Unit, next: () -> Unit, previous: () -> Unit, refresh: () -> Unit, libraryMessage: String, repeatMode: Int, cycleRepeat: () -> Unit, shuffleEnabled: Boolean, toggleShuffle: () -> Unit, equalizerEnabled: Boolean, equalizerLevels: List<Int>, equalizerRange: IntRange, setEqualizerEnabled: (Boolean) -> Unit, setEqualizerBand: (Int, Int) -> Unit, currentPositionMs: State<Long>, seekTo: (Long) -> Unit, deleteTracks: (List<Track>, (Boolean) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mmplayer", 0) }
    var lang by remember { mutableStateOf(prefs.getString("lang", "ar") ?: "ar") }
    var themeNo by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, 9)) }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark") }
    var libraryLayout by remember { mutableStateOf((prefs.getString("library_layout", "list") ?: "list").let { if (it == "free") "grid" else it }) }
    var artworkShape by remember { mutableStateOf(prefs.getString("artwork_shape", "square") ?: "square") }
    var selectedTrackIds by remember { mutableStateOf(setOf<Long>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("home") }
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("manual") }
    var lists by remember { mutableStateOf(getLists(ctx)) }
    var activeList by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Track?>(null) }
    var deleteTargets by remember { mutableStateOf<List<Track>>(emptyList()) }
    var pendingDeletePlaylist by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var equalizerDialog by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(runCatching { val a = JSONArray(prefs.getString("manual_order", "[]") ?: "[]"); (0 until a.length()).map { a.getLong(it) } }.getOrDefault(emptyList())) }
    var categoryOverrides by remember { mutableStateOf(runCatching { val j = JSONObject(prefs.getString("category_overrides", "{}") ?: "{}"); buildMap<Long, Boolean> { j.keys().forEach { put(it.toLong(), j.getBoolean(it)) } } }.getOrDefault(emptyMap())) }
    var playCounts by remember { mutableStateOf(runCatching { val j = JSONObject(prefs.getString("play_counts", "{}") ?: "{}"); buildMap<Long, Int> { j.keys().forEach { put(it.toLong(), j.getInt(it)) } } }.getOrDefault(emptyMap())) }
    LaunchedEffect(current?.id) { current?.let { t -> playCounts = playCounts + (t.id to ((playCounts[t.id] ?: 0) + 1)); val j = JSONObject(); playCounts.forEach { (id, n) -> j.put(id.toString(), n) }; prefs.edit().putString("play_counts", j.toString()).putLong("last_track_id", t.id).apply() } }
    var hiddenFolders by remember { mutableStateOf(runCatching { val a = JSONArray(prefs.getString("hidden_folders", "[]") ?: "[]"); (0 until a.length()).map { a.getString(it) }.toSet() }.getOrDefault(emptySet())) }
    var showFoldersDialog by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf("") }
    var playerEffect by remember { mutableStateOf(prefs.getString("player_effect", "glow") ?: "glow") }
    var playerTheme by remember { mutableStateOf(prefs.getString("player_theme", "3d") ?: "3d") }
    LaunchedEffect(Unit) { if (!prefs.getBoolean("player_theme_default_3d_v1", false)) { playerTheme = "3d"; prefs.edit().putString("player_theme", "3d").putBoolean("player_theme_default_3d_v1", true).apply() } }
    var swipeTabsEnabled by remember { mutableStateOf(prefs.getBoolean("swipe_tabs", true)) }
    var backgroundPickerTab by remember { mutableStateOf("home") }
    val tabKeys = listOf("home", "music", "quran", "lists", "settings")
    val pagerState = rememberPagerState(initialPage = tabKeys.indexOf(if (tab == "playlist") "lists" else tab).coerceAtLeast(0), pageCount = { tabKeys.size })
    LaunchedEffect(tab) { val mappedTab = when (tab) { "playlist" -> "lists"; "most", "recent" -> "home"; else -> tab }; val page = tabKeys.indexOf(mappedTab).coerceAtLeast(0); if (pagerState.currentPage != page) pagerState.animateScrollToPage(page) }
    LaunchedEffect(pagerState.settledPage) { val pageTab = tabKeys[pagerState.settledPage.coerceIn(tabKeys.indices)]; if (!((tab == "playlist" && pageTab == "lists") || (tab in listOf("most", "recent") && pageTab == "home"))) tab = pageTab }
    val tabBackgrounds = remember { mutableStateMapOf<String, String>().apply { tabKeys.forEach { put(it, prefs.getString("bg_" + it, "") ?: "") } } }
    val tabDim = remember { mutableStateMapOf<String, Float>().apply { tabKeys.forEach { val saved = prefs.getFloat("dim_" + it, 0.68f); put(it, if (saved < 0.55f) 0.68f else saved) } } }
    val tabBlur = remember { mutableStateMapOf<String, Float>().apply { tabKeys.forEach { put(it, prefs.getFloat("blur_" + it, 0f)) } } }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            tabBackgrounds[backgroundPickerTab] = uri.toString()
            prefs.edit().putString("bg_" + backgroundPickerTab, uri.toString()).apply()
        } catch (_: Exception) { Toast.makeText(ctx, "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show() }
    }
    val folderCounts = remember(tracks) { tracks.groupingBy { it.path.substringBeforeLast('/', "").ifBlank { "Unknown folder" } }.eachCount().toSortedMap() }
    val visibleTracks = remember(tracks, categoryOverrides, hiddenFolders) { tracks.map { t -> categoryOverrides[t.id]?.let { t.copy(quran = it) } ?: t }.filter { it.path.substringBeforeLast('/', "").ifBlank { "Unknown folder" } !in hiddenFolders } }
    val darkMode = when (themeMode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val baseTheme = appThemes[themeNo]
    val th = if (darkMode) baseTheme else baseTheme.copy(bg = Color(0xFFF3F5F8), panel = Color.White, second = Color(0xFF596575))
    val primaryText = th.primaryText
    val secondaryText = th.secondaryText
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

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme(primary = th.accent, secondary = th.second, background = th.bg, surface = th.panel) else lightColorScheme(primary = th.accent, secondary = th.second, background = th.bg, surface = th.panel, onSurface = primaryText, onBackground = primaryText)) {
        val canHandleBack = expanded || equalizerDialog || showFoldersDialog || settingsSection.isNotBlank() || dialog.isNotBlank() || selected != null || pendingDeletePlaylist != null || search.isNotBlank() || tab != "home"
        BackHandler(enabled = canHandleBack) {
            when {
                equalizerDialog -> equalizerDialog = false
                showFoldersDialog -> showFoldersDialog = false
                settingsSection.isNotBlank() -> settingsSection = ""
                dialog.isNotBlank() -> dialog = ""
                pendingDeletePlaylist != null -> pendingDeletePlaylist = null
                selected != null -> selected = null
                expanded -> expanded = false
                search.isNotBlank() -> search = ""
                tab == "playlist" -> { tab = "lists"; activeList = "" }
                tab != "home" -> tab = "home"
            }
        }
        if (expanded && current != null) {
            FullPlayer(current, playerQueue, playing, th, ar, playerEffect, playerTheme, currentPositionMs, { expanded = false }, toggle, next, previous, { play(it, playerQueue) }, { seekTo(it) }, repeatMode, cycleRepeat, shuffleEnabled, toggleShuffle, { equalizerDialog = true })
        } else {
            Scaffold(containerColor = th.bg, bottomBar = {
                Column {
                    AnimatedVisibility(visible = current != null, enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }, exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 }) {
                    if (current != null) Row(Modifier.fillMaxWidth().animateContentSize().padding(8.dp).clip(RoundedCornerShape(15.dp)).background(th.panel).clickable { expanded = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Art(current, Modifier.size(42.dp), th)
                        Column(Modifier.weight(1f).padding(horizontal = 9.dp)) { Text(current.title, color = primaryText, maxLines = 1); Text(current.artist, color = secondaryText, fontSize = 11.sp) }
                        IconButton(onClick = toggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = th.accent) }
                        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White) }
                    }
                    }
                    NavigationBar(containerColor = th.panel) {
                        listOf(Triple("home", Icons.Rounded.Home, tr("الرئيسية", "Home")), Triple("music", Icons.Rounded.MusicNote, tr("الموسيقى", "Music")), Triple("quran", Icons.Rounded.MenuBook, tr("القرآن", "Quran")), Triple("lists", Icons.Rounded.QueueMusic, tr("قوائمي", "Playlists")), Triple("settings", Icons.Rounded.Settings, tr("الإعدادات", "Settings"))).forEach { item ->
                            NavigationBarItem(selected = tabKeys[pagerState.currentPage.coerceIn(tabKeys.indices)] == item.first, onClick = { tab = item.first }, icon = { AnimatedContent(targetState = tab == item.first, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "nav-icon") { active -> Icon(item.second, null, tint = if (active) th.accent else Color.LightGray) } }, label = { Text(item.third, fontSize = 9.sp) })
                        }
                    }
                }
            }) { pad ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(pad),
                    userScrollEnabled = swipeTabsEnabled,
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp
                ) { page ->
                    val pageKey = tabKeys[page]
                    val activeTab = when { pageKey == "lists" && tab == "playlist" -> "playlist"; pageKey == "home" && tab in listOf("most", "recent") -> tab; else -> pageKey }
                    val bg = tabBackgrounds[pageKey].orEmpty()
                    // Each page owns its category data while neighbouring pager pages are composed.
                    val pageBase = remember(activeTab, visibleTracks, lists, activeList) { when (activeTab) {
                        "quran" -> visibleTracks.filter { it.quran }
                        "music" -> visibleTracks.filter { !it.quran }
                        "playlist" -> lists[activeList].orEmpty().mapNotNull { id -> visibleTracks.firstOrNull { it.id == id } }
                        else -> visibleTracks.filter { !it.quran }
                    } }
                    val pageFiltered = remember(pageBase, search) { pageBase.filter { it.title.contains(search, true) || it.artist.contains(search, true) } }
                    val pageShown = remember(pageFiltered, sort, manual) { when (sort) {
                        "name" -> pageFiltered.sortedBy { it.title.lowercase() }
                        "date" -> pageFiltered.sortedByDescending { it.added }
                        "size" -> pageFiltered.sortedByDescending { it.size }
                        else -> if (manual.isEmpty()) pageFiltered else pageFiltered.sortedBy { manual.indexOf(it.id).let { n -> if (n < 0) Int.MAX_VALUE else n } }
                    } }
                    Box(Modifier.fillMaxSize().background(th.bg)) {
                        if (bg.isNotBlank()) AsyncImage(model = Uri.parse(bg), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur((tabBlur[pageKey] ?: 0f).dp))
                        Box(Modifier.fillMaxSize().background(if (bg.isBlank()) Color.Transparent else Color.Black.copy(alpha = tabDim[pageKey] ?: 0.68f)))
                        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            if (activeTab != "home") {
                                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("MMplayer", color = primaryText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                                        Text(when (activeTab) { "settings" -> tr("الإعدادات", "Settings"); "quran" -> tr("مكتبة القرآن", "Quran library"); "music" -> tr("مكتبة الموسيقى", "Music library"); "lists" -> tr("قوائم التشغيل", "Playlists"); "playlist" -> activeList; "most" -> tr("الأكثر تشغيلًا", "Most played"); "recent" -> tr("أُضيفت حديثاً", "Recently added"); else -> tr("مساحتك الصوتية", "Your audio space") }, color = th.accent)
                                    }
                                    IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, null, tint = Color.White) }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxWidth()) {
                    when (activeTab) {
                        "settings" -> {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text(tr("الإعدادات", "Settings"), color = primaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
                            SettingsCategoryCard(tr("المظهر واللغة", "Appearance & language"), tr("اختيار اللغة ولون التطبيق والثيم", "Choose language, app color and theme"), Icons.Rounded.Palette, th) { settingsSection = "appearance" }
                            SettingsCategoryCard(tr("عرض المكتبة", "Library display"), tr("قائمة أو شبكة، وشكل الأغلفة", "List or grid, and artwork shape"), Icons.Rounded.GridView, th) { settingsSection = "library" }
                            SettingsCategoryCard(tr("الخلفيات والمؤثرات", "Backgrounds & effects"), tr("صورة مستقلة لكل واجهة مع التحكم بالتعتيم والتمويه", "Separate image per tab with dim and blur controls"), Icons.Rounded.Wallpaper, th) { settingsSection = "backgrounds" }
                            SettingsCategoryCard(tr("إظهار وإخفاء المجلدات", "Folder visibility"), tr("اختيار المجلدات التي تظهر ملفاتها في المكتبة", "Choose which folders appear in your library"), Icons.Rounded.FolderOpen, th) { showFoldersDialog = true }
                            SettingsCategoryCard(tr("التنقل والحركات", "Navigation & transitions"), tr("السحب بين التبويبات واختيار تأثير الانتقال", "Swipe between tabs and choose transition style"), Icons.Rounded.Swipe, th) { settingsSection = "navigation" }
                            SettingsCategoryCard(tr("مظهر المشغل والمؤثرات", "Player themes & effects"), tr("اختيار شكل المشغل وحركة تغيير الملفات", "Choose player style and track-change animation"), Icons.Rounded.GraphicEq, th) { settingsSection = "player" }
                            SettingsCategoryCard(tr("الصوت والتشغيل", "Audio & playback"), tr("الموازن الصوتي والتكرار والتشغيل العشوائي", "Equalizer, repeat and shuffle"), Icons.Rounded.Equalizer, th) { settingsSection = "playback" }
                            SettingsCategoryCard(tr("مكتبة الصوت", "Audio library"), tr("تحديث وفحص ملفات الصوت في الهاتف", "Refresh and scan audio files on your phone"), Icons.Rounded.LibraryMusic, th) { refresh() }
                            }
                        }
                        "lists" -> {
                            Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr("قوائم التشغيل المحفوظة", "Saved playlists"), color = primaryText, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { dialog = "new" }) { Icon(Icons.Rounded.Add, null, tint = th.accent) }
                                    IconButton(onClick = { importFile.launch(arrayOf("text/*", "audio/x-mpegurl", "application/octet-stream")) }) { Icon(Icons.Rounded.FileOpen, null, tint = th.accent) }
                                }
                                if (lists.isEmpty()) {
                                    Text(tr("أنشئ قائمة أو استورد ملف M3U", "Create a playlist or import an M3U file"), color = secondaryText, modifier = Modifier.padding(top = 12.dp))
                                } else {
                                    if (libraryLayout == "grid") {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            gridItems(lists.keys.toList(), key = { it }) { name ->
                                                Column(
                                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                                        .background(th.panel).clickable { activeList = name; tab = "playlist" }
                                                        .padding(12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    val coverTracks = remember(lists, visibleTracks, name) {
                                                        lists[name].orEmpty().mapNotNull { id -> visibleTracks.firstOrNull { it.id == id } }.take(4)
                                                    }
                                                    Box(
                                                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                                            .background(Brush.linearGradient(listOf(th.accent.copy(alpha = .28f), th.panel))),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (coverTracks.isEmpty()) {
                                                            Icon(Icons.Rounded.QueueMusic, null, tint = th.accent, modifier = Modifier.size(42.dp))
                                                        } else {
                                                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                    Art(coverTracks[0], Modifier.weight(1f).fillMaxHeight(), th)
                                                                    if (coverTracks.size > 1) Art(coverTracks[1], Modifier.weight(1f).fillMaxHeight(), th)
                                                                    else Spacer(Modifier.weight(1f).fillMaxHeight())
                                                                }
                                                                if (coverTracks.size > 2) {
                                                                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                        Art(coverTracks[2], Modifier.weight(1f).fillMaxHeight(), th)
                                                                        if (coverTracks.size > 3) Art(coverTracks[3], Modifier.weight(1f).fillMaxHeight(), th)
                                                                        else Spacer(Modifier.weight(1f).fillMaxHeight())
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Text(name, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                                    Text(lists[name].orEmpty().size.toString() + " " + tr("ملف", "tracks"), color = secondaryText, fontSize = 12.sp)
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                        IconButton(onClick = { activeList = name; exportFile.launch(name + ".m3u") }) { Icon(Icons.Rounded.FileDownload, null, tint = th.accent) }
                                                        IconButton(onClick = { pendingDeletePlaylist = name }) { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF8888)) }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            items(lists.keys.toList(), key = { it }) { name ->
                                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(th.panel).clickable { activeList = name; tab = "playlist" }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    val cover = lists[name].orEmpty().firstNotNullOfOrNull { id -> visibleTracks.firstOrNull { it.id == id } }
                                                    if (cover != null) Art(cover, Modifier.size(48.dp), th) else Icon(Icons.Rounded.QueueMusic, null, tint = th.accent)
                                                    Text(name, color = primaryText, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                                                    Text(lists[name].orEmpty().size.toString(), color = secondaryText)
                                                    IconButton(onClick = { activeList = name; exportFile.launch(name + ".m3u") }) { Icon(Icons.Rounded.FileDownload, null, tint = th.accent) }
                                                    IconButton(onClick = { lists = lists - name; putLists(ctx, lists) }) { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF8888)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "home" -> {
                            val recent = remember(visibleTracks) { visibleTracks.sortedByDescending { it.added }.take(10) }
                            val popular = remember(visibleTracks, playCounts) { visibleTracks.sortedByDescending { playCounts[it.id] ?: 0 }.filter { (playCounts[it.id] ?: 0) > 0 }.take(10) }
                            val last = visibleTracks.firstOrNull { it.id == prefs.getLong("last_track_id", -1L) }
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                                    Text("MMplayer", color = primaryText, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(tr("مساحتك الصوتية", "YOUR AUDIO SPACE"), color = th.accent, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), letterSpacing = 3.sp)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeDashboardTile(tr("الموسيقى", "Music"), visibleTracks.count { !it.quran }.toString(), Icons.Rounded.MusicNote, visibleTracks.filter { !it.quran }.take(3), Modifier.weight(1f), th) { tab = "music" }
                                    HomeDashboardTile(tr("القرآن", "Quran"), visibleTracks.count { it.quran }.toString(), Icons.Rounded.MenuBook, visibleTracks.filter { it.quran }.take(3), Modifier.weight(1f), th) { tab = "quran" }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeDashboardTile(tr("الأكثر تشغيلًا", "Most played"), popular.size.toString(), Icons.Rounded.TrendingUp, popular.take(3), Modifier.weight(1f), th) { tab = "most" }
                                    HomeDashboardTile(tr("أُضيفت حديثاً", "Recently added"), recent.size.toString(), Icons.Rounded.LibraryAdd, recent.take(3), Modifier.weight(1f), th) { tab = "recent" }
                                }
                                Spacer(Modifier.height(10.dp))
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = th.panel), shape = RoundedCornerShape(18.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (last != null) Art(last, Modifier.size(54.dp), th) else Icon(Icons.Rounded.History, null, tint = th.accent, modifier = Modifier.size(48.dp))
                                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text(tr("آخر ملف تم تشغيله", "Last played"), color = th.accent, fontSize = 11.sp)
                                            Text(last?.title ?: tr("لم يتم تشغيل ملف بعد", "Nothing played yet"), color = primaryText, maxLines = 1)
                                            CurrentPositionLabel(currentPositionMs, last?.id == current?.id, prefs.getLong("last_position_ms", 0L), secondaryText)
                                        }
                                        IconButton(onClick = previous) { Icon(Icons.Rounded.SkipPrevious, null, tint = th.accent) }
                                        IconButton(onClick = { if (current == null && last != null) play(last, visibleTracks.filter { it.quran == last.quran }) else toggle() }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = th.accent) }
                                        IconButton(onClick = next) { Icon(Icons.Rounded.SkipNext, null, tint = th.accent) }
                                    }
                                }
                            Text(tr("الأكثر تشغيلًا", "Most played"), color = primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                            HomeTrackStrip(popular, current, th, { play(it, visibleTracks.filter { q -> q.quran == it.quran }) })
                            Text(tr("المضاف حديثاً", "Recently added"), color = primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                            HomeTrackStrip(recent, current, th, { play(it, visibleTracks.filter { q -> q.quran == it.quran }) })
                            }
                        }
                        "most" -> TrackRows(visibleTracks.sortedByDescending { playCounts[it.id] ?: 0 }.filter { (playCounts[it.id] ?: 0) > 0 }.take(10), current, th, { play(it, visibleTracks.filter { q -> q.quran == it.quran }) }, { selected = it }, { _, _ -> })
                        "recent" -> TrackRows(visibleTracks.sortedByDescending { it.added }.take(10), current, th, { play(it, visibleTracks.filter { q -> q.quran == it.quran }) }, { selected = it }, { _, _ -> })
                        else -> {
                            Column(Modifier.fillMaxSize()) {
                                if (activeTab == "playlist") {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(activeList, color = primaryText, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { exportFile.launch(activeList + ".m3u") }) { Icon(Icons.Rounded.FileDownload, null, tint = th.accent) }
                                    }
                                }
                                OutlinedTextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    placeholder = { Text(tr("ابحث عن ملف صوتي", "Search audio")) },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                    singleLine = true
                                )
                                var sortMenu by remember { mutableStateOf(false) }
                                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(pageShown.size.toString() + " " + tr("ملف", "files"), color = secondaryText, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { selectionMode = !selectionMode; if (!selectionMode) selectedTrackIds = emptySet() }) { Icon(if (selectionMode) Icons.Rounded.Close else Icons.Rounded.Checklist, tr("تحديد ملفات", "Select tracks"), tint = th.accent) }
                                    Box {
                                        TextButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, null); Text(tr("ترتيب", "Sort")) }
                                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                            listOf("manual" to tr("يدوي", "Manual"), "name" to tr("الاسم", "Name"), "date" to tr("التاريخ", "Date"), "size" to tr("الحجم", "Size")).forEach { (v, label) ->
                                                DropdownMenuItem(text = { Text(label) }, onClick = { sort = v; sortMenu = false })
                                            }
                                        }
                                    }
                                }
                                if (tracks.isEmpty() || libraryMessage.isNotBlank()) {
                                    Text(libraryMessage, color = if (tracks.isEmpty()) th.second else Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                                }
                                if (selectionMode) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(selectedTrackIds.size.toString() + " " + tr("محدد", "selected"), color = secondaryText, modifier = Modifier.weight(1f))
                                        TextButton(enabled = selectedTrackIds.isNotEmpty(), onClick = { newName = ""; dialog = "new_from_selection" }) { Text(tr("إنشاء قائمة تشغيل", "Create playlist")) }
                                        TextButton(enabled = selectedTrackIds.isNotEmpty(), onClick = { deleteTargets = pageShown.filter { it.id in selectedTrackIds }; dialog = "delete_tracks" }) { Text(tr("حذف من الجهاز", "Delete from device"), color = Color(0xFFFF7777)) }
                                        TextButton(onClick = { selectionMode = false; selectedTrackIds = emptySet() }) { Text(tr("إلغاء", "Cancel")) }
                                    }
                                }
                                TrackLibraryView(
                                    rows = pageShown, current = current, th = th, play = { play(it, pageBase) }, menu = { selected = it },
                                    move = { a, b ->
                                        val ids = pageShown.map { it.id }.toMutableList()
                                        if (a in ids.indices && b in ids.indices) {
                                            val v = ids.removeAt(a); ids.add(b, v); manual = ids
                                            prefs.edit().putString("manual_order", JSONArray(ids).toString()).apply(); sort = "manual"
                                        }
                                    },
                                    layout = libraryLayout, artShape = if (activeTab == "playlist") "square" else artworkShape, selectedIds = selectedTrackIds,
                                    onToggleSelect = if (selectionMode) ({ t -> selectedTrackIds = if (t.id in selectedTrackIds) selectedTrackIds - t.id else selectedTrackIds + t.id }) else null,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                            }
                        }
                    }
                }
            }
        }
    } 
    if (settingsSection.isNotBlank()) {
        val sectionTitle = when (settingsSection) {
            "appearance" -> tr("المظهر واللغة", "Appearance & language")
            "library" -> tr("عرض المكتبة", "Library display")
            "backgrounds" -> tr("الخلفيات والمؤثرات", "Backgrounds & effects")
            "navigation" -> tr("التنقل والحركات", "Navigation & transitions")
            "player" -> tr("مظهر المشغل والمؤثرات", "Player themes & effects")
            else -> tr("الصوت والتشغيل", "Audio & playback")
        }
        AlertDialog(
            containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText, iconContentColor = th.accent,
            onDismissRequest = { settingsSection = "" },
            title = { Text(sectionTitle) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    when (settingsSection) {
                        "appearance" -> {
                            Text(tr("لغة التطبيق", "App language"), color = th.accent, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = ar, onClick = { lang = "ar"; prefs.edit().putString("lang", lang).apply() }, label = { Text("العربية") }, colors = FilterChipDefaults.filterChipColors(containerColor = th.bg, labelColor = th.secondaryText, selectedContainerColor = th.accent.copy(alpha = .22f), selectedLabelColor = th.accent))
                                Spacer(Modifier.width(8.dp))
                                FilterChip(selected = !ar, onClick = { lang = "en"; prefs.edit().putString("lang", lang).apply() }, label = { Text("English") }, colors = FilterChipDefaults.filterChipColors(containerColor = th.bg, labelColor = th.secondaryText, selectedContainerColor = th.accent.copy(alpha = .22f), selectedLabelColor = th.accent))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(tr("نمط الإضاءة", "Appearance mode"), color = th.accent, fontWeight = FontWeight.Bold)
                            listOf("light" to tr("فاتح", "Light"), "dark" to tr("غامق", "Dark"), "system" to tr("حسب النظام", "Follow system")).forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (themeMode == value) th.accent.copy(alpha = .15f) else Color.Transparent).clickable { themeMode = value; prefs.edit().putString("theme_mode", value).apply() }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = primaryText, modifier = Modifier.weight(1f))
                                    if (themeMode == value) Icon(Icons.Rounded.CheckCircle, null, tint = th.accent)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(tr("لون التطبيق والثيم", "App color & theme"), color = th.accent, fontWeight = FontWeight.Bold)
                            appThemes.forEachIndexed { i, item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(if (themeNo == i) th.accent.copy(alpha = 0.16f) else th.panel).clickable { themeNo = i; prefs.edit().putInt("theme", i).apply() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(26.dp).clip(CircleShape).background(item.accent))
                                    Text(item.name, color = primaryText, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                                    if (themeNo == i) Icon(Icons.Rounded.Check, null, tint = th.accent)
                                }
                            }
                        }
                        "library" -> {
                            Text(tr("طريقة عرض الملفات", "Track layout"), color = th.accent, fontWeight = FontWeight.Bold)
                            listOf("list" to tr("قائمة", "List"), "grid" to tr("شبكة", "Grid")).forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (libraryLayout == value) th.accent.copy(alpha = .15f) else Color.Transparent).clickable { libraryLayout = value; prefs.edit().putString("library_layout", value).apply() }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = primaryText, modifier = Modifier.weight(1f))
                                    if (libraryLayout == value) Icon(Icons.Rounded.CheckCircle, null, tint = th.accent)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(tr("شكل صور الملفات", "Artwork shape"), color = th.accent, fontWeight = FontWeight.Bold)
                            listOf("square" to tr("مربعة", "Square"), "circle" to tr("دائرية", "Circular")).forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { artworkShape = value; prefs.edit().putString("artwork_shape", value).apply() }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = primaryText, modifier = Modifier.weight(1f))
                                    if (artworkShape == value) Icon(Icons.Rounded.CheckCircle, null, tint = th.accent)
                                }
                            }
                            Text(tr("يمكنك تحديد عدة ملفات من المكتبة ثم إنشاء قائمة تشغيل منها.", "Select multiple tracks in the library to create a playlist."), color = secondaryText, fontSize = 12.sp)
                        }
                        "backgrounds" -> {
                            Text(tr("اختر صورة وخيارات التأثير لكل واجهة", "Choose an image and effects for each tab"), color = secondaryText, fontSize = 12.sp)
                            tabKeys.forEach { key ->
                                val label = if (ar) mapOf("home" to "الرئيسية", "music" to "الموسيقى", "quran" to "القرآن", "lists" to "القوائم", "settings" to "الإعدادات")[key].orEmpty() else key.replaceFirstChar { it.uppercase() }
                                Text(label, color = th.accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (tabBackgrounds[key].orEmpty().isBlank()) tr("لم يتم اختيار صورة", "No image selected") else tr("تم اختيار صورة", "Image selected"), color = primaryText, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                    TextButton(onClick = { backgroundPickerTab = key; backgroundPicker.launch(arrayOf("image/*")) }) { Text(tr("اختيار", "Choose")) }
                                    if (tabBackgrounds[key].orEmpty().isNotBlank()) IconButton(onClick = { tabBackgrounds[key] = ""; prefs.edit().remove("bg_" + key).apply() }) { Icon(Icons.Rounded.Delete, null, tint = th.second) }
                                }
                                Text(tr("التعتيم", "Dim"), color = primaryText, fontSize = 11.sp)
                                Slider(value = tabDim[key] ?: 0.68f, onValueChange = { tabDim[key] = it; prefs.edit().putFloat("dim_" + key, it).apply() }, valueRange = 0.55f..0.96f)
                                Text(tr("التمويه", "Blur"), color = primaryText, fontSize = 11.sp)
                                Slider(value = tabBlur[key] ?: 0f, onValueChange = { tabBlur[key] = it; prefs.edit().putFloat("blur_" + key, it).apply() }, valueRange = 0f..24f)
                            }
                        }
                        "navigation" -> {
                            Text(tr("التنقل بالسحب الأفقي", "Swipe navigation"), color = th.accent, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("تفعيل السحب يميناً ويساراً", "Enable left / right swiping"), color = primaryText, modifier = Modifier.weight(1f))
                                Switch(checked = swipeTabsEnabled, onCheckedChange = { swipeTabsEnabled = it; prefs.edit().putBoolean("swipe_tabs", it).apply() })
                            }
                            Text(tr("تتحرك الصفحات مع إصبعك أثناء السحب، ثم تستقر عند الإفلات.", "Pages follow your finger while dragging, then settle on release."), color = secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        "player" -> {
                            Text(tr("ثيم المشغل", "Player theme"), color = th.accent, fontWeight = FontWeight.Bold)
                            listOf(
                                "3d" to tr("ثلاثي الأبعاد (افتراضي)", "3D Neumorphism (Default)"),
                                "glass" to tr("الزجاج الشفاف", "Glassmorphism"),
                                "neon" to tr("النيون", "Neon"),
                                "cinematic" to tr("السينمائي", "Cinematic"),
                                "wave" to tr("الموجات الصوتية", "Waveform"),
                                "minimal" to tr("البساطة الفاخرة", "Minimal Premium"),
                                "cassette" to tr("الكاسيت القديم", "Retro Cassette"),
                                "vinyl" to tr("الأسطوانة الدوارة", "Vinyl Record")
                            ).forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (playerTheme == value) th.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { playerTheme = value; prefs.edit().putString("player_theme", value).apply() }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = primaryText, modifier = Modifier.weight(1f))
                                    if (playerTheme == value) Icon(Icons.Rounded.CheckCircle, null, tint = th.accent)
                                }
                            }
                            Text(tr("تأثير تغيير الملف أثناء التشغيل", "Track-change effect"), color = th.accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                            listOf("glow" to tr("تلاشي ناعم", "Soft fade"), "slide" to tr("انزلاق أفقي", "Horizontal slide"), "rise" to tr("صعود", "Rise"), "drop" to tr("هبوط", "Drop"), "softzoom" to tr("تكبير لطيف", "Soft zoom"), "blur" to tr("ضبابي", "Blur dissolve"), "bounce" to tr("ارتداد", "Bounce"), "ripple" to tr("تموّج", "Ripple"), "shutter" to tr("غالق سريع", "Shutter"), "fade" to tr("تلاشي متداخل", "Cross fade")).forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (playerEffect == value) th.accent.copy(alpha = 0.16f) else Color.Transparent).clickable { playerEffect = value; prefs.edit().putString("player_effect", value).apply() }.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = primaryText, modifier = Modifier.weight(1f))
                                    if (playerEffect == value) Icon(Icons.Rounded.CheckCircle, null, tint = th.accent)
                                }
                            }
                        }
                        "playback" -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("الموازن الصوتي", "Equalizer"), color = primaryText, modifier = Modifier.weight(1f))
                                TextButton(onClick = { settingsSection = ""; equalizerDialog = true }) { Text(tr("فتح", "Open")) }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("التكرار", "Repeat"), color = primaryText, modifier = Modifier.weight(1f))
                                TextButton(onClick = cycleRepeat) { Text(when (repeatMode) { Player.REPEAT_MODE_ONE -> tr("مقطع واحد", "One"); Player.REPEAT_MODE_ALL -> tr("الكل", "All"); else -> tr("إيقاف", "Off") }) }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("التشغيل العشوائي", "Shuffle"), color = primaryText, modifier = Modifier.weight(1f))
                                Switch(checked = shuffleEnabled, onCheckedChange = { toggleShuffle() })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { settingsSection = "" }) { Text(tr("تم", "Done")) } }
        )
    }

    if (showFoldersDialog) {
        AlertDialog(
            containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText, iconContentColor = th.accent,
            onDismissRequest = { showFoldersDialog = false },
            title = { Text(tr("إظهار وإخفاء المجلدات", "Show / hide folders")) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        tr("أوقف المفتاح لإخفاء جميع الملفات الصوتية داخل المجلد. يمكنك إظهارها مجدداً في أي وقت.", "Turn a switch off to hide all audio files in that folder. You can show them again at any time."),
                        color = secondaryText,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (folderCounts.isEmpty()) {
                        Text(tr("لم يتم العثور على مجلدات. حدّث مكتبة الصوت أولاً.", "No folders found. Refresh the audio library first."))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(folderCounts.entries.toList(), key = { it.key }) { entry ->
                                val path = entry.key
                                val label = path.substringAfterLast('/').ifBlank { path }
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(label, color = primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(entry.value.toString() + " " + tr("ملف صوتي", "audio files"), color = secondaryText, fontSize = 11.sp, maxLines = 1)
                                    }
                                    Switch(
                                        checked = path !in hiddenFolders,
                                        onCheckedChange = { show ->
                                            hiddenFolders = if (show) hiddenFolders - path else hiddenFolders + path
                                            prefs.edit().putString("hidden_folders", JSONArray(hiddenFolders.toList()).toString()).apply()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFoldersDialog = false }) { Text(tr("تم", "Done")) } }
        )
    }
    if (equalizerDialog) {
        AlertDialog(
            containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText, iconContentColor = th.accent,
            onDismissRequest = { equalizerDialog = false },
            title = { Text(if (ar) "المعادل الصوتي" else "Equalizer") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ar) "تفعيل المعادل" else "Enable equalizer", modifier = Modifier.weight(1f))
                        Switch(checked = equalizerEnabled, onCheckedChange = setEqualizerEnabled)
                    }
                    val labels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
                    equalizerLevels.forEachIndexed { index, level ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(labels.getOrElse(index) { "Band " + (index + 1) }, modifier = Modifier.weight(1f))
                                Text((level / 100).toString() + " dB")
                            }
                            Slider(value = level.toFloat().coerceIn(equalizerRange.first.toFloat(), equalizerRange.last.toFloat()), onValueChange = { setEqualizerBand(index, it.toInt()) }, valueRange = equalizerRange.first.toFloat()..equalizerRange.last.toFloat(), enabled = equalizerEnabled)
                        }
                    }
                    if (equalizerLevels.isEmpty()) Text(if (ar) "المعادل غير متاح على هذا الجهاز حالياً." else "Equalizer is not currently available on this device.")
                }
            },
            confirmButton = { TextButton(onClick = { equalizerDialog = false }) { Text(if (ar) "تم" else "Done") } }
        )
    }
    if (pendingDeletePlaylist != null) {
        val playlistName = pendingDeletePlaylist!!
        AlertDialog(containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText,
            onDismissRequest = { pendingDeletePlaylist = null },
            title = { Text(tr("تأكيد حذف القائمة", "Confirm playlist deletion")) },
            text = { Text(tr("هل تريد حذف قائمة التشغيل «$playlistName»؟ لن يتم حذف الملفات الصوتية من الهاتف.", "Delete playlist '$playlistName'? Audio files on the phone will remain.")) },
            confirmButton = { TextButton(onClick = { lists = lists - playlistName; putLists(ctx, lists); if (activeList == playlistName) { activeList = ""; tab = "lists" }; pendingDeletePlaylist = null }) { Text(tr("حذف القائمة", "Delete playlist"), color = Color(0xFFFF7777)) } },
            dismissButton = { TextButton(onClick = { pendingDeletePlaylist = null }) { Text(tr("إلغاء", "Cancel")) } })
    }
    if (dialog == "delete_tracks") {
        AlertDialog(containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText,
            onDismissRequest = { dialog = ""; deleteTargets = emptyList() },
            title = { Text(tr("تأكيد الحذف من الجهاز", "Confirm device deletion")) },
            text = { Text(if (deleteTargets.size == 1) tr("سيتم حذف الملف نهائياً من الهاتف بعد موافقة Android. لا يمكن التراجع عن هذه العملية.", "This file will be permanently deleted from the phone after Android approval. This cannot be undone.") else tr("سيتم حذف " + deleteTargets.size + " ملفات نهائياً من الهاتف بعد موافقة Android. لا يمكن التراجع عن هذه العملية.", deleteTargets.size.toString() + " files will be permanently deleted after Android approval. This cannot be undone.")) },
            confirmButton = { TextButton(onClick = { val targets = deleteTargets; deleteTracks(targets) { success -> if (success) { val removed = targets.map { it.id }.toSet(); lists = lists.mapValues { (_, ids) -> ids.filterNot { it in removed } }; putLists(ctx, lists); selectedTrackIds = selectedTrackIds - removed }; deleteTargets = emptyList(); dialog = "" } }) { Text(tr("حذف نهائياً", "Delete permanently"), color = Color(0xFFFF7777)) } },
            dismissButton = { TextButton(onClick = { dialog = ""; deleteTargets = emptyList() }) { Text(tr("إلغاء", "Cancel")) } })
    }
    if (dialog == "new" || dialog == "new_from_selection") AlertDialog(containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText, iconContentColor = th.accent, onDismissRequest = { dialog = "" }, title = { Text(tr("قائمة جديدة", "New playlist"), color = primaryText) }, text = { OutlinedTextField(newName, { newName = it }, label = { Text(tr("اسم القائمة", "Playlist name")) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.primaryText, unfocusedTextColor = th.primaryText, focusedBorderColor = th.accent, unfocusedBorderColor = th.secondaryText, focusedLabelColor = th.accent, unfocusedLabelColor = th.secondaryText, cursorColor = th.accent)) }, confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) { val ids = if (dialog == "new_from_selection") selectedTrackIds.toList() else emptyList(); lists = lists + (newName.trim() to ids); putLists(ctx, lists); activeList = newName.trim(); selectedTrackIds = emptySet(); selectionMode = false }; newName = ""; dialog = "" }) { Text(tr("إنشاء", "Create")) } }, dismissButton = { TextButton(onClick = { dialog = ""; selectedTrackIds = emptySet(); selectionMode = false }) { Text(tr("إلغاء", "Cancel")) } })
    if (dialog == "add") AlertDialog(containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText, iconContentColor = th.accent, onDismissRequest = { dialog = "" }, title = { Text(tr("إضافة إلى قائمة", "Add to playlist")) }, text = { Column { lists.keys.forEach { n -> TextButton(onClick = { val t = selected; if (t != null) { lists = lists + (n to (lists[n].orEmpty() + t.id).distinct()); putLists(ctx, lists) }; selected = null; dialog = "" }) { Text(n) } } } }, confirmButton = { TextButton(onClick = { dialog = ""; selected = null }) { Text(tr("إغلاق", "Close")) } })
    selected?.let { t ->
        AlertDialog(
            containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText, iconContentColor = th.accent,
            onDismissRequest = { selected = null }, title = { Text(t.title, maxLines = 1) }, text = { Column {
            TextButton(onClick = { val updated = categoryOverrides + (t.id to !t.quran); categoryOverrides = updated; val json = JSONObject(); updated.forEach { (id, isQuran) -> json.put(id.toString(), isQuran) }; prefs.edit().putString("category_overrides", json.toString()).apply(); selected = null; Toast.makeText(ctx, if (t.quran) tr("تم نقل الملف إلى تصنيف الموسيقى", "File moved to Music") else tr("تم نقل الملف إلى تصنيف القرآن", "File moved to Quran"), Toast.LENGTH_SHORT).show() }) { Text(if (t.quran) tr("تغيير التصنيف إلى الموسيقى", "Change category to Music") else tr("تغيير التصنيف إلى القرآن", "Change category to Quran")) }
            TextButton(onClick = { dialog = "add" }) { Text(tr("إضافة إلى قائمة تشغيل", "Add to playlist")) }
            TextButton(onClick = { dialog = "new"; newName = "" }) { Text(tr("إنشاء قائمة جديدة", "Create new playlist")) }
            TextButton(onClick = { dialog = "details" }) { Text(tr("معلومات الملف وموقعه وحجمه", "File details, path and size")) }
            TextButton(onClick = { lists = lists.mapValues { it.value - t.id }; putLists(ctx, lists); selected = null; Toast.makeText(ctx, tr("أزيل من القوائم فقط ولم يُحذف من الجهاز", "Removed from playlists only; file retained"), Toast.LENGTH_LONG).show() }) { Text(tr("إزالة من قوائم التشغيل", "Remove from playlists only")) }
            TextButton(onClick = { deleteTargets = listOf(t); selected = null; dialog = "delete_tracks" }) { Text(tr("حذف نهائياً من الجهاز", "Permanently delete from device"), color = Color(0xFFFF7777)) }
            TextButton(onClick = { Toast.makeText(ctx, tr("تغيير اسم الملف الأصلي يحتاج إذن Android خاصاً؛ لم يتم تغيير الملف", "Renaming the original file requires Android permission; file unchanged"), Toast.LENGTH_LONG).show() }) { Text(tr("تعديل الاسم / الخصائص", "Edit name / properties")) }
        } }, confirmButton = { TextButton(onClick = { selected = null }) { Text(tr("إغلاق", "Close")) } })
        if (dialog == "details") AlertDialog(
            containerColor = th.panel, titleContentColor = th.primaryText, textContentColor = th.secondaryText, iconContentColor = th.accent,
            onDismissRequest = { dialog = "" }, title = { Text(tr("تفاصيل الملف", "File details")) }, text = { Column { Text(tr("الفنان", "Artist") + ": " + t.artist); Text(tr("المدة", "Duration") + ": " + time(t.duration)); Text(tr("الحجم", "Size") + ": " + sizeText(t.size)); Text(tr("الموقع", "Location") + ": " + t.path) } }, confirmButton = { TextButton(onClick = { dialog = ""; selected = null }) { Text("OK") } })
    }
}

@Composable
private fun CurrentPositionLabel(position: State<Long>, isCurrent: Boolean, savedPosition: Long, color: Color) {
    Text(time(if (isCurrent) position.value else savedPosition), color = color, fontSize = 11.sp)
}

@Composable
private fun trackTransition(effect: String): AnimatedContentTransitionScope<Track>.() -> ContentTransform = {
    when (effect) {
        "fade" -> fadeIn(tween(180)) togetherWith fadeOut(tween(120))
        "slide" -> (slideInHorizontally(tween(190)) { it / 3 } + fadeIn(tween(160))) togetherWith (slideOutHorizontally(tween(150)) { -it / 4 } + fadeOut(tween(120)))
        "rise" -> (slideInVertically(tween(210)) { it / 2 } + fadeIn(tween(180))) togetherWith (slideOutVertically(tween(150)) { -it / 4 } + fadeOut(tween(120)))
        "drop" -> (slideInVertically(tween(210)) { -it / 2 } + fadeIn(tween(180))) togetherWith (slideOutVertically(tween(150)) { it / 4 } + fadeOut(tween(120)))
        "softzoom" -> (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220))) togetherWith (fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)))
        "blur" -> (fadeIn(tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = tween(240))) togetherWith fadeOut(tween(160))
        "bounce" -> (slideInHorizontally(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) { it / 2 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(170)) { -it / 5 } + fadeOut(tween(140)))
        "ripple" -> (fadeIn(tween(260)) + scaleIn(initialScale = 0.90f, animationSpec = tween(260))) togetherWith (fadeOut(tween(170)) + scaleOut(targetScale = 1.04f, animationSpec = tween(170)))
        "shutter" -> (slideInHorizontally(tween(180)) { it } + fadeIn(tween(140))) togetherWith (slideOutHorizontally(tween(150)) { -it } + fadeOut(tween(110)))
        else -> (fadeIn(tween(200)) + scaleIn(initialScale = 0.97f, animationSpec = tween(200))) togetherWith fadeOut(tween(140))
    }
}

@Composable
private fun WaveformBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase = transition.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val count = 32
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val amp = if (playing) 0.2f + 0.8f * abs(sin(phase.value + i * 0.45f) * cos(phase.value * 0.7f + i * 0.2f)) else 0.1f
            val h = size.height * amp
            drawRoundRect(color.copy(alpha = 0.45f + 0.55f * amp), topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2f), size = androidx.compose.ui.geometry.Size(barWidth, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
        }
    }
}

@Composable
private fun CassetteDeck(track: Track, th: AppTheme, accent: Color, angle: Float) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).aspectRatio(1.45f).shadow(14.dp, shape).clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF5A3A24), Color(0xFF2A1B12)))).border(2.dp, accent.copy(alpha = 0.5f), shape)) {
        Art(track, Modifier.align(Alignment.TopCenter).fillMaxWidth(0.86f).fillMaxHeight(0.5f).padding(top = 12.dp), th)
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.86f).fillMaxHeight(0.38f).padding(bottom = 12.dp)) {
            drawRoundRect(Color(0xFF14100C), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
            val radius = size.height * 0.42f
            listOf(size.width * 0.28f, size.width * 0.72f).forEach { cx ->
                val center = Offset(cx, size.height / 2f)
                drawCircle(Color(0xFFE8DCC8), radius, center)
                for (k in 0 until 6) {
                    val a = Math.toRadians((k * 60f + angle).toDouble())
                    drawLine(Color(0xFF14100C), center, Offset(center.x + radius * 0.85f * cos(a).toFloat(), center.y + radius * 0.85f * sin(a).toFloat()), strokeWidth = 3.dp.toPx())
                }
                drawCircle(Color(0xFF14100C), radius * 0.35f, center)
            }
        }
    }
}

@Composable
private fun FullPlayer(track: Track, queue: List<Track>, playing: Boolean, th: AppTheme, ar: Boolean, playerEffect: String, playerTheme: String, currentPositionMs: State<Long>, close: () -> Unit, toggle: () -> Unit, next: () -> Unit, previous: () -> Unit, play: (Track) -> Unit, seekTo: (Long) -> Unit, repeatMode: Int, cycleRepeat: () -> Unit, shuffleEnabled: Boolean, toggleShuffle: () -> Unit, openEqualizer: () -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }; var dy by remember { mutableFloatStateOf(0f) }
    val playerBackground = when (playerTheme) {
        "3d" -> Color(0xFF171A22)
        "glass" -> Color(0xCC17212D)
        "neon" -> Color(0xFF080B12)
        "cinematic" -> Color(0xFF090A10)
        "wave" -> Color(0xFF0B1720)
        "minimal" -> Color(0xFF101114)
        "cassette" -> Color(0xFF17130F)
        "vinyl" -> Color(0xFF0D0D12)
        else -> th.bg
    }
    val playerAccent = when (playerTheme) {
        "3d" -> Color(0xFF83B9E8)
        "neon" -> Color(0xFF39D8F2)
        "cinematic" -> Color(0xFFD8B77A)
        "wave" -> Color(0xFF48D6C8)
        "cassette" -> Color(0xFFE2A56D)
        "vinyl" -> Color(0xFFB8A0FF)
        "minimal" -> Color(0xFFE5E7EB)
        else -> th.accent
    }
    var reelAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, playerTheme) {
        if (playing && (playerTheme == "vinyl" || playerTheme == "cassette")) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                reelAngle = (reelAngle + (now - last) / 1_000_000_000f * if (playerTheme == "vinyl") 36f else 90f) % 360f
                last = now
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(playerBackground, when (playerTheme) { "3d" -> Color(0xFF202D40); "cinematic" -> Color(0xFF19151D); "vinyl" -> Color(0xFF211B30); else -> playerBackground }, Color(0xFF080A10)))).pointerInput(track.id) {
        detectDragGestures(onDragEnd = { if (dy > 85) close() else if (dx < -60) next() else if (dx > 60) previous(); dx = 0f; dy = 0f }) { change, d -> dx += d.x; dy += d.y; change.consume() }
    }.padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(Color.Gray)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White) }; Text(if (ar) "قيد التشغيل" else "Now playing", color = th.primaryText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center); Icon(Icons.Rounded.MoreHoriz, null, tint = Color.White) }
        Spacer(Modifier.weight(0.3f))
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).aspectRatio(1f)
                .then(if (playerTheme == "3d") Modifier.shadow(22.dp, RoundedCornerShape(26.dp)) else Modifier)
                .clip(if (playerTheme == "vinyl") CircleShape else RoundedCornerShape(if (playerTheme == "cassette") 18.dp else 24.dp))
                .background(when (playerTheme) {
                    "3d" -> Brush.linearGradient(listOf(Color(0xFF303642), Color(0xFF11141B)))
                    "glass" -> Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x221A2A3A)))
                    "neon" -> Brush.linearGradient(listOf(Color(0xFF102A35), Color(0xFF160F2B)))
                    "cinematic" -> Brush.linearGradient(listOf(Color(0xFF2C2520), Color(0xFF08090E)))
                    "wave" -> Brush.linearGradient(listOf(Color(0xFF10383B), Color(0xFF0A1420)))
                    "cassette" -> Brush.linearGradient(listOf(Color(0xFF49301F), Color(0xFF17120F)))
                    "vinyl" -> Brush.linearGradient(listOf(Color(0xFF393044), Color(0xFF0B0B10)))
                    else -> Brush.linearGradient(listOf(Color(0xFF292D35), Color(0xFF0B0D12)))
                })
                .border(if (playerTheme == "3d" || playerTheme == "glass") 1.dp else 0.dp, Color.White.copy(alpha = .14f), if (playerTheme == "vinyl") CircleShape else RoundedCornerShape(24.dp))
        ) {
            if (playerTheme == "cassette") {
                CassetteDeck(track, th, playerAccent, reelAngle)
            } else {
                AnimatedContent(targetState = track, transitionSpec = trackTransition(playerEffect), label = "player-artwork") { animatedArt ->
                    Art(animatedArt, Modifier.fillMaxSize().graphicsLayer { if (playerTheme == "vinyl") rotationZ = reelAngle }, th, circular = playerTheme == "vinyl")
                }
                if (playerTheme == "vinyl") {
                    Canvas(Modifier.fillMaxSize()) {
                        for (i in 1..6) drawCircle(Color.Black.copy(alpha = 0.18f), radius = size.minDimension / 2f * (0.30f + i * 0.11f), style = Stroke(1.dp.toPx()))
                    }
                    Box(Modifier.align(Alignment.Center).size(22.dp).clip(CircleShape).background(Color(0xFFB8A0FF)).border(4.dp, Color(0xFF17151D), CircleShape))
                }
            }
        }
        if (playerTheme == "wave" || playerTheme == "neon") WaveformBars(playing, playerAccent, Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        AnimatedContent(targetState = track, transitionSpec = trackTransition(playerEffect), label = "now-playing-track") { animatedTrack ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(animatedTrack.title, color = th.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(animatedTrack.artist, color = th.secondaryText, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Slider(value = currentPositionMs.value.coerceIn(0L, track.duration.coerceAtLeast(1L)).toFloat(), onValueChange = { seekTo(it.toLong()) }, valueRange = 0f..track.duration.coerceAtLeast(1L).toFloat(), colors = SliderDefaults.colors(thumbColor = playerAccent, activeTrackColor = playerAccent, inactiveTrackColor = Color.Gray.copy(alpha = 0.45f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time(currentPositionMs.value), color = th.secondaryText, fontSize = 11.sp)
                Text("-" + time((track.duration - currentPositionMs.value).coerceAtLeast(0L)) + " / " + time(track.duration), color = th.secondaryText, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (playerTheme == "3d") {
                NeumorphicPlayerButton(Icons.Rounded.SkipPrevious, if (ar) "السابق" else "Previous", previous, th, Modifier.size(62.dp))
                Spacer(Modifier.width(22.dp))
                NeumorphicPlayerButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "إيقاف مؤقت" else "تشغيل", toggle, th, Modifier.size(78.dp), primary = true)
                Spacer(Modifier.width(22.dp))
                NeumorphicPlayerButton(Icons.Rounded.SkipNext, if (ar) "التالي" else "Next", next, th, Modifier.size(62.dp))
            } else {
                IconButton(onClick = previous) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
                IconButton(onClick = toggle, modifier = Modifier.padding(horizontal = 22.dp).size(66.dp).background(playerAccent, CircleShape)) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = th.bg, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = next) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
            }
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
        IconButton(onClick = openEqualizer) { Icon(Icons.Rounded.Equalizer, null, tint = playerAccent) }
        Spacer(Modifier.weight(0.2f))
        Text(if (ar) "قائمة التشغيل الحالية" else "Current playlist", color = th.accent, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(queue, key = { it.id }) { item -> Column(Modifier.width(88.dp).clip(RoundedCornerShape(12.dp)).background(if (item.id == track.id) th.accent.copy(alpha = 0.20f) else th.panel).border(if (item.id == track.id) 2.dp else 1.dp, if (item.id == track.id) th.accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable { play(item) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) { Art(item, Modifier.size(72.dp), th); Text(item.title, color = if (item.id == track.id) th.accent else Color.White, fontSize = 10.sp, fontWeight = if (item.id == track.id) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
    }
}
@Composable
private fun Art(t: Track, m: Modifier, th: AppTheme, circular: Boolean = false) {
    Box(m.clip(if (circular) CircleShape else RoundedCornerShape(13.dp)).background(th.panel), contentAlignment = Alignment.Center) {
        if (t.cover != null) { AsyncImage(model = t.cover, contentDescription = t.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))) } else DefaultCoverArtwork(t, Modifier.fillMaxSize(), th)
    }
}
@Composable
private fun SettingsCategoryCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, th: AppTheme, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = th.panel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = th.accent, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(title, color = th.primaryText, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = th.secondaryText, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = th.accent)
        }
    }
}

@Composable
private fun HomeDashboardTile(title: String, count: String, icon: androidx.compose.ui.graphics.vector.ImageVector, covers: List<Track>, modifier: Modifier, th: AppTheme, click: () -> Unit) {
    Card(modifier.height(112.dp).clickable { click() }, colors = CardDefaults.cardColors(containerColor = th.panel), shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                covers.take(3).forEachIndexed { index, track ->
                    Box(Modifier.size(43.dp).rotate(30f + index * 2).clip(RoundedCornerShape(8.dp)).background(th.bg)) {
                        if (track.cover != null) AsyncImage(track.cover, track.title, Modifier.fillMaxSize())
                        else Art(track, Modifier.fillMaxSize(), th)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Icon(icon, null, tint = th.accent, modifier = Modifier.size(19.dp))
                Text(count, color = th.primaryText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(title, color = th.secondaryText, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeTrackStrip(rows: List<Track>, current: Track?, th: AppTheme, play: (Track) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
        items(rows, key = { it.id }) { t ->
            Column(Modifier.width(112.dp).clip(RoundedCornerShape(13.dp)).background(if (current?.id == t.id) th.accent.copy(alpha = 0.22f) else th.panel).clickable { play(t) }.padding(8.dp)) {
                Art(t, Modifier.size(92.dp).align(Alignment.CenterHorizontally), th)
                Text(t.title, color = th.primaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = th.secondaryText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Tile(title: String, count: String, icon: androidx.compose.ui.graphics.vector.ImageVector, m: Modifier, th: AppTheme, click: () -> Unit) {
    Card(m.clickable { click() }, colors = CardDefaults.cardColors(containerColor = th.panel), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(15.dp)) { Icon(icon, null, tint = th.accent); Text(count, color = th.primaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(title, color = th.secondaryText) } }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRows(rows: List<Track>, current: Track?, th: AppTheme, play: (Track) -> Unit, menu: (Track) -> Unit, move: (Int, Int) -> Unit, modifier: Modifier = Modifier, selectedIds: Set<Long> = emptySet(), onToggleSelect: ((Track) -> Unit)? = null, circular: Boolean = false) {
    var draggingId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val currentMove by rememberUpdatedState(move)
    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(rows, key = { it.id }) { t ->
            var dragOffset by remember(t.id) { mutableFloatStateOf(0f) }
            var dragIndex by remember(t.id) { mutableIntStateOf(rows.indexOfFirst { it.id == t.id }) }
            val isDragging = draggingId == t.id
            Row(
                Modifier.fillMaxWidth()
                    .zIndex(if (isDragging) 2f else 0f)
                    .offset { IntOffset(0, if (isDragging) dragOffset.toInt() else 0) }
                    .graphicsLayer {
                        val scale = if (isDragging) 1.045f else 1f
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = if (isDragging) 14.dp.toPx() else 0f
                    }
                    .animateItemPlacement()
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        when {
                            isDragging -> th.accent.copy(alpha = 0.38f)
                            current?.id == t.id -> th.accent.copy(alpha = 0.24f)
                            else -> Color.Transparent
                        }
                    )
                    .pointerInput(t.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = t.id
                                dragIndex = rows.indexOfFirst { it.id == t.id }.coerceAtLeast(0)
                                dragOffset = 0f
                            },
                            onDragEnd = { draggingId = null; dragOffset = 0f },
                            onDragCancel = { draggingId = null; dragOffset = 0f }
                        ) { change, dragAmount ->
                            dragOffset += dragAmount.y
                            change.consume()
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val own = visible.firstOrNull { it.key == t.id }
                            if (own != null) {
                                val draggedCenter = own.offset + own.size / 2f + dragOffset
                                val below = visible.firstOrNull { it.index == dragIndex + 1 }
                                val above = visible.firstOrNull { it.index == dragIndex - 1 }
                                if (below != null && draggedCenter > below.offset + below.size / 2f) {
                                    currentMove(dragIndex, dragIndex + 1)
                                    dragIndex += 1
                                    dragOffset -= own.size.toFloat()
                                } else if (above != null && draggedCenter < above.offset + above.size / 2f) {
                                    currentMove(dragIndex, dragIndex - 1)
                                    dragIndex -= 1
                                    dragOffset += own.size.toFloat()
                                }
                            }
                        }
                    }
                    .padding(vertical = 5.dp, horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f).clickable { if (onToggleSelect != null) onToggleSelect(t) else play(t) }, verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleSelect != null) Checkbox(checked = t.id in selectedIds, onCheckedChange = { onToggleSelect(t) })
                    Art(t, Modifier.size(46.dp), th, circular)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(t.title, color = if (current?.id == t.id) th.accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist + " • " + time(t.duration), color = th.secondaryText, fontSize = 11.sp, maxLines = 1)
                    }
                }
                IconButton(onClick = { menu(t) }) { Icon(Icons.Rounded.MoreVert, null, tint = Color.LightGray) }
                Icon(Icons.Rounded.DragHandle, null, tint = if (isDragging) Color.White else th.accent, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}


@Composable
private fun TrackLibraryView(rows: List<Track>, current: Track?, th: AppTheme, play: (Track) -> Unit, menu: (Track) -> Unit, move: (Int, Int) -> Unit, layout: String, artShape: String, selectedIds: Set<Long>, onToggleSelect: ((Track) -> Unit)?, modifier: Modifier = Modifier) {
    val circular = artShape == "circle"
    when (layout) {
        "grid" -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = modifier, contentPadding = PaddingValues(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gridItems(rows, key = { it.id }) { t ->
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (current?.id == t.id) th.accent.copy(alpha = .16f) else th.panel).clickable { if (onToggleSelect != null) onToggleSelect(t) else play(t) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Art(t, Modifier.fillMaxWidth().aspectRatio(1f), th, circular)
                        if (onToggleSelect != null) Checkbox(checked = t.id in selectedIds, onCheckedChange = { onToggleSelect(t) }, modifier = Modifier.align(Alignment.TopEnd))
                    }
                    Text(t.title, color = th.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Text(time(t.duration), color = th.secondaryText, fontSize = 10.sp)
                    IconButton(onClick = { menu(t) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, null, tint = th.secondaryText) }
                }
            }
        }
        "free" -> {
            val horizontal = rememberScrollState()
            val vertical = rememberScrollState()
            BoxWithConstraints(modifier = modifier) {
                val canvasWidth = maxWidth * 2.15f
                Column(Modifier.width(canvasWidth).horizontalScroll(horizontal).verticalScroll(vertical).padding(10.dp)) {
                    rows.chunked(4).forEachIndexed { rowIndex, group ->
                        Row(Modifier.fillMaxWidth().padding(start = if (rowIndex % 2 == 0) 0.dp else 48.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            group.forEach { t ->
                                Column(Modifier.width(112.dp).clip(RoundedCornerShape(18.dp)).background(if (current?.id == t.id) th.accent.copy(alpha = .18f) else th.panel).clickable { if (onToggleSelect != null) onToggleSelect(t) else play(t) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        Art(t, Modifier.size(76.dp), th, circular)
                                        if (onToggleSelect != null) Checkbox(checked = t.id in selectedIds, onCheckedChange = { onToggleSelect(t) }, modifier = Modifier.align(Alignment.TopEnd))
                                    }
                                    Text(t.title, color = th.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                                    Text(t.artist, color = th.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> TrackRows(rows, current, th, play, menu, move, modifier, selectedIds, onToggleSelect, circular)
    }
}

@Composable
private fun DefaultCoverArtwork(track: Track, modifier: Modifier, th: AppTheme) {
    val base = if (track.quran) Color(0xFF142D32) else Color(0xFF17152E)
    val glow = if (track.quran) Color(0xFF56C6AE) else Color(0xFF8C79FF)
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(base, th.panel, Color(0xFF080B16)))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) * 0.30f
            for (i in 1..4) {
                drawCircle(glow.copy(alpha = 0.12f / i), radius = radius * (0.72f + i * 0.22f), center = center, style = Stroke(width = (1.5f + i).dp.toPx()))
            }
            val bars = listOf(.20f,.36f,.58f,.82f,.56f,.34f,.68f,.42f,.22f)
            val gap = size.width * .035f
            val barW = (size.width * .58f - gap * (bars.size - 1)) / bars.size
            val startX = (size.width - (barW * bars.size + gap * (bars.size - 1))) / 2f
            bars.forEachIndexed { i, h ->
                val height = size.height * h * .34f
                drawRoundRect(glow.copy(alpha = .42f + (i % 3) * .16f), topLeft = Offset(startX + i * (barW + gap), center.y - height / 2f), size = androidx.compose.ui.geometry.Size(barW, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW))
            }
        }
        Icon(if (track.quran) Icons.Rounded.MenuBook else Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size( if (track.quran) 38.dp else 42.dp ))
    }
}

@Composable
private fun NeumorphicPlayerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    th: AppTheme,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val shape = CircleShape
    val face = if (primary) Brush.linearGradient(listOf(th.accent, th.accent.copy(alpha = 0.72f))) else Brush.linearGradient(listOf(Color(0xFF303642), Color(0xFF171B23)))
    Box(
        modifier
            .shadow(if (primary) 18.dp else 12.dp, shape)
            .clip(shape)
            .background(face)
            .border(1.dp, if (primary) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().padding(3.dp).border(1.dp, Color.Black.copy(alpha = if (primary) 0.06f else 0.25f), shape))
        Icon(icon, contentDescription = description, tint = if (primary) Color(0xFF10141B) else Color(0xFFE5EAF3), modifier = Modifier.size(if (primary) 38.dp else 30.dp))
    }
}
