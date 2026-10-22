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
	fun updateRootPath(value: String) {
		rootPath = value.trim('/')
		rootChosen = true
		prefs().edit { putString("root_path", rootPath); putBoolean("root_chosen", true) }
	}
}
