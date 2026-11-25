# MMplayer

Native Android music player project. Package: `com.mmplayer.music`.

## Current baseline
- Arabic-first dark Compose interface.
- Reads audio files indexed by Android MediaStore (with runtime permission).
- Separates likely Quran recitations from general music using Arabic/English metadata keywords.
- Search and playback using AndroidX Media3 ExoPlayer.
- GitHub Actions builds a debug APK and uploads it as an artifact.

## Build
Run `gradle assembleDebug` with JDK 17 and Gradle 8.9, or run the **Android APK** GitHub Actions workflow.

## Important
Quran classification is currently an initial metadata-based heuristic. It does not delete, move, or modify source audio files. Playlist persistence, background playback/media notification service, drag-and-drop ordering, equalizer, cloud sync, lyrics, and multiple themes are planned follow-up work.
