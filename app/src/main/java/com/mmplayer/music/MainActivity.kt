package com.mmplayer.music

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long, val isQuran: Boolean)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var tracks by mutableStateOf(listOf<Track>())
    private var current by mutableStateOf<Track?>(null)
    private var playing by mutableStateOf(false)
    private var tab by mutableStateOf("home")
    private var query by mutableStateOf("")

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanAudio()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) scanAudio()
        else permission.launch(p)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF72E0C0), secondary = Color(0xFFB6A0FF),
                background = Color(0xFF10131A), surface = Color(0xFF1B202A)
            )) {
                MMPlayerScreen(
                    tracks = tracks, current = current, playing = playing, tab = tab,
                    query = query, onTab = { tab = it }, onQuery = { query = it },
                    onPlay = { play(it) }, onToggle = { togglePlayback() },
                    onRescan = { scanAudio() }
                )
            }
        }
    }

    private fun scanAudio() {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.IS_MUSIC)
        val found = mutableListOf<Track>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c: Cursor ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Unknown"
                val artist = c.getString(artistCol) ?: "Unknown artist"
                val quran = (title + " " + artist).contains(Regex("quran|قرآن|قران|سورة|سوره|تلاوة|تلاوه|عبدالباسط|مشاري|السديس|العفاسي", RegexOption.IGNORE_CASE))
                found += Track(id, title, artist, ContentUris.withAppendedId(collection, id), c.getLong(durationCol), quran)
            }
        }
        tracks = found
    }

    private fun play(track: Track) {
        current = track
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.play()
        playing = true
    }
    private fun togglePlayback() {
        if (player.isPlaying) { player.pause(); playing = false }
        else if (current != null) { player.play(); playing = true }
    }
    override fun onDestroy() { player.release(); super.onDestroy() }
}

@Composable
private fun MMPlayerScreen(
    tracks: List<Track>, current: Track?, playing: Boolean, tab: String, query: String,
    onTab: (String) -> Unit, onQuery: (String) -> Unit, onPlay: (Track) -> Unit,
    onToggle: () -> Unit, onRescan: () -> Unit
) {
    val arabic = true
    val music = tracks.filter { !it.isQuran }
    val quran = tracks.filter { it.isQuran }
    val visible = when (tab) {
        "quran" -> quran
        "music" -> music
        else -> music
    }.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    Scaffold(
        containerColor = Color(0xFF10131A),
        bottomBar = {
            Column {
                if (current != null) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFF202733)).clickable { onToggle() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Album, null, tint = Color(0xFF72E0C0), modifier = Modifier.size(34.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(current.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current.artist, color = Color.LightGray, fontSize = 12.sp)
                        }
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "تشغيل/إيقاف", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
                NavigationBar(containerColor = Color(0xFF171C25)) {
                    listOf(Triple("home", Icons.Rounded.Home, "الرئيسية"), Triple("music", Icons.Rounded.MusicNote, "الموسيقى"), Triple("quran", Icons.Rounded.MenuBook, "القرآن"), Triple("playlists", Icons.Rounded.QueueMusic, "قوائمي")).forEach { item ->
                        NavigationBarItem(selected = tab == item.first, onClick = { onTab(item.first) }, icon = { Icon(item.second, null) }, label = { Text(item.third, fontSize = 10.sp) })
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MMplayer", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(when(tab) { "quran" -> "مكتبة القرآن الكريم"; "music" -> "مكتبة الموسيقى"; "playlists" -> "قوائم التشغيل"; else -> "مساحتك الصوتية" }, color = Color(0xFF72E0C0), fontSize = 13.sp)
                }
                IconButton(onClick = onRescan) { Icon(Icons.Rounded.Refresh, "تحديث المكتبة", tint = Color.White) }
            }
            if (tab == "home") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeTile("الموسيقى", music.size.toString(), Icons.Rounded.MusicNote, Modifier.weight(1f)) { onTab("music") }
                    HomeTile("القرآن", quran.size.toString(), Icons.Rounded.MenuBook, Modifier.weight(1f)) { onTab("quran") }
                }
                Text("أُضيفت مؤخراً", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                TrackList(tracks.takeLast(8).reversed(), current, onPlay)
            } else if (tab == "playlists") {
                Text("قوائم التشغيل المخصصة ستكون ضمن المرحلة التالية", color = Color.LightGray, modifier = Modifier.padding(top = 30.dp))
            } else {
                OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("ابحث عن ملف صوتي") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
                Text("${visible.size} ملف", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                TrackList(visible, current, onPlay)
            }
        }
    }
}

@Composable
private fun HomeTile(title: String, count: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color(0xFF202733)), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = Color(0xFF72E0C0), modifier = Modifier.size(26.dp))
            Text(count, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.LightGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TrackList(items: List<Track>, current: Track?, onPlay: (Track) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.id }) { track ->
            Row(Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color(0xFF27313D), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (track.isQuran) Icons.Rounded.MenuBook else Icons.Rounded.MusicNote, null, tint = Color(0xFF72E0C0))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, color = if (current?.id == track.id) Color(0xFF72E0C0) else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(track.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                }
                Icon(Icons.Rounded.MoreVert, null, tint = Color.LightGray)
            }
        }
    }
}
