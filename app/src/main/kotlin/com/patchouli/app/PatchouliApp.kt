package com.patchouli.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.patchouli.app.catalog.Episodes
import com.patchouli.app.catalog.Metadata
import com.patchouli.app.library.Progress
import com.patchouli.app.player.TrackPrefs
import com.patchouli.app.ui.AppPrefs
import com.patchouli.app.ui.kit.ThemePrefs

class PatchouliApp : Application(), SingletonImageLoader.Factory {

	override fun onCreate() {
		super.onCreate()
		instance = this
		ThemePrefs.init(this)
		AppPrefs.init(this)
		Progress.init(this)
		Metadata.init(this)
		Episodes.init(this)
		TrackPrefs.init(this)
	}

	/** Covers come from the network; thumbnails are frames pulled out of the files themselves. */
	override fun newImageLoader(context: PlatformContext): ImageLoader =
		ImageLoader.Builder(context)
			.components {
				add(OkHttpNetworkFetcherFactory())
				add(VideoFrameDecoder.Factory())
			}
			.crossfade(true)
			.build()

	companion object {
		lateinit var instance: PatchouliApp
			private set

		/** A string of the app in the user's language, for code without its own Context. */
		fun text(id: Int, vararg args: Any): String = instance.getString(id, *args)
	}
}
