package com.patchouli.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchouli.app.BuildConfig
import com.patchouli.app.R
import androidx.compose.ui.platform.LocalContext
import com.patchouli.app.library.Organizer
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.rememberRootPicker
import com.patchouli.app.ui.kit.KitCheckbox
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitSectionHead
import com.patchouli.app.ui.kit.KitSegmented
import com.patchouli.app.ui.kit.KitSegmentedItem
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality
import com.patchouli.app.ui.kit.ThemePrefs
import com.patchouli.app.ui.kit.Themes

/** Settings: appearance (Touhou palettes, mode), playback, library, about. */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, onRescan: () -> Unit) {
	val colors = LocalPersonality.current.colors
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(contentPadding)
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
			.padding(top = 4.dp, bottom = 24.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		KitSectionHead(label = stringResource(R.string.appearance), kicker = "外観")
		ThemePicker()

		KitSectionHead(label = stringResource(R.string.playback), kicker = "再生")
		KitListContainer {
			KitListRow(
				title = stringResource(R.string.resume_playback),
				subtitle = stringResource(R.string.resume_playback_summary),
				onClick = { AppPrefs.updateResume(!AppPrefs.resume) },
				showDivider = true,
				trailing = { KitCheckbox(checked = AppPrefs.resume, onCheckedChange = { AppPrefs.updateResume(it) }) },
			)
			KitListRow(
				title = stringResource(R.string.autoplay_next),
				subtitle = stringResource(R.string.autoplay_next_summary),
				onClick = { AppPrefs.updateAutoplayNext(!AppPrefs.autoplayNext) },
				showDivider = true,
				trailing = { KitCheckbox(checked = AppPrefs.autoplayNext, onCheckedChange = { AppPrefs.updateAutoplayNext(it) }) },
			)
			KitListRow(
				title = stringResource(R.string.default_decoder),
				subtitle = stringResource(R.string.decoder_summary),
				showDivider = true,
				trailing = {
					KitSegmented(
						selected = AppPrefs.hardwareDecoder,
						items = listOf(KitSegmentedItem(true, stringResource(R.string.decoder_hw)), KitSegmentedItem(false, stringResource(R.string.decoder_sw))),
						onSelect = { AppPrefs.updateHardwareDecoder(it) },
						height = 34.dp,
					)
				},
			)
			KitListRow(title = stringResource(R.string.seek_step), subtitle = stringResource(R.string.seek_step_summary))
			KitSegmented(
				selected = AppPrefs.seekStepSec,
				items = listOf(5, 10, 15, 30, 60).map { KitSegmentedItem(it, "$it s") },
				onSelect = { AppPrefs.updateSeekStep(it) },
				modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 12.dp),
				height = 34.dp,
			)
			KitListRow(title = stringResource(R.string.gestures), subtitle = stringResource(R.string.gestures_summary))
		}

		KitSectionHead(label = stringResource(R.string.library_section), kicker = "書庫")
		val pickRoot = rememberRootPicker()
		KitListContainer {
			KitListRow(
				title = stringResource(R.string.root_folder),
				subtitle = AppPrefs.rootPath.ifEmpty { stringResource(R.string.root_whole_phone) } + " · " + stringResource(R.string.root_folder_summary),
				onClick = pickRoot,
				showDivider = true,
			)
			if (AppPrefs.rootPath.isNotEmpty()) {
				KitListRow(title = stringResource(R.string.root_whole_phone), onClick = { AppPrefs.updateRootPath("") }, showDivider = true)
			}
			KitListRow(
				title = stringResource(R.string.covers_online),
				subtitle = stringResource(R.string.covers_online_summary),
				onClick = { AppPrefs.updateCoversOnline(!AppPrefs.coversOnline) },
				showDivider = true,
				trailing = { KitCheckbox(checked = AppPrefs.coversOnline, onCheckedChange = { AppPrefs.updateCoversOnline(it) }) },
			)
			KitListRow(title = stringResource(R.string.rescan), subtitle = stringResource(R.string.rescan_summary), onClick = onRescan, showDivider = true)
			val context = LocalContext.current
			KitListRow(
				title = stringResource(R.string.organize_setting),
				subtitle = stringResource(R.string.organize_setting_summary) + " · " + stringResource(if (Organizer.hasPermission(context)) R.string.organize_permission_ok else R.string.organize_permission_missing),
				onClick = { Organizer.permissionIntent(context)?.let { runCatching { context.startActivity(it) } } },
			)
		}

		KitSectionHead(label = stringResource(R.string.about), kicker = "情報")
		AboutSection()

		KitText(
			text = "― ${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} ―",
			role = KitTextRole.Stamp, color = colors.onSurfaceVariant, fontSize = 11.sp, uppercase = false, textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker() {
	val colors = LocalPersonality.current.colors
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		KitSegmented(
			selected = ThemePrefs.mode,
			items = listOf(
				KitSegmentedItem("system", stringResource(R.string.mode_system)),
				KitSegmentedItem("light", stringResource(R.string.mode_light)),
				KitSegmentedItem("dark", stringResource(R.string.mode_dark)),
			),
			onSelect = { ThemePrefs.selectMode(it) },
			modifier = Modifier.fillMaxWidth(),
			fillWidth = true,
		)
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Themes.all.forEach { theme ->
				val selected = theme.id == ThemePrefs.themeId
				val palette = if (colors.isDark) theme.dark else theme.light
				Row(
					modifier = Modifier
						.clip(RoundedCornerShape(8.dp))
						.background(if (selected) colors.primary else colors.surface)
						.border(1.dp, if (selected) colors.primary else colors.outline, RoundedCornerShape(8.dp))
						.clickable { ThemePrefs.selectTheme(theme.id) }
						.padding(horizontal = 10.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Row {
						Swatch(Color(palette.primary)); Swatch(Color(palette.secondary)); Swatch(Color(palette.gold))
					}
					KitText(text = theme.name, role = KitTextRole.Label, color = if (selected) colors.onPrimary else colors.onSurface, uppercase = false, fontSize = 12.sp)
				}
			}
		}
	}
}

@Composable
private fun Swatch(color: Color) {
	val colors = LocalPersonality.current.colors
	Box(Modifier.size(14.dp).clip(CircleShape).background(color).border(1.dp, colors.outline, CircleShape))
	Spacer(Modifier.width(2.dp))
}
