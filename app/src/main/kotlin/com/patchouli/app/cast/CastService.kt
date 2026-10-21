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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What is being sent to the TV right now, for the player's remote screen and the notification. */
data class CastState(val tv: String, val title: String, val playing: Boolean, val busy: Boolean = false, val error: String? = null)

/**
 * Keeps a video flowing to the TV after the player is gone: a foreground service that runs the
 * file server on the Wi-Fi address, tells the TV what to play (and where the phone was in it),
 * and answers pause/play/stop from the player's remote screen or the notification.
 */
class CastService : Service() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var server: FileServer? = null
	private var renderer: Renderer? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_START -> start(intent)
			ACTION_PLAY -> control(playing = true)
			ACTION_PAUSE -> control(playing = false)
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
		state.value = CastState(r.name, title, playing = false, busy = true)
		startForegroundCompat(buildNotification(r.name, title, playing = false))
		scope.launch {
			try {
				val address = Dlna.lanAddress() ?: error(getString(R.string.cast_no_network))
				val s = server ?: FileServer(address).also { it.start(30_000, false); server = it }
				Dlna.setUri(r, s.urlFor(path), title, FileServer.mimeOf(path))
				Dlna.play(r)
				// pick up where the phone was; a TV that cannot seek just starts over
				if (positionMs > 5_000) runCatching { Dlna.seek(r, positionMs) }.onFailure { Log.i(TAG, "seek refused: ${it.message}") }
				state.value = CastState(r.name, title, playing = true)
				notify(buildNotification(r.name, title, playing = true))
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
		fun stop(context: Context) = context.startForegroundService(Intent(context, CastService::class.java).setAction(ACTION_STOP))
	}
}
