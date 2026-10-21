package com.patchouli.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.patchouli.app.R
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitCheckbox
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitScreentone
import com.patchouli.app.ui.kit.KitSurface
import com.patchouli.app.ui.kit.KitSurfaceElevation
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality

const val KEEP_ANDROID_OPEN_URL = "https://keepandroidopen.org/es/"
const val REPO_URL = "https://github.com/Chidaruma696/Patchouli"
private const val NOTICES_URL = "$REPO_URL/blob/main/THIRD_PARTY_NOTICES.md"
private const val KOMI_URL = "https://github.com/komi-store/komi-store"
private const val LIBVLC_URL = "https://code.videolan.org/videolan/vlc-android"
private const val ANILIST_URL = "https://anilist.co"
private const val ART_URL = "https://www.deviantart.com/fantastiic/art/Chibi-Patchouli-Knownledge-Touhou-305044472"

fun Context.openUrl(url: String) {
	runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Keep Android Open notice. Google's developer verification (announced 2025, enforced from 2027) blocks apps from
 * unregistered developers on every certified device, Play Store or not; Patchouli cannot exist under that rule.
 */
@Composable
fun KeepAndroidOpenBanner(modifier: Modifier = Modifier) {
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	KitSurface(
		modifier = modifier.fillMaxWidth(),
		elevation = KitSurfaceElevation.Card,
		screentone = KitScreentone.Corner,
		borderColor = colors.primary,
		contentPadding = PaddingValues(14.dp),
	) {
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			KitText(text = stringResource(R.string.kao_kicker), role = KitTextRole.Stamp, color = colors.primary, fontSize = 11.sp)
			KitText(text = stringResource(R.string.kao_title), role = KitTextRole.Title)
			KitText(text = stringResource(R.string.kao_body), role = KitTextRole.Body, color = colors.onSurfaceVariant, fontSize = 13.sp, uppercase = false)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
				KitButton(onClick = { context.openUrl(KEEP_ANDROID_OPEN_URL) }, label = stringResource(R.string.kao_learn), size = KitButtonSize.Sm, emphasized = true)
				KitButton(onClick = { AppPrefs.updateKaoBanner(false) }, label = stringResource(R.string.kao_hide), size = KitButtonSize.Sm, variant = KitButtonVariant.Text)
			}
		}
	}
}

/** Settings > About: Keep Android Open first, then credits (Komi Store design, libVLC, AniList) and licenses. */
@Composable
fun AboutSection() {
	val context = LocalContext.current
	val colors = LocalPersonality.current.colors
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		KitListContainer {
			KitListRow(
				title = "Keep Android Open",
				subtitle = stringResource(R.string.kao_row_summary),
				strong = true,
				onClick = { context.openUrl(KEEP_ANDROID_OPEN_URL) },
				showDivider = true,
			)
			KitListRow(
				title = stringResource(R.string.kao_show_banner),
				onClick = { AppPrefs.updateKaoBanner(!AppPrefs.kaoBannerVisible) },
				trailing = { KitCheckbox(checked = AppPrefs.kaoBannerVisible, onCheckedChange = { AppPrefs.updateKaoBanner(it) }) },
			)
		}
		KitListContainer {
			KitListRow(title = stringResource(R.string.credit_art), subtitle = stringResource(R.string.credit_art_summary), onClick = { context.openUrl(ART_URL) }, showDivider = true)
			KitListRow(title = stringResource(R.string.credit_design), subtitle = stringResource(R.string.credit_design_summary), onClick = { context.openUrl(KOMI_URL) }, showDivider = true)
			KitListRow(title = stringResource(R.string.credit_player), subtitle = stringResource(R.string.credit_player_summary), onClick = { context.openUrl(LIBVLC_URL) }, showDivider = true)
			KitListRow(title = stringResource(R.string.credit_catalog), subtitle = stringResource(R.string.credit_catalog_summary), onClick = { context.openUrl(ANILIST_URL) }, showDivider = true)
			KitListRow(title = stringResource(R.string.credit_licenses), subtitle = stringResource(R.string.credit_licenses_summary), onClick = { context.openUrl(NOTICES_URL) }, showDivider = true)
			KitListRow(title = stringResource(R.string.credit_source_code), subtitle = stringResource(R.string.credit_source_code_summary), onClick = { context.openUrl(REPO_URL) })
		}
		KitText(
			text = stringResource(R.string.credit_touhou),
			role = KitTextRole.Body, color = colors.onSurfaceVariant, fontSize = 12.sp, uppercase = false,
			modifier = Modifier.padding(horizontal = 4.dp),
		)
	}
}
