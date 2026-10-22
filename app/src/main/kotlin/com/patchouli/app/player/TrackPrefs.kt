package com.patchouli.app.player

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Which subtitle and audio track a series is watched with, by folder: the choice made in one
 * episode carries over to the next. Tracks are remembered by name, which is what libVLC gives
 * ("Track 1 - [Japanese]", "Español", the file name of an external subtitle).
 */
object TrackPrefs {
	private const val FILE = "tracks"
	const val KIND_SUBTITLES = "spu"
	const val KIND_AUDIO = "audio"
	/** Stored when the user switched the subtitles off. */
	const val OFF = "\u0000off"

	private lateinit var prefs: SharedPreferences

	fun init(context: Context) {
		prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
	}

	fun get(kind: String, folder: String): String? = prefs.getString("$kind:$folder", null)

	fun set(kind: String, folder: String, name: String) = prefs.edit { putString("$kind:$folder", name) }

	private val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sub")
	private val bracketed = Regex("""\[([^\]]+)\]""")

	/** Whether a track name is the one remembered: the same name, the same language in brackets, or both external files. */
	fun matches(name: String, wanted: String): Boolean {
		if (name.equals(wanted, ignoreCase = true)) return true
		val a = bracketed.find(name)?.groupValues?.get(1)?.trim()
		val b = bracketed.find(wanted)?.groupValues?.get(1)?.trim()
		if (a != null && b != null && a.equals(b, ignoreCase = true)) return true
		return isExternal(name) && isExternal(wanted)
	}

	private fun isExternal(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in subtitleExtensions
}
