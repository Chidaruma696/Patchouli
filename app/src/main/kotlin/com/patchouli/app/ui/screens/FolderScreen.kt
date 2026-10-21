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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.patchouli.app.R
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.library.Video
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
	val subfolders = remember(folder) { folder.sortedFolders() }
	val videos = remember(folder) { folder.sortedVideos() }
	var menu by remember { mutableStateOf(false) }
	var menuFor by remember { mutableStateOf<Folder?>(null) }
	val title = anime?.title ?: Names.series(folder.name)

	KitScaffold(
		topBar = {
			KitTopBar(
				title = title,
				subtitle = folder.path,
				leading = { KitIconButton(ImageVector.vectorResource(R.drawable.ic_back), stringResource(R.string.back), onClick = onBack) },
				actions = { KitIconButton(ImageVector.vectorResource(R.drawable.ic_more), stringResource(R.string.more), onClick = { menu = true }) },
			)
		},
	) { padding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			if (anime != null) {
				item { AnimeHeader(anime = anime, cover = Metadata.cover(folder), watched = videos.count { Progress[it.path]?.finished == true }) }
			}
			if (subfolders.isNotEmpty()) {
				item { KitSectionHead(label = stringResource(R.string.folders), kicker = "書棚") }
				item { CoversBanner(subfolders) }
				items(subfolders.chunked(2)) { row ->
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						row.forEach { sub -> Box(Modifier.weight(1f)) { FolderCard(sub, onClick = { onOpenFolder(sub) }, onLongClick = { menuFor = sub }) } }
						if (row.size == 1) Spacer(Modifier.weight(1f))
					}
				}
			}
			if (videos.isNotEmpty()) {
				item { KitSectionHead(label = stringResource(if (anime != null) R.string.episodes else R.string.videos), kicker = "話") }
				item {
					KitListContainer {
						videos.forEachIndexed { index, video ->
							VideoRow(video, showDivider = index < videos.lastIndex, onClick = { onPlay(video, videos) })
						}
					}
				}
			}
		}
	}

	if (menu) FolderMenu(folder, onDismiss = { menu = false })
	menuFor?.let { sub -> FolderMenu(sub, onDismiss = { menuFor = null }) }
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
					modifier = Modifier.width(96.dp).aspectRatio(2f / 3f).clip(kitShape(8.dp)).background(colors.surfaceVariant),
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

@Composable
fun VideoRow(video: Video, showDivider: Boolean, onClick: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val mark = Progress[video.path]
	KitListRow(
		title = video.title,
		subtitle = buildString {
			video.episode?.let { append(stringResource(R.string.episode_n, it)); append(" · ") }
			append(fmtTime(video.durationMs))
			if (mark?.finished == true) append(" · ✓ ${stringResource(R.string.watched)}")
			else if (mark?.started == true) append(" · ${stringResource(R.string.resume_at, fmtTime(mark.positionMs))}")
		},
		onClick = onClick,
		showDivider = showDivider,
		trailing = {
			Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(kitShape(6.dp)).background(Color.Black)) {
				AsyncImage(model = File(video.path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
				if (mark != null && mark.fraction > 0f) {
					KitLinearProgress(progress = { mark.fraction }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
				}
				if (mark?.finished == true) {
					Icon(ImageVector.vectorResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).align(Alignment.TopEnd).padding(2.dp))
				}
			}
		},
	)
}
