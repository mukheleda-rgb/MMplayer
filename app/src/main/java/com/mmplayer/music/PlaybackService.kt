package com.mmplayer.music

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = folderItem(ROOT_ID, "MMplayer", "مكتبة الموسيقى والقرآن")
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
            val children = when (parentId) {
                ROOT_ID -> listOf(
                    folderItem(MUSIC_ID, "الموسيقى", "ملفاتك الموسيقية"),
                    folderItem(QURAN_ID, "القرآن الكريم", "التلاوات والقرآن الكريم")
                )
                MUSIC_ID -> queryAudioItems(quranOnly = false)
                QURAN_ID -> queryAudioItems(quranOnly = true)
                else -> emptyList()
            }
            val start = (page.toLong() * pageSize).coerceAtMost(children.size.toLong()).toInt()
            val end = (start + pageSize).coerceAtMost(children.size)
            val paged = if (start < end) children.subList(start, end) else emptyList()
            return Futures.immediateFuture(LibraryResult.ofItemList(paged, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = mediaId.removePrefix(TRACK_PREFIX).toLongOrNull()?.let { findAudioItem(it) }
            return if (item != null) Futures.immediateFuture(LibraryResult.ofItem(item, null))
            else Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE, null))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Pause immediately when Bluetooth or wired headphones are disconnected.
            .setHandleAudioBecomingNoisy(true)
            .build()
        audioSessionId = player.audioSessionId
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, libraryCallback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaLibrarySession = null
        audioSessionId = 0
        super.onDestroy()
    }

    private fun folderItem(id: String, title: String, subtitle: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun queryAudioItems(quranOnly: Boolean): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        return try {
            contentResolver.query(
                uri,
                columns,
                null,
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown artist"
                    val isQuran = (title + " " + artist).contains(
                        Regex("quran|قرآن|قران|سورة|سوره|تلاوة|تلاوه|عبدالباسط|مشاري|السديس|العفاسي", RegexOption.IGNORE_CASE)
                    )
                    if (isQuran == quranOnly) result += playableItem(id, title, artist)
                }
                result
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findAudioItem(id: Long): MediaItem? {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val columns = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        return try {
            contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else playableItem(
                    id,
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown",
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown artist"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun playableItem(id: Long, title: String, artist: String): MediaItem {
        val uri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        return MediaItem.Builder()
            .setMediaId(TRACK_PREFIX + id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    companion object {
        private const val ROOT_ID = "mmplayer_root"
        private const val MUSIC_ID = "mmplayer_music"
        private const val QURAN_ID = "mmplayer_quran"
        private const val TRACK_PREFIX = "mmplayer_track:"
        @Volatile
        var audioSessionId: Int = 0
            private set
    }
}
