package com.patchouli.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.patchouli.app.R
import com.patchouli.app.library.Library
import com.patchouli.app.library.Trash
import com.patchouli.app.library.Video
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitDialog
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality
import kotlinx.coroutines.launch

/**
 * A function that deletes videos the proper way: asks first in a panel of ours, lets the system
 * ask again when it must, and rescans the shelves after. Call it with the list; it does the rest.
 */
@Composable
fun rememberVideoDeleter(onDone: () -> Unit = {}): (List<Video>) -> Unit {
	val context = LocalContext.current
	val colors = LocalPersonality.current.colors
	val scope = rememberCoroutineScope()
	var asking by remember { mutableStateOf<List<Video>?>(null) }
	var pending by remember { mutableStateOf<List<Video>>(emptyList()) }
	val finish: () -> Unit = {
		scope.launch { Library.scan(context) }
		onDone()
	}
	val systemAsk = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) Trash.forgetAll(pending)
		pending = emptyList()
		finish()
	}
	asking?.let { videos ->
		KitDialog(
			onDismissRequest = { asking = null },
			title = { KitText(pluralStringResource(R.plurals.delete_ask_n, videos.size, videos.size), role = KitTextRole.Title) },
			text = {
				Column {
					KitText(
						text = if (videos.size == 1) videos.first().title else videos.joinToString("\n") { it.title }.let { if (videos.size > 6) videos.take(6).joinToString("\n") { v -> v.title } + "\n…" else it },
						role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false, maxLines = 8,
					)
					KitText(stringResource(R.string.delete_warning), role = KitTextRole.Body, color = colors.error, uppercase = false)
				}
			},
			confirmButton = {
				KitButton(
					onClick = {
						asking = null
						val sender = runCatching { Trash.delete(context, videos) }.getOrNull()
						if (sender != null) {
							pending = videos
							systemAsk.launch(IntentSenderRequest.Builder(sender).build())
						} else finish()
					},
					label = stringResource(R.string.delete),
					container = colors.error,
				)
			},
			dismissButton = { KitButton(onClick = { asking = null }, label = stringResource(R.string.cancel), variant = KitButtonVariant.Text) },
		)
	}
	return { videos -> if (videos.isNotEmpty()) asking = videos }
}
