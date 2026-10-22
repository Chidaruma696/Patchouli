package com.patchouli.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.patchouli.app.R
import com.patchouli.app.catalog.Anime
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitSheet
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextField
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality
import com.patchouli.app.ui.kit.kitShape
import kotlinx.coroutines.launch

/** Long press on a folder: another cover from the catalogue, one from the phone, or "not a series". */
@Composable
fun FolderMenu(folder: Folder, onDismiss: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val lookup = Metadata[folder]
	val notSeries = lookup?.manual == true
	var searching by remember { mutableStateOf(false) }
	var query by remember { mutableStateOf(lookup?.query ?: Names.series(folder.name)) }
	var results by remember { mutableStateOf<List<Anime>?>(null) }
	var busy by remember { mutableStateOf(false) }
	var web by remember { mutableStateOf(false) }
	var organize by remember { mutableStateOf(false) }
	if (web) {
		WebCoverPicker(folder, initialQuery = "$query anime cover", onDismiss = { web = false }, onPicked = { web = false; onDismiss() })
		return
	}
	if (organize) {
		OrganizeSheet(folder, onDismiss = { organize = false; onDismiss() })
		return
	}
	val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
		if (uri != null && Metadata.setCustomCover(folder, context, uri)) onDismiss()
	}

	fun search() {
		busy = true
		scope.launch {
			results = runCatching { Metadata.candidates(query) }.getOrDefault(emptyList())
			busy = false
		}
	}

	KitSheet(onDismiss = onDismiss, title = Names.series(folder.name), titleJp = if (searching) "検索" else lookup?.anime?.title) {
		if (!searching) {
			KitListContainer {
				KitListRow(
					title = stringResource(R.string.cover_other),
					subtitle = stringResource(R.string.cover_other_summary),
					onClick = { searching = true; if (results == null) search() },
					showDivider = true,
				)
				KitListRow(
					title = stringResource(R.string.cover_web),
					subtitle = stringResource(R.string.cover_web_summary),
					onClick = { web = true },
					showDivider = true,
				)
				KitListRow(
					title = stringResource(R.string.cover_custom),
					subtitle = stringResource(R.string.cover_custom_summary),
					onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
					showDivider = true,
				)
				if (lookup?.customCover != null) {
					KitListRow(title = stringResource(R.string.cover_custom_remove), onClick = { Metadata.clearCustomCover(folder); onDismiss() }, showDivider = true)
				}
				KitListRow(
					title = stringResource(if (notSeries) R.string.is_a_series else R.string.not_a_series),
					subtitle = if (notSeries) null else stringResource(R.string.not_a_series_summary),
					onClick = { Metadata.setNotSeries(folder, !notSeries); onDismiss() },
					showDivider = true,
				)
				KitListRow(
					title = stringResource(R.string.organize),
					subtitle = stringResource(R.string.organize_summary),
					onClick = { organize = true },
					showDivider = folder.total > 0,
				)
				// the whole shelf at once: everything watched, or nothing yet
				if (folder.total > 0) {
					val allWatched = folder.fullyWatched
					KitListRow(
						title = stringResource(if (allWatched) R.string.mark_folder_unwatched else R.string.mark_folder_watched),
						subtitle = stringResource(R.string.mark_folder_summary, Progress.watchedIn(folder), folder.total),
						onClick = { Progress.markFolder(folder, !allWatched); onDismiss() },
					)
				}
			}
			if (lookup != null && lookup.anime == null && !notSeries) {
				KitText(stringResource(R.string.no_match) + " · " + lookup.query, role = KitTextRole.Body, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp), uppercase = false)
			}
			Metadata.errors[folder.path]?.let { err ->
				KitText(err, role = KitTextRole.Mono, color = colors.error, modifier = Modifier.padding(top = 10.dp))
			}
		} else {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				KitTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), onCommit = { search() })
				KitButton(onClick = { search() }, label = stringResource(R.string.search), enabled = !busy)
			}
			Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				when {
					busy -> KitCircularProgress(Modifier.padding(16.dp).align(Alignment.CenterHorizontally))
					results?.isEmpty() == true -> KitText(stringResource(R.string.cover_results_empty), role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)
					else -> KitListContainer {
						results.orEmpty().forEachIndexed { index, anime ->
							KitListRow(
								title = anime.title,
								subtitle = listOfNotNull(anime.titleEnglish?.takeIf { it != anime.title }, anime.year?.toString(), anime.format, anime.episodes?.let { "$it ep" }).joinToString(" · "),
								selected = anime.id == lookup?.anime?.id && anime.source == lookup?.anime?.source,
								onClick = { Metadata.choose(folder, anime); onDismiss() },
								showDivider = index < results!!.lastIndex,
								trailing = {
									AsyncImage(
										model = anime.cover, contentDescription = null, contentScale = ContentScale.Crop,
										modifier = Modifier.width(44.dp).aspectRatio(2f / 3f).clip(kitShape(4.dp)).background(colors.surfaceVariant),
									)
								},
							)
						}
					}
				}
			}
		}
	}
}
