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

    @Test
    fun `partiallyCounteredBy matches a typing sharing exactly one counter type`() {
        // Fire/Steel/Rock's counter is Water/Fighting — mono-Water shares one type without matching.
        val triangles = TypeTriangles.partiallyCounteredBy(listOf("water"))
        assertTrue(triangles.any { it.title == "Fire / Steel / Rock" })
    }

    @Test
    fun `partiallyCounteredBy excludes exact counter matches`() {
        // Fire/Flying is the exact counter to Fire/Grass/Ground — an exact match, not a partial one.
        val triangles = TypeTriangles.partiallyCounteredBy(listOf("fire", "flying"))
        assertFalse(triangles.any { it.title == "Fire / Grass / Ground" })
    }

    @Test
    fun `partiallyCounteredBy is empty for a typing sharing no counter type`() {
        assertTrue(TypeTriangles.partiallyCounteredBy(listOf("normal")).isEmpty())
    }

    @Test
    fun `all 16 triangles have their counters recognized as perfect counters`() {
        for (triangle in TypeTriangles.ALL) {
            assertTrue(
                "Counter ${triangle.counter.types} for triangle '${triangle.title}' should be recognized as a perfect counter",
                TypeTriangles.isPerfectCounter(triangle.counter.types)
            )
        }
    }
}
