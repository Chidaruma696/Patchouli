package com.patchouli.app.player

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
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
import com.patchouli.app.ui.kit.KitSlider
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
import com.patchouli.app.ui.kit.KitButtonVariant
import com.patchouli.app.ui.kit.KitCircularProgress
import com.patchouli.app.ui.kit.KitScreentone
import com.patchouli.app.ui.kit.KitSurface
import com.patchouli.app.ui.kit.KitSurfaceElevation
import com.patchouli.app.ui.kit.LocalPersonality
import com.patchouli.app.ui.kit.hardShadow
import com.patchouli.app.ui.kit.inkPress
import com.patchouli.app.ui.kit.screentoneCorner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
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
private val SleepMinutes = listOf(15, 30, 45, 60)
/** One step of the subtitle and audio delay buttons. */
private const val DELAY_STEP_MS = 100L

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
				MediaPlayer.Event.Playing -> { isPlaying = true; buffering = false; error = false; player.rate = speed; activity.updatePipParams(); activity.applyRememberedTracks(); activity.onPlayingStarted(); activity.syncSession(true, speed) }
				// a track showed up: maybe the one the series was last watched with
				MediaPlayer.Event.ESAdded -> activity.applyRememberedTracks()
				MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> { isPlaying = false; activity.updatePipParams(); activity.syncSession(false, speed) }
				MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
				MediaPlayer.Event.PositionChanged -> position = event.positionChanged
				MediaPlayer.Event.TimeChanged -> timeMs = event.timeChanged
				MediaPlayer.Event.LengthChanged -> { length = event.lengthChanged; activity.updateSessionMetadata(event.lengthChanged) }
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

	// the sleep timer: when its moment comes, the player closes
	LaunchedEffect(activity.sleepAt) {
		val at = activity.sleepAt ?: return@LaunchedEffect
		delay((at - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L))
		activity.sleepNow()
	}

	// the opening or the ending we are in right now, if AniSkip knows where it is (ending before its last second, so a seek to it does not land back inside)
	val skipNow = activity.skips.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs - 1_000 }
	val skipLabel = skipNow?.let { stringResource(if (it.isOpening) R.string.skip_opening else if (it.isEnding) R.string.skip_ending else R.string.skip_recap) }
	val skippedLabel = skipNow?.let { stringResource(if (it.isOpening) R.string.skipped_opening else if (it.isEnding) R.string.skipped_ending else R.string.skipped_recap) }
	// which ones were already skipped on their own in this file: each gets one chance, so a seek back into it is respected
	val autoSkipped = remember(activity.index) { mutableSetOf<Long>() }
	fun skip(interval: com.patchouli.app.catalog.SkipInterval, word: String) {
		player.time = interval.endMs
		timeMs = interval.endMs
		hud = Hud(R.drawable.ic_next, word)
	}
	LaunchedEffect(skipNow?.startMs, AppPrefs.skipMode) {
		val interval = skipNow ?: return@LaunchedEffect
		if (AppPrefs.skipMode == AppPrefs.SKIP_AUTO && autoSkipped.add(interval.startMs)) {
			// a breath after it starts, so the cut is not felt as a glitch
			delay(400)
			skip(interval, skippedLabel.orEmpty())
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
			Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitCircularProgress() }
		}
		if (error) {
			// the bad news comes in a panel of the comic, not in white on black
			Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
				KitSurface(elevation = KitSurfaceElevation.Raised, screentone = KitScreentone.Corner, contentPadding = PaddingValues(20.dp)) {
					Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
						KitText(stringResource(R.string.err_cannot_play), role = KitTextRole.Body, uppercase = false, textAlign = TextAlign.Center)
						Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
							KitButton(onClick = { error = false; player.stop(); player.play() }, label = stringResource(R.string.retry), size = KitButtonSize.Sm)
							KitButton(onClick = { activity.toggleDecoder() }, label = stringResource(if (activity.hardware) R.string.decoder_sw else R.string.decoder_hw), size = KitButtonSize.Sm, variant = KitButtonVariant.Outline)
						}
					}
				}
			}
		}

		hud?.let { HudView(it) }

		// the skip stamp, bottom right, above the strip when the controls are out; never while locked
		if (skipNow != null && skipLabel != null && !locked && AppPrefs.skipMode == AppPrefs.SKIP_BUTTON) {
			Box(Modifier.fillMaxSize().padding(end = 16.dp, bottom = if (controlsVisible) 124.dp else 24.dp), contentAlignment = Alignment.BottomEnd) {
				KitButton(onClick = { skip(skipNow, skippedLabel.orEmpty()) }, label = skipLabel, trailingIcon = ImageVector.vectorResource(R.drawable.ic_next), emphasized = true)
			}
		}

		// it picked up where it was left days ago: a caption says so for a while, with the way back to the start
		activity.staleResume?.let { mark ->
			LaunchedEffect(mark) { delay(10_000); activity.dismissStaleResume() }
			Box(Modifier.fillMaxSize().padding(top = 84.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
				KitSurface(elevation = KitSurfaceElevation.Raised, screentone = KitScreentone.Corner, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						KitText(
							text = stringResource(R.string.resume_stale, fmtTime(mark.positionMs), DateUtils.getRelativeTimeSpanString(mark.at).toString()),
							role = KitTextRole.Body, uppercase = false, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis,
						)
						KitButton(onClick = { activity.restartFromStart() }, label = stringResource(R.string.resume_restart), size = KitButtonSize.Sm, variant = KitButtonVariant.Outline)
						KitButton(onClick = { activity.dismissStaleResume() }, label = stringResource(R.string.resume_keep), size = KitButtonSize.Sm)
					}
				}
			}
		}

		if (locked) {
			if (unlockVisible) {
				Box(Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
					Stamp(R.drawable.ic_lock, stringResource(R.string.unlock), size = 52.dp, iconSize = 26.dp) { locked = false; unlockVisible = false; controlsVisible = true }
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
			onSelect = { player.setSpuTrack(it); activity.rememberTrack(TrackPrefs.KIND_SUBTITLES, it); sheet = null }, onDismiss = { sheet = null },
			extra = stringResource(R.string.subtitles_load), onExtra = { activity.pickSubtitle(); sheet = null },
			delayMs = activity.subtitleDelayMs, onDelay = { activity.setSubtitleDelay(it) },
		)
		"audio" -> TrackSheet(
			title = audioLabel, tracks = player.audioTracks?.toList().orEmpty().map { it.id to it.name }, selected = player.audioTrack,
			onSelect = { player.setAudioTrack(it); activity.rememberTrack(TrackPrefs.KIND_AUDIO, it); sheet = null }, onDismiss = { sheet = null },
			delayMs = activity.audioDelayMs, onDelay = { activity.setAudioDelay(it) },
		)
		"chapters" -> KitSheet(onDismiss = { sheet = null }, title = stringResource(R.string.chapters), titleJp = "章") {
			val current = runCatching { player.chapter }.getOrDefault(-1)
			KitListContainer {
				activity.chapters.forEachIndexed { index, chapter ->
					KitListRow(
						title = chapter.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chapter_n, index + 1),
						subtitle = fmtTime(chapter.timeOffset) + (if (chapter.duration > 0) " · " + fmtTime(chapter.duration) else ""),
						selected = index == current,
						onClick = { player.time = chapter.timeOffset; timeMs = chapter.timeOffset; sheet = null },
						showDivider = index < activity.chapters.lastIndex,
					)
				}
			}
		}
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
				// the sleep timer: so many minutes, or the end of this episode
				val sleepLeft = activity.sleepAt?.let { ((it - android.os.SystemClock.elapsedRealtime()) / 60_000L + 1).coerceAtLeast(1) }
				KitListRow(
					title = stringResource(R.string.sleep_timer),
					subtitle = when {
						activity.sleepAtEnd -> stringResource(R.string.sleep_at_end)
						sleepLeft != null -> stringResource(R.string.sleep_left, sleepLeft)
						else -> stringResource(R.string.sleep_off)
					},
					showDivider = true,
				)
				KitSegmented(
					selected = when { activity.sleepAtEnd -> -1; activity.sleepAt == null -> 0; else -> sleepLeft?.let { m -> SleepMinutes.minByOrNull { abs(it - m.toInt()) } } ?: 0 },
					items = listOf(KitSegmentedItem(0, stringResource(R.string.sleep_off_short))) + SleepMinutes.map { KitSegmentedItem(it, "$it′") } + KitSegmentedItem(-1, stringResource(R.string.sleep_end_short)),
					onSelect = { if (it == -1) activity.sleepWhenEpisodeEnds(true) else activity.setSleepMinutes(it.takeIf { m -> m > 0 }) },
					modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(vertical = 10.dp),
					height = 34.dp,
					fillWidth = true,
				)
				// the player's own volume above the phone's, for the quiet files
				KitListRow(title = stringResource(R.string.volume_boost), subtitle = stringResource(R.string.volume_boost_summary), showDivider = true)
				KitSegmented(
					selected = activity.volumeBoost,
					items = listOf(100, 125, 150, 200).map { KitSegmentedItem(it, "$it %") },
					onSelect = { activity.boostVolume(it) },
					modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(vertical = 10.dp),
					height = 34.dp,
					fillWidth = true,
				)
				if (activity.chapters.isNotEmpty()) {
					KitListRow(
						title = stringResource(R.string.chapters),
						subtitle = stringResource(R.string.chapters_n, activity.chapters.size),
						onClick = { sheet = "chapters" },
						showDivider = true,
					)
				}
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

/** What a gesture says, as a caption box of the comic: paper, the pen around it, a hard ink shadow behind, over the video. */
@Composable
private fun HudView(hud: Hud) {
	val personality = LocalPersonality.current
	val colors = personality.colors
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(
			modifier = Modifier
				.hardShadow(DpOffset(personality.shadow.card, personality.shadow.card), colors.shadow)
				.background(colors.surface)
				.border(personality.shape.border, colors.outline)
				.padding(horizontal = 22.dp, vertical = 14.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			hud.icon?.let { Icon(ImageVector.vectorResource(it), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(30.dp)) }
			KitText(hud.text, role = KitTextRole.Stamp, color = colors.onSurface, fontSize = 18.sp)
			hud.fraction?.let { f ->
				KitLinearProgress(progress = { f }, modifier = Modifier.width(140.dp).height(8.dp).border(2.dp, colors.outline))
			}
		}
	}
}

/**
 * A button of the player: a square stamp of paper (of the accent for the play), the pen around it
 * and a hard shadow it sinks onto, laid out MX style: every one the same box so the rows line up.
 */
@Composable
private fun Stamp(icon: Int, description: String, modifier: Modifier = Modifier, size: Dp = 38.dp, iconSize: Dp = 20.dp, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
	val colors = LocalPersonality.current.colors
	val interaction = remember { MutableInteractionSource() }
	val pressed by interaction.collectIsPressedAsState()
	val press = animateFloatAsState(if (pressed && enabled) 1f else 0f, label = "stampPress")
	val alpha = if (enabled) 1f else 0.4f
	val big = size >= 50.dp
	val shadow = if (big) 5.dp else 3.dp
	Box(
		modifier = modifier
			.inkPress(pressProgress = { press.value }, shadow = DpOffset(shadow, shadow), shadowColor = colors.shadow.copy(alpha = alpha), shape = RectangleShape)
			.size(size)
			.background(if (accent) colors.primary else colors.surface)
			.border(if (big) 3.dp else 2.5.dp, colors.outline.copy(alpha = alpha))
			.clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(ImageVector.vectorResource(icon), contentDescription = description, tint = (if (accent) colors.onPrimary else colors.onSurface).copy(alpha = alpha), modifier = Modifier.size(iconSize))
	}
}

/** The chrome of the player as a comic page: strips of paper over the video, a rule of ink where they meet it, stamps for buttons. */
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
	val colors = LocalPersonality.current.colors
	Column(Modifier.fillMaxSize()) {
		// top strip, MX layout on paper: back and title on the left; HW/SW, TV, floating window, subtitles, audio, more on the right
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.background(colors.surface)
				.screentoneCorner(colors.onSurface, colors.screentoneOpacity, boost = 1.3f, regionWidth = 180.dp, regionHeight = 60.dp)
				.drawBehind { drawRect(colors.outline, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx())) }
				.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 11.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Stamp(R.drawable.ic_back, stringResource(R.string.back)) { activity.finish() }
			Column(Modifier.weight(1f).padding(start = 2.dp, end = 4.dp)) {
				KitText(activity.title, role = KitTextRole.Stamp, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
				if (activity.count > 1) {
					KitText("${activity.index + 1} / ${activity.count}", role = KitTextRole.Mono, color = colors.onSurfaceVariant, maxLines = 1)
				}
			}
			KitButton(onClick = { activity.toggleDecoder() }, label = stringResource(if (activity.hardware) R.string.decoder_hw else R.string.decoder_sw), size = KitButtonSize.Sm, variant = KitButtonVariant.Outline)
			if (onCast != null) Stamp(R.drawable.ic_cast, stringResource(R.string.cast_to_tv), onClick = onCast)
			Stamp(R.drawable.ic_pip, stringResource(R.string.pip_enter), onClick = onPip)
			Stamp(R.drawable.ic_subtitles, stringResource(R.string.subtitles), onClick = onSubtitles)
			Stamp(R.drawable.ic_audio, stringResource(R.string.audio_tracks), onClick = onAudio)
			Stamp(R.drawable.ic_more, stringResource(R.string.more), onClick = onMore)
		}
		Spacer(Modifier.weight(1f))
		// bottom strip: times and slider, then lock | prev play next | aspect rotate, all on one line
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.background(colors.surface)
				.drawBehind { drawRect(colors.outline, size = Size(size.width, 3.dp.toPx())) }
				.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
		) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				KitText(fmtTime(timeMs), role = KitTextRole.Mono, fontSize = 13.sp)
				KitSlider(value = position, onValueChange = onSeekTo, modifier = Modifier.weight(1f))
				KitText(
					text = if (showRemaining) "−" + fmtTime((length - timeMs).coerceAtLeast(0)) else fmtTime(length),
					role = KitTextRole.Mono, fontSize = 13.sp,
					modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleRemaining),
				)
			}
			Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
				Stamp(R.drawable.ic_lock, stringResource(R.string.lock), modifier = Modifier.align(Alignment.CenterStart), onClick = onLock)
				// the three in the exact middle of the screen, whatever sits at the sides; the play is the big stamp of the accent
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.align(Alignment.Center)) {
					Stamp(R.drawable.ic_prev, stringResource(R.string.previous), size = 42.dp, iconSize = 22.dp, enabled = activity.hasPrevious) { activity.previous() }
					Stamp(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play, stringResource(if (isPlaying) R.string.pause else R.string.play), size = 58.dp, iconSize = 34.dp, accent = true, onClick = onPlayPause)
					Stamp(R.drawable.ic_next, stringResource(R.string.next), size = 42.dp, iconSize = 22.dp, enabled = activity.hasNext) { activity.next() }
				}
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterEnd)) {
					Stamp(R.drawable.ic_aspect, scaleLabel, onClick = onAspect)
					Stamp(R.drawable.ic_rotate, stringResource(R.string.rotate)) { activity.cycleOrientation() }
				}
			}
		}
	}
}

@Composable
private fun TrackSheet(
	title: String, tracks: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit,
	extra: String? = null, onExtra: (() -> Unit)? = null,
	delayMs: Long? = null, onDelay: ((Long) -> Unit)? = null,
) {
	val colors = LocalPersonality.current.colors
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
		// the delay, a tenth of a second at a time; a tap on the number puts it back to zero
		if (delayMs != null && onDelay != null && tracks.size > 1) {
			KitListContainer(Modifier.padding(top = 12.dp)) {
				KitListRow(
					title = stringResource(R.string.track_delay),
					subtitle = stringResource(R.string.track_delay_summary),
					trailing = {
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							KitButton(onClick = { onDelay(delayMs - DELAY_STEP_MS) }, label = "−", size = KitButtonSize.Sm, variant = KitButtonVariant.Outline)
							KitText(
								text = String.format(java.util.Locale.ROOT, "%+.1f s", delayMs / 1000.0),
								role = KitTextRole.Mono, color = if (delayMs == 0L) colors.onSurfaceVariant else colors.onSurface,
								modifier = Modifier.width(64.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDelay(0L) },
								textAlign = TextAlign.Center,
							)
							KitButton(onClick = { onDelay(delayMs + DELAY_STEP_MS) }, label = "+", size = KitButtonSize.Sm, variant = KitButtonVariant.Outline)
						}
					},
				)
			}
		}
	}
}
