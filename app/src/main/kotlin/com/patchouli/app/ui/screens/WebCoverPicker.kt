package com.patchouli.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.patchouli.app.R
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Folder
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitDialog
import com.patchouli.app.ui.kit.KitIconButton
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextField
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.kit.LocalPersonality
import com.patchouli.app.ui.kit.kitShape
import kotlinx.coroutines.launch
import java.net.URLEncoder

private const val DDG = "https://duckduckgo.com/"

private fun imagesUrl(query: String) = "$DDG?q=${URLEncoder.encode(query, "UTF-8")}&iax=images&ia=images"

/** A tap on any image of the page comes to us instead of opening DuckDuckGo's detail panel. */
private const val TAP_SCRIPT = """
(function () {
	if (window.__patchouli) return;
	window.__patchouli = true;
	document.addEventListener('click', function (e) {
		var t = e.target;
		while (t && t.tagName !== 'IMG') t = t.parentElement;
		if (!t) return;
		var src = t.currentSrc || t.src || t.getAttribute('data-src');
		if (!src) return;
		e.preventDefault();
		e.stopPropagation();
		Patchouli.pick(src);
	}, true);
})();
"""

/** The image a tap landed on: the original behind DuckDuckGo's thumbnail proxy first, the thumbnail itself after. */
private fun candidatesFor(src: String): List<String> {
	val url = if (src.startsWith("//")) "https:$src" else src
	val original = runCatching {
		if (url.contains("/iu/?") || url.contains("/iu?")) android.net.Uri.parse(url).getQueryParameter("u") else null
	}.getOrNull()?.takeIf { it.startsWith("http") }
	return listOfNotNull(original, url.takeIf { it.startsWith("http") }).distinct()
}

/**
 * DuckDuckGo's image search inside the app, as a real browser: no API to break. A long press on
 * any image offers it as the folder's cover.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebCoverPicker(folder: Folder, initialQuery: String, onDismiss: () -> Unit, onPicked: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val scope = rememberCoroutineScope()
	var query by remember { mutableStateOf(initialQuery) }
	var webView by remember { mutableStateOf<WebView?>(null) }
	var candidate by remember { mutableStateOf<List<String>?>(null) }
	var saving by remember { mutableStateOf(false) }
	var failed by remember { mutableStateOf(false) }

	Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
		BackHandler {
			val w = webView
			if (w != null && w.canGoBack()) w.goBack() else onDismiss()
		}
		Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
			Row(
				modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				KitIconButton(ImageVector.vectorResource(R.drawable.ic_back), stringResource(R.string.back), onClick = onDismiss)
				KitTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), onCommit = { webView?.loadUrl(imagesUrl(query)) })
				KitButton(onClick = { webView?.loadUrl(imagesUrl(query)) }, label = stringResource(R.string.search), size = KitButtonSize.Sm)
			}
			KitText(
				stringResource(R.string.cover_web_hint), role = KitTextRole.Label, color = colors.onSurfaceVariant, fontSize = 12.sp, uppercase = false,
				modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
			)
			AndroidView(
				modifier = Modifier.fillMaxSize(),
				factory = { ctx ->
					WebView(ctx).apply {
						settings.javaScriptEnabled = true
						settings.domStorageEnabled = true
						settings.loadWithOverviewMode = true
						settings.useWideViewPort = true
						webViewClient = object : WebViewClient() {
							override fun onPageFinished(view: WebView, url: String?) {
								view.evaluateJavascript(TAP_SCRIPT, null)
							}
						}
						addJavascriptInterface(object {
							@JavascriptInterface
							fun pick(src: String) {
								post { candidate = candidatesFor(src).takeIf { it.isNotEmpty() } }
							}
						}, "Patchouli")
						// a long press still works, for the odd image the script does not catch
						setOnLongClickListener {
							val hit = hitTestResult
							val url = hit.extra
							if (url != null && (hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
								candidate = candidatesFor(url).takeIf { it.isNotEmpty() }
								true
							} else false
						}
						loadUrl(imagesUrl(initialQuery))
						webView = this
					}
				},
				onRelease = { it.destroy() },
			)
		}
	}

	candidate?.let { urls ->
		val url = urls.last() // the thumbnail is the one sure to show in the preview
		KitDialog(
			onDismissRequest = { if (!saving) { candidate = null; failed = false } },
			title = { KitText(stringResource(R.string.cover_use_title), role = KitTextRole.Title) },
			text = {
				Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Box(Modifier.width(160.dp).aspectRatio(2f / 3f).clip(kitShape(8.dp)).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
						AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
						if (saving) KitCircularProgress()
					}
					if (failed) KitText(stringResource(R.string.cover_web_failed), role = KitTextRole.Body, color = colors.error, uppercase = false)
				}
			},
			dismissButton = { KitButton(onClick = { candidate = null; failed = false }, label = stringResource(R.string.cancel), variant = KitButtonVariant.Text, enabled = !saving) },
			confirmButton = {
				KitButton(
					onClick = {
						saving = true; failed = false
						scope.launch {
							val ok = Metadata.setCustomCoverFromUrl(folder, urls, DDG)
							saving = false
							if (ok) { candidate = null; onPicked() } else failed = true
						}
					},
					label = stringResource(R.string.cover_use),
					enabled = !saving,
				)
			},
		)
	}
}
