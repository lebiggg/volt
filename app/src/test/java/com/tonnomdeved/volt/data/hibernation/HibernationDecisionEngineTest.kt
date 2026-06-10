package com.tonnomdeved.volt.data.hibernation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HibernationDecisionEngineTest {

    @Test fun `Thresholds default values are coherent`() {
        val t = HibernationDecisionEngine.Thresholds.DEFAULT
        assertEquals(true, t.softAbove < t.mediumAbove)
        assertEquals(true, t.mediumAbove < t.hardAbove)
    }

    @Test fun `Thresholds reject out-of-bounds values`() {
        assertThrows(IllegalArgumentException::class.java) {
            HibernationDecisionEngine.Thresholds(softAbove = -1, mediumAbove = 60, hardAbove = 85)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HibernationDecisionEngine.Thresholds(softAbove = 30, mediumAbove = 200, hardAbove = 85)
        }
    }

    @Test fun `Thresholds reject inverted order`() {
        assertThrows(IllegalArgumentException::class.java) {
            HibernationDecisionEngine.Thresholds(softAbove = 80, mediumAbove = 40, hardAbove = 85)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HibernationDecisionEngine.Thresholds(softAbove = 30, mediumAbove = 90, hardAbove = 60)
        }
    }

    /*
     * Tests d'intégration de decide() — nécessitent fakes pour NocivityScorer
     * et WhitelistResolver (qui tous deux dépendent du Context Android). Sont
     * couverts par les tests instrumented dans androidTest/, à exécuter sur
     * device GrapheneOS réel.
     */
}
