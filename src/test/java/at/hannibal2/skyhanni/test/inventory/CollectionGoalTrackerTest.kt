package at.hannibal2.skyhanni.test.inventory

import at.hannibal2.skyhanni.features.inventory.collections.CollectionGoalTracker
import at.hannibal2.skyhanni.features.inventory.collections.CollectionGoalTracker.GoalCommandResult
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.RenderUtils.HorizontalAlignment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionGoalTrackerTest {

    @Test
    fun testParseGoalCommandEmpty() {
        val result = CollectionGoalTracker.parseGoalCommand(emptyArray())
        assertTrue(result is GoalCommandResult.ShowStatus)
    }

    @Test
    fun testParseGoalCommandClear() {
        val clearResult = CollectionGoalTracker.parseGoalCommand(arrayOf("clear"))
        assertTrue(clearResult is GoalCommandResult.ClearGoal)
        assertNull((clearResult as GoalCommandResult.ClearGoal).entry)

        val resetResult = CollectionGoalTracker.parseGoalCommand(arrayOf("reset"))
        assertTrue(resetResult is GoalCommandResult.ClearGoal)

        val clearSpecificResult = CollectionGoalTracker.parseGoalCommand(arrayOf("clear", "leather"))
        assertTrue(clearSpecificResult is GoalCommandResult.ClearGoal)
        assertEquals("leather", (clearSpecificResult as GoalCommandResult.ClearGoal).entry?.key)
    }

    @Test
    fun testParseGoalCommandItemAndAmount() {
        val result1 = CollectionGoalTracker.parseGoalCommand(arrayOf("wheat", "100k"))
        assertTrue(result1 is GoalCommandResult.SetGoal)
        val setGoal1 = result1 as GoalCommandResult.SetGoal
        assertEquals("wheat", setGoal1.entry.key)
        assertEquals(100_000L, setGoal1.amount)

        val result2 = CollectionGoalTracker.parseGoalCommand(arrayOf("sugar_cane", "50k"))
        assertTrue(result2 is GoalCommandResult.SetGoal)
        val setGoal2 = result2 as GoalCommandResult.SetGoal
        assertEquals("sugar_cane", setGoal2.entry.key)
        assertEquals(50_000L, setGoal2.amount)

        val result3 = CollectionGoalTracker.parseGoalCommand(arrayOf("sugar", "cane", "50k"))
        assertTrue(result3 is GoalCommandResult.SetGoal)
        val setGoal3 = result3 as GoalCommandResult.SetGoal
        assertEquals("sugar_cane", setGoal3.entry.key)
        assertEquals(50_000L, setGoal3.amount)

        val result4 = CollectionGoalTracker.parseGoalCommand(arrayOf("dark", "oak", "wood", "2m"))
        assertTrue(result4 is GoalCommandResult.SetGoal)
        val setGoal4 = result4 as GoalCommandResult.SetGoal
        assertEquals("dark_oak_wood", setGoal4.entry.key)
        assertEquals(2_000_000L, setGoal4.amount)
    }

    @Test
    fun testParseGoalCommandAmountFirst() {
        val result = CollectionGoalTracker.parseGoalCommand(arrayOf("200k", "potato"))
        assertTrue(result is GoalCommandResult.SetGoal)
        val setGoal = result as GoalCommandResult.SetGoal
        assertEquals("potato", setGoal.entry.key)
        assertEquals(200_000L, setGoal.amount)
    }

    @Test
    fun testParseGoalCommandHeldItem() {
        val heldItem = "CARROT_ITEM".toInternalName()
        val result = CollectionGoalTracker.parseGoalCommand(arrayOf("50k"), heldItem)
        assertTrue(result is GoalCommandResult.SetGoal)
        val setGoal = result as GoalCommandResult.SetGoal
        assertEquals("carrot", setGoal.entry.key)
        assertEquals(50_000L, setGoal.amount)
    }

    @Test
    fun testResolveCollection() {
        assertNotNull(CollectionGoalTracker.resolveCollection("wheat"))
        assertNotNull(CollectionGoalTracker.resolveCollection("melon"))
        assertNotNull(CollectionGoalTracker.resolveCollection("sugar_cane"))
        assertNotNull(CollectionGoalTracker.resolveCollection("sugar cane"))
        assertNotNull(CollectionGoalTracker.resolveCollection("cane"))
        assertNotNull(CollectionGoalTracker.resolveCollection("cobblestone"))
        assertNotNull(CollectionGoalTracker.resolveCollection("diamond"))
        assertNotNull(CollectionGoalTracker.resolveCollection("lapis"))
        assertNotNull(CollectionGoalTracker.resolveCollection("rotten_flesh"))
        assertNotNull(CollectionGoalTracker.resolveCollection("raw_fish"))
        assertNotNull(CollectionGoalTracker.resolveCollection("dark_oak_wood"))
        assertNotNull(CollectionGoalTracker.resolveCollection("wilted_berberis"))
        assertNotNull(CollectionGoalTracker.resolveCollection("necron"))

        // Newly added collections:
        val leather = CollectionGoalTracker.resolveCollection("leather")
        assertNotNull(leather)
        assertEquals("leather", leather?.key)

        val cow = CollectionGoalTracker.resolveCollection("cow")
        assertNotNull(cow)
        assertEquals("leather", cow?.key)

        val mutton = CollectionGoalTracker.resolveCollection("mutton")
        assertNotNull(mutton)
        assertEquals("mutton", mutton?.key)

        val rawMutton = CollectionGoalTracker.resolveCollection("raw_mutton")
        assertNotNull(rawMutton)
        assertEquals("mutton", rawMutton?.key)

        val sheep = CollectionGoalTracker.resolveCollection("sheep")
        assertNotNull(sheep)
        assertEquals("mutton", sheep?.key)

        val beef = CollectionGoalTracker.resolveCollection("raw_beef")
        assertNotNull(beef)

        val pork = CollectionGoalTracker.resolveCollection("porkchop")
        assertNotNull(pork)

        val chicken = CollectionGoalTracker.resolveCollection("chicken")
        assertNotNull(chicken)

        val feather = CollectionGoalTracker.resolveCollection("feather")
        assertNotNull(feather)

        val rabbit = CollectionGoalTracker.resolveCollection("rabbit")
        assertNotNull(rabbit)

        val rabbitFoot = CollectionGoalTracker.resolveCollection("rabbit_foot")
        assertNotNull(rabbitFoot)

        val redSand = CollectionGoalTracker.resolveCollection("red_sand")
        assertNotNull(redSand)

        val mycelium = CollectionGoalTracker.resolveCollection("mycelium")
        assertNotNull(mycelium)

        val magmafish = CollectionGoalTracker.resolveCollection("magmafish")
        assertNotNull(magmafish)
    }

    @Test
    fun testProgressBarRenderableCentering() {
        val bar = CollectionGoalTracker.createProgressBar(0.5, 140, 7)
        assertEquals(140, bar.width)
        assertEquals(7, bar.height)
        assertEquals(HorizontalAlignment.CENTER, bar.horizontalAlign)
    }
}
