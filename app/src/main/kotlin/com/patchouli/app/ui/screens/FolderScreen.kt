package com.patchouli.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFramePercent
import com.patchouli.app.R
import com.patchouli.app.catalog.Episodes
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.library.Video
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitChip
import com.patchouli.app.ui.kit.KitIconButton
import com.patchouli.app.ui.kit.KitLinearProgress
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitScaffold
import com.patchouli.app.ui.kit.KitSectionHead
import com.patchouli.app.ui.kit.KitSheet
import com.patchouli.app.ui.kit.KitSurface
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.KitTopBar
import com.patchouli.app.ui.kit.LocalPersonality
import com.patchouli.app.ui.kit.kitShape
import java.io.File

/** One folder: what AniList knows about it, the folders under it, then its videos in episode order. */
@Composable
fun FolderScreen(folder: Folder, onOpenFolder: (Folder) -> Unit, onPlay: (Video, List<Video>) -> Unit, onBack: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val anime = Metadata.rememberAnime(folder)
	val lookup = Metadata[folder]
	val hideWatched = AppPrefs.hideWatched
	// not remembered: the order and the filter follow the bookmarks as they change
	val subfolders = folder.sortedFolders().let { all -> if (hideWatched) all.filterNot { it.fullyWatched } else all }
	val allVideos = remember(folder) { folder.sortedVideos() }
	// the watched episodes step aside when asked; the player still gets the whole folder as its playlist
	val videos = if (hideWatched) allVideos.filterNot { Progress[it.path]?.finished == true } else allVideos
	val hidden = allVideos.size - videos.size
	// the names of the episodes, from the catalogue, once the folder is known to be a series
	val titles = anime?.let { Episodes.rememberTitles(it) }.orEmpty()
	var menu by remember { mutableStateOf(false) }
	var menuFor by remember { mutableStateOf<Folder?>(null) }
	var videoMenuFor by remember { mutableStateOf<Video?>(null) }
	var layoutSheet by remember { mutableStateOf(false) }
	val title = Names.series(folder.name)
	// the same list-or-grid setting as the home screen
	val columns = AppPrefs.libraryColumns
	val list = columns == 0

	KitScaffold(
		topBar = {
			KitTopBar(
				title = title,
				subtitle = folder.path,
				leading = { KitIconButton(ImageVector.vectorResource(R.drawable.ic_back), stringResource(R.string.back), onClick = onBack) },
				actions = {
					if (subfolders.isNotEmpty()) {
						KitIconButton(ImageVector.vectorResource(if (list) R.drawable.ic_list else R.drawable.ic_grid), stringResource(R.string.library_layout), onClick = { layoutSheet = true })
					}
					KitIconButton(ImageVector.vectorResource(R.drawable.ic_more), stringResource(R.string.more), onClick = { menu = true })
				},
			)
		},
	) { padding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			if (anime != null) {
				item { AnimeHeader(anime = anime, cover = Metadata.cover(folder), watched = allVideos.count { Progress[it.path]?.finished == true }) }
			}
			if (subfolders.isNotEmpty()) {
				item { KitSectionHead(label = stringResource(R.string.folders), kicker = "書棚") }
				item { CoversBanner(subfolders) }
				if (list) {
					item {
						KitListContainer {
							subfolders.forEachIndexed { index, sub ->
								FolderRow(sub, showDivider = index < subfolders.lastIndex, onClick = { onOpenFolder(sub) }, onLongClick = { menuFor = sub })
							}
						}
					}
				} else {
					val gap = if (columns >= 4) 8.dp else 12.dp
					items(subfolders.chunked(columns)) { row ->
						Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
							row.forEach { sub -> Box(Modifier.weight(1f)) { FolderCard(sub, compact = columns >= 3, onClick = { onOpenFolder(sub) }, onLongClick = { menuFor = sub }) } }
							repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
						}
					}
				}
			}
			if (videos.isNotEmpty()) {
				item { KitSectionHead(label = stringResource(if (anime != null) R.string.episodes else R.string.videos), kicker = "話") }
				item {
					KitListContainer {
						videos.forEachIndexed { index, video ->
							VideoRow(video, showDivider = index < videos.lastIndex, episodeTitle = video.episode?.let { titles[it] }, onClick = { onPlay(video, allVideos) }, onLongClick = { videoMenuFor = video })
						}
					}
				}
			}
			if (hidden > 0) {
				item {
					KitText(
						text = pluralStringResource(R.plurals.hidden_watched_n, hidden, hidden),
						role = KitTextRole.Label, color = colors.onSurfaceVariant, uppercase = false, fontSize = 12.sp,
						modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
					)
				}
			}
		}
	}

	if (menu) FolderMenu(folder, onDismiss = { menu = false })
	menuFor?.let { sub -> FolderMenu(sub, onDismiss = { menuFor = null }) }
	videoMenuFor?.let { video -> VideoMenu(video, onDismiss = { videoMenuFor = null }) }
	if (layoutSheet) LibraryLayoutSheet(onDismiss = { layoutSheet = false })
}

/** Long press on an episode: call it watched, or not watched, by hand. */
@Composable
fun VideoMenu(video: Video, onDismiss: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val mark = Progress[video.path]
	val finished = mark?.finished == true
	KitSheet(onDismiss = onDismiss, title = video.title, titleJp = "話") {
		KitText(
			text = buildString {
				append(fmtTime(video.durationMs))
				if (finished) append(" · ✓ ${stringResource(R.string.watched)}")
				else if (mark?.started == true) append(" · ${stringResource(R.string.resume_at, fmtTime(mark.positionMs))}")
			},
			role = KitTextRole.Mono, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp),
		)
		KitListContainer {
			KitListRow(
				title = stringResource(if (finished) R.string.mark_unwatched else R.string.mark_watched),
				icon = ImageVector.vectorResource(R.drawable.ic_check),
				onClick = { if (finished) Progress.markUnwatched(video) else Progress.markWatched(video); onDismiss() },
				showDivider = mark?.started == true,
			)
			if (mark?.started == true) {
				KitListRow(
					title = stringResource(R.string.forget_position),
					subtitle = stringResource(R.string.forget_position_summary),
					onClick = { Progress.forget(video.path); onDismiss() },
				)
			}
		}
	}
}

@Composable
private fun AnimeHeader(anime: com.patchouli.app.catalog.Anime, cover: Any?, watched: Int) {
	val colors = LocalPersonality.current.colors
	var expanded by remember { mutableStateOf(false) }
	KitSurface(contentPadding = PaddingValues(12.dp)) {
		Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				AsyncImage(
					model = cover ?: anime.cover, contentDescription = null, contentScale = ContentScale.Crop,
					modifier = Modifier.width(96.dp).aspectRatio(2f / 3f).background(colors.surfaceVariant).border(2.dp, colors.outline),
				)
				Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
					KitText(anime.title, role = KitTextRole.Title, maxLines = 3, overflow = TextOverflow.Ellipsis)
					anime.titleEnglish?.takeIf { it != anime.title }?.let {
						KitText(it, role = KitTextRole.Body, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, uppercase = false, fontSize = 13.sp)
					}
					Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						anime.year?.let { KitChip(it.toString(), small = true) }
						anime.format?.let { KitChip(it, small = true) }
						anime.score?.let { KitChip("$it%", small = true, fill = colors.gold, ink = colors.onGold) }
					}
					anime.episodes?.let {
						KitText(stringResource(R.string.episodes_of, watched, it), role = KitTextRole.Label, color = colors.onSurfaceVariant, uppercase = false, fontSize = 12.sp)
					}
				}
			}
			anime.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
				Column {
					KitText(synopsis, role = KitTextRole.Body, color = colors.onSurfaceVariant, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, uppercase = false, fontSize = 13.sp)
					KitButton(
						onClick = { expanded = !expanded },
						label = stringResource(if (expanded) R.string.synopsis_less else R.string.synopsis_more),
						size = KitButtonSize.Sm, variant = KitButtonVariant.Text,
					)
				}
			}
		}
	}
}

/** One episode: its name from the catalogue when there is one (the file's otherwise), the number and length under it, a frame at the right with the ink bar of how far it got and the stamp of the watched. */
@Composable
fun VideoRow(video: Video, showDivider: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, episodeTitle: String? = null) {
	val colors = LocalPersonality.current.colors
	val mark = Progress[video.path]
	val finished = mark?.finished == true
	KitListRow(
		title = episodeTitle ?: video.title,
		subtitle = buildString {
			video.episode?.let { append(stringResource(R.string.episode_n, it)); append(" · ") }
			append(fmtTime(video.durationMs))
			if (finished) append(" · ✓ ${stringResource(R.string.watched)}")
			else if (mark?.started == true) append(" · ${stringResource(R.string.resume_at, fmtTime(mark.positionMs))}")
		},
		onClick = onClick,
		onLongClick = onLongClick,
		showDivider = showDivider,
		trailing = {
			Box(Modifier.width(96.dp).aspectRatio(16f / 9f).background(Color.Black).border(2.dp, colors.outline)) {
				// a frame from well inside the episode: the first one is black more often than not
				AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(video.path)).videoFramePercent(0.2).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (finished) Modifier.alpha(0.55f) else Modifier))
				if (mark != null && mark.fraction > 0f && !finished) {
					// the ink bar: how much of it is behind
					KitLinearProgress(progress = { mark.fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).drawBehind { drawRect(colors.outline, topLeft = Offset(0f, 0f), size = Size(size.width, 1.5.dp.toPx())) })
				}
				if (finished) {
					// a little stamp of the accent in the corner, tilted as if pressed by hand
					Box(
						modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).rotate(-8f).size(22.dp).background(colors.primary).border(2.dp, colors.outline),
						contentAlignment = Alignment.Center,
					) {
						Icon(ImageVector.vectorResource(R.drawable.ic_check), contentDescription = stringResource(R.string.watched), tint = colors.onPrimary, modifier = Modifier.size(14.dp))
					}
				}
			}
		},
	)
}
