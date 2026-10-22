package com.patchouli.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NamesTest {

	@Test
	fun episodeFromDownloaderNames() {
		assertEquals(1, Names.episode("Fate_Zero - E01.mp4"))
		assertEquals(3, Names.episode("03 Episodio 3 (AnimeAv1).mp4"))
		assertEquals(12, Names.episode("Fate_stay night - E12"))
	}

	@Test
	fun episodeFromReleaseNames() {
		assertEquals(7, Names.episode("[Group] Series Name - 07 [1080p].mkv"))
		assertEquals(24, Names.episode("Series.Name.S01E24.1080p.WEB"))
		assertEquals(5, Names.episode("Episode 5"))
	}

	@Test
	fun ignoresYearsAndResolutions() {
		assertNull(Names.episode("Movie (2019) 1080p"))
		assertNull(Names.episode("Trailer 720p"))
	}

	@Test
	fun episodeFromTheOrganizerAndFromSpanishDownloads() {
		assertEquals(1, Names.episode("Dark Gathering - E01"))
		assertEquals(12, Names.episode("Higurashi - Ep. 12"))
		assertEquals(3, Names.episode("Capitulo 3 (1080p)"))
		assertEquals(4, Names.episode("Hellsing Ultimate 04"))
	}

	@Test
	fun titleDropsTheExtensionOnly() {
		assertEquals("Fate_Zero - E01", Names.title("Fate_Zero - E01.mp4"))
		assertEquals("Series.Name.S01E24.1080p", Names.title("Series.Name.S01E24.1080p.mkv"))
	}

	@Test
	fun seriesFromFolder() {
		assertEquals("Fate stay night", Names.series("01 - Fate_stay night"))
		assertEquals("Higurashi", Names.series("Higurashi"))
	}

	@Test
	fun naturalOrder() {
		val sorted = listOf("10 - B", "2 - A", "1 - C").sortedWith(Names.natural)
		assertEquals(listOf("1 - C", "2 - A", "10 - B"), sorted)
	}
}
