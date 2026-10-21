package com.patchouli.app.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

enum class RenameKind { Episode, Season, Unchanged, NoNumber, Conflict }

/** One file or folder and what it would be called. */
data class Rename(val from: File, val to: File, val kind: RenameKind) {
	val changes: Boolean get() = kind == RenameKind.Episode || kind == RenameKind.Season
}

/**
 * Puts a folder in the layout the library reads best, when it was downloaded some other way:
 * the folders under it numbered in order (`01 - Name`), the videos in it named `Name - E01`.
 * Nothing moves until the user has seen the whole list and said yes.
 */
object Organizer {
	private const val TAG = "Organizer"
	private val numbered = Regex("""^\d{1,3}\s*[-.]\s*""")

	fun hasPermission(context: Context): Boolean =
		if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
		else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

	/** The system screen that grants all-files access (11+); below that the runtime permission is asked instead. */
	fun permissionIntent(context: Context): Intent? =
		if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")) else null

	/** What would change; [yearOf] gives the catalogue's year for a subfolder so seasons line up in order. */
	fun plan(folder: Folder, yearOf: (Folder) -> Int?): List<Rename> {
		val dir = Library.absolute(folder)
		val out = ArrayList<Rename>()

		// the folders under it, numbered in order: by year when the catalogue knows it, else by name
		if (folder.folders.size >= 2) {
			val ordered = folder.folders.sortedWith(compareBy<Folder>({ yearOf(it) ?: Int.MAX_VALUE }).thenComparator { a, b -> Names.natural.compare(a.name, b.name) })
			ordered.forEachIndexed { index, sub ->
				val bare = sub.name.replace(numbered, "").trim().ifEmpty { sub.name }
				val target = String.format(Locale.ROOT, "%02d - %s", index + 1, bare)
				out += Rename(File(dir, sub.name), File(dir, target), if (target == sub.name) RenameKind.Unchanged else RenameKind.Season)
			}
		}

		// the videos in it, named after the folder with their episode number
		val name = Names.series(folder.name)
		val videos = folder.sortedVideos()
		val width = if ((videos.maxOfOrNull { it.episode ?: 0 } ?: 0) > 99) 3 else 2
		val targets = HashMap<String, Int>()
		val proposed = videos.map { v ->
			val ep = v.episode
			val ext = v.fileName.substringAfterLast('.', "")
			val target = if (ep == null) null else String.format(Locale.ROOT, "%s - E%0${width}d%s", name, ep, if (ext.isEmpty()) "" else ".$ext")
			if (target != null) targets[target] = (targets[target] ?: 0) + 1
			v to target
		}
		proposed.forEach { (v, target) ->
			val from = File(v.path)
			out += when {
				target == null -> Rename(from, from, RenameKind.NoNumber)
				(targets[target] ?: 0) > 1 -> Rename(from, File(dir, target), RenameKind.Conflict)
				target == v.fileName -> Rename(from, from, RenameKind.Unchanged)
				else -> Rename(from, File(dir, target), RenameKind.Episode)
			}
		}
		return out
	}

	/** Renames everything in [plan] that changes; returns how many went through. The library rescans after. */
	suspend fun apply(context: Context, plan: List<Rename>): Int = withContext(Dispatchers.IO) {
		var done = 0
		val toScan = ArrayList<String>()
		// files first, folders after, so a folder's new name never gets in the way of its files
		val ordered = plan.filter { it.changes }.sortedBy { if (it.kind == RenameKind.Season) 1 else 0 }
		for (r in ordered) {
			if (r.to.exists()) { Log.w(TAG, "skip ${r.from.name}: ${r.to.name} exists"); continue }
			val wasDir = r.from.isDirectory
			val oldFiles = if (wasDir) r.from.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() else listOf(r.from.absolutePath)
			if (!r.from.renameTo(r.to)) { Log.w(TAG, "rename failed: ${r.from} -> ${r.to}"); continue }
			done++
			if (wasDir) {
				Progress.movePrefix(r.from.absolutePath, r.to.absolutePath)
				toScan += oldFiles
				toScan += r.to.walkTopDown().filter { it.isFile }.map { it.absolutePath }
			} else {
				Progress.move(r.from.absolutePath, r.to.absolutePath)
				toScan += r.from.absolutePath
				toScan += r.to.absolutePath
			}
		}
		if (toScan.isNotEmpty()) scan(context, toScan)
		Library.scan(context)
		done
	}

	/** Tells the MediaStore about the old paths (gone) and the new ones, and waits for it. */
	private suspend fun scan(context: Context, paths: List<String>) = suspendCancellableCoroutine { cont ->
		var left = paths.size
		MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
			synchronized(cont) { left--; if (left == 0 && cont.isActive) cont.resume(Unit) }
		}
	}
}
