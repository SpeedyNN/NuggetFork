package at.hannibal2.skyhanni.test.inventory

import at.hannibal2.skyhanni.features.dungeon.DungeonFloor
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
    fun testBossCollectionsResolution() {
        // Bonzo - F1 / M1
        val bonzo = CollectionGoalTracker.resolveCollection("bonzo")
        assertNotNull(bonzo)
        assertEquals(DungeonFloor.F1, bonzo?.dungeonFloor)
        assertEquals(DungeonFloor.F1, CollectionGoalTracker.resolveCollection("f1")?.dungeonFloor)
        assertEquals(DungeonFloor.F1, CollectionGoalTracker.resolveCollection("m1")?.dungeonFloor)

        // Scarf - F2 / M2
        val scarf = CollectionGoalTracker.resolveCollection("scarf")
        assertNotNull(scarf)
        assertEquals(DungeonFloor.F2, scarf?.dungeonFloor)
        assertEquals(DungeonFloor.F2, CollectionGoalTracker.resolveCollection("f2")?.dungeonFloor)
        assertEquals(DungeonFloor.F2, CollectionGoalTracker.resolveCollection("m2")?.dungeonFloor)

        // The Professor - F3 / M3
        val prof = CollectionGoalTracker.resolveCollection("professor")
        assertNotNull(prof)
        assertEquals(DungeonFloor.F3, prof?.dungeonFloor)
        assertEquals(DungeonFloor.F3, CollectionGoalTracker.resolveCollection("the_professor")?.dungeonFloor)
        assertEquals(DungeonFloor.F3, CollectionGoalTracker.resolveCollection("the professor")?.dungeonFloor)
        assertEquals(DungeonFloor.F3, CollectionGoalTracker.resolveCollection("f3")?.dungeonFloor)
        assertEquals(DungeonFloor.F3, CollectionGoalTracker.resolveCollection("m3")?.dungeonFloor)

        // Thorn - F4 / M4
        val thorn = CollectionGoalTracker.resolveCollection("thorn")
        assertNotNull(thorn)
        assertEquals(DungeonFloor.F4, thorn?.dungeonFloor)
        assertEquals(DungeonFloor.F4, CollectionGoalTracker.resolveCollection("f4")?.dungeonFloor)
        assertEquals(DungeonFloor.F4, CollectionGoalTracker.resolveCollection("m4")?.dungeonFloor)

        // Livid - F5 / M5
        val livid = CollectionGoalTracker.resolveCollection("livid")
        assertNotNull(livid)
        assertEquals(DungeonFloor.F5, livid?.dungeonFloor)
        assertEquals(DungeonFloor.F5, CollectionGoalTracker.resolveCollection("f5")?.dungeonFloor)
        assertEquals(DungeonFloor.F5, CollectionGoalTracker.resolveCollection("m5")?.dungeonFloor)

        // Sadan - F6 / M6
        val sadan = CollectionGoalTracker.resolveCollection("sadan")
        assertNotNull(sadan)
        assertEquals(DungeonFloor.F6, sadan?.dungeonFloor)
        assertEquals(DungeonFloor.F6, CollectionGoalTracker.resolveCollection("f6")?.dungeonFloor)
        assertEquals(DungeonFloor.F6, CollectionGoalTracker.resolveCollection("m6")?.dungeonFloor)

        // Necron - F7 / M7
        val necron = CollectionGoalTracker.resolveCollection("necron")
        assertNotNull(necron)
        assertEquals(DungeonFloor.F7, necron?.dungeonFloor)
        assertEquals(DungeonFloor.F7, CollectionGoalTracker.resolveCollection("f7")?.dungeonFloor)
        assertEquals(DungeonFloor.F7, CollectionGoalTracker.resolveCollection("m7")?.dungeonFloor)
    }

    @Test
    fun testDungeonFloorByBossName() {
        assertEquals(DungeonFloor.F1, DungeonFloor.byBossName("Bonzo"))
        assertEquals(DungeonFloor.F1, DungeonFloor.byBossName("bonzo"))
        assertEquals(DungeonFloor.F1, DungeonFloor.byBossName("Bonzo Collection"))
        assertEquals(DungeonFloor.F2, DungeonFloor.byBossName("Scarf"))
        assertEquals(DungeonFloor.F2, DungeonFloor.byBossName("scarf"))
        assertEquals(DungeonFloor.F3, DungeonFloor.byBossName("The Professor"))
        assertEquals(DungeonFloor.F3, DungeonFloor.byBossName("the professor"))
        assertEquals(DungeonFloor.F3, DungeonFloor.byBossName("Professor"))
        assertEquals(DungeonFloor.F3, DungeonFloor.byBossName("professor"))
        assertEquals(DungeonFloor.F3, DungeonFloor.byBossName("The Professor Collection"))
        assertEquals(DungeonFloor.F4, DungeonFloor.byBossName("Thorn"))
        assertEquals(DungeonFloor.F5, DungeonFloor.byBossName("Livid"))
        assertEquals(DungeonFloor.F6, DungeonFloor.byBossName("Sadan"))
        assertEquals(DungeonFloor.F7, DungeonFloor.byBossName("Necron"))
        assertEquals(DungeonFloor.F7, DungeonFloor.byBossName("necron"))
        assertEquals(DungeonFloor.F7, DungeonFloor.byBossName("Necron Collection"))
        assertEquals(DungeonFloor.F7, DungeonFloor.byBossName("f7"))
        assertEquals(DungeonFloor.F7, DungeonFloor.byBossName("m7"))
        assertEquals(DungeonFloor.E, DungeonFloor.byBossName("The Watcher"))
    }

    @Test
    fun testParseBossGoalCommands() {
        val necronResult = CollectionGoalTracker.parseGoalCommand(arrayOf("necron", "500"))
        assertTrue(necronResult is GoalCommandResult.SetGoal)
        val necronGoal = necronResult as GoalCommandResult.SetGoal
        assertEquals("necron", necronGoal.entry.key)
        assertEquals(500L, necronGoal.amount)

        val f7Result = CollectionGoalTracker.parseGoalCommand(arrayOf("f7", "1000"))
        assertTrue(f7Result is GoalCommandResult.SetGoal)
        val f7Goal = f7Result as GoalCommandResult.SetGoal
        assertEquals("necron", f7Goal.entry.key)
        assertEquals(1000L, f7Goal.amount)

        val profResult = CollectionGoalTracker.parseGoalCommand(arrayOf("the", "professor", "250"))
        assertTrue(profResult is GoalCommandResult.SetGoal)
        val profGoal = profResult as GoalCommandResult.SetGoal
        assertEquals("professor", profGoal.entry.key)
        assertEquals(250L, profGoal.amount)
    }

    @Test
    fun testProgressBarRenderableCentering() {
        val bar = CollectionGoalTracker.createProgressBar(0.5, 140, 7)
        assertEquals(140, bar.width)
        assertEquals(7, bar.height)
        assertEquals(HorizontalAlignment.CENTER, bar.horizontalAlign)
    }
}
