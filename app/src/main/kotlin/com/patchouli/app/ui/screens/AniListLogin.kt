package com.patchouli.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.patchouli.app.R
import com.patchouli.app.catalog.AniListSync
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitIconButton
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality
import kotlinx.coroutines.launch

/**
 * AniList's own login page, inside the app. When it sends the browser back to our address with
 * the token in the fragment, the page is closed and the token kept. The session cookies are
 * dropped after, so the next login starts clean.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AniListLogin(onDismiss: () -> Unit, onConnected: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val scope = rememberCoroutineScope()
	var busy by remember { mutableStateOf(false) }
	var failed by remember { mutableStateOf(false) }

	fun take(url: String): Boolean {
		if (!url.startsWith(AniListSync.REDIRECT)) return false
		val token = url.substringAfter('#', "").split('&').map { it.split('=', limit = 2) }.firstOrNull { it[0] == "access_token" }?.getOrNull(1)
		if (token.isNullOrEmpty()) { failed = true; return true }
		busy = true
		scope.launch {
			val ok = AniListSync.connect(token)
			busy = false
			if (ok) onConnected() else failed = true
		}
		return true
	}

	Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
		BackHandler { onDismiss() }
		Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
			Row(
				modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				KitIconButton(ImageVector.vectorResource(R.drawable.ic_back), stringResource(R.string.back), onClick = onDismiss)
				KitText(stringResource(R.string.anilist_login_title), role = KitTextRole.Title, fontSize = 18.sp)
			}
			if (failed) {
				KitText(stringResource(R.string.anilist_login_failed), role = KitTextRole.Body, color = colors.error, uppercase = false, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
			}
			Box(Modifier.weight(1f)) {
				AndroidView(
					factory = { ctx ->
						CookieManager.getInstance().removeAllCookies(null)
						WebView(ctx).apply {
							settings.javaScriptEnabled = true
							settings.domStorageEnabled = true
							webViewClient = object : WebViewClient() {
								override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = take(request.url.toString())
							}
							loadUrl(AniListSync.authorizeUrl)
						}
					},
					modifier = Modifier.fillMaxSize(),
				)
				if (busy) Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) { KitCircularProgress() }
			}
		}
	}
}
