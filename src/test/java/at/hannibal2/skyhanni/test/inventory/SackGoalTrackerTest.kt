package at.hannibal2.skyhanni.test.inventory

import at.hannibal2.skyhanni.features.garden.tracker.FarmingProfitTrackerCrops
import at.hannibal2.skyhanni.features.inventory.sacks.SackGoalTracker
import at.hannibal2.skyhanni.features.inventory.sacks.SackGoalTracker.GoalCommandResult
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SackGoalTrackerTest {

    @Test
    fun testParseGoalCommandEmpty() {
        val result = SackGoalTracker.parseGoalCommand(emptyArray())
        assertTrue(result is GoalCommandResult.ShowStatus)
    }

    @Test
    fun testParseGoalCommandClear() {
        val clearResult = SackGoalTracker.parseGoalCommand(arrayOf("clear"))
        assertTrue(clearResult is GoalCommandResult.ClearGoal)

        val resetResult = SackGoalTracker.parseGoalCommand(arrayOf("reset"))
        assertTrue(resetResult is GoalCommandResult.ClearGoal)
    }

    @Test
    fun testParseGoalCommandHeldItem() {
        val heldItem = "ENCHANTED_MELON".toInternalName()
        val result = SackGoalTracker.parseGoalCommand(arrayOf("50k"), heldItem)
        assertTrue(result is GoalCommandResult.SetGoal)
        val setGoal = result as GoalCommandResult.SetGoal
        assertEquals(heldItem, setGoal.internalName)
        assertEquals(50_000L, setGoal.amount)
    }

    @Test
    fun testParseGoalCommandItemAndAmount() {
        val result1 = SackGoalTracker.parseGoalCommand(arrayOf("melon", "100k"))
        assertTrue(result1 is GoalCommandResult.SetGoal)
        val setGoal1 = result1 as GoalCommandResult.SetGoal
        assertEquals(FarmingProfitTrackerCrops.MELON, setGoal1.internalName)
        assertEquals(100_000L, setGoal1.amount)

        val result2 = SackGoalTracker.parseGoalCommand(arrayOf("enchanted", "melon", "25,000"))
        assertTrue(result2 is GoalCommandResult.SetGoal)
        val setGoal2 = result2 as GoalCommandResult.SetGoal
        assertEquals(FarmingProfitTrackerCrops.ENCHANTED_MELON, setGoal2.internalName)
        assertEquals(25_000L, setGoal2.amount)

        val result3 = SackGoalTracker.parseGoalCommand(arrayOf("enchanted", "hay", "bale", "1.5m"))
        assertTrue(result3 is GoalCommandResult.SetGoal)
        val setGoal3 = result3 as GoalCommandResult.SetGoal
        assertEquals(FarmingProfitTrackerCrops.ENCHANTED_HAY_BALE, setGoal3.internalName)
        assertEquals(1_500_000L, setGoal3.amount)
    }

    @Test
    fun testParseGoalCommandAmountFirst() {
        val result = SackGoalTracker.parseGoalCommand(arrayOf("200k", "wheat"))
        assertTrue(result is GoalCommandResult.SetGoal)
        val setGoal = result as GoalCommandResult.SetGoal
        assertEquals(FarmingProfitTrackerCrops.WHEAT, setGoal.internalName)
        assertEquals(200_000L, setGoal.amount)
    }

    @Test
    fun testResolveTrackedItemAliases() {
        assertEquals(FarmingProfitTrackerCrops.CARROT_ITEM, SackGoalTracker.resolveTrackedItem("carrot"))
        assertEquals(FarmingProfitTrackerCrops.ENCHANTED_GOLDEN_CARROT, SackGoalTracker.resolveTrackedItem("enchanted golden carrot"))
        assertEquals(FarmingProfitTrackerCrops.POTATO_ITEM, SackGoalTracker.resolveTrackedItem("potato"))
        assertEquals(FarmingProfitTrackerCrops.ENCHANTED_BAKED_POTATO, SackGoalTracker.resolveTrackedItem("enchanted baked potato"))
        assertEquals(FarmingProfitTrackerCrops.NETHER_STALK, SackGoalTracker.resolveTrackedItem("nether wart"))
        assertEquals(FarmingProfitTrackerCrops.MUTANT_NETHER_STALK, SackGoalTracker.resolveTrackedItem("mutant nether wart"))
        assertEquals(FarmingProfitTrackerCrops.POLISHED_PUMPKIN, SackGoalTracker.resolveTrackedItem("polished pumpkin"))
        assertEquals(FarmingProfitTrackerCrops.ENCHANTED_COOKIE, SackGoalTracker.resolveTrackedItem("enchanted cookie"))
        assertEquals(FarmingProfitTrackerCrops.ENCHANTED_SUGAR_CANE, SackGoalTracker.resolveTrackedItem("enchanted sugar cane"))
        assertEquals(FarmingProfitTrackerCrops.COMPACTED_SUNFLOWER, SackGoalTracker.resolveTrackedItem("compacted sunflower"))
    }

    @Test
    fun testProgressBarRenderableDimensions() {
        val bar = SackGoalTracker.createProgressBar(0.65, 140, 8)
        assertEquals(140, bar.width)
        assertEquals(8, bar.height)
    }
}

