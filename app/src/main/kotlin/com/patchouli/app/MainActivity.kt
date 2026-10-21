package com.patchouli.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.patchouli.app.library.Folder
import com.patchouli.app.library.Library
import com.patchouli.app.library.Video
import com.patchouli.app.player.PlayerActivity
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.rememberRootPicker
import com.patchouli.app.ui.kit.KitBottomBar
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitIconButton
import com.patchouli.app.ui.kit.KitNavItem
import com.patchouli.app.ui.kit.KitScaffold
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.KitTopBar
import com.patchouli.app.ui.kit.LocalPersonality
import com.patchouli.app.ui.kit.PatchouliTheme
import com.patchouli.app.ui.screens.FolderScreen
import com.patchouli.app.ui.screens.LibraryScreen
import com.patchouli.app.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private const val TAB_LIBRARY = "library"
private const val TAB_SETTINGS = "settings"

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		setContent {
			PatchouliTheme { App() }
		}
	}

	override fun onResume() {
		super.onResume()
		// something may have been downloaded or deleted while we were away
		if (hasPermission() && Library.root != null) lifecycleScope.launch { Library.scan(this@MainActivity) }
	}

	private fun hasPermission(): Boolean =
		ContextCompat.checkSelfPermission(this, videoPermission()) == PackageManager.PERMISSION_GRANTED

	companion object {
		fun videoPermission(): String =
			if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
	}
}

@Composable
private fun App() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val permission = MainActivity.videoPermission()
	var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
	val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

	LaunchedEffect(granted) {
		if (granted && Library.root == null) Library.scan(context)
	}

	if (!granted) {
		PermissionScreen(onAsk = { ask.launch(permission) })
		return
	}
	if (!AppPrefs.rootChosen) {
		RootScreen()
		return
	}

	var tab by rememberSaveable { mutableStateOf(TAB_LIBRARY) }
	val stack = remember { mutableStateListOf<String>() }
	BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

	val open: (Folder) -> Unit = { stack += it.path }
	val play: (Video, List<Video>) -> Unit = { video, playlist ->
		context.startActivity(PlayerActivity.intent(context, playlist.map { it.path }, playlist.indexOf(video).coerceAtLeast(0)))
	}
	val rescan: () -> Unit = { scope.launch { Library.scan(context) } }

	val current = stack.lastOrNull()?.let { Library.find(it) }
	if (stack.isNotEmpty() && current == null) {
		// the folder went away (a rescan after deleting): fall back
		stack.clear()
	}

	if (current != null) {
		FolderScreen(folder = current, onOpenFolder = open, onPlay = play, onBack = { stack.removeAt(stack.lastIndex) })
		return
	}

	KitScaffold(
		topBar = {
			KitTopBar(
				title = stringResource(R.string.app_name),
				titleAccent = "chouli",
				subtitle = stringResource(if (tab == TAB_LIBRARY) R.string.library_kicker else R.string.settings),
				actions = {
					if (tab == TAB_LIBRARY) KitIconButton(ImageVector.vectorResource(R.drawable.ic_refresh), stringResource(R.string.rescan), onClick = rescan)
				},
			)
		},
		bottomBar = {
			KitBottomBar(
				items = listOf(
					KitNavItem(TAB_LIBRARY, stringResource(R.string.library), ImageVector.vectorResource(R.drawable.ic_library)),
					KitNavItem(TAB_SETTINGS, stringResource(R.string.settings), ImageVector.vectorResource(R.drawable.ic_settings)),
				),
				selectedId = tab,
				onSelect = { tab = it },
			)
		},
	) { padding ->
		when (tab) {
			TAB_SETTINGS -> SettingsScreen(contentPadding = padding, onRescan = rescan)
			else -> LibraryScreen(contentPadding = padding, onOpenFolder = open, onPlay = { video -> play(video, Library.siblingsOf(video)) })
		}
	}
}

/** First start: which folder is the library. */
@Composable
private fun RootScreen() {
	val colors = LocalPersonality.current.colors
	val pick = rememberRootPicker()
	KitScaffold(topBar = { KitTopBar(title = stringResource(R.string.app_name), titleAccent = "chouli") }) { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Image(painterResource(R.drawable.patchouli), contentDescription = null, modifier = Modifier.height(160.dp))
			KitText(stringResource(R.string.root_title), role = KitTextRole.Display)
			KitText(stringResource(R.string.root_text), role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)
			KitButton(onClick = pick, label = stringResource(R.string.root_choose), emphasized = true)
			KitButton(onClick = { AppPrefs.updateRootPath("") }, label = stringResource(R.string.root_whole_phone), variant = KitButtonVariant.Text)
		}
	}
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
	val context = LocalContext.current
	val colors = LocalPersonality.current.colors
	KitScaffold(topBar = { KitTopBar(title = stringResource(R.string.app_name), titleAccent = "chouli") }) { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			KitText(stringResource(R.string.permission_title), role = KitTextRole.Display)
			KitText(stringResource(R.string.permission_text), role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)
			KitButton(onClick = onAsk, label = stringResource(R.string.permission_button), emphasized = true)
			KitButton(
				onClick = {
					context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
				},
				label = stringResource(R.string.permission_settings),
				variant = KitButtonVariant.Text,
			)
		}
	}
}
