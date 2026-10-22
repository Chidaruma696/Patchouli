package com.patchouli.app.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DlnaTest {

	@Test
	fun parsesTheTimesTvsReport() {
		assertEquals(83_000L, Dlna.parseTime("0:01:23"))
		assertEquals(83_000L, Dlna.parseTime("0:01:23.500"))
		assertEquals(3_723_000L, Dlna.parseTime("01:02:03"))
	}

	@Test
	fun rejectsWhatIsNotATime() {
		assertNull(Dlna.parseTime(null))
		assertNull(Dlna.parseTime(""))
		assertNull(Dlna.parseTime("NOT_IMPLEMENTED"))
		assertNull(Dlna.parseTime("1:23"))
	}
}
