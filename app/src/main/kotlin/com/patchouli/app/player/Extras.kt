package com.patchouli.app.player

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** The small things the system can do for the picture and the sound: refresh rate, loudness, a snapshot. */
object Extras {
	private const val TAG = "Extras"

	/** The first SurfaceView under a view: libVLC's video surface. */
	fun videoSurface(root: View): SurfaceView? {
		if (root is SurfaceView) return root
		if (root is ViewGroup) for (i in 0 until root.childCount) videoSurface(root.getChildAt(i))?.let { return it }
		return null
	}

	/**
	 * Asks the display for a refresh rate the video divides evenly (24 fps on a 120 Hz panel, say),
	 * so the picture stops juddering. On 11+ the surface says what it wants; before that a display
	 * mode of the same size is picked by hand.
	 */
	fun matchFrameRate(activity: Activity, surface: SurfaceView?, fps: Float) {
		if (fps <= 1f || fps > 121f) return
		runCatching {
			if (Build.VERSION.SDK_INT >= 30 && surface != null) {
				surface.holder.surface.setFrameRate(fps, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
			}
			val display = if (Build.VERSION.SDK_INT >= 30) activity.display else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay
			display ?: return
			val current = display.mode
			val best = display.supportedModes
				.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
				.filter { mode -> val k = mode.refreshRate / fps; abs(k - k.roundToInt()) < 0.01f && k >= 1f }
				.maxByOrNull { it.refreshRate } ?: return
			if (best.modeId != current.modeId) {
				activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = best.modeId }
				Log.i(TAG, "display ${best.refreshRate} Hz for $fps fps")
			}
		}.onFailure { Log.w(TAG, "frame rate", it) }
	}

	/** Back to whatever the system likes when the player closes. */
	fun releaseFrameRate(activity: Activity) {
		runCatching { activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 0 } }
	}

	/**
	 * A compressor and a limiter on the player's audio session (9+): quiet dialogue comes up,
	 * the opening does not blow the speaker. Null when the phone has no such effect.
	 */
	fun normalizer(sessionId: Int): DynamicsProcessing? {
		if (Build.VERSION.SDK_INT < 28 || sessionId == 0) return null
		return runCatching {
			val config = DynamicsProcessing.Config.Builder(
				DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION, 2,
				false, 0, // no pre EQ
				true, 1,  // one wide band of compression
				false, 0, // no post EQ
				true,     // and a limiter at the end
			).build()
			val effect = DynamicsProcessing(0, sessionId, config)
			val band = effect.getMbcBandByChannelIndex(0, 0).apply {
				isEnabled = true
				cutoffFrequency = 20_000f
				attackTime = 5f
				releaseTime = 120f
				ratio = 3f
				threshold = -24f
				kneeWidth = 6f
				noiseGateThreshold = -80f
				expanderRatio = 1f
				preGain = 0f
				postGain = 8f
			}
			effect.setMbcBandAllChannelsTo(0, band)
			val limiter = effect.getLimiterByChannelIndex(0).apply {
				isEnabled = true
				attackTime = 1f
				releaseTime = 60f
				ratio = 10f
				threshold = -2f
				postGain = 0f
			}
			effect.setLimiterAllChannelsTo(limiter)
			effect.enabled = true
			effect
		}.onFailure { Log.w(TAG, "normalizer", it) }.getOrNull()
	}

	/** Copies what the video surface shows into a PNG in Pictures/Patchouli; [onDone] says whether it worked. */
	fun snapshot(context: Context, surface: SurfaceView, name: String, onDone: (Boolean) -> Unit) {
		val w = surface.width
		val h = surface.height
		if (w <= 0 || h <= 0) { onDone(false); return }
		val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		runCatching {
			PixelCopy.request(surface, bitmap, { result ->
				if (result != PixelCopy.SUCCESS) { onDone(false); return@request }
				Thread {
					val ok = runCatching { save(context, bitmap, name) }.onFailure { Log.w(TAG, "snapshot", it) }.getOrDefault(false)
					Handler(Looper.getMainLooper()).post { onDone(ok) }
				}.start()
			}, Handler(Looper.getMainLooper()))
		}.onFailure { Log.w(TAG, "pixel copy", it); onDone(false) }
	}

	private fun save(context: Context, bitmap: Bitmap, name: String): Boolean {
		val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
		if (Build.VERSION.SDK_INT >= 29) {
			val values = ContentValues().apply {
				put(MediaStore.Images.Media.DISPLAY_NAME, "$safe.png")
				put(MediaStore.Images.Media.MIME_TYPE, "image/png")
				put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Patchouli")
				put(MediaStore.Images.Media.IS_PENDING, 1)
			}
			val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
			context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
			context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
			return true
		}
		val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Patchouli").also { it.mkdirs() }
		val file = File(dir, "$safe.png")
		file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
		MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
		return true
	}
}
