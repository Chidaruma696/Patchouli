package com.patchouli.app.library

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Deleting videos. Files we may touch directly (all-files access granted, or the old storage
 * permission) go at once; for the rest the system asks the user itself, through an intent the
 * caller has to launch, and the files go when it answers yes.
 */
object Trash {
	private const val TAG = "Trash"

	/** Deletes what it can right away; returns the system's own question for the rest, or null when everything is gone. */
	fun delete(context: Context, videos: List<Video>): IntentSender? {
		val remaining = ArrayList<Video>()
		val direct = Organizer.hasPermission(context)
		for (video in videos) {
			val file = File(video.path)
			if (direct && (file.delete() || !file.exists())) {
				runCatching { context.contentResolver.delete(video.uri, null, null) }
				Progress.forget(video.path)
				Log.i(TAG, "deleted ${video.path}")
			} else {
				remaining += video
			}
		}
		if (remaining.isEmpty()) return null
		if (Build.VERSION.SDK_INT >= 30) {
			return MediaStore.createDeleteRequest(context.contentResolver, remaining.map { it.uri }).intentSender
		}
		for (video in remaining) {
			try {
				context.contentResolver.delete(video.uri, null, null)
				Progress.forget(video.path)
			} catch (e: SecurityException) {
				if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) return e.userAction.actionIntent.intentSender
				throw e
			}
		}
		return null
	}

	/** The system said yes: the bookmarks of the files go too. */
	fun forgetAll(videos: List<Video>) = videos.forEach { Progress.forget(it.path) }
}
