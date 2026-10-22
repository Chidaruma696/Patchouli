package com.patchouli.app.cast

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A TV or box on the network that accepts a video URL: its name and where to send commands. */
data class Renderer(val name: String, val location: String, val controlUrl: String)

/** Where the TV is in the video and how long the video is, as the TV reports it (0 when it does not say). */
data class Position(val relMs: Long, val durationMs: Long)

/**
 * The little of DLNA/UPnP that sending a video to a TV needs, written here instead of pulling a
 * UPnP stack: an SSDP search for AVTransport renderers, their description XML for the name and
 * the control URL, and a handful of SOAP actions (SetAVTransportURI, Play, Pause, Stop, Seek).
 * The TV fetches the file itself from the phone's little server on the Wi-Fi.
 */
object Dlna {

	private const val TAG = "Dlna"
	private const val SSDP_HOST = "239.255.255.250"
	private const val SSDP_PORT = 1900
	private const val SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"

	private val client: OkHttpClient by lazy {
		OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
	}

	/** The phone's address on the Wi-Fi, the one the TV can reach; null when there is no network. */
	fun lanAddress(): String? = try {
		NetworkInterface.getNetworkInterfaces().toList()
			.filter { it.isUp && !it.isLoopback }
			.sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
			.flatMap { nic -> nic.inetAddresses.toList().map { nic to it } }
			.firstOrNull { (_, a) -> a is java.net.Inet4Address && a.isSiteLocalAddress }
			?.second?.hostAddress
	} catch (e: Throwable) {
		null
	}

	/**
	 * Asks the network for renderers during [timeoutMs]; [onFound] runs for each new one as it
	 * answers, with its description already fetched. Replies come back unicast, so no multicast lock.
	 */
	suspend fun discover(timeoutMs: Long = 4000, onFound: suspend (Renderer) -> Unit) = withContext(Dispatchers.IO) {
		val seen = HashSet<String>()
		val search = "M-SEARCH * HTTP/1.1\r\nHOST: $SSDP_HOST:$SSDP_PORT\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $SERVICE\r\n\r\n".toByteArray()
		DatagramSocket().use { socket ->
			socket.soTimeout = 1000
			val group = InetAddress.getByName(SSDP_HOST)
			repeat(2) { socket.send(DatagramPacket(search, search.size, group, SSDP_PORT)) }
			val deadline = System.currentTimeMillis() + timeoutMs
			val buffer = ByteArray(2048)
			while (System.currentTimeMillis() < deadline) {
				val packet = DatagramPacket(buffer, buffer.size)
				try { socket.receive(packet) } catch (e: SocketTimeoutException) { continue }
				val reply = String(packet.data, 0, packet.length)
				val location = reply.lines().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }?.substringAfter(':')?.trim() ?: continue
				if (!seen.add(location)) continue
				val renderer = try { describe(location) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Log.w(TAG, "description failed: $location", e); null }
				if (renderer != null) onFound(renderer)
			}
		}
	}

	/** The device description: friendly name and the AVTransport control URL, made absolute. */
	private fun describe(location: String): Renderer? {
		val xml = client.newCall(Request.Builder().url(location).build()).execute().use { it.body.string() }
		val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
		var name: String? = null
		var urlBase: String? = null
		var inService = false
		var serviceType: String? = null
		var controlUrl: String? = null
		var found: String? = null
		var tag: String? = null
		while (parser.next() != XmlPullParser.END_DOCUMENT) {
			when (parser.eventType) {
				XmlPullParser.START_TAG -> {
					tag = parser.name
					if (tag == "service") { inService = true; serviceType = null; controlUrl = null }
				}
				XmlPullParser.TEXT -> when (tag) {
					"friendlyName" -> if (name == null) name = parser.text.trim()
					"URLBase" -> urlBase = parser.text.trim()
					"serviceType" -> if (inService) serviceType = parser.text.trim()
					"controlURL" -> if (inService) controlUrl = parser.text.trim()
				}
				XmlPullParser.END_TAG -> {
					if (parser.name == "service") {
						if (found == null && serviceType?.contains("AVTransport") == true && controlUrl != null) found = controlUrl
						inService = false
					}
					tag = null
				}
			}
		}
		val control = found ?: return null
		val base = urlBase?.takeIf { it.isNotBlank() } ?: location
		return Renderer(name ?: URL(location).host, location, URL(URL(base), control).toString())
	}

	/** Hands the TV the link and what to call it; [mime] is what the server will send. */
	suspend fun setUri(renderer: Renderer, url: String, title: String, mime: String) {
		val metadata = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>${esc(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:$mime:*">${esc(url)}</res></item></DIDL-Lite>"""
		action(renderer, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>${esc(metadata)}</CurrentURIMetaData>")
	}

	suspend fun play(renderer: Renderer) = action(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
	suspend fun pause(renderer: Renderer) = action(renderer, "Pause", "<InstanceID>0</InstanceID>")
	suspend fun stop(renderer: Renderer) = action(renderer, "Stop", "<InstanceID>0</InstanceID>")

	/** Jumps to [ms] into the video, so the TV picks up where the phone was. */
	suspend fun seek(renderer: Renderer, ms: Long) {
		val s = ms / 1000
		val target = String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
		action(renderer, "Seek", "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>$target</Target>")
	}

	/** Where the TV is right now (GetPositionInfo); null when it does not report a time. */
	suspend fun position(renderer: Renderer): Position? {
		val xml = action(renderer, "GetPositionInfo", "<InstanceID>0</InstanceID>")
		val rel = parseTime(tagText(xml, "RelTime")) ?: return null
		val duration = parseTime(tagText(xml, "TrackDuration")) ?: 0L
		return Position(rel, duration)
	}

	/** PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING… (GetTransportInfo); null when it does not say. */
	suspend fun transportState(renderer: Renderer): String? =
		tagText(action(renderer, "GetTransportInfo", "<InstanceID>0</InstanceID>"), "CurrentTransportState")

	/** Sends one SOAP action and gives back the TV's answer, for the actions that return something. */
	private suspend fun action(renderer: Renderer, name: String, arguments: String): String = withContext(Dispatchers.IO) {
		val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$name xmlns:u="$SERVICE">$arguments</u:$name></s:Body></s:Envelope>"""
		val request = Request.Builder().url(renderer.controlUrl)
			.header("SOAPACTION", "\"$SERVICE#$name\"")
			.header("Content-Type", "text/xml; charset=\"utf-8\"")
			.post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
			.build()
		client.newCall(request).execute().use { response ->
			val text = response.body.string()
			if (!response.isSuccessful) {
				val code = Regex("<errorCode>(\\d+)</errorCode>").find(text)?.groupValues?.get(1)
				error("$name: HTTP ${response.code}${code?.let { " (UPnP $it)" } ?: ""}")
			}
			text
		}
	}

	private fun tagText(xml: String, tag: String): String? = Regex("<$tag>([^<]*)</$tag>").find(xml)?.groupValues?.get(1)?.trim()

	/** "0:01:23" or "0:01:23.500" to milliseconds; NOT_IMPLEMENTED and the like give null. */
	private fun parseTime(s: String?): Long? {
		val parts = s?.takeIf { it.isNotBlank() }?.substringBefore('.')?.split(':') ?: return null
		if (parts.size != 3) return null
		val h = parts[0].toLongOrNull() ?: return null
		val m = parts[1].toLongOrNull() ?: return null
		val sec = parts[2].toLongOrNull() ?: return null
		return (h * 3600 + m * 60 + sec) * 1000
	}

	private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
