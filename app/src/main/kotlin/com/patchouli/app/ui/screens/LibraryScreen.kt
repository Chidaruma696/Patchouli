package com.patchouli.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import android.text.format.DateUtils
import com.patchouli.app.R
import com.patchouli.app.catalog.Episodes
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Library
import com.patchouli.app.library.Mark
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.library.Video
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitCheckbox
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitFab
import com.patchouli.app.ui.kit.KitLinearProgress
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitSectionHead
import com.patchouli.app.ui.kit.KitSegmented
import com.patchouli.app.ui.kit.KitSegmentedItem
import com.patchouli.app.ui.kit.KitSheet
import com.patchouli.app.ui.kit.KitSurface
import com.patchouli.app.ui.kit.KitSurfaceElevation
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextField
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality
import com.patchouli.app.ui.kit.kitShape
import java.io.File
import java.util.Locale

/** The shelves: what was left half-way, then every top folder with videos. */
@Composable
fun LibraryScreen(contentPadding: PaddingValues, onOpenFolder: (Folder) -> Unit, onPlay: (Video) -> Unit) {
	val colors = LocalPersonality.current.colors
	// a shelf with nothing left to watch stays out of sight when asked
	val shelves = Library.shelves.let { all -> if (AppPrefs.hideWatched) all.filterNot { it.fullyWatched } else all }
	// the newest half-watched video that is still on the phone
	val last = Progress.continueWatching(5).firstNotNullOfOrNull { (path, mark) -> Library.videoAt(path)?.let { it to mark } }
	var menuFor by remember { mutableStateOf<Folder?>(null) }
	var videoMenuFor by remember { mutableStateOf<Video?>(null) }
	var resumeSheet by remember { mutableStateOf(false) }
	var query by remember { mutableStateOf("") }
	menuFor?.let { FolderMenu(it, onDismiss = { menuFor = null }) }
	videoMenuFor?.let { VideoMenu(it, onDismiss = { videoMenuFor = null }) }
	if (resumeSheet && last != null) ResumeSheet(last.first, last.second, onDismiss = { resumeSheet = false }, onPlay = { resumeSheet = false; onPlay(last.first) })
	if (Library.loading && Library.root == null) {
		Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
			Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
				KitCircularProgress()
				KitText(stringResource(R.string.scanning), role = KitTextRole.Label, color = colors.onSurfaceVariant)
			}
		}
		return
	}
	// 0 is a list of rows; otherwise that many covers across
	val columns = AppPrefs.libraryColumns
	val list = columns == 0
	Box(Modifier.fillMaxSize()) {
	LazyVerticalGrid(
		columns = GridCells.Fixed(if (list) 1 else columns),
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(
			start = 16.dp, end = 16.dp,
			top = contentPadding.calculateTopPadding() + 4.dp,
			bottom = contentPadding.calculateBottomPadding() + 24.dp,
		),
		// room for the 6 dp ink shadow of every panel, whatever the count across
		horizontalArrangement = Arrangement.spacedBy(if (list) 0.dp else if (columns >= 4) 10.dp else 14.dp),
		verticalArrangement = Arrangement.spacedBy(if (list) 0.dp else if (columns >= 4) 10.dp else 14.dp),
	) {
		fun wide(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }
		/** The folders, as cards across or as one list of rows. */
		fun folderItems(folders: List<Folder>, keyPrefix: String) {
			if (list) {
				wide {
					KitListContainer {
						folders.forEachIndexed { index, folder ->
							FolderRow(folder, showDivider = index < folders.lastIndex, onClick = { onOpenFolder(folder) }, onLongClick = { menuFor = folder })
						}
					}
				}
			} else {
				items(folders, key = { keyPrefix + it.path }) { folder -> FolderCard(folder, compact = columns >= 3, onClick = { onOpenFolder(folder) }, onLongClick = { menuFor = folder }) }
			}
		}
		if (AppPrefs.kaoBannerVisible) {
			wide { KeepAndroidOpenBanner() }
		}
		// the search box: while something is typed, the shelves give way to what matches
		if (Library.root != null && (shelves.isNotEmpty() || query.isNotEmpty())) {
			wide {
				KitTextField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.search_library), modifier = Modifier.fillMaxWidth().padding(bottom = if (list) 10.dp else 0.dp))
			}
		}
		if (query.isNotBlank()) {
			val (folders, videos) = Library.search(query)
			if (folders.isEmpty() && videos.isEmpty()) {
				wide { KitText(stringResource(R.string.search_empty), role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, modifier = Modifier.padding(top = 16.dp)) }
			}
			if (folders.isNotEmpty()) {
				wide { KitSectionHead(label = stringResource(R.string.folders), kicker = "書棚") }
				folderItems(folders, "s:")
			}
			if (videos.isNotEmpty()) {
				wide { KitSectionHead(label = stringResource(R.string.videos), kicker = "話") }
				wide {
					KitListContainer {
						videos.forEachIndexed { index, video -> VideoRow(video, showDivider = index < videos.lastIndex, onClick = { onPlay(video) }, onLongClick = { videoMenuFor = video }) }
					}
				}
			}
			return@LazyVerticalGrid
		}
		val loose = Library.home?.sortedVideos().orEmpty().let { all -> if (AppPrefs.hideWatched) all.filterNot { Progress[it.path]?.finished == true } else all }
		if (shelves.isEmpty() && loose.isEmpty()) {
			wide {
				Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Image(painterResource(R.drawable.patchouli), contentDescription = null, modifier = Modifier.height(180.dp).padding(bottom = 8.dp))
					KitText(stringResource(R.string.empty_library), role = KitTextRole.Title, textAlign = TextAlign.Center)
					val hint = if (AppPrefs.rootPath.isEmpty()) stringResource(R.string.empty_library_hint) else stringResource(R.string.empty_root_hint, AppPrefs.rootPath)
					KitText(hint, role = KitTextRole.Body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, uppercase = false)
				}
			}
		} else {
			if (shelves.isNotEmpty()) {
				wide { KitSectionHead(label = stringResource(R.string.folders), kicker = "書棚") }
				wide { CoversBanner(shelves) }
				folderItems(shelves, "")
			}
			// videos lying directly in the root folder, outside any shelf
			if (loose.isNotEmpty()) {
				wide { KitSectionHead(label = stringResource(R.string.videos), kicker = "話") }
				wide {
					KitListContainer {
						loose.forEachIndexed { index, video -> VideoRow(video, showDivider = index < loose.lastIndex, onClick = { onPlay(video) }, onLongClick = { videoMenuFor = video }) }
					}
				}
			}
		}
	}
	// the one button: play on, from where the last video was left; floats over the shelves, bottom right.
	// Held down, it first says what it would play.
	if (last != null) {
		KitFab(
			onClick = { onPlay(last.first) },
			onLongClick = { resumeSheet = true },
			icon = ImageVector.vectorResource(R.drawable.ic_play),
			contentDescription = stringResource(R.string.resume_at, fmtTime(last.second.positionMs)),
			modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
		)
	}
	}
}

/** Behind a long press on the floating play: the cover, the episode, how far it got, and the choice to start it over. */
@Composable
private fun ResumeSheet(video: Video, mark: Mark, onDismiss: () -> Unit, onPlay: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val folder = Library.find(video.folderPath)
	val series = folder?.let { Names.series(it.name) } ?: video.folderPath.substringAfterLast('/')
	val episodeTitle = folder?.let { Metadata.rememberAnime(it) }?.let { Episodes.rememberTitles(it) }?.get(video.episode ?: -1)
	KitSheet(onDismiss = onDismiss, title = series, titleJp = "続き") {
		Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			if (folder != null) FolderCover(folder, modifier = Modifier.width(88.dp).aspectRatio(2f / 3f).border(2.dp, colors.outline), note = false)
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
				KitText(
					text = buildString {
						video.episode?.let { append(stringResource(R.string.episode_short, it)) }
						episodeTitle?.let { if (isNotEmpty()) append(" · "); append(it) }
						if (isEmpty()) append(video.title)
					},
					role = KitTextRole.Stamp, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
				)
				KitText(stringResource(R.string.resume_of, fmtTime(mark.positionMs), fmtTime(mark.lengthMs)), role = KitTextRole.Mono, color = colors.onSurfaceVariant)
				KitLinearProgress(progress = { mark.fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).border(2.dp, colors.outline))
				KitText(DateUtils.getRelativeTimeSpanString(mark.at).toString(), role = KitTextRole.Label, color = colors.onSurfaceVariant, uppercase = false, fontSize = 12.sp)
			}
		}
		Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			KitButton(onClick = onPlay, label = stringResource(R.string.resume_keep), emphasized = true, modifier = Modifier.weight(1f))
			KitButton(onClick = { Progress.forget(video.path); onPlay() }, label = stringResource(R.string.resume_restart), variant = KitButtonVariant.Outline, modifier = Modifier.weight(1f))
		}
	}
}

/** The strip above the shelves while covers are being looked up, with a way out of the feature. */
@Composable
fun CoversBanner(folders: List<Folder>) {
	val colors = LocalPersonality.current.colors
	val statuses = folders.map { Metadata.status(it) }
	val failed = statuses.any { it == Metadata.Status.Failed }
	val pending = statuses.any { it == Metadata.Status.Pending }
	if (!failed && !pending) return
	KitSurface(modifier = Modifier.fillMaxWidth(), elevation = KitSurfaceElevation.Flat, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			if (failed) Icon(ImageVector.vectorResource(R.drawable.ic_refresh), contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
			else KitCircularProgress(modifier = Modifier.size(18.dp))
			KitText(
				text = stringResource(if (failed) R.string.covers_banner_failed else R.string.covers_banner_searching),
				role = KitTextRole.Body, color = colors.onSurfaceVariant, fontSize = 12.sp, uppercase = false,
				modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
			)
			if (failed) KitButton(onClick = { Metadata.retry(folders) }, label = stringResource(R.string.covers_retry), size = KitButtonSize.Sm, variant = KitButtonVariant.Tonal)
			KitButton(onClick = { AppPrefs.updateCoversOnline(false) }, label = stringResource(R.string.covers_skip), size = KitButtonSize.Sm, variant = KitButtonVariant.Text)
		}
	}
}

/** The sheet behind the top-bar icon: a list or a grid two to four across, the order of the shelves, and whether the watched stay in sight. */
@Composable
fun LibraryLayoutSheet(onDismiss: () -> Unit) {
	val colors = LocalPersonality.current.colors
	KitSheet(onDismiss = onDismiss, title = stringResource(R.string.library_layout)) {
		KitText(stringResource(R.string.layout_kind), role = KitTextRole.Overline, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
		KitSegmented(
			selected = AppPrefs.libraryColumns,
			items = listOf(
				KitSegmentedItem(0, stringResource(R.string.layout_list), ImageVector.vectorResource(R.drawable.ic_list)),
				KitSegmentedItem(2, "2"),
				KitSegmentedItem(3, "3"),
				KitSegmentedItem(4, "4"),
			),
			onSelect = { AppPrefs.updateLibraryColumns(it) },
			modifier = Modifier.fillMaxWidth(),
			height = 38.dp,
			fillWidth = true,
		)
		KitText(stringResource(R.string.sort_by), role = KitTextRole.Overline, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
		KitSegmented(
			selected = AppPrefs.librarySort,
			items = listOf(
				KitSegmentedItem(AppPrefs.SORT_NAME, stringResource(R.string.sort_name)),
				KitSegmentedItem(AppPrefs.SORT_RECENT, stringResource(R.string.sort_recent)),
				KitSegmentedItem(AppPrefs.SORT_ADDED, stringResource(R.string.sort_added)),
			),
			onSelect = { AppPrefs.updateLibrarySort(it) },
			modifier = Modifier.fillMaxWidth(),
			height = 38.dp,
			fillWidth = true,
		)
		KitListContainer(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
			KitListRow(
				title = stringResource(R.string.hide_watched),
				subtitle = stringResource(R.string.hide_watched_summary),
				onClick = { AppPrefs.updateHideWatched(!AppPrefs.hideWatched) },
				trailing = { KitCheckbox(checked = AppPrefs.hideWatched, onCheckedChange = { AppPrefs.updateHideWatched(it) }) },
			)
		}
	}
}

/** The cover of a folder: from the catalogue, else a frame of its first video, else a folder glyph; with a word on why when it is not there yet. */
@Composable
private fun FolderCover(folder: Folder, modifier: Modifier = Modifier, note: Boolean = true) {
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	val status = Metadata.status(folder)
	Box(modifier.background(colors.surfaceVariant)) {
		// no cover yet: a frame from inside the first video, not its black opening
		val model: Any? = Metadata.cover(folder) ?: folder.firstVideo?.let { ImageRequest.Builder(context).data(File(it.path)).videoFramePercent(0.2).build() }
		if (model != null) {
			AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
		} else {
			Icon(ImageVector.vectorResource(R.drawable.ic_folder), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(if (note) 40.dp else 22.dp).align(Alignment.Center))
		}
		val word = when (status) {
			Metadata.Status.Pending -> R.string.cover_searching
			Metadata.Status.Failed -> R.string.cover_failed
			Metadata.Status.None -> R.string.cover_none
			else -> null
		}
		if (note && word != null) {
			Row(
				modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 7.dp, vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(5.dp),
			) {
				if (status == Metadata.Status.Pending) KitCircularProgress(modifier = Modifier.size(10.dp), color = Color.White)
				KitText(stringResource(word), role = KitTextRole.Label, color = Color.White, fontSize = 10.sp, maxLines = 1, uppercase = false)
			}
		} else if (!note && status == Metadata.Status.Pending) {
			KitCircularProgress(modifier = Modifier.size(12.dp).align(Alignment.Center), color = Color.White)
		}
	}
}

/** A folder as a book on the shelf: cover on top, its own name under; [compact] when three or more fit across. */
@Composable
fun FolderCard(folder: Folder, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, compact: Boolean = false) {
	val colors = LocalPersonality.current.colors
	val anime = Metadata.rememberAnime(folder)
	KitSurface(onClick = onClick, onLongClick = onLongClick, elevation = KitSurfaceElevation.Card) {
		Column {
			FolderCover(folder, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f), note = !compact)
			// the pen separates the picture from its caption, as in a panel
			Box(Modifier.fillMaxWidth().height(2.dp).background(colors.outline))
			Column(Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 7.dp else 10.dp)) {
				// the folder's own name, always: the catalogue's title lives inside the folder, not on its spine
				KitText(Names.series(folder.name), role = KitTextRole.Stamp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = if (compact) 11.sp else 14.sp)
				KitText(
					text = buildString {
						append(pluralStringResource(R.plurals.videos_n, folder.total, folder.total))
						if (!compact) anime?.year?.let { append(" · $it") }
					},
					role = KitTextRole.Label, color = colors.onSurfaceVariant, fontSize = if (compact) 9.sp else 11.sp, maxLines = 1, uppercase = false,
				)
			}
		}
	}
}

/** A folder as one line of the list: a small cover on the right, name and count on the left. */
@Composable
fun FolderRow(folder: Folder, showDivider: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
	val anime = Metadata.rememberAnime(folder)
	KitListRow(
		title = Names.series(folder.name),
		subtitle = buildString {
			append(pluralStringResource(R.plurals.videos_n, folder.total, folder.total))
			anime?.year?.let { append(" · $it") }
		},
		onClick = onClick,
		onLongClick = onLongClick,
		showDivider = showDivider,
		trailing = { FolderCover(folder, modifier = Modifier.width(40.dp).aspectRatio(2f / 3f).border(2.dp, LocalPersonality.current.colors.outline), note = false) },
	)
}

fun fmtTime(ms: Long): String {
	val s = ms / 1000
	val h = s / 3600
	val m = (s % 3600) / 60
	val sec = s % 60
	return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}
