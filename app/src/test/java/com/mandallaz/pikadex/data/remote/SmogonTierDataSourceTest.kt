package com.mandallaz.pikadex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SmogonTierDataSourceTest {

    @Test
    fun `gen 9 resolves to the root formats-data file, no mod folder`() {
        assertEquals(
            "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/formats-data.ts",
            SmogonTierDataSource.tierUrlFor("sv")
        )
    }

    @Test
    fun `an older generation resolves to its mod folder's formats-data file`() {
        assertEquals(
            "https://raw.githubusercontent.com/smogon/pokemon-showdown/master/data/mods/gen8/formats-data.ts",
            SmogonTierDataSource.tierUrlFor("ss")
        )
    }

    // Regression: an unrecognized generation code used to fall through to an empty map, which the
    // fetchTiers() caller's AsyncCache would then memoize as "this generation has no tiers at all"
    // for the rest of the process instead of retrying.
    @Test
    fun `an unrecognized generation code throws rather than resolving to nothing`() {
        assertThrows(IOException::class.java) { SmogonTierDataSource.tierUrlFor("not-a-real-gen") }
    }
}
