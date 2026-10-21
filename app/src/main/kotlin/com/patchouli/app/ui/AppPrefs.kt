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

	fun init(context: Context) {
		val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
		resume = p.getBoolean("resume", true)
		autoplayNext = p.getBoolean("autoplay_next", true)
		hardwareDecoder = p.getBoolean("hw_decoder", true)
		seekStepSec = p.getInt("seek_step", 10)
		coversOnline = p.getBoolean("covers_online", true)
		kaoBannerVisible = p.getBoolean("kao_banner", true)
	}

	private fun prefs() = PatchouliApp.instance.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	fun updateResume(value: Boolean) { resume = value; prefs().edit { putBoolean("resume", value) } }
	fun updateAutoplayNext(value: Boolean) { autoplayNext = value; prefs().edit { putBoolean("autoplay_next", value) } }
	fun updateHardwareDecoder(value: Boolean) { hardwareDecoder = value; prefs().edit { putBoolean("hw_decoder", value) } }
	fun updateSeekStep(value: Int) { seekStepSec = value.coerceIn(5, 60); prefs().edit { putInt("seek_step", seekStepSec) } }
	fun updateCoversOnline(value: Boolean) { coversOnline = value; prefs().edit { putBoolean("covers_online", value) } }
	fun updateKaoBanner(value: Boolean) { kaoBannerVisible = value; prefs().edit { putBoolean("kao_banner", value) } }
}
