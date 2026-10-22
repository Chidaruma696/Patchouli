package com.patchouli.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.patchouli.app.PatchouliApp

/** App-wide switches, observable from Compose. */
object AppPrefs {
	private const val FILE = "app"

	/** Open each video where it was left. */
	var resume by mutableStateOf(true)
		private set
	/** When a video ends, start the next file of the folder. */
	var autoplayNext by mutableStateOf(true)
		private set
	/** Hardware decoding first; software plays anything but eats the battery. */
	var hardwareDecoder by mutableStateOf(true)
		private set
	/** Seconds a double tap skips. */
	var seekStepSec by mutableStateOf(10)
		private set
	/** Ask AniList for covers and summaries of the folders. */
	var coversOnline by mutableStateOf(true)
		private set
	/** Keep Android Open notice in the library; hidden once dismissed, can be shown again from Settings. */
	var kaoBannerVisible by mutableStateOf(true)
		private set
	/** The folder the library lives in, relative to the storage root ("Movies"); empty is the whole phone. */
	var rootPath by mutableStateOf("")
		private set
	/** Whether the user has answered the root question at least once. */
	var rootChosen by mutableStateOf(false)
		private set
	/** How the shelves are laid out: 0 is a list, 2 to 4 are grid columns. */
	var libraryColumns by mutableStateOf(2)
		private set
	/** The order of the shelves: by name, by what was played last, or by what arrived last. */
	var librarySort by mutableStateOf(SORT_NAME)
		private set
	/** Leave out what is already watched: whole folders on the shelves, episodes inside one. */
	var hideWatched by mutableStateOf(false)
		private set

	const val SORT_NAME = "name"
	const val SORT_RECENT = "recent"
	const val SORT_ADDED = "added"

	/** Openings and endings, as AniSkip knows them: a button to skip them, skipped on their own, or left alone. */
	var skipMode by mutableStateOf(SKIP_BUTTON)
		private set

	const val SKIP_BUTTON = "button"
	const val SKIP_AUTO = "auto"
	const val SKIP_NEVER = "never"

	/** How big the subtitles are drawn, as libVLC's relative font size: the smaller the number, the bigger the text. Takes effect when the player opens. */
	var subtitleSize by mutableStateOf(SUBS_NORMAL)
		private set
	/** Write every episode watched to the end on the user's AniList list (when an account is connected). */
	var anilistSync by mutableStateOf(true)
		private set

	const val SUBS_SMALL = 20
	const val SUBS_NORMAL = 16
	const val SUBS_LARGE = 12
	const val SUBS_HUGE = 8

	/** The pen of the subtitles without a style of their own: colour, outline, a dark box behind, bold. Taken when the player opens. */
	var subtitleColor by mutableStateOf(SUB_WHITE)
		private set
	var subtitleOutline by mutableStateOf(OUTLINE_NORMAL)
		private set
	var subtitleBackground by mutableStateOf(false)
		private set
	var subtitleBold by mutableStateOf(false)
		private set
	/** Ask the display for a refresh rate the video divides evenly (needs a 90/120 Hz panel to matter). */
	var frameRateMatch by mutableStateOf(true)
		private set
	/** A compressor on the sound: dialogue up, openings down. */
	var normalizeAudio by mutableStateOf(false)
		private set

	const val SUB_WHITE = 0xFFFFFF
	const val SUB_YELLOW = 0xFFFF00
	const val SUB_CYAN = 0x00FFFF
	const val OUTLINE_THIN = 2
	const val OUTLINE_NORMAL = 4
	const val OUTLINE_THICK = 6

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		rootPath = p.getString("root_path", "") ?: ""
		rootChosen = p.getBoolean("root_chosen", false)
		resume = p.getBoolean("resume", true)
		autoplayNext = p.getBoolean("autoplay_next", true)
		hardwareDecoder = p.getBoolean("hw_decoder", true)
		seekStepSec = p.getInt("seek_step", 10)
		coversOnline = p.getBoolean("covers_online", true)
		kaoBannerVisible = p.getBoolean("kao_banner", true)
		libraryColumns = p.getInt("library_columns", 2)
		librarySort = p.getString("library_sort", SORT_NAME) ?: SORT_NAME
		hideWatched = p.getBoolean("hide_watched", false)
		skipMode = p.getString("skip_mode", SKIP_BUTTON) ?: SKIP_BUTTON
		subtitleSize = p.getInt("subtitle_size", SUBS_NORMAL)
		anilistSync = p.getBoolean("anilist_sync", true)
		subtitleColor = p.getInt("subtitle_color", SUB_WHITE)
		subtitleOutline = p.getInt("subtitle_outline", OUTLINE_NORMAL)
		subtitleBackground = p.getBoolean("subtitle_background", false)
		subtitleBold = p.getBoolean("subtitle_bold", false)
		frameRateMatch = p.getBoolean("frame_rate_match", true)
		normalizeAudio = p.getBoolean("normalize_audio", false)
	}

	private fun prefs() = PatchouliApp.instance.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	fun updateResume(value: Boolean) { resume = value; prefs().edit { putBoolean("resume", value) } }
	fun updateAutoplayNext(value: Boolean) { autoplayNext = value; prefs().edit { putBoolean("autoplay_next", value) } }
	fun updateHardwareDecoder(value: Boolean) { hardwareDecoder = value; prefs().edit { putBoolean("hw_decoder", value) } }
	fun updateSeekStep(value: Int) { seekStepSec = value.coerceIn(5, 60); prefs().edit { putInt("seek_step", seekStepSec) } }
	fun updateCoversOnline(value: Boolean) { coversOnline = value; prefs().edit { putBoolean("covers_online", value) } }
	fun updateKaoBanner(value: Boolean) { kaoBannerVisible = value; prefs().edit { putBoolean("kao_banner", value) } }
	fun updateLibraryColumns(value: Int) { libraryColumns = if (value == 0) 0 else value.coerceIn(2, 4); prefs().edit { putInt("library_columns", libraryColumns) } }
	fun updateLibrarySort(value: String) { librarySort = value; prefs().edit { putString("library_sort", value) } }
	fun updateHideWatched(value: Boolean) { hideWatched = value; prefs().edit { putBoolean("hide_watched", value) } }
	fun updateSkipMode(value: String) { skipMode = value; prefs().edit { putString("skip_mode", value) } }
	fun updateSubtitleSize(value: Int) { subtitleSize = value; prefs().edit { putInt("subtitle_size", value) } }
	fun updateAnilistSync(value: Boolean) { anilistSync = value; prefs().edit { putBoolean("anilist_sync", value) } }
	fun updateSubtitleColor(value: Int) { subtitleColor = value; prefs().edit { putInt("subtitle_color", value) } }
	fun updateSubtitleOutline(value: Int) { subtitleOutline = value; prefs().edit { putInt("subtitle_outline", value) } }
	fun updateSubtitleBackground(value: Boolean) { subtitleBackground = value; prefs().edit { putBoolean("subtitle_background", value) } }
	fun updateSubtitleBold(value: Boolean) { subtitleBold = value; prefs().edit { putBoolean("subtitle_bold", value) } }
	fun updateFrameRateMatch(value: Boolean) { frameRateMatch = value; prefs().edit { putBoolean("frame_rate_match", value) } }
	fun updateNormalizeAudio(value: Boolean) { normalizeAudio = value; prefs().edit { putBoolean("normalize_audio", value) } }
	fun updateRootPath(value: String) {
		rootPath = value.trim('/')
		rootChosen = true
		prefs().edit { putString("root_path", rootPath); putBoolean("root_chosen", true) }
	}
}
