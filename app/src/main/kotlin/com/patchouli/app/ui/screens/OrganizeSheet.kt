package com.patchouli.app.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.patchouli.app.R
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Names
import com.patchouli.app.library.Organizer
import com.patchouli.app.library.Rename
import com.patchouli.app.library.RenameKind
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitChip
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitSheet
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality
import kotlinx.coroutines.launch

/** The list of what "organize this folder" would rename, and the button that does it. */
@Composable
fun OrganizeSheet(folder: Folder, onDismiss: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var granted by remember { mutableStateOf(Organizer.hasPermission(context)) }
	var busy by remember { mutableStateOf(false) }
	val plan = remember(folder) { Organizer.plan(folder) { Metadata[it]?.anime?.year } }
	val changes = plan.count { it.changes }
	val doneText = stringResource(R.string.organize_done)

	// coming back from the system's permission screen
	val lifecycle = LocalLifecycleOwner.current
	DisposableEffect(lifecycle) {
		val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = Organizer.hasPermission(context) }
		lifecycle.lifecycle.addObserver(observer)
		onDispose { lifecycle.lifecycle.removeObserver(observer) }
	}
	val askLegacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

	KitSheet(onDismiss = { if (!busy) onDismiss() }, title = stringResource(R.string.organize), titleJp = Names.series(folder.name)) {
		KitText(stringResource(R.string.organize_explain), role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, modifier = Modifier.padding(bottom = 12.dp))
		if (!granted) {
			KitText(stringResource(R.string.organize_permission_text), role = KitTextRole.Body, uppercase = false, modifier = Modifier.padding(bottom = 10.dp))
			KitButton(
				onClick = {
					val intent = Organizer.permissionIntent(context)
					if (intent != null) runCatching { context.startActivity(intent) } else if (Build.VERSION.SDK_INT < 30) askLegacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
				},
				label = stringResource(R.string.organize_grant), emphasized = true,
			)
			return@KitSheet
		}
		if (changes == 0) {
			KitText(stringResource(R.string.organize_nothing), role = KitTextRole.Body, uppercase = false)
		} else {
			KitListContainer {
				plan.forEachIndexed { index, r -> RenameRow(r, showDivider = index < plan.lastIndex) }
			}
			Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
				KitButton(onClick = onDismiss, label = stringResource(R.string.cancel), variant = KitButtonVariant.Text, enabled = !busy)
				KitButton(
					onClick = {
						busy = true
						scope.launch {
							val n = Organizer.apply(context, plan)
							busy = false
							Toast.makeText(context, String.format(doneText, n), Toast.LENGTH_SHORT).show()
							onDismiss()
						}
					},
					label = stringResource(R.string.organize_apply, changes), emphasized = true, enabled = !busy,
				)
			}
		}
	}
}

@Composable
private fun RenameRow(r: Rename, showDivider: Boolean) {
	val colors = LocalPersonality.current.colors
	val (label, fill) = when (r.kind) {
		RenameKind.Episode -> R.string.rename_episode to colors.primary
		RenameKind.Season -> R.string.rename_season to colors.secondary
		RenameKind.Unchanged -> R.string.rename_same to colors.surfaceVariant
		RenameKind.NoNumber -> R.string.rename_no_number to colors.gold
		RenameKind.Conflict -> R.string.rename_conflict to colors.error
	}
	KitListRow(
		title = if (r.changes) r.to.name else r.from.name,
		subtitle = if (r.changes) r.from.name else null,
		showDivider = showDivider,
		trailing = { KitChip(stringResource(label), small = true, fill = fill) },
	)
}
