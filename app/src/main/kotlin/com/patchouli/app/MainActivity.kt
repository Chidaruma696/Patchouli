package com.patchouli.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.patchouli.app.ui.kit.KitScreentone
import com.patchouli.app.ui.kit.KitSurface
import com.patchouli.app.ui.kit.KitSurfaceElevation
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
import com.patchouli.app.library.Names
import com.patchouli.app.library.Progress
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
import com.patchouli.app.ui.screens.LibraryLayoutSheet
import com.patchouli.app.ui.screens.LibraryScreen
import com.patchouli.app.ui.screens.SettingsScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAB_LIBRARY = "library"
private const val TAB_SETTINGS = "settings"

class MainActivity : ComponentActivity() {

	/** A download finished, a file was deleted: the MediaStore says so, and the shelves follow after a short pause (one scan for a burst of changes). */
	private var pendingScan: Job? = null
	private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
		override fun onChange(selfChange: Boolean, uri: Uri?) {
			if (!hasPermission() || Library.root == null) return
			pendingScan?.cancel()
			pendingScan = lifecycleScope.launch {
				delay(1_500)
				Library.scan(this@MainActivity)
			}
		}
	}

	/** The launcher's shortcut asked to play on: honoured once the shelves are read. */
	var resumeRequested by mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
		if (intent?.action == ACTION_RESUME) resumeRequested = true
		setContent {
			PatchouliTheme { App() }
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		if (intent.action == ACTION_RESUME) resumeRequested = true
	}

	override fun onDestroy() {
		super.onDestroy()
		contentResolver.unregisterContentObserver(mediaObserver)
	}

	override fun onResume() {
		super.onResume()
		// something may have been downloaded or deleted while we were away
		if (hasPermission() && Library.root != null) lifecycleScope.launch { Library.scan(this@MainActivity) }
	}

	override fun onPause() {
		super.onPause()
		updateShortcut()
	}

	/** A long press on the icon offers to play on from the last video, named. */
	private fun updateShortcut() {
		val manager = getSystemService(ShortcutManager::class.java) ?: return
		val last = Progress.continueWatching(5).firstNotNullOfOrNull { (path, _) -> Library.videoAt(path) }
		runCatching {
			if (last == null) { manager.removeDynamicShortcuts(listOf(SHORTCUT_RESUME)); return }
			val series = Library.find(last.folderPath)?.let { Names.series(it.name) }
			val label = last.episode?.let { getString(R.string.episode_short, it) }?.let { if (series != null) "$it · $series" else it } ?: last.title
			val shortcut = ShortcutInfo.Builder(this, SHORTCUT_RESUME)
				.setShortLabel(label.take(25))
				.setLongLabel(getString(R.string.resume_at, label).take(50))
				.setIcon(Icon.createWithResource(this, R.drawable.ic_play))
				.setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_RESUME))
				.build()
			manager.dynamicShortcuts = listOf(shortcut)
		}
	}

	companion object {
		const val ACTION_RESUME = "com.patchouli.app.RESUME"
		private const val SHORTCUT_RESUME = "resume"
	}

	private fun hasPermission(): Boolean =
		ContextCompat.checkSelfPermission(this, videoPermission()) == PackageManager.PERMISSION_GRANTED

	fun videoPermission(): String =
		if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
}

@Composable
private fun App() {
	val context = LocalContext.current
	val activity = context as MainActivity
	val scope = rememberCoroutineScope()
	val permission = activity.videoPermission()
	var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
	val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

	LaunchedEffect(granted) {
		if (granted && Library.root == null) Library.scan(context)
	}
	// the shortcut on the icon: play on from the last video, as soon as the shelves are read
	LaunchedEffect(activity.resumeRequested, Library.root) {
		if (!activity.resumeRequested || Library.root == null) return@LaunchedEffect
		activity.resumeRequested = false
		val last = Progress.continueWatching(5).firstNotNullOfOrNull { (path, _) -> Library.videoAt(path) } ?: return@LaunchedEffect
		val playlist = Library.siblingsOf(last)
		context.startActivity(PlayerActivity.intent(context, playlist.map { it.path }, playlist.indexOf(last).coerceAtLeast(0)))
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
	var layoutSheet by remember { mutableStateOf(false) }
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
				art = painterResource(R.drawable.patchouli),
				// grid or list, and how many across: the icon shows which one is on
				actions = if (tab == TAB_LIBRARY) {
					{
						KitIconButton(
							ImageVector.vectorResource(if (AppPrefs.libraryColumns == 0) R.drawable.ic_list else R.drawable.ic_grid),
							stringResource(R.string.library_layout),
							onClick = { layoutSheet = true },
						)
					}
				} else null,
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
	if (layoutSheet) LibraryLayoutSheet(onDismiss = { layoutSheet = false })
}

/**
 * The first pages of the book: Patchouli herself, big, then a panel with the question and its buttons.
 * Used for the permission and for the choice of the library folder.
 */
@Composable
private fun WelcomePage(step: String, title: String, text: String, buttons: @Composable ColumnScope.() -> Unit) {
	val colors = LocalPersonality.current.colors
	KitScaffold(topBar = { KitTopBar(title = stringResource(R.string.app_name), titleAccent = "chouli", subtitle = step) }) { padding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(18.dp),
		) {
			Image(painterResource(R.drawable.patchouli), contentDescription = null, modifier = Modifier.height(200.dp))
			KitSurface(modifier = Modifier.fillMaxWidth(), elevation = KitSurfaceElevation.Raised, screentone = KitScreentone.Corner, contentPadding = PaddingValues(20.dp)) {
				Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
					KitText(title, role = KitTextRole.Display, fontSize = 26.sp, lineHeight = 30.sp)
					KitText(text, role = KitTextRole.Body, color = colors.onSurfaceVariant, uppercase = false)
					buttons()
				}
			}
		}
	}
}

/** First start: which folder is the library. */
@Composable
private fun RootScreen() {
	val pick = rememberRootPicker()
	WelcomePage(step = stringResource(R.string.welcome_step_folder), title = stringResource(R.string.root_title), text = stringResource(R.string.root_text)) {
		KitButton(onClick = pick, label = stringResource(R.string.root_choose), emphasized = true, modifier = Modifier.fillMaxWidth())
		KitButton(onClick = { AppPrefs.updateRootPath("") }, label = stringResource(R.string.root_whole_phone), variant = KitButtonVariant.Outline, modifier = Modifier.fillMaxWidth())
	}
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
	val context = LocalContext.current
	WelcomePage(step = stringResource(R.string.welcome_step_permission), title = stringResource(R.string.permission_title), text = stringResource(R.string.permission_text)) {
		KitButton(onClick = onAsk, label = stringResource(R.string.permission_button), emphasized = true, modifier = Modifier.fillMaxWidth())
		KitButton(
			onClick = {
				context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
			},
			label = stringResource(R.string.permission_settings),
			variant = KitButtonVariant.Text,
			modifier = Modifier.fillMaxWidth(),
		)
	}
}
