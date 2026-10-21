package com.patchouli.app.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.patchouli.app.ui.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One file the MediaStore knows about. */
data class Video(
	val id: Long,
	val path: String,
	val fileName: String,
	val folderPath: String,
	val size: Long,
	val durationMs: Long,
	val dateAdded: Long,
) {
	val title: String get() = Names.title(fileName)
	val episode: Int? by lazy { Names.episode(title) }
	val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
}

/** A folder of the tree: its own videos and the folders under it. [path] is relative to the storage root. */
class Folder(val path: String, val name: String) {
	val folders = mutableListOf<Folder>()
	val videos = mutableListOf<Video>()

	val total: Int get() = videos.size + folders.sumOf { it.total }
	val latest: Long get() = maxOf(videos.maxOfOrNull { it.dateAdded } ?: 0L, folders.maxOfOrNull { it.latest } ?: 0L)

	fun sortedVideos(): List<Video> = videos.sortedWith(compareBy<Video>({ it.episode ?: Int.MAX_VALUE }).thenComparator { a, b -> Names.natural.compare(a.title, b.title) })
	fun sortedFolders(): List<Folder> = folders.map { it.collapsed() }.sortedWith { a, b -> Names.natural.compare(a.name, b.name) }

	/** The first video of the shelf, for a thumbnail when there is no cover. */
	val firstVideo: Video? get() = sortedVideos().firstOrNull() ?: sortedFolders().firstNotNullOfOrNull { it.firstVideo }

	/** A folder that only holds one folder shows as that one: "Movies/Seija" reads "Seija". */
	fun collapsed(): Folder {
		var f = this
		while (f.videos.isEmpty() && f.folders.size == 1) f = f.folders.first()
		return f
	}
}

/** Everything the phone has, read from the MediaStore and kept as a tree of folders. */
object Library {
	var root by mutableStateOf<Folder?>(null)
		private set
	var loading by mutableStateOf(false)
		private set

	private var byPath: Map<String, Video> = emptyMap()

	/** The folder the user chose as the library (or everything when none), as far as it exists. */
	val home: Folder? get() = root?.let { if (AppPrefs.rootPath.isEmpty()) it else find(AppPrefs.rootPath) }

	/** The shelves of the library: the folders under the chosen root, chains of single folders collapsed. */
	val shelves: List<Folder> get() = home?.sortedFolders().orEmpty()

	fun videoAt(path: String): Video? = byPath[path]

	/** The videos of the same folder, in order: what plays after this one. */
	fun siblingsOf(video: Video): List<Video> = find(video.folderPath)?.sortedVideos()?.takeIf { it.isNotEmpty() } ?: listOf(video)

	fun find(path: String): Folder? {
		var node = root ?: return null
		if (path.isEmpty()) return node
		for (part in path.split('/')) {
			node = node.folders.firstOrNull { it.name == part } ?: return null
		}
		return node
	}

	suspend fun scan(context: Context) {
		loading = true
		try {
			val tree = withContext(Dispatchers.IO) { read(context) }
			val index = HashMap<String, Video>()
			fun walk(f: Folder) { f.videos.forEach { index[it.path] = it }; f.folders.forEach(::walk) }
			walk(tree)
			byPath = index
			root = tree
		} finally {
			loading = false
		}
	}

	private fun read(context: Context): Folder {
		val tree = Folder("", "")
		val primary = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
		val projection = arrayOf(
			MediaStore.Video.Media._ID,
			MediaStore.Video.Media.DISPLAY_NAME,
			MediaStore.Video.Media.DATA,
			MediaStore.Video.Media.SIZE,
			MediaStore.Video.Media.DURATION,
			MediaStore.Video.Media.DATE_ADDED,
		)
		context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, MediaStore.Video.Media.DATA)?.use { c ->
			val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
			val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
			val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
			val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
			val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
			val addedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
			while (c.moveToNext()) {
				val data = c.getString(dataCol) ?: continue
				val fileName = c.getString(nameCol) ?: data.substringAfterLast('/')
				// the primary storage is the tree; an SD card hangs from it under its own name
				val relative = if (data.startsWith(primary)) data.removePrefix(primary) else data.removePrefix("/storage/")
				val folderPath = relative.substringBeforeLast('/', "")
				val video = Video(
					id = c.getLong(idCol),
					path = data,
					fileName = fileName,
					folderPath = folderPath,
					size = c.getLong(sizeCol),
					durationMs = c.getLong(durCol),
					dateAdded = c.getLong(addedCol),
				)
				folderFor(tree, folderPath).videos += video
			}
		}
		return tree
	}

	private fun folderFor(root: Folder, path: String): Folder {
		if (path.isEmpty()) return root
		var node = root
		var soFar = ""
		for (part in path.split('/')) {
			soFar = if (soFar.isEmpty()) part else "$soFar/$part"
			node = node.folders.firstOrNull { it.name == part } ?: Folder(soFar, part).also { node.folders += it }
		}
		return node
	}
}
