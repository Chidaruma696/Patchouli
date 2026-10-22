package com.patchouli.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import coil3.compose.AsyncImage
import com.patchouli.app.R
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Library
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.library.Video
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitFab
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
	val shelves = Library.shelves
	// the newest half-watched video that is still on the phone
	val last = Progress.continueWatching(5).firstNotNullOfOrNull { (path, mark) -> Library.videoAt(path)?.let { it to mark } }
	var menuFor by remember { mutableStateOf<Folder?>(null) }
	var query by remember { mutableStateOf("") }
	menuFor?.let { FolderMenu(it, onDismiss = { menuFor = null }) }
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
		horizontalArrangement = Arrangement.spacedBy(if (list) 0.dp else if (columns >= 4) 8.dp else 12.dp),
		verticalArrangement = Arrangement.spacedBy(if (list) 0.dp else if (columns >= 4) 8.dp else 12.dp),
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
						videos.forEachIndexed { index, video -> VideoRow(video, showDivider = index < videos.lastIndex, onClick = { onPlay(video) }) }
					}
				}
			}
			return@LazyVerticalGrid
		}
		val loose = Library.home?.sortedVideos().orEmpty()
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
						loose.forEachIndexed { index, video -> VideoRow(video, showDivider = index < loose.lastIndex, onClick = { onPlay(video) }) }
					}
				}
			}
		}
	}
	// the one button: play on, from where the last video was left; floats over the shelves, bottom right
	if (last != null) {
		KitFab(
			onClick = { onPlay(last.first) },
			icon = ImageVector.vectorResource(R.drawable.ic_play),
			contentDescription = stringResource(R.string.resume_at, fmtTime(last.second.positionMs)),
			modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
		)
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

/** The sheet behind the top-bar icon: a list, or a grid two to four covers across. */
@Composable
fun LibraryLayoutSheet(onDismiss: () -> Unit) {
	KitSheet(onDismiss = onDismiss, title = stringResource(R.string.library_layout)) {
		KitSegmented(
			selected = AppPrefs.libraryColumns,
			items = listOf(
				KitSegmentedItem(0, stringResource(R.string.layout_list), ImageVector.vectorResource(R.drawable.ic_list)),
				KitSegmentedItem(2, "2"),
				KitSegmentedItem(3, "3"),
				KitSegmentedItem(4, "4"),
			),
			onSelect = { AppPrefs.updateLibraryColumns(it); onDismiss() },
			modifier = Modifier.fillMaxWidth(),
			height = 38.dp,
			fillWidth = true,
		)
		KitText(
			text = if (AppPrefs.libraryColumns == 0) stringResource(R.string.layout_list) else stringResource(R.string.layout_columns, AppPrefs.libraryColumns),
			role = KitTextRole.Label, color = LocalPersonality.current.colors.onSurfaceVariant, uppercase = false, fontSize = 12.sp,
			modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
		)
	}
}

/** The cover of a folder: from the catalogue, else a frame of its first video, else a folder glyph; with a word on why when it is not there yet. */
@Composable
private fun FolderCover(folder: Folder, modifier: Modifier = Modifier, note: Boolean = true) {
	val colors = LocalPersonality.current.colors
	val status = Metadata.status(folder)
	Box(modifier.background(colors.surfaceVariant)) {
		val model: Any? = Metadata.cover(folder) ?: folder.firstVideo?.let { File(it.path) }
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
				modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(kitShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 7.dp, vertical = 4.dp),
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
		trailing = { FolderCover(folder, modifier = Modifier.width(40.dp).aspectRatio(2f / 3f).clip(kitShape(6.dp)), note = false) },
	)
}

fun fmtTime(ms: Long): String {
	val s = ms / 1000
	val h = s / 3600
	val m = (s % 3600) / 60
	val sec = s % 60
	return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}
