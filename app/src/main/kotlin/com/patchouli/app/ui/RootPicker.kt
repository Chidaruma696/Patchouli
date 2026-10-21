package com.patchouli.app.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.patchouli.app.R

/**
 * The system folder picker, turned into a path of our tree: "primary:Movies/Anime" is "Movies/Anime",
 * an SD card's "1234-5678:Anime" is "1234-5678/Anime" (the tree hangs other volumes under their name).
 */
fun treeUriToPath(uri: Uri): String? {
	if (uri.authority != "com.android.externalstorage.documents") return null
	val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
	val volume = docId.substringBefore(':')
	val rest = docId.substringAfter(':', "").trim('/')
	return when {
		volume == "primary" -> rest
		rest.isEmpty() -> volume
		else -> "$volume/$rest"
	}
}

/** A launcher that opens the picker and saves the chosen folder as the library root. */
@Composable
fun rememberRootPicker(onChosen: () -> Unit = {}): () -> Unit {
	val context = LocalContext.current
	val unsupported = stringResource(R.string.root_unsupported)
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		val path = treeUriToPath(uri)
		if (path == null) {
			Toast.makeText(context, unsupported, Toast.LENGTH_LONG).show()
		} else {
			AppPrefs.updateRootPath(path)
			onChosen()
		}
	}
	return { launcher.launch(null) }
}
