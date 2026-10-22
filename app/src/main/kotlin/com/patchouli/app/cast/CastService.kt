package com.patchouli.app.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.patchouli.app.MainActivity
import com.patchouli.app.R
import com.patchouli.app.library.Mark
import com.patchouli.app.library.Progress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What is being sent to the TV right now, for the player's remote screen and the notification;
 * [positionMs] and [durationMs] are what the TV reports, 0 until it does.
 */
data class CastState(
	val tv: String, val title: String, val playing: Boolean, val busy: Boolean = false, val error: String? = null,
	val positionMs: Long = 0, val durationMs: Long = 0,
)

/**
 * Keeps a video flowing to the TV after the player is gone: a foreground service that runs the
 * file server on the Wi-Fi address, tells the TV what to play (and where the phone was in it),
 * asks it every second where it is, and answers pause/play/seek/stop from the player's remote
 * screen or the notification.
 */
class CastService : Service() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var server: FileServer? = null
	private var renderer: Renderer? = null
	private var ticker: Job? = null
	/** When the phone last asked for a jump: the TV's old position is ignored for a moment after. */
	@Volatile private var seekedAt = 0L

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_START -> start(intent)
			ACTION_PLAY -> control(playing = true)
			ACTION_PAUSE -> control(playing = false)
			ACTION_SEEK -> seekTo(intent.getLongExtra(EXTRA_POSITION, 0L))
			ACTION_STOP -> stopCasting()
		}
		return START_NOT_STICKY
	}

	private fun start(intent: Intent) {
		val path = intent.getStringExtra(EXTRA_PATH) ?: run { stopSelf(); return }
		val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
		val positionMs = intent.getLongExtra(EXTRA_POSITION, 0L)
		val r = Renderer(intent.getStringExtra(EXTRA_TV_NAME).orEmpty(), intent.getStringExtra(EXTRA_TV_LOCATION).orEmpty(), intent.getStringExtra(EXTRA_TV_CONTROL).orEmpty())
		renderer = r
		ticker?.cancel()
		state.value = CastState(r.name, title, playing = false, busy = true)
		startForegroundCompat(buildNotification(r.name, title, playing = false))
		scope.launch {
			try {
				val address = Dlna.lanAddress() ?: error(getString(R.string.cast_no_network))
				val s = server ?: FileServer(address).also { it.start(30_000, false); server = it }
				Dlna.setUri(r, s.urlFor(path), title, FileServer.mimeOf(path))
				Dlna.play(r)
				state.value = CastState(r.name, title, playing = true, positionMs = positionMs)
				notify(buildNotification(r.name, title, playing = true))
				// pick up where the phone was; a TV that cannot seek just starts over
				if (positionMs > Mark.RESUME_MIN_MS) resumeAt(r, positionMs)
				ticker = launch { follow(r, path) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				Log.w(TAG, "cast failed", e)
				state.value = CastState(r.name, title, playing = false, error = e.message ?: e.javaClass.simpleName)
				stopForeground(STOP_FOREGROUND_REMOVE)
				stopSelf()
			}
		}
	}

	/**
	 * A Seek sent right after Play lands while the TV is still opening the file, and most TVs drop
	 * it on the floor and start from zero. So: wait until it says PLAYING, seek, ask where it is,
	 * and try again a few times if it did not move.
	 */
	private suspend fun resumeAt(r: Renderer, ms: Long) {
		val deadline = System.currentTimeMillis() + 20_000
		var attempts = 0
		while (System.currentTimeMillis() < deadline && attempts < 4) {
			val transport = runCatching { Dlna.transportState(r) }.getOrNull()
			if (transport != null && transport != "PLAYING" && transport != "PAUSED_PLAYBACK") {
				delay(500)
				continue
			}
			attempts++
			val accepted = runCatching { Dlna.seek(r, ms) }.onFailure { Log.i(TAG, "seek refused: ${it.message}") }.isSuccess
			delay(1500)
			val p = runCatching { Dlna.position(r) }.getOrNull()
			when {
				p == null -> if (accepted) return // a TV that does not report positions: trust the seek
				p.relMs >= ms - 5_000 -> { Log.i(TAG, "resumed at ${p.relMs} (asked $ms)"); return }
				else -> Log.i(TAG, "seek to $ms not taken yet, TV at ${p.relMs}")
			}
		}
		Log.w(TAG, "could not resume at $ms")
	}

	/** Once a second: where the TV is, for the slider and the bookmark; now and then whether it still plays. */
	private suspend fun follow(r: Renderer, path: String) {
		var tick = 0
		while (true) {
			delay(1000)
			val current = state.value ?: return
			if (System.currentTimeMillis() - seekedAt < 2_500) continue
			val p = runCatching { Dlna.position(r) }.getOrNull()
			if (p != null) {
				state.value = (state.value ?: return).copy(positionMs = p.relMs, durationMs = if (p.durationMs > 0) p.durationMs else current.durationMs)
				if (p.durationMs > 0) withContext(Dispatchers.Main) { Progress.update(path, p.relMs, p.durationMs) }
			}
			// paused from the TV's own remote: the phone should show it
			if (tick++ % 3 == 0) {
				val playing = when (runCatching { Dlna.transportState(r) }.getOrNull()) {
					"PLAYING", "TRANSITIONING" -> true
					"PAUSED_PLAYBACK", "STOPPED" -> false
					else -> null
				}
				if (playing != null && playing != state.value?.playing) {
					state.value = state.value?.copy(playing = playing)
					state.value?.let { notify(buildNotification(it.tv, it.title, it.playing)) }
				}
			}
		}
	}

	private fun seekTo(ms: Long) {
		val r = renderer ?: return
		seekedAt = System.currentTimeMillis()
		state.value = state.value?.copy(positionMs = ms)
		scope.launch {
			runCatching { Dlna.seek(r, ms) }.onFailure { Log.w(TAG, "seek failed", it) }
		}
	}

	private fun control(playing: Boolean) {
		val r = renderer ?: return
		scope.launch {
			try {
				if (playing) Dlna.play(r) else Dlna.pause(r)
				state.value = state.value?.copy(playing = playing)
				state.value?.let { notify(buildNotification(it.tv, it.title, it.playing)) }
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				Log.w(TAG, "cast control failed", e)
			}
		}
	}

	private fun stopCasting() {
		ticker?.cancel()
		renderer?.let { r -> scope.launch { try { Dlna.stop(r) } catch (e: Throwable) { Log.w(TAG, "stop failed", e) } } }
		state.value = null
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun buildNotification(tv: String, title: String, playing: Boolean): Notification {
		val manager = getSystemService(NotificationManager::class.java)
		manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.cast_to_tv), NotificationManager.IMPORTANCE_LOW))
		fun action(action: String): PendingIntent =
			PendingIntent.getForegroundService(this, action.hashCode(), Intent(this, CastService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
		val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
		return NotificationCompat.Builder(this, CHANNEL)
			.setSmallIcon(R.drawable.ic_cast)
			.setContentTitle(getString(R.string.cast_sending, tv))
			.setContentText(title)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setContentIntent(open)
			.addAction(0, getString(if (playing) R.string.pause else R.string.play), action(if (playing) ACTION_PAUSE else ACTION_PLAY))
			.addAction(0, getString(R.string.cast_stop), action(ACTION_STOP))
			.build()
	}

	private fun notify(notification: Notification) {
		getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
	}

	private fun startForegroundCompat(notification: Notification) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
		} else {
			startForeground(NOTIFICATION_ID, notification)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		server?.stop()
		server = null
		scope.cancel()
		if (state.value?.error == null) state.value = null
	}

	companion object {
		private const val TAG = "Cast"
		private const val CHANNEL = "cast"
		private const val NOTIFICATION_ID = 1002
		private const val ACTION_START = "com.patchouli.app.cast.START"
		private const val ACTION_PLAY = "com.patchouli.app.cast.PLAY"
		private const val ACTION_PAUSE = "com.patchouli.app.cast.PAUSE"
		private const val ACTION_SEEK = "com.patchouli.app.cast.SEEK"
		private const val ACTION_STOP = "com.patchouli.app.cast.STOP"
		private const val EXTRA_TV_NAME = "tv_name"
		private const val EXTRA_TV_LOCATION = "tv_location"
		private const val EXTRA_TV_CONTROL = "tv_control"
		private const val EXTRA_PATH = "path"
		private const val EXTRA_TITLE = "title"
		private const val EXTRA_POSITION = "position"

		/** Null when nothing is being sent; observed by the player's remote screen. */
		val state = MutableStateFlow<CastState?>(null)

		fun start(context: Context, renderer: Renderer, path: String, title: String, positionMs: Long) {
			val intent = Intent(context, CastService::class.java).setAction(ACTION_START)
				.putExtra(EXTRA_TV_NAME, renderer.name).putExtra(EXTRA_TV_LOCATION, renderer.location).putExtra(EXTRA_TV_CONTROL, renderer.controlUrl)
				.putExtra(EXTRA_PATH, path).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_POSITION, positionMs)
			context.startForegroundService(intent)
		}

		fun play(context: Context) = context.startForegroundService(Intent(context, CastService::class.java).setAction(ACTION_PLAY))
		fun pause(context: Context) = context.startForegroundService(Intent(context, CastService::class.java).setAction(ACTION_PAUSE))
		fun seek(context: Context, ms: Long) = context.startForegroundService(Intent(context, CastService::class.java).setAction(ACTION_SEEK).putExtra(EXTRA_POSITION, ms))
		fun stop(context: Context) = context.startForegroundService(Intent(context, CastService::class.java).setAction(ACTION_STOP))
	}
}
