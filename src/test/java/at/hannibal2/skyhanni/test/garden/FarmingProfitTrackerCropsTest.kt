package at.hannibal2.skyhanni.test.garden

import at.hannibal2.skyhanni.features.garden.tracker.FarmingProfitTrackerCrops
import at.hannibal2.skyhanni.features.garden.tracker.FarmingProfitTrackerCrops.TrackedCandidate
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmingProfitTrackerCropsTest {

    private val melon = "MELON".toInternalName()
    private val melonBlock = "MELON_BLOCK".toInternalName()
    private val enchantedMelon = "ENCHANTED_MELON".toInternalName()
    private val enchantedMelonBlock = "ENCHANTED_MELON_BLOCK".toInternalName()
    private val hayBale = "HAY_BLOCK".toInternalName()
    private val enchantedHayBlock = "ENCHANTED_HAY_BLOCK".toInternalName()

    @Test
    fun `melon blocks are removed when crafting enchanted melon`() {
        val candidates = listOf(
            TrackedCandidate(melonBlock, 9L, 160L),
            TrackedCandidate(melon, 1L, 0L),
        )

        // Compacting 1 Enchanted Melon (multiplier 1440)
        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 1440L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon should be craftable")
        assertEquals(160L, deductions[melonBlock], "All 160 Melon Blocks must be deducted to prevent double-counting")
        assertEquals(null, deductions[melon], "No Melon Slices needed to be deducted")
    }

    @Test
    fun `enchanted melons are removed when crafting enchanted melon block`() {
        val candidates = listOf(
            TrackedCandidate(enchantedMelon, 1440L, 160L),
            TrackedCandidate(melonBlock, 9L, 0L),
            TrackedCandidate(melon, 1L, 0L),
        )

        // Compacting 1 Enchanted Melon Block (multiplier 230,400)
        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 230400L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon Block should be craftable")
        assertEquals(160L, deductions[enchantedMelon], "All 160 Enchanted Melons must be deducted")
        assertEquals(null, deductions[melonBlock])
    }

    @Test
    fun `mixed intermediate and raw crops are converted correctly`() {
        // 100 blocks (= 900 raw) + 540 slices (= 540 raw) = 1440 raw total
        val candidates = listOf(
            TrackedCandidate(melon, 1L, 540L),
            TrackedCandidate(melonBlock, 9L, 100L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 1440L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon should be craftable")
        assertEquals(100L, deductions[melonBlock], "All 100 Melon Blocks should be consumed first (higher tier)")
        assertEquals(540L, deductions[melon], "Remaining 540 Melon Slices should be consumed")
    }

    @Test
    fun `insufficient raw items does not convert and does not pollute session`() {
        val candidates = listOf(
            TrackedCandidate(melonBlock, 9L, 10L), // Only 90 raw available, needs 1440
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 1440L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(0L, craftable, "No Enchanted Melon should be craftable with insufficient items")
        assertTrue(deductions.isEmpty(), "No items should be deducted")
    }

    @Test
    fun `wheat hay bale to enchanted hay block conversion`() {
        val candidates = listOf(
            TrackedCandidate(hayBale, 9L, 144L), // 144 * 9 = 1296 raw
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 1296L, // Enchanted Hay Block = 144 * 9
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Hay Block should be craftable")
        assertEquals(144L, deductions[hayBale], "All 144 Hay Bales must be deducted")
    }

    @Test
    fun `partial crafting when multiple requested but only enough for one`() {
        // 200 blocks * 9 = 1800 raw available; requested 2 (needs 2880), enough for 1 (1440)
        val candidates = listOf(
            TrackedCandidate(melonBlock, 9L, 200L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 1440L,
            amount = 2L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "Only 1 Enchanted Melon should be crafted")
        assertEquals(160L, deductions[melonBlock], "Exactly 160 Melon Blocks (1440 raw) should be deducted")
    }
}
