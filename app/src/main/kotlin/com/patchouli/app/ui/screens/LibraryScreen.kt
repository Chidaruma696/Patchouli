package com.patchouli.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.patchouli.app.library.Mark
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.library.Video
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitLinearProgress
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitSectionHead
import com.patchouli.app.ui.kit.KitSurface
import com.patchouli.app.ui.kit.KitSurfaceElevation
import com.patchouli.app.ui.kit.KitText
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
	val continuing = Progress.continueWatching()
	var menuFor by remember { mutableStateOf<Folder?>(null) }
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
	LazyVerticalGrid(
		columns = GridCells.Fixed(2),
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(
			start = 16.dp, end = 16.dp,
			top = contentPadding.calculateTopPadding() + 4.dp,
			bottom = contentPadding.calculateBottomPadding() + 24.dp,
		),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		if (AppPrefs.kaoBannerVisible) {
			item(span = { GridItemSpan(2) }) { KeepAndroidOpenBanner() }
		}
		if (continuing.isNotEmpty()) {
			item(span = { GridItemSpan(2) }) {
				Column {
					KitSectionHead(label = stringResource(R.string.continue_watching), kicker = "続き")
					LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
						items(continuing, key = { it.first }) { (path, mark) ->
							ContinueCard(path, mark, onClick = { Library.videoAt(path)?.let(onPlay) })
						}
					}
				}
			}
		}
		val loose = Library.home?.sortedVideos().orEmpty()
		if (shelves.isEmpty() && loose.isEmpty()) {
			item(span = { GridItemSpan(2) }) {
				Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					KitText(stringResource(R.string.empty_library), role = KitTextRole.Title, textAlign = TextAlign.Center)
					val hint = if (AppPrefs.rootPath.isEmpty()) stringResource(R.string.empty_library_hint) else stringResource(R.string.empty_root_hint, AppPrefs.rootPath)
					KitText(hint, role = KitTextRole.Body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, uppercase = false)
				}
			}
		} else {
			if (shelves.isNotEmpty()) {
				item(span = { GridItemSpan(2) }) { KitSectionHead(label = stringResource(R.string.folders), kicker = "書棚") }
				item(span = { GridItemSpan(2) }) { CoversBanner(shelves) }
				items(shelves, key = { it.path }) { folder -> FolderCard(folder, onClick = { onOpenFolder(folder) }, onLongClick = { menuFor = folder }) }
			}
			// videos lying directly in the root folder, outside any shelf
			if (loose.isNotEmpty()) {
				item(span = { GridItemSpan(2) }) { KitSectionHead(label = stringResource(R.string.videos), kicker = "話") }
				item(span = { GridItemSpan(2) }) {
					KitListContainer {
						loose.forEachIndexed { index, video -> VideoRow(video, showDivider = index < loose.lastIndex, onClick = { onPlay(video) }) }
					}
				}
			}
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

/** A folder as a book on the shelf: cover from AniList, or a frame of its first video with a word on why. */
@Composable
fun FolderCard(folder: Folder, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
	val colors = LocalPersonality.current.colors
	val anime = Metadata.rememberAnime(folder)
	val status = Metadata.status(folder)
	KitSurface(onClick = onClick, onLongClick = onLongClick, elevation = KitSurfaceElevation.Card) {
		Column {
			Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(colors.surfaceVariant)) {
				val model: Any? = Metadata.cover(folder) ?: folder.firstVideo?.let { File(it.path) }
				if (model != null) {
					AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
				} else {
					Icon(ImageVector.vectorResource(R.drawable.ic_folder), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(40.dp).align(Alignment.Center))
				}
				val note = when (status) {
					Metadata.Status.Pending -> R.string.cover_searching
					Metadata.Status.Failed -> R.string.cover_failed
					Metadata.Status.None -> R.string.cover_none
					else -> null
				}
				if (note != null) {
					Row(
						modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(kitShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 7.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						if (status == Metadata.Status.Pending) KitCircularProgress(modifier = Modifier.size(10.dp), color = Color.White)
						KitText(stringResource(note), role = KitTextRole.Label, color = Color.White, fontSize = 10.sp, maxLines = 1, uppercase = false)
					}
				}
			}
			Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
				KitText(anime?.title ?: Names.series(folder.name), role = KitTextRole.Stamp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
				KitText(
					text = buildString {
						append(pluralStringResource(R.plurals.videos_n, folder.total, folder.total))
						anime?.year?.let { append(" · $it") }
					},
					role = KitTextRole.Label, color = colors.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, uppercase = false,
				)
			}
		}
	}
}

@Composable
private fun ContinueCard(path: String, mark: Mark, onClick: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val video = Library.videoAt(path)
	KitSurface(onClick = onClick, modifier = Modifier.width(200.dp)) {
		Column {
			Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
				AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
				Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
					KitLinearProgress(progress = { mark.fraction }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(kitShape(2.dp)))
				}
			}
			Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
				KitText(video?.title ?: path.substringAfterLast('/'), role = KitTextRole.Label, maxLines = 1, overflow = TextOverflow.Ellipsis, uppercase = false)
				Spacer(Modifier.height(2.dp))
				KitText(
					text = (video?.folderPath?.substringAfterLast('/')?.let { Names.series(it) } ?: "") + "  " + fmtTime(mark.positionMs),
					role = KitTextRole.Mono, color = colors.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

fun fmtTime(ms: Long): String {
	val s = ms / 1000
	val h = s / 3600
	val m = (s % 3600) / 60
	val sec = s % 60
	return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}
