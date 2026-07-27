package com.plankton.one102.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsScreenUtilsTest {
    @Test
    fun parsesOnlyPositiveFiniteDefaultVolume() {
        assertEquals(20.5, parsePositiveDefaultVolume(" 20.5 ")!!, 0.0)
        assertNull(parsePositiveDefaultVolume("0"))
        assertNull(parsePositiveDefaultVolume("-1"))
        assertNull(parsePositiveDefaultVolume("NaN"))
        assertNull(parsePositiveDefaultVolume(""))
    }
}
