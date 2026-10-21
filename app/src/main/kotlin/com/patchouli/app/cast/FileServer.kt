package com.patchouli.app.cast

import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * A small HTTP server on the Wi-Fi address that hands a TV the video file, whole or the byte range
 * it asks for (206), so the TV can seek. Only files the app points it at are reachable, by a path
 * hidden in the link; nothing is listed.
 */
class FileServer(private val publicHost: String) : NanoHTTPD("0.0.0.0", 0) {

	/** The link a TV fetches [path] from. */
	fun urlFor(path: String): String = "http://$publicHost:$listeningPort/f?p=${b64(path)}"

	override fun serve(session: IHTTPSession): Response {
		if (session.method == Method.OPTIONS) return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
		if (session.uri != "/f") return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
		val path = session.parameters["p"]?.firstOrNull()?.let(::unb64) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing path")
		val file = File(path)
		if (!file.isFile) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
		val mime = mimeOf(path)
		val total = file.length()
		val range = session.headers["range"]?.removePrefix("bytes=")
		val response = if (range == null) {
			newFixedLengthResponse(Response.Status.OK, mime, file.inputStream(), total)
		} else {
			val start = range.substringBefore('-').toLongOrNull() ?: 0L
			val end = range.substringAfter('-', "").toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
			val stream = file.inputStream().also { it.skip(start) }
			newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, stream, end - start + 1).also {
				it.addHeader("Content-Range", "bytes $start-$end/$total")
			}
		}
		response.addHeader("Accept-Ranges", "bytes")
		// some TVs read these before anything else
		response.addHeader("transferMode.dlna.org", "Streaming")
		response.addHeader("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
		return response
	}

	private fun b64(s: String): String = Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
	private fun unb64(s: String): String = String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))

	companion object {
		/** The type a TV expects for the file; a wrong one is refused by many. */
		fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
			"mkv" -> "video/x-matroska"
			"webm" -> "video/webm"
			"avi" -> "video/x-msvideo"
			"ts", "m2ts" -> "video/mp2t"
			"mov" -> "video/quicktime"
			"3gp" -> "video/3gpp"
			"wmv" -> "video/x-ms-wmv"
			"flv" -> "video/x-flv"
			else -> "video/mp4"
		}
	}
}
