package com.patchouli.app.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.patchouli.app.BuildConfig
import com.patchouli.app.R
import com.patchouli.app.catalog.AniSkip
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.catalog.SkipInterval
import com.patchouli.app.library.Library
import com.patchouli.app.library.Mark
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.PatchouliTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.File

/**
 * libVLC-backed player of local files. Gets a playlist (the videos of a folder, in order) and an
 * index, or a single uri when another app hands us a video. Remembers where each file was left,
 * picks up subtitle files lying next to the video, and shrinks to a small floating window
 * (picture in picture) when asked with the button.
 */
class PlayerActivity : ComponentActivity() {

	private lateinit var libVlc: LibVLC
	lateinit var player: MediaPlayer
		private set

	private var playlist: List<String> = emptyList()
	private var contentUri: Uri? = null
	private var descriptor: ParcelFileDescriptor? = null

	var index by mutableStateOf(0)
		private set
	var title by mutableStateOf("")
		private set
	var hardware by mutableStateOf(AppPrefs.hardwareDecoder)
		private set
	var orientation by mutableStateOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
		private set
	/** True while the activity is the small floating window: no controls, no gestures. */
	var inPip by mutableStateOf(false)
		private set
	/** Subtitle files found next to the current video, in the order they were added. */
	var subtitleFiles by mutableStateOf<List<File>>(emptyList())
		private set
	/** The bookmark this file resumed from when it is old enough that the user may rather start over; null otherwise. */
	var staleResume by mutableStateOf<Mark?>(null)
		private set
	/** The opening, the ending, a recap of the current episode, as AniSkip has them; empty until known, or when nobody marked it. */
	var skips by mutableStateOf<List<SkipInterval>>(emptyList())
		private set
	private var skipJob: Job? = null

	// --- the system's side: the media session (headset buttons, Bluetooth, the lock screen) and the audio focus ---

	private lateinit var session: MediaSession
	private lateinit var audioManager: AudioManager
	private var focusRequest: AudioFocusRequest? = null
	/** Paused by a call or another app's sound: resumes when the focus comes back. */
	private var pausedByFocus = false

	private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
		when (change) {
			AudioManager.AUDIOFOCUS_LOSS -> { pausedByFocus = false; if (player.isPlaying) player.pause() }
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (player.isPlaying) { pausedByFocus = true; player.pause() } }
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.volume = 30
			AudioManager.AUDIOFOCUS_GAIN -> {
				player.volume = 100
				if (pausedByFocus) { pausedByFocus = false; player.play() }
			}
		}
	}

	/** Headphones pulled out: nobody wants the speaker to take over. */
	private val noisyReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && ::player.isInitialized && player.isPlaying) player.pause()
		}
	}

	/** The file on disk being played, or null when another app handed us a content uri (a TV cannot fetch that). */
	val currentFile: String? get() = currentPath

	val count: Int get() = playlist.size
	val hasPrevious: Boolean get() = index > 0
	val hasNext: Boolean get() = index < playlist.lastIndex
	private val currentPath: String? get() = playlist.getOrNull(index)

	private val pickSubtitleLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) addSubtitleFrom(uri)
	}

	/** The play/pause button on the floating window talks back through this. */
	private val pipReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (intent.action != ACTION_PIP) return
			if (player.isPlaying) player.pause() else player.play()
			updatePipParams()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		WindowInsetsControllerCompat(window, window.decorView).apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}
		requestedOrientation = orientation

		val list = intent.getStringArrayListExtra(EXTRA_PLAYLIST)
		when {
			!list.isNullOrEmpty() -> {
				playlist = list
				index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, list.lastIndex)
			}
			intent.data != null -> {
				val uri = intent.data!!
				if (uri.scheme == "file" && uri.path != null) playlist = listOf(uri.path!!) else contentUri = uri
			}
			else -> { finish(); return }
		}

		val options = arrayListOf("--audio-time-stretch") // keeps the pitch when playing faster
		if (BuildConfig.DEBUG) options += "-vv"
		libVlc = LibVLC(this, options)
		player = MediaPlayer(libVlc)
		audioManager = getSystemService(AudioManager::class.java)
		session = MediaSession(this, "Patchouli").apply {
			setCallback(object : MediaSession.Callback() {
				override fun onPlay() { player.play() }
				override fun onPause() { player.pause() }
				override fun onSkipToNext() { next() }
				override fun onSkipToPrevious() { previous() }
				override fun onSeekTo(pos: Long) { player.time = pos }
				override fun onFastForward() { player.time = (player.time + AppPrefs.seekStepSec * 1000L).coerceAtMost(player.length) }
				override fun onRewind() { player.time = (player.time - AppPrefs.seekStepSec * 1000L).coerceAtLeast(0) }
				override fun onStop() { finish() }
			})
			isActive = true
		}
		registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
		load(index, autoplay = false, fromMs = null)

		val filter = IntentFilter(ACTION_PIP)
		if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(pipReceiver, filter)

		setContent {
			PatchouliTheme { PlayerScreen(this) }
		}
	}

	/** Points the player at entry [i]; [fromMs] overrides the remembered position (used when reloading). */
	private fun load(i: Int, autoplay: Boolean, fromMs: Long?) {
		saveProgress()
		ended = false
		index = i.coerceIn(0, maxOf(playlist.lastIndex, 0))
		val path = currentPath
		val media = if (contentUri != null) {
			descriptor?.close()
			val pfd = contentResolver.openFileDescriptor(contentUri!!, "r") ?: run { finish(); return }
			descriptor = pfd
			Media(libVlc, pfd.fileDescriptor)
		} else {
			Media(libVlc, path!!)
		}
		media.setHWDecoderEnabled(hardware, false)
		val mark = path?.let { Progress[it] }?.takeIf { AppPrefs.resume && it.started }
		val start = fromMs ?: mark?.positionMs
		if (start != null && start > Mark.RESUME_MIN_MS) media.addOption(":start-time=${start / 1000.0}")
		// a bookmark left days ago: it resumes, but says so, with a way back to the start
		staleResume = mark?.takeIf { fromMs == null && System.currentTimeMillis() - it.at > STALE_RESUME_MS }
		spuApplied = false
		audioApplied = false
		player.stop()
		player.media = media
		media.release()
		title = path?.let { Names.title(it.substringAfterLast('/')) } ?: contentUri?.lastPathSegment?.let { Names.title(it) }.orEmpty()
		subtitleFiles = path?.let { attachSubtitles(File(it)) }.orEmpty()
		lookupSkips(path)
		updateSessionMetadata(mark?.lengthMs ?: 0L)
		Log.i(TAG, "play ${path ?: contentUri} hw=$hardware from=$start subs=${subtitleFiles.size}")
		if (autoplay) player.play()
	}

	// --- openings and endings ---

	/** Asks AniSkip for the current episode, when the folder is a known series and the file carries an episode number. */
	private fun lookupSkips(path: String?) {
		skipJob?.cancel()
		skips = emptyList()
		if (path == null || !AppPrefs.coversOnline || AppPrefs.skipMode == AppPrefs.SKIP_NEVER) return
		val video = Library.videoAt(path) ?: return
		val episode = video.episode ?: return
		val malId = Library.find(video.folderPath)?.let { Metadata[it] }?.anime?.malId ?: return
		skipJob = lifecycleScope.launch {
			val found = runCatching { AniSkip.intervals(malId, episode, video.durationMs) }.onFailure { Log.w(TAG, "aniskip", it) }.getOrDefault(emptyList())
			if (currentPath == path) skips = found
		}
	}

	// --- the media session ---

	/** What the lock screen and the headset know: playing or paused, where, and how fast. */
	fun syncSession(playing: Boolean, speed: Float) {
		if (!::session.isInitialized) return
		val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
			PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or
			(if (hasNext) PlaybackState.ACTION_SKIP_TO_NEXT else 0L) or (if (hasPrevious) PlaybackState.ACTION_SKIP_TO_PREVIOUS else 0L)
		val state = PlaybackState.Builder()
			.setActions(actions)
			.setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, player.time.coerceAtLeast(0), if (playing) speed else 0f)
			.build()
		runCatching { session.setPlaybackState(state) }
		if (playing) requestFocus()
	}

	fun updateSessionMetadata(lengthMs: Long) {
		if (!::session.isInitialized) return
		val series = currentPath?.let { Library.videoAt(it) }?.let { Library.find(it.folderPath) }?.let { Names.series(it.name) }
		val metadata = MediaMetadata.Builder()
			.putString(MediaMetadata.METADATA_KEY_TITLE, title)
			.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
			.apply { if (series != null) { putString(MediaMetadata.METADATA_KEY_ARTIST, series); putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, series) } }
			.apply { if (lengthMs > 0) putLong(MediaMetadata.METADATA_KEY_DURATION, lengthMs) }
			.build()
		runCatching { session.setMetadata(metadata) }
	}

	private fun requestFocus() {
		if (focusRequest != null) return
		val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
			.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
			.setOnAudioFocusChangeListener(focusListener)
			.build()
		focusRequest = request
		if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = null
	}

	private fun abandonFocus() {
		focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
		focusRequest = null
	}

	/** The user would rather start this one from the beginning after all. */
	fun restartFromStart() {
		staleResume = null
		player.time = 0
	}

	fun dismissStaleResume() { staleResume = null }

	// --- tracks remembered by series ---

	private var spuApplied = false
	private var audioApplied = false
	private val currentFolder: String? get() = currentPath?.substringBeforeLast('/')

	/** The user picked a track: the series remembers it for the next episode. */
	fun rememberTrack(kind: String, id: Int) {
		val folder = currentFolder ?: return
		val name = if (id == -1) TrackPrefs.OFF else tracksOf(kind).firstOrNull { it.id == id }?.name ?: return
		TrackPrefs.set(kind, folder, name)
		if (kind == TrackPrefs.KIND_SUBTITLES) spuApplied = true else audioApplied = true
	}

	/** Called as tracks appear (libVLC adds them one by one): switches to the ones the series was last watched with. */
	fun applyRememberedTracks() {
		val folder = currentFolder ?: return
		if (!spuApplied) spuApplied = apply(TrackPrefs.KIND_SUBTITLES, TrackPrefs.get(TrackPrefs.KIND_SUBTITLES, folder))
		if (!audioApplied) audioApplied = apply(TrackPrefs.KIND_AUDIO, TrackPrefs.get(TrackPrefs.KIND_AUDIO, folder))
	}

	private fun tracksOf(kind: String): List<MediaPlayer.TrackDescription> =
		(if (kind == TrackPrefs.KIND_SUBTITLES) player.spuTracks else player.audioTracks)?.toList().orEmpty()

	/** True once the wish is met, or there is none; false while the tracks it could match are still to come. */
	private fun apply(kind: String, wanted: String?): Boolean {
		if (wanted == null) return true
		val subtitles = kind == TrackPrefs.KIND_SUBTITLES
		if (wanted == TrackPrefs.OFF) {
			if (subtitles) player.setSpuTrack(-1)
			return true
		}
		val hit = tracksOf(kind).filter { it.id != -1 }.firstOrNull { TrackPrefs.matches(it.name, wanted) } ?: return false
		val current = if (subtitles) player.spuTrack else player.audioTrack
		if (current != hit.id) {
			if (subtitles) player.setSpuTrack(hit.id) else player.setAudioTrack(hit.id)
			Log.i(TAG, "$kind track '${hit.name}' as last time in this folder")
		}
		return true
	}

	// --- subtitles ---

	/**
	 * Subtitle files lying next to the video: the same name with .srt/.ass/.ssa/.vtt/.sub, or any
	 * such file whose name starts with the video's. The first one is switched on.
	 */
	private fun attachSubtitles(video: File): List<File> {
		val base = video.nameWithoutExtension
		val found = runCatching {
			video.parentFile?.listFiles { f -> f.isFile && f.extension.lowercase() in SUBTITLE_EXTENSIONS && f.nameWithoutExtension.startsWith(base, ignoreCase = true) }
		}.getOrNull().orEmpty().sortedWith(compareBy({ it.nameWithoutExtension.length }, { it.name }))
		found.forEachIndexed { n, f -> player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(f), n == 0) }
		return found
	}

	/** Opens the system picker for a subtitle file anywhere on the phone. */
	fun pickSubtitle() {
		runCatching { pickSubtitleLauncher.launch(arrayOf("*/*")) }.onFailure { Log.w(TAG, "subtitle picker", it) }
	}

	/** A picked document is copied into the cache (libVLC wants a path) and switched on. */
	private fun addSubtitleFrom(uri: Uri) {
		runCatching {
			val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
				val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
				if (c.moveToFirst() && i >= 0) c.getString(i) else null
			} ?: "subtitle.srt"
			val dir = File(cacheDir, "subs").apply { mkdirs() }
			val copy = File(dir, name)
			contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
			player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(copy), true)
			subtitleFiles = subtitleFiles + copy
		}.onFailure { Log.w(TAG, "subtitle from $uri", it) }
	}

	// --- picture in picture ---

	private val pipSupported: Boolean
		get() = Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

	private fun pipParams(): PictureInPictureParams {
		val track = runCatching { player.currentVideoTrack }.getOrNull()
		val ratio = if (track != null && track.width > 0 && track.height > 0) Rational(track.width, track.height) else Rational(16, 9)
		// the system refuses anything wider than 2.39:1 or taller than 1:2.39
		val safe = if (ratio.toFloat() > 2.39f) Rational(239, 100) else if (ratio.toFloat() < 1 / 2.39f) Rational(100, 239) else ratio
		val playing = runCatching { player.isPlaying }.getOrDefault(false)
		val toggle = RemoteAction(
			Icon.createWithResource(this, if (playing) R.drawable.ic_pause else R.drawable.ic_play),
			getString(if (playing) R.string.pause else R.string.play),
			getString(if (playing) R.string.pause else R.string.play),
			PendingIntent.getBroadcast(this, 1, Intent(ACTION_PIP).setPackage(packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
		)
		// never on its own when the user leaves: only from the button
		return PictureInPictureParams.Builder().setAspectRatio(safe).setActions(listOf(toggle)).apply {
			if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(false)
		}.build()
	}

	/** Keeps the system's copy of the params current (the button, the auto-enter flag). */
	fun updatePipParams() {
		if (!pipSupported) return
		runCatching { setPictureInPictureParams(pipParams()) }.onFailure { Log.w(TAG, "pip params", it) }
	}

	/** Shrinks into the floating window now. */
	fun enterPip() {
		if (!pipSupported) return
		runCatching { enterPictureInPictureMode(pipParams()) }.onFailure { Log.w(TAG, "pip", it) }
	}

	override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
		super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
		inPip = isInPictureInPictureMode
	}

	fun next() { if (hasNext) load(index + 1, autoplay = true, fromMs = null) }
	fun previous() { if (hasPrevious) load(index - 1, autoplay = true, fromMs = null) }

	/** Switches between hardware and software decoding; the file reopens where it was. */
	fun toggleDecoder() {
		hardware = !hardware
		load(index, autoplay = true, fromMs = player.time.coerceAtLeast(0))
	}

	fun cycleOrientation() {
		orientation = when (orientation) {
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
			ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
			else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
		}
		requestedOrientation = orientation
	}

	/** Set when the current file ran to its end, so nothing saves a stray position over the "watched" mark. */
	private var ended = false

	/** Called when a file ends (from libVLC's thread): marks it watched, then the next one, or out. */
	fun onEnded() {
		ended = true
		val path = currentPath
		// once stopped the player may answer 0 for the length: the bookmark remembers it
		val length = player.length.takeIf { it > 0 } ?: path?.let { Progress[it] }?.lengthMs ?: 0L
		runOnUiThread {
			if (path != null && length > 0) Progress.update(path, length, length)
			if (AppPrefs.autoplayNext && hasNext) next() else finish()
		}
	}

	fun saveProgress() {
		if (!::player.isInitialized || ended) return
		val path = currentPath ?: return
		val length = player.length
		val time = player.time
		if (length <= 0 || time < 0) return
		// a 0 right after opening or stopping must not wipe a finished video back to unwatched
		if (time < 1_000 && Progress[path]?.finished == true) return
		Progress.update(path, time, length)
	}

	override fun onPause() {
		super.onPause()
		saveProgress()
		// the floating window is "paused" to Android but must keep playing
		if (!inPip && ::player.isInitialized && player.isPlaying) player.pause()
	}

	override fun onStop() {
		super.onStop()
		// the floating window was swiped away: we are done
		if (inPip) finish()
	}

	override fun onDestroy() {
		super.onDestroy()
		runCatching { unregisterReceiver(pipReceiver) }
		runCatching { unregisterReceiver(noisyReceiver) }
		if (::session.isInitialized) { session.isActive = false; session.release() }
		if (::audioManager.isInitialized) abandonFocus()
		if (::player.isInitialized) {
			saveProgress()
			player.stop()
			player.detachViews()
			player.release()
			libVlc.release()
		}
		descriptor?.close()
	}

	companion object {
		private const val TAG = "Player"
		private const val EXTRA_PLAYLIST = "playlist"
		private const val EXTRA_INDEX = "index"
		private const val ACTION_PIP = "com.patchouli.app.PIP_TOGGLE"
		/** A bookmark older than this is worth a word when it resumes. */
		private const val STALE_RESUME_MS = 3L * 24 * 3600 * 1000
		private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub")

		fun intent(context: Context, playlist: List<String>, index: Int): Intent =
			Intent(context, PlayerActivity::class.java)
				.putStringArrayListExtra(EXTRA_PLAYLIST, ArrayList(playlist))
				.putExtra(EXTRA_INDEX, index)
	}
}
