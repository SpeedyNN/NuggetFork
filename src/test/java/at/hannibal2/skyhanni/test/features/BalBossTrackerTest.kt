package at.hannibal2.skyhanni.test.features

import at.hannibal2.skyhanni.features.mining.crystalhollows.BalBossTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BalBossTrackerTest {

    @Test
    fun `test base drop rate constants`() {
        assertEquals(0.000126, BalBossTracker.BASE_EPIC_CHANCE, 1e-9)
        assertEquals(0.000054, BalBossTracker.BASE_LEG_CHANCE, 1e-9)
        assertEquals(0.00018, BalBossTracker.BASE_TOTAL_CHANCE, 1e-9)
    }

    @Test
    fun `test cumulative probability calculation`() {
        val p = 0.00018 // Base chance

        // 0 hits -> 0%
        assertEquals(0.0, BalBossTracker.calculateProbability(0, p), 1e-9)
        assertEquals(0.0, BalBossTracker.calculateProbability(-5, p), 1e-9)

        // 1 hit -> p
        assertEquals(p, BalBossTracker.calculateProbability(1, p), 1e-9)

        // 5556 hits (roughly 1 in p hits) -> 1 - (1 - 1/5556)^5556 ≈ 1 - 1/e ≈ 63.2%
        val probAtMean = BalBossTracker.calculateProbability(5556, p)
        assertTrue(probAtMean > 0.63 && probAtMean < 0.64)

        // 10000 hits -> 1 - (1 - 0.00018)^10000 ≈ 83.47%
        val probAt10k = BalBossTracker.calculateProbability(10000, p)
        assertTrue(probAt10k > 0.83 && probAt10k < 0.84)
    }

    @Test
    fun `test probability with magic find and pet luck multiplier`() {
        val baseChance = 0.00018
        // 100 MF + 50 Pet Luck = +150% = 2.5x multiplier
        val multiplier = 2.5
        val boostedChance = baseChance * multiplier // 0.00045

        val probAt1000 = BalBossTracker.calculateProbability(1000, boostedChance)
        // 1 - (1 - 0.00045)^1000 ≈ 36.24%
        assertTrue(probAt1000 > 0.36 && probAt1000 < 0.37)
    }

    @Test
    fun `test epic and legendary individual probabilities`() {
        val pEpic = BalBossTracker.BASE_EPIC_CHANCE
        val pLeg = BalBossTracker.BASE_LEG_CHANCE

        // Check exact ratios
        assertEquals(0.000126, pEpic, 1e-9)
        assertEquals(0.000054, pLeg, 1e-9)
        assertEquals(0.00018, pEpic + pLeg, 1e-9)

        // At 1000 hits without stats:
        // Epic: 1 - (1 - 0.000126)^1000 ≈ 11.84%
        val epicAt1k = BalBossTracker.calculateProbability(1000, pEpic)
        assertTrue(epicAt1k > 0.118 && epicAt1k < 0.119)

        // Legendary: 1 - (1 - 0.000054)^1000 ≈ 5.26%
        val legAt1k = BalBossTracker.calculateProbability(1000, pLeg)
        assertTrue(legAt1k > 0.052 && legAt1k < 0.053)

        // At mean of Legendary (18,519 hits): ≈ 63.2%
        val legAtMean = BalBossTracker.calculateProbability(18519, pLeg)
        assertTrue(legAtMean > 0.63 && legAtMean < 0.64)
    }
}

