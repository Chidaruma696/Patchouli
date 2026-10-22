package com.patchouli.app.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPrefsTest {

	@Test
	fun sameNameMatches() {
		assertTrue(TrackPrefs.matches("Track 1 - [Japanese]", "Track 1 - [Japanese]"))
		assertTrue(TrackPrefs.matches("Español", "español"))
	}

	@Test
	fun sameLanguageInBracketsMatches() {
		assertTrue(TrackPrefs.matches("Track 2 - [Spanish]", "Track 1 - [Spanish]"))
		assertFalse(TrackPrefs.matches("Track 2 - [English]", "Track 1 - [Spanish]"))
	}

	@Test
	fun externalFilesMatchEachOther() {
		assertTrue(TrackPrefs.matches("Series - E02.srt", "Series - E01.srt"))
		assertTrue(TrackPrefs.matches("Series - E02.ass", "Series - E01.srt"))
		assertFalse(TrackPrefs.matches("Series - E02.srt", "Track 1 - [Spanish]"))
	}
}
