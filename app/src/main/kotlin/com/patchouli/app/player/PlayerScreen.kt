package com.patchouli.app.player

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.collectAsState
import com.patchouli.app.R
import com.patchouli.app.cast.CastRemote
import com.patchouli.app.cast.CastSheet
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.KitButton
import com.patchouli.app.ui.kit.KitButtonSize
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitLinearProgress
import com.patchouli.app.ui.kit.KitListContainer
import com.patchouli.app.ui.kit.KitListRow
import com.patchouli.app.ui.kit.KitSegmented
import com.patchouli.app.ui.kit.KitSegmentedItem
import com.patchouli.app.ui.kit.KitSheet
import com.patchouli.app.ui.kit.KitText
import com.patchouli.app.ui.kit.KitTextRole
import com.patchouli.app.ui.screens.fmtTime
import kotlinx.coroutines.delay
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.roundToInt

private val ScaleModes = listOf(
	MediaPlayer.ScaleType.SURFACE_BEST_FIT to R.string.scale_fit,
	MediaPlayer.ScaleType.SURFACE_FIT_SCREEN to R.string.scale_fill,
	MediaPlayer.ScaleType.SURFACE_FILL to R.string.scale_stretch,
	MediaPlayer.ScaleType.SURFACE_16_9 to R.string.scale_169,
	MediaPlayer.ScaleType.SURFACE_4_3 to R.string.scale_43,
	MediaPlayer.ScaleType.SURFACE_ORIGINAL to R.string.scale_original,
)

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** What a gesture shows while it lasts: an icon, a line of text, a bar. */
private data class Hud(val icon: Int?, val text: String, val fraction: Float? = null)

private const val SEEK_SPAN_MS = 120_000f // a drag across the whole width moves this much
private const val HIDE_AFTER_MS = 4_000L

@Composable
fun PlayerScreen(activity: PlayerActivity) {
	val player = activity.player
	val context = LocalContext.current
	val audio = remember { context.getSystemService(AudioManager::class.java) }
	val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

	var controlsVisible by remember { mutableStateOf(true) }
	var locked by remember { mutableStateOf(false) }
	var unlockVisible by remember { mutableStateOf(false) }
	var isPlaying by remember { mutableStateOf(false) }
	var position by remember { mutableFloatStateOf(0f) }
	var length by remember { mutableLongStateOf(0L) }
	var timeMs by remember { mutableLongStateOf(0L) }
	var buffering by remember { mutableStateOf(true) }
	var error by remember { mutableStateOf(false) }
	var scaleIndex by remember { mutableIntStateOf(0) }
	var zoom by remember { mutableFloatStateOf(1f) }
	var speed by remember { mutableFloatStateOf(1f) }
	var showRemaining by remember { mutableStateOf(false) }
	var hud by remember { mutableStateOf<Hud?>(null) }
	var hudHold by remember { mutableStateOf(false) }
	var sheet by remember { mutableStateOf<String?>(null) }
	var surface by remember { mutableStateOf(IntSize.Zero) }
	var seekPreview by remember { mutableStateOf<Long?>(null) }
	var seekStartMs by remember { mutableLongStateOf(0L) }
	var casting by remember { mutableStateOf(false) }
	val castState by com.patchouli.app.cast.CastService.state.collectAsState()
	var brightness by remember {
		val lp = activity.window.attributes.screenBrightness
		mutableFloatStateOf(if (lp >= 0f) lp else runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f))
	}
	var volumeAcc by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

	val subtitleLabel = stringResource(R.string.subtitles)
	val audioLabel = stringResource(R.string.audio_tracks)
	val watchedLabel = stringResource(R.string.watched)

	DisposableEffect(player) {
		val listener = MediaPlayer.EventListener { event ->
			when (event.type) {
				MediaPlayer.Event.Opening -> { buffering = true; error = false }
				MediaPlayer.Event.Playing -> { isPlaying = true; buffering = false; error = false; player.rate = speed; activity.updatePipParams() }
				MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> { isPlaying = false; activity.updatePipParams() }
				MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
				MediaPlayer.Event.PositionChanged -> position = event.positionChanged
				MediaPlayer.Event.TimeChanged -> timeMs = event.timeChanged
				MediaPlayer.Event.LengthChanged -> length = event.lengthChanged
				MediaPlayer.Event.EncounteredError -> { buffering = false; error = true }
				// a tick in the middle for a second: this one is done
				MediaPlayer.Event.EndReached -> { hud = Hud(R.drawable.ic_check, "✓  $watchedLabel"); activity.onEnded() }
			}
		}
		player.setEventListener(listener)
		onDispose { player.setEventListener(null) }
	}
	LaunchedEffect(controlsVisible, isPlaying, sheet) {
		if (controlsVisible && isPlaying && sheet == null) {
			delay(HIDE_AFTER_MS)
			controlsVisible = false
		}
	}
	LaunchedEffect(hud, hudHold) {
		if (hud != null && !hudHold) {
			delay(700)
			hud = null
		}
	}
	LaunchedEffect(unlockVisible) {
		if (unlockVisible) { delay(2500); unlockVisible = false }
	}
	// the bookmark, while it plays
	LaunchedEffect(isPlaying) {
		while (isPlaying) {
			delay(5_000)
			activity.saveProgress()
		}
	}

	fun applyZoom() {
		val track = player.currentVideoTrack
		if (abs(zoom - 1f) < 0.03f || track == null || surface == IntSize.Zero || track.width == 0 || track.height == 0) {
			zoom = 1f
			player.scale = 0f
			return
		}
		val fit = minOf(surface.width.toFloat() / track.width, surface.height.toFloat() / track.height)
		player.scale = fit * zoom
	}

	fun seekBy(ms: Long) {
		val target = (player.time + ms).coerceIn(0L, maxOf(length, 0L))
		player.time = target
		timeMs = target
	}

	val step = AppPrefs.seekStepSec * 1000L
	val gestures = remember(step, maxVolume) {
		GestureCallbacks(
			onTap = { controlsVisible = !controlsVisible },
			onDoubleTap = { x ->
				when {
					x < 0.35f -> { seekBy(-step); hud = Hud(R.drawable.ic_prev, "−${step / 1000} s") }
					x > 0.65f -> { seekBy(step); hud = Hud(R.drawable.ic_next, "+${step / 1000} s") }
					else -> if (player.isPlaying) player.pause() else player.play()
				}
			},
			onLongPressStart = { if (player.isPlaying) { player.rate = 2f; hudHold = true; hud = Hud(R.drawable.ic_speed, "2×") } },
			onLongPressEnd = { player.rate = speed; hudHold = false },
			onVerticalDrag = { side, delta ->
				hudHold = true
				if (side == 0) {
					brightness = (brightness + delta * 1.2f).coerceIn(0.01f, 1f)
					activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness }
					hud = Hud(R.drawable.ic_brightness, "${(brightness * 100).roundToInt()} %", brightness)
				} else {
					volumeAcc = (volumeAcc + delta * 1.2f * maxVolume).coerceIn(0f, maxVolume.toFloat())
					val v = volumeAcc.roundToInt()
					if (v != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
					hud = Hud(R.drawable.ic_volume, "${(volumeAcc / maxVolume * 100).roundToInt()} %", volumeAcc / maxVolume)
				}
			},
			onVerticalEnd = { hudHold = false },
			onSeekDrag = { total ->
				if (seekPreview == null) seekStartMs = player.time
				hudHold = true
				val target = (seekStartMs + (total * SEEK_SPAN_MS).toLong()).coerceIn(0L, maxOf(length, 0L))
				seekPreview = target
				val delta = target - seekStartMs
				hud = Hud(null, "${if (delta >= 0) "+" else "−"}${fmtTime(abs(delta))}  ·  ${fmtTime(target)}")
			},
			onSeekEnd = {
				seekPreview?.let { player.time = it; timeMs = it }
				seekPreview = null
				hudHold = false
			},
			onZoom = { factor ->
				zoom = (zoom * factor).coerceIn(0.5f, 4f)
				applyZoom()
				hudHold = true
				hud = Hud(R.drawable.ic_aspect, "${(zoom * 100).roundToInt()} %")
				hudHold = false
			},
		)
	}

	Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { surface = it }) {
		AndroidView(
			factory = { ctx ->
				VLCVideoLayout(ctx).also { layout ->
					player.attachViews(layout, null, true, false)
					player.videoScale = ScaleModes[scaleIndex].first
					player.play()
				}
			},
			modifier = Modifier.fillMaxSize(),
		)
		// the floating window shows the video and nothing else
		if (activity.inPip) return@Box

		// the gestures live on a layer above the video so the surface never eats them
		Box(
			Modifier.fillMaxSize().pointerInput(locked) {
				if (locked) {
					// locked: a tap only brings the unlock button
					detectTapGestures(onTap = { unlockVisible = true })
				} else {
					playerGestures(gestures)
				}
			},
		)

		if (buffering && !error) {
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitCircularProgress(color = Color.White) }
		}
		if (error) {
			Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
				Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
					KitText(stringResource(R.string.err_cannot_play), role = KitTextRole.Body, color = Color.White, uppercase = false, textAlign = TextAlign.Center)
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						KitButton(onClick = { error = false; player.stop(); player.play() }, label = stringResource(R.string.retry), size = KitButtonSize.Sm)
						KitButton(onClick = { activity.toggleDecoder() }, label = stringResource(if (activity.hardware) R.string.decoder_sw else R.string.decoder_hw), size = KitButtonSize.Sm)
					}
				}
			}
		}

		hud?.let { HudView(it) }

		if (locked) {
			if (unlockVisible) {
				Box(Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
					FlatButton(R.drawable.ic_lock, stringResource(R.string.unlock), size = 52.dp, backed = true) { locked = false; unlockVisible = false; controlsVisible = true }
				}
			}
		} else if (controlsVisible) {
			Controls(
				activity = activity,
				isPlaying = isPlaying,
				position = position,
				timeMs = timeMs,
				length = length,
				showRemaining = showRemaining,
				scaleLabel = stringResource(ScaleModes[scaleIndex].second),
				onSeekTo = { p -> position = p; player.position = p },
				onToggleRemaining = { showRemaining = !showRemaining },
				onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
				onLock = { locked = true; controlsVisible = false },
				onAspect = {
					scaleIndex = (scaleIndex + 1) % ScaleModes.size
					zoom = 1f
					player.scale = 0f
					player.videoScale = ScaleModes[scaleIndex].first
					// the button is a glyph now, so the mode's name flashes in the middle instead
					hud = Hud(R.drawable.ic_aspect, context.getString(ScaleModes[scaleIndex].second))
				},
				onSubtitles = { sheet = "spu" },
				onAudio = { sheet = "audio" },
				onMore = { sheet = "more" },
				onPip = { activity.enterPip() },
				onCast = activity.currentFile?.let { { casting = true } },
			)
		}
		// while the TV has the video, the phone is only its remote
		if (castState != null) CastRemote(onBackToPhone = { at ->
			// the phone picks up where the TV was, not where it was paused when the TV took over
			if (at > 0 && length > 0) { player.time = at.coerceAtMost(length); timeMs = player.time }
			player.play()
		})
	}
	if (casting) activity.currentFile?.let { file ->
		CastSheet(path = file, title = activity.title, positionMs = player.time.coerceAtLeast(0), onDismiss = { casting = false }, onSent = { if (player.isPlaying) player.pause() })
	}

	if (activity.inPip) return
	when (sheet) {
		"spu" -> TrackSheet(
			title = subtitleLabel, tracks = player.spuTracks?.toList().orEmpty().map { it.id to it.name }, selected = player.spuTrack,
			onSelect = { player.setSpuTrack(it); sheet = null }, onDismiss = { sheet = null },
			extra = stringResource(R.string.subtitles_load), onExtra = { activity.pickSubtitle(); sheet = null },
		)
		"audio" -> TrackSheet(title = audioLabel, tracks = player.audioTracks?.toList().orEmpty().map { it.id to it.name }, selected = player.audioTrack, onSelect = { player.setAudioTrack(it); sheet = null }, onDismiss = { sheet = null })
		"more" -> KitSheet(onDismiss = { sheet = null }, title = activity.title) {
			KitListContainer {
				KitListRow(title = stringResource(R.string.speed), showDivider = true)
				KitSegmented(
					selected = speed,
					items = Speeds.map { KitSegmentedItem(it, if (it == 1f) "1×" else "${it}×") },
					onSelect = { speed = it; player.rate = it },
					modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(vertical = 10.dp),
					height = 34.dp,
					fillWidth = true,
				)
				KitListRow(
					title = stringResource(R.string.default_decoder),
					subtitle = stringResource(R.string.decoder_summary),
					showDivider = true,
					trailing = {
						KitSegmented(
							selected = activity.hardware,
							items = listOf(KitSegmentedItem(true, stringResource(R.string.decoder_hw)), KitSegmentedItem(false, stringResource(R.string.decoder_sw))),
							onSelect = { if (it != activity.hardware) { activity.toggleDecoder(); sheet = null } },
							height = 34.dp,
						)
					},
				)
				KitListRow(
					title = stringResource(R.string.rotate),
					subtitle = when (activity.orientation) {
						ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> "Landscape"
						ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> "Portrait"
						else -> "Auto"
					},
					onClick = { activity.cycleOrientation() },
				)
			}
		}
	}
}

@Composable
private fun HudView(hud: Hud) {
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(
			modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 22.dp, vertical = 14.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			hud.icon?.let { Icon(ImageVector.vectorResource(it), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
			KitText(hud.text, role = KitTextRole.Mono, color = Color.White, fontSize = 16.sp)
			hud.fraction?.let { f ->
				KitLinearProgress(progress = { f }, modifier = Modifier.width(140.dp).height(4.dp))
			}
		}
	}
}

/** The player's buttons, MX style: a bare white glyph on the dark strip, every one the same box, no discs. */
@Composable
private fun FlatButton(icon: Int, description: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp, iconSize: androidx.compose.ui.unit.Dp = 26.dp, enabled: Boolean = true, backed: Boolean = false, onClick: () -> Unit) {
	Box(
		modifier = modifier
			.size(size)
			.clip(CircleShape)
			// alone on the bare video (the unlock button) it needs a disc to read as a button
			.background(if (backed) Color.Black.copy(alpha = 0.5f) else Color.Transparent)
			.clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(ImageVector.vectorResource(icon), contentDescription = description, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(iconSize))
	}
}

/** A word where MX uses one (HW/SW): same box as the glyph buttons so the row lines up. */
@Composable
private fun FlatWord(text: String, onClick: () -> Unit) {
	Box(
		modifier = Modifier
			.height(44.dp)
			.clip(RoundedCornerShape(8.dp))
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
			.padding(horizontal = 8.dp),
		contentAlignment = Alignment.Center,
	) {
		KitText(text, role = KitTextRole.Mono, color = Color.White, fontSize = 13.sp, maxLines = 1)
	}
}

@Composable
private fun Controls(
	activity: PlayerActivity,
	isPlaying: Boolean,
	position: Float,
	timeMs: Long,
	length: Long,
	showRemaining: Boolean,
	scaleLabel: String,
	onSeekTo: (Float) -> Unit,
	onToggleRemaining: () -> Unit,
	onPlayPause: () -> Unit,
	onLock: () -> Unit,
	onAspect: () -> Unit,
	onSubtitles: () -> Unit,
	onAudio: () -> Unit,
	onMore: () -> Unit,
	onPip: () -> Unit,
	onCast: (() -> Unit)? = null,
) {
	Column(Modifier.fillMaxSize()) {
		// top, MX style: back and title on the left; HW/SW, TV, floating window, subtitles, audio, more on the right
		Row(
			modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))).padding(horizontal = 6.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			FlatButton(R.drawable.ic_back, stringResource(R.string.back)) { activity.finish() }
			Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
				KitText(activity.title, role = KitTextRole.Label, color = Color.White, uppercase = false, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
				if (activity.count > 1) {
					KitText("${activity.index + 1} / ${activity.count}", role = KitTextRole.Mono, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
				}
			}
			FlatWord(stringResource(if (activity.hardware) R.string.decoder_hw else R.string.decoder_sw)) { activity.toggleDecoder() }
			if (onCast != null) FlatButton(R.drawable.ic_cast, stringResource(R.string.cast_to_tv), onClick = onCast)
			FlatButton(R.drawable.ic_pip, stringResource(R.string.pip_enter), onClick = onPip)
			FlatButton(R.drawable.ic_subtitles, stringResource(R.string.subtitles), onClick = onSubtitles)
			FlatButton(R.drawable.ic_audio, stringResource(R.string.audio_tracks), onClick = onAudio)
			FlatButton(R.drawable.ic_more, stringResource(R.string.more), onClick = onMore)
		}
		Spacer(Modifier.weight(1f))
		// bottom: times and slider, then lock | prev play next | aspect rotate, all on one line
		Column(
			modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))).padding(horizontal = 10.dp, vertical = 4.dp),
		) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
				KitText(fmtTime(timeMs), role = KitTextRole.Mono, color = Color.White, fontSize = 13.sp)
				Slider(
					value = position,
					onValueChange = onSeekTo,
					modifier = Modifier.weight(1f),
					colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
				)
				KitText(
					text = if (showRemaining) "−" + fmtTime((length - timeMs).coerceAtLeast(0)) else fmtTime(length),
					role = KitTextRole.Mono, color = Color.White, fontSize = 13.sp,
					modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleRemaining),
				)
			}
			Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
				FlatButton(R.drawable.ic_lock, stringResource(R.string.lock), modifier = Modifier.align(Alignment.CenterStart), onClick = onLock)
				// the three in the exact middle of the screen, whatever sits at the sides
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.align(Alignment.Center)) {
					FlatButton(R.drawable.ic_prev, stringResource(R.string.previous), enabled = activity.hasPrevious) { activity.previous() }
					FlatButton(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, stringResource(if (isPlaying) R.string.pause else R.string.play), size = 64.dp, iconSize = 44.dp, onClick = onPlayPause)
					FlatButton(R.drawable.ic_next, stringResource(R.string.next), enabled = activity.hasNext) { activity.next() }
				}
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd)) {
					FlatButton(R.drawable.ic_aspect, scaleLabel, onClick = onAspect)
					FlatButton(R.drawable.ic_rotate, stringResource(R.string.rotate)) { activity.cycleOrientation() }
				}
			}
		}
	}
}

@Composable
private fun TrackSheet(title: String, tracks: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit, extra: String? = null, onExtra: (() -> Unit)? = null) {
	KitSheet(onDismiss = onDismiss, title = title) {
		if (tracks.isEmpty() && extra == null) {
			KitText(stringResource(R.string.no_tracks), role = KitTextRole.Body, uppercase = false)
		} else {
			KitListContainer {
				tracks.forEachIndexed { index, (id, name) ->
					KitListRow(
						title = if (id == -1) stringResource(R.string.track_disable) else name.ifBlank { stringResource(R.string.track_n, index) },
						selected = id == selected,
						onClick = { onSelect(id) },
						showDivider = index < tracks.lastIndex || extra != null,
					)
				}
				// a way to bring a file from anywhere on the phone (a .srt downloaded apart, say)
				if (extra != null) KitListRow(title = extra, strong = true, onClick = onExtra)
			}
		}
	}
}
