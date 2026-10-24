package at.hannibal2.skyhanni.test.garden

import at.hannibal2.skyhanni.config.features.garden.FarmingProfitTrackerConfig.TrackedSource
import at.hannibal2.skyhanni.features.garden.tracker.FarmingProfitTrackerCrops
import at.hannibal2.skyhanni.features.garden.tracker.FarmingProfitTrackerCrops.SourceTrackedCandidate
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

    private val wheat = "WHEAT".toInternalName()
    private val enchantedWheat = "ENCHANTED_WHEAT".toInternalName()
    private val enchantedHayBale = "ENCHANTED_HAY_BALE".toInternalName()

    private val seeds = "SEEDS".toInternalName()
    private val enchantedSeeds = "ENCHANTED_SEEDS".toInternalName()
    private val boxOfSeeds = "BOX_OF_SEEDS".toInternalName()

    private val carrot = "CARROT_ITEM".toInternalName()
    private val enchantedCarrot = "ENCHANTED_CARROT".toInternalName()
    private val enchantedGoldenCarrot = "ENCHANTED_GOLDEN_CARROT".toInternalName()

    private val potato = "POTATO_ITEM".toInternalName()
    private val enchantedPotato = "ENCHANTED_POTATO".toInternalName()
    private val enchantedBakedPotato = "ENCHANTED_BAKED_POTATO".toInternalName()

    private val netherStalk = "NETHER_STALK".toInternalName()
    private val enchantedNetherStalk = "ENCHANTED_NETHER_STALK".toInternalName()
    private val mutantNetherStalk = "MUTANT_NETHER_STALK".toInternalName()

    private val pumpkin = "PUMPKIN".toInternalName()
    private val enchantedPumpkin = "ENCHANTED_PUMPKIN".toInternalName()
    private val polishedPumpkin = "POLISHED_PUMPKIN".toInternalName()

    private val cocoa = "INK_SACK:3".toInternalName()
    private val enchantedCocoa = "ENCHANTED_COCOA".toInternalName()
    private val enchantedCookie = "ENCHANTED_COOKIE".toInternalName()

    private val sugarCane = "SUGAR_CANE".toInternalName()
    private val enchantedSugar = "ENCHANTED_SUGAR".toInternalName()
    private val enchantedSugarCane = "ENCHANTED_SUGAR_CANE".toInternalName()

    private val cactus = "CACTUS".toInternalName()
    private val enchantedCactusGreen = "ENCHANTED_CACTUS_GREEN".toInternalName()
    private val enchantedCactus = "ENCHANTED_CACTUS".toInternalName()

    private val redMushroom = "RED_MUSHROOM".toInternalName()
    private val enchantedRedMushroom = "ENCHANTED_RED_MUSHROOM".toInternalName()
    private val enchantedHugeMushroom2 = "ENCHANTED_HUGE_MUSHROOM_2".toInternalName()

    private val brownMushroom = "BROWN_MUSHROOM".toInternalName()
    private val enchantedBrownMushroom = "ENCHANTED_BROWN_MUSHROOM".toInternalName()
    private val enchantedHugeMushroom1 = "ENCHANTED_HUGE_MUSHROOM_1".toInternalName()

    private val sunflower = "DOUBLE_PLANT".toInternalName()
    private val enchantedSunflower = "ENCHANTED_SUNFLOWER".toInternalName()
    private val compactedSunflower = "COMPACTED_SUNFLOWER".toInternalName()

    private val moonflower = "MOONFLOWER".toInternalName()
    private val enchantedMoonflower = "ENCHANTED_MOONFLOWER".toInternalName()
    private val compactedMoonflower = "COMPACTED_MOONFLOWER".toInternalName()

    private val wildRose = "WILD_ROSE".toInternalName()
    private val enchantedWildRose = "ENCHANTED_WILD_ROSE".toInternalName()
    private val compactedWildRose = "COMPACTED_WILD_ROSE".toInternalName()

    @Test
    fun `melon slices to enchanted melon slice conversion`() {
        val candidates = listOf(
            TrackedCandidate(melon, 1L, 160L),
        )

        // Compacting 1 Enchanted Melon Slice (multiplier 160)
        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon Slice should be craftable")
        assertEquals(160L, deductions[melon], "All 160 Melon Slices must be deducted")
    }

    @Test
    fun `enchanted melon slices to enchanted melon block conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedMelon, 160L, 160L),
            TrackedCandidate(melon, 1L, 0L),
        )

        // Compacting 1 Enchanted Melon Block (multiplier 25,600)
        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon Block should be craftable")
        assertEquals(160L, deductions[enchantedMelon], "All 160 Enchanted Melons must be deducted")
    }

    @Test
    fun `mixed intermediate and raw crops are converted correctly`() {
        // 100 enchanted melons (= 16,000 raw) + 9,600 slices (= 9,600 raw) = 25,600 raw total
        val candidates = listOf(
            TrackedCandidate(melon, 1L, 9600L),
            TrackedCandidate(enchantedMelon, 160L, 100L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon Block should be craftable")
        assertEquals(100L, deductions[enchantedMelon], "All 100 Enchanted Melons should be consumed first (higher tier)")
        assertEquals(9600L, deductions[melon], "Remaining 9600 Melon Slices should be consumed")
    }

    @Test
    fun `insufficient raw items does not convert and does not pollute session`() {
        val candidates = listOf(
            TrackedCandidate(melon, 1L, 100L), // Only 100 raw available, needs 160
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(0L, craftable, "No Enchanted Melon should be craftable with insufficient items")
        assertTrue(deductions.isEmpty(), "No items should be deducted")
    }

    @Test
    fun `wheat to enchanted wheat conversion`() {
        val candidates = listOf(
            TrackedCandidate(wheat, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Wheat should be craftable")
        assertEquals(160L, deductions[wheat], "All 160 Wheat must be deducted")
    }

    @Test
    fun `enchanted wheat to enchanted hay bale conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedWheat, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L, // Enchanted Hay Bale = 160 * 160
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Hay Bale should be craftable")
        assertEquals(160L, deductions[enchantedWheat], "All 160 Enchanted Wheat must be deducted")
    }

    @Test
    fun `seeds to enchanted seeds conversion`() {
        val candidates = listOf(
            TrackedCandidate(seeds, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Seeds should be craftable")
        assertEquals(160L, deductions[seeds], "All 160 Seeds must be deducted")
    }

    @Test
    fun `enchanted seeds to box of seeds conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedSeeds, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L, // Box of Seeds = 160 * 160
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Box of Seeds should be craftable")
        assertEquals(160L, deductions[enchantedSeeds], "All 160 Enchanted Seeds must be deducted")
    }

    @Test
    fun `carrot to enchanted carrot conversion`() {
        val candidates = listOf(
            TrackedCandidate(carrot, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[carrot])
    }

    @Test
    fun `enchanted carrot to enchanted golden carrot conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedCarrot, 160L, 128L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 20480L, // Enchanted Golden Carrot = 128 * 160
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Golden Carrot should be craftable")
        assertEquals(128L, deductions[enchantedCarrot], "All 128 Enchanted Carrots must be deducted")
    }

    @Test
    fun `potato to enchanted potato conversion`() {
        val candidates = listOf(
            TrackedCandidate(potato, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[potato])
    }

    @Test
    fun `enchanted potato to enchanted baked potato conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedPotato, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedPotato])
    }

    @Test
    fun `nether stalk to enchanted nether stalk conversion`() {
        val candidates = listOf(
            TrackedCandidate(netherStalk, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[netherStalk])
    }

    @Test
    fun `enchanted nether stalk to mutant nether stalk conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedNetherStalk, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedNetherStalk])
    }

    @Test
    fun `pumpkin to enchanted pumpkin conversion`() {
        val candidates = listOf(
            TrackedCandidate(pumpkin, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[pumpkin])
    }

    @Test
    fun `enchanted pumpkin to polished pumpkin conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedPumpkin, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedPumpkin])
    }

    @Test
    fun `cocoa to enchanted cocoa conversion`() {
        val candidates = listOf(
            TrackedCandidate(cocoa, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[cocoa])
    }

    @Test
    fun `enchanted cocoa to enchanted cookie conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedCocoa, 160L, 128L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 20480L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(128L, deductions[enchantedCocoa])
    }

    @Test
    fun `sugar cane to enchanted sugar conversion`() {
        val candidates = listOf(
            TrackedCandidate(sugarCane, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[sugarCane])
    }

    @Test
    fun `enchanted sugar to enchanted sugar cane conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedSugar, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedSugar])
    }

    @Test
    fun `cactus to enchanted cactus green conversion`() {
        val candidates = listOf(
            TrackedCandidate(cactus, 1L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 160L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[cactus])
    }

    @Test
    fun `enchanted cactus green to enchanted cactus conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedCactusGreen, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedCactusGreen])
    }

    @Test
    fun `red mushroom to enchanted huge mushroom 2 conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedRedMushroom, 160L, 32L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 5120L, // 32 * 160 = 5120
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(32L, deductions[enchantedRedMushroom])
    }

    @Test
    fun `brown mushroom to enchanted huge mushroom 1 conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedBrownMushroom, 160L, 32L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 5120L, // 32 * 160 = 5120
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(32L, deductions[enchantedBrownMushroom])
    }

    @Test
    fun `sunflower to compacted sunflower conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedSunflower, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedSunflower])
    }

    @Test
    fun `moonflower to compacted moonflower conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedMoonflower, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedMoonflower])
    }

    @Test
    fun `wild rose to compacted wild rose conversion`() {
        val candidates = listOf(
            TrackedCandidate(enchantedWildRose, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable)
        assertEquals(160L, deductions[enchantedWildRose])
    }

    @Test
    fun `pest drops in PESTS bucket are deducted correctly when compacted`() {
        // Player kills earthworm pest, gets 160 enchanted melons in PESTS bucket
        val candidates = listOf(
            SourceTrackedCandidate(TrackedSource.PESTS, enchantedMelon, 160L, 160L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateMultiSourceCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon Block should be craftable")
        assertEquals(160L, deductions[TrackedSource.PESTS to enchantedMelon], "Candidates from PESTS must be deducted from PESTS")
        assertEquals(1, deductions.size)
    }

    @Test
    fun `multi source candidates across CROPS and PESTS are deducted correctly`() {
        // 100 enchanted melons from CROPS, 60 from PESTS = 160 total
        val candidates = listOf(
            SourceTrackedCandidate(TrackedSource.CROPS, enchantedMelon, 160L, 100L),
            SourceTrackedCandidate(TrackedSource.PESTS, enchantedMelon, 160L, 60L),
        )

        val (craftable, deductions) = FarmingProfitTrackerCrops.calculateMultiSourceCompactionDeductions(
            targetMultiplier = 25600L,
            amount = 1L,
            candidates = candidates,
        )

        assertEquals(1L, craftable, "1 Enchanted Melon Block should be craftable from combined sources")
        assertEquals(100L, deductions[TrackedSource.CROPS to enchantedMelon], "All 100 from CROPS should be deducted")
        assertEquals(60L, deductions[TrackedSource.PESTS to enchantedMelon], "Remaining 60 from PESTS should be deducted")
    }

    @Test
    fun `crop tier definitions contain valid parent candidate filters`() {
        // Verify carrot
        val goldenCarrotDef = FarmingProfitTrackerCrops.run { enchantedGoldenCarrot.getCropTierDefinitionOrNull() }
        assertTrue(goldenCarrotDef != null)
        assertEquals(20480L, goldenCarrotDef!!.rawMultiplier)
        assertTrue(enchantedCarrot in goldenCarrotDef.validCandidates)

        // Verify cookie
        val cookieDef = FarmingProfitTrackerCrops.run { enchantedCookie.getCropTierDefinitionOrNull() }
        assertTrue(cookieDef != null)
        assertEquals(20480L, cookieDef!!.rawMultiplier)
        assertTrue(enchantedCocoa in cookieDef.validCandidates)

        // Verify red mushroom vs brown mushroom separation
        val redHugeDef = FarmingProfitTrackerCrops.run { enchantedHugeMushroom2.getCropTierDefinitionOrNull() }
        assertTrue(redHugeDef != null)
        assertEquals(5120L, redHugeDef!!.rawMultiplier)
        assertTrue(enchantedRedMushroom in redHugeDef.validCandidates)
        assertTrue(enchantedBrownMushroom !in redHugeDef.validCandidates, "Brown mushroom must never be candidate for red mushroom")

        // Verify seeds
        val boxOfSeedsDef = FarmingProfitTrackerCrops.run { boxOfSeeds.getCropTierDefinitionOrNull() }
        assertTrue(boxOfSeedsDef != null)
        assertEquals(25600L, boxOfSeedsDef!!.rawMultiplier)
        assertTrue(enchantedSeeds in boxOfSeedsDef.validCandidates)
        assertTrue(wheat !in boxOfSeedsDef.validCandidates, "Wheat must never be candidate for seeds")

        // Verify wheat
        val hayBaleDef = FarmingProfitTrackerCrops.run { enchantedHayBale.getCropTierDefinitionOrNull() }
        assertTrue(hayBaleDef != null)
        assertEquals(25600L, hayBaleDef!!.rawMultiplier)
        assertTrue(enchantedWheat in hayBaleDef.validCandidates)
        assertTrue(seeds !in hayBaleDef.validCandidates, "Seeds must never be candidate for wheat")
    }

    @Test
    fun `isCropVariant identifies crop items across all tiers and rejects non-crop items`() {
        FarmingProfitTrackerCrops.run {
            // Raw crops
            assertTrue(melon.isCropVariant())
            assertTrue(wheat.isCropVariant())
            assertTrue(carrot.isCropVariant())
            assertTrue(seeds.isCropVariant())

            // Enchanted crops
            assertTrue(enchantedMelon.isCropVariant())
            assertTrue(enchantedWheat.isCropVariant())
            assertTrue(enchantedCarrot.isCropVariant())
            assertTrue(enchantedSeeds.isCropVariant())

            // Super-enchanted crops
            assertTrue(enchantedMelonBlock.isCropVariant())
            assertTrue(enchantedHayBale.isCropVariant())
            assertTrue(enchantedGoldenCarrot.isCropVariant())
            assertTrue(boxOfSeeds.isCropVariant())

            // Non-crop items
            assertTrue(!"OVERCLOCKER_3000".toInternalName().isCropVariant())
            assertTrue(!"CROPIE".toInternalName().isCropVariant())
            assertTrue(!"SQUASH".toInternalName().isCropVariant())
            assertTrue(!"FERMENTO".toInternalName().isCropVariant())
            assertTrue(!"DUNG".toInternalName().isCropVariant())
            assertTrue(!"TOOL_EXP_CAPSULE".toInternalName().isCropVariant())
        }
    }

    @Test
    fun `isSuperEnchantedCrop identifies only highest tier products and rejects raw and enchanted forms`() {
        FarmingProfitTrackerCrops.run {
            // Super-enchanted items must be true
            assertTrue(enchantedMelonBlock.isSuperEnchantedCrop())
            assertTrue(enchantedHayBale.isSuperEnchantedCrop())
            assertTrue(boxOfSeeds.isSuperEnchantedCrop())
            assertTrue(enchantedGoldenCarrot.isSuperEnchantedCrop())
            assertTrue(enchantedBakedPotato.isSuperEnchantedCrop())
            assertTrue(mutantNetherStalk.isSuperEnchantedCrop())
            assertTrue(polishedPumpkin.isSuperEnchantedCrop())
            assertTrue(enchantedCookie.isSuperEnchantedCrop())
            assertTrue(enchantedSugarCane.isSuperEnchantedCrop())
            assertTrue(enchantedCactus.isSuperEnchantedCrop())
            assertTrue(enchantedHugeMushroom1.isSuperEnchantedCrop())
            assertTrue(enchantedHugeMushroom2.isSuperEnchantedCrop())
            assertTrue(compactedSunflower.isSuperEnchantedCrop())
            assertTrue(compactedMoonflower.isSuperEnchantedCrop())
            assertTrue(compactedWildRose.isSuperEnchantedCrop())

            // Raw items must be false
            assertTrue(!melon.isSuperEnchantedCrop())
            assertTrue(!wheat.isSuperEnchantedCrop())
            assertTrue(!seeds.isSuperEnchantedCrop())
            assertTrue(!carrot.isSuperEnchantedCrop())
            assertTrue(!potato.isSuperEnchantedCrop())
            assertTrue(!netherStalk.isSuperEnchantedCrop())
            assertTrue(!pumpkin.isSuperEnchantedCrop())

            // Intermediate enchanted items must be false
            assertTrue(!enchantedMelon.isSuperEnchantedCrop(), "Enchanted melon slice is not super-enchanted")
            assertTrue(!enchantedWheat.isSuperEnchantedCrop())
            assertTrue(!enchantedSeeds.isSuperEnchantedCrop())
            assertTrue(!enchantedCarrot.isSuperEnchantedCrop())
            assertTrue(!enchantedPotato.isSuperEnchantedCrop())
            assertTrue(!enchantedNetherStalk.isSuperEnchantedCrop())
            assertTrue(!enchantedPumpkin.isSuperEnchantedCrop())
        }
    }
}
