package com.patchouli.app.library

/** What a file or folder name tells us: the episode number, the name of the series. */
object Names {

	// in the order to try them: an explicit E01 beats a bare number
	private val episodePatterns = listOf(
		Regex("""[Ss]\d{1,2}[Ee](\d{1,4})(?!\d)"""),                                           // S01E24
		Regex("""(?<![A-Za-z0-9])[Ee](?:p(?:isodio|isode|isódio)?\.?)?\s*(\d{1,4})(?!\d)"""), // E01, Ep 3, Episodio 12
		Regex("""\s-\s(\d{1,4})(?:\s|$|v\d|\.)"""),                                           // Serie - 03
		Regex("""^(\d{1,4})(?:\s|$|\.|-)"""),                                                   // 03 Title
		Regex("""(?<![\dx×])(\d{1,4})(?:\s*\[|\s*\(|\s*$)"""),                                  // Title 03 [1080p]
	)

	/** The episode number a file name carries, or null when it has none we trust. */
	fun episode(title: String): Int? {
		val clean = title.substringBeforeLast('.', title)
		for (pattern in episodePatterns) {
			for (match in pattern.findAll(clean)) {
				val n = match.groupValues[1].toIntOrNull() ?: continue
				val after = clean.substring(match.range.last + 1)
				if (n in 1900..2100 && match.groupValues[1].length == 4) continue // a year
				if (after.startsWith("p", ignoreCase = true)) continue             // 720p, 1080p
				return n
			}
		}
		return null
	}

	/** A folder name as a search query: no leading "01 - ", underscores back to spaces (Seija writes them for slashes). */
	fun series(folderName: String): String =
		folderName
			.replace(Regex("""^\d{1,3}\s*[-.]\s*"""), "")
			.replace('_', ' ')
			.replace(Regex("""\s+"""), " ")
			.trim()

	/** The file name without the extension. */
	fun title(fileName: String): String = fileName.substringBeforeLast('.', fileName)

	/** For sorting: numbers inside names compare as numbers, so "2" goes before "10". */
	val natural: Comparator<String> = Comparator { a, b ->
		val ta = tokens(a)
		val tb = tokens(b)
		for (i in 0 until minOf(ta.size, tb.size)) {
			val x = ta[i]
			val y = tb[i]
			val c = if (x.first != null && y.first != null) x.first!!.compareTo(y.first!!) else x.second.compareTo(y.second, ignoreCase = true)
			if (c != 0) return@Comparator c
		}
		ta.size - tb.size
	}

	private val tokenPattern = Regex("""\d+|\D+""")

	private fun tokens(s: String): List<Pair<Long?, String>> =
		tokenPattern.findAll(s).map { m -> (if (m.value[0].isDigit()) m.value.toLongOrNull() else null) to m.value }.toList()
}
