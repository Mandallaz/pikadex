package com.mandallaz.pikadex.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeTrianglesTest {

    @Test
    fun `isPerfectCounter is true for a typing that exactly matches a triangle's counter`() {
        assertTrue(TypeTriangles.isPerfectCounter(listOf("fire", "flying"))) // counters Fire/Grass/Ground
    }

    @Test
    fun `isPerfectCounter ignores counter type order`() {
        assertTrue(TypeTriangles.isPerfectCounter(listOf("flying", "fire")))
    }

    @Test
    fun `isPerfectCounter is false for a typing that counters nothing`() {
        assertFalse(TypeTriangles.isPerfectCounter(listOf("normal")))
    }

    @Test
    fun `isPerfectCounter is false for a typing that is a triangle member, not its counter`() {
        assertFalse(TypeTriangles.isPerfectCounter(listOf("fire", "grass")))
    }
}
