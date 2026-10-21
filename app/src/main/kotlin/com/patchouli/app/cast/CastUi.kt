package com.patchouli.app.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patchouli.app.R
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitSheet
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality

/** The TVs found on the network as they answer; tapping one sends the file at [path] to it. */
@Composable
fun CastSheet(path: String, title: String, positionMs: Long, onDismiss: () -> Unit, onSent: () -> Unit) {
	val context = LocalContext.current
	val colors = LocalPersonality.current.colors
	val renderers = remember { mutableStateListOf<Renderer>() }
	var searching by remember { mutableStateOf(true) }
	LaunchedEffect(Unit) {
		try { Dlna.discover(5000) { r -> if (renderers.none { it.location == r.location }) renderers += r } } catch (e: Throwable) { }
		searching = false
	}
	KitSheet(onDismiss = onDismiss, title = stringResource(R.string.cast_to_tv)) {
		if (renderers.isNotEmpty()) {
			KitListContainer {
				renderers.forEachIndexed { index, r ->
					KitListRow(
						title = r.name,
						subtitle = java.net.URL(r.location).host,
						onClick = { CastService.start(context, r, path, title, positionMs); onSent(); onDismiss() },
						showDivider = index < renderers.lastIndex,
						trailing = { KitText(text = "📺", role = KitTextRole.Label, color = colors.primary) },
					)
				}
			}
			Spacer(Modifier.height(10.dp))
		}
		if (searching) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				KitCircularProgress()
				KitText(text = stringResource(R.string.cast_searching), role = KitTextRole.Body, uppercase = false)
			}
		} else if (renderers.isEmpty()) {
			KitText(text = stringResource(R.string.cast_none), role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)
		}
		Spacer(Modifier.height(8.dp))
	}
}

/**
 * While something is on the TV the phone is a remote, nothing else: the player underneath is
 * paused and covered. One screen with where it is going, what, pause/play, and two ways out:
 * stop, or back to the phone.
 */
@Composable
fun CastRemote(modifier: Modifier = Modifier, onBackToPhone: () -> Unit) {
	val context = LocalContext.current
	val colors = LocalPersonality.current.colors
	val state by CastService.state.collectAsState()
	val s = state ?: return
	Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
		Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(32.dp)) {
			KitText(text = "📺", role = KitTextRole.Title, color = colors.primary)
			KitText(
				text = if (s.error != null) stringResource(R.string.cast_failed, s.error) else stringResource(R.string.cast_sending, s.tv),
				role = KitTextRole.Title, color = if (s.error != null) colors.error else colors.onBackground, uppercase = false, maxLines = 3,
			)
			KitText(text = s.title, role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, maxLines = 2)
			Spacer(Modifier.height(10.dp))
			if (s.error == null) {
				if (s.busy) {
					KitCircularProgress()
				} else {
					KitButton(
						onClick = { if (s.playing) CastService.pause(context) else CastService.play(context) },
						label = if (s.playing) "❚❚  " + stringResource(R.string.pause) else "▶  " + stringResource(R.string.play),
					)
				}
			}
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				KitButton(
					onClick = { if (s.error != null) CastService.state.value = null else CastService.stop(context) },
					label = "■  " + stringResource(R.string.cast_stop), size = KitButtonSize.Sm, variant = KitButtonVariant.Outline,
				)
				KitButton(
					onClick = { if (s.error == null) CastService.stop(context) else CastService.state.value = null; onBackToPhone() },
					label = stringResource(R.string.cast_back_to_phone), size = KitButtonSize.Sm, variant = KitButtonVariant.Tonal,
				)
			}
		}
	}
}
