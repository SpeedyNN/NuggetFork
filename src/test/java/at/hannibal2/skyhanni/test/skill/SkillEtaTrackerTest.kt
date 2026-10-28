package at.hannibal2.skyhanni.test.skill

import at.hannibal2.skyhanni.features.skillprogress.SkillEtaTracker
import at.hannibal2.skyhanni.features.skillprogress.SkillEtaTracker.EtaCommandResult
import at.hannibal2.skyhanni.features.skillprogress.SkillType
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkillEtaTrackerTest {

    @Test
    fun testParseEtaCommandEmpty() {
        val result = SkillEtaTracker.parseEtaCommand(emptyArray())
        assertTrue(result is EtaCommandResult.ShowStatus)
    }

    @Test
    fun testParseEtaCommandClear() {
        assertTrue(SkillEtaTracker.parseEtaCommand(arrayOf("clear")) is EtaCommandResult.ClearGoal)
        assertTrue(SkillEtaTracker.parseEtaCommand(arrayOf("reset")) is EtaCommandResult.ClearGoal)
        assertTrue(SkillEtaTracker.parseEtaCommand(arrayOf("stop")) is EtaCommandResult.ClearGoal)
        assertTrue(SkillEtaTracker.parseEtaCommand(arrayOf("remove")) is EtaCommandResult.ClearGoal)
        assertTrue(SkillEtaTracker.parseEtaCommand(arrayOf("CLEAR")) is EtaCommandResult.ClearGoal)
    }

    @Test
    fun testParseEtaCommandValidSkills() {
        val farmResult = SkillEtaTracker.parseEtaCommand(arrayOf("farming", "40"))
        assertTrue(farmResult is EtaCommandResult.SetGoal)
        val farmGoal = farmResult as EtaCommandResult.SetGoal
        assertEquals(SkillType.FARMING, farmGoal.skill)
        assertEquals(40, farmGoal.targetLevel)

        val mineResult = SkillEtaTracker.parseEtaCommand(arrayOf("Mining", "60"))
        assertTrue(mineResult is EtaCommandResult.SetGoal)
        val mineGoal = mineResult as EtaCommandResult.SetGoal
        assertEquals(SkillType.MINING, mineGoal.skill)
        assertEquals(60, mineGoal.targetLevel)

        val combatResult = SkillEtaTracker.parseEtaCommand(arrayOf("combat", "50"))
        assertTrue(combatResult is EtaCommandResult.SetGoal)
        val combatGoal = combatResult as EtaCommandResult.SetGoal
        assertEquals(SkillType.COMBAT, combatGoal.skill)
        assertEquals(50, combatGoal.targetLevel)

        val fishResult = SkillEtaTracker.parseEtaCommand(arrayOf("fishing", "50"))
        assertTrue(fishResult is EtaCommandResult.SetGoal)
        assertEquals(SkillType.FISHING, (fishResult as EtaCommandResult.SetGoal).skill)

        val enchantResult = SkillEtaTracker.parseEtaCommand(arrayOf("enchanting", "60"))
        assertTrue(enchantResult is EtaCommandResult.SetGoal)
        assertEquals(SkillType.ENCHANTING, (enchantResult as EtaCommandResult.SetGoal).skill)
    }

    @Test
    fun testParseEtaCommandAliases() {
        val farmAlias = SkillEtaTracker.parseEtaCommand(arrayOf("farm", "40"))
        assertTrue(farmAlias is EtaCommandResult.SetGoal)
        assertEquals(SkillType.FARMING, (farmAlias as EtaCommandResult.SetGoal).skill)

        val mineAlias = SkillEtaTracker.parseEtaCommand(arrayOf("mine", "60"))
        assertTrue(mineAlias is EtaCommandResult.SetGoal)
        assertEquals(SkillType.MINING, (mineAlias as EtaCommandResult.SetGoal).skill)

        val woodAlias = SkillEtaTracker.parseEtaCommand(arrayOf("wood", "50"))
        assertTrue(woodAlias is EtaCommandResult.SetGoal)
        assertEquals(SkillType.FORAGING, (woodAlias as EtaCommandResult.SetGoal).skill)

        val alchAlias = SkillEtaTracker.parseEtaCommand(arrayOf("alch", "50"))
        assertTrue(alchAlias is EtaCommandResult.SetGoal)
        assertEquals(SkillType.ALCHEMY, (alchAlias as EtaCommandResult.SetGoal).skill)

        val carpAlias = SkillEtaTracker.parseEtaCommand(arrayOf("carp", "50"))
        assertTrue(carpAlias is EtaCommandResult.SetGoal)
        assertEquals(SkillType.CARPENTRY, (carpAlias as EtaCommandResult.SetGoal).skill)

        val tameAlias = SkillEtaTracker.parseEtaCommand(arrayOf("tame", "60"))
        assertTrue(tameAlias is EtaCommandResult.SetGoal)
        assertEquals(SkillType.TAMING, (tameAlias as EtaCommandResult.SetGoal).skill)

        val huntAlias = SkillEtaTracker.parseEtaCommand(arrayOf("hunt", "50"))
        assertTrue(huntAlias is EtaCommandResult.SetGoal)
        assertEquals(SkillType.HUNTING, (huntAlias as EtaCommandResult.SetGoal).skill)
    }

    @Test
    fun testParseEtaCommandInvalidInputs() {
        // Missing target level
        val missingLevel = SkillEtaTracker.parseEtaCommand(arrayOf("farming"))
        assertTrue(missingLevel is EtaCommandResult.Error)
        assertTrue((missingLevel as EtaCommandResult.Error).message.contains("Please specify a target level"))

        // Missing skill
        val missingSkill = SkillEtaTracker.parseEtaCommand(arrayOf("40"))
        assertTrue(missingSkill is EtaCommandResult.Error)
        assertTrue((missingSkill as EtaCommandResult.Error).message.contains("Please specify which skill"))

        // Unknown skill
        val unknownSkill = SkillEtaTracker.parseEtaCommand(arrayOf("gardening", "40"))
        assertTrue(unknownSkill is EtaCommandResult.Error)
        assertTrue((unknownSkill as EtaCommandResult.Error).message.contains("Unknown skill"))

        // Invalid number
        val invalidNumber = SkillEtaTracker.parseEtaCommand(arrayOf("farming", "abc"))
        assertTrue(invalidNumber is EtaCommandResult.Error)
        assertTrue((invalidNumber as EtaCommandResult.Error).message.contains("Invalid target level"))

        // Out of range level
        val outOfRangeLow = SkillEtaTracker.parseEtaCommand(arrayOf("farming", "0"))
        assertTrue(outOfRangeLow is EtaCommandResult.Error)
        assertTrue((outOfRangeLow as EtaCommandResult.Error).message.contains("between 1 and 400"))

        val outOfRangeHigh = SkillEtaTracker.parseEtaCommand(arrayOf("farming", "401"))
        assertTrue(outOfRangeHigh is EtaCommandResult.Error)
        assertTrue((outOfRangeHigh as EtaCommandResult.Error).message.contains("between 1 and 400"))
    }

    @Test
    fun testResolveSkillAllTypes() {
        for (skill in SkillType.entries) {
            assertEquals(skill, SkillEtaTracker.resolveSkill(skill.displayName))
            assertEquals(skill, SkillEtaTracker.resolveSkill(skill.displayName.lowercase()))
            assertEquals(skill, SkillEtaTracker.resolveSkill(skill.displayName.uppercase()))
            assertEquals(skill, SkillEtaTracker.resolveSkill(skill.name))
        }

        assertNull(SkillEtaTracker.resolveSkill("unknown_skill_xyz"))
        assertNull(SkillEtaTracker.resolveSkill(""))
    }

    @Test
    fun testSkillXpMath() {
        // Level 1: 50
        assertEquals(50L, SkillUtil.xpRequiredForLevel(1))
        // Level 2: 50 + 125 = 175
        assertEquals(175L, SkillUtil.xpRequiredForLevel(2))
        // Level 50: 55,172,425
        assertEquals(55_172_425L, SkillUtil.xpRequiredForLevel(50))
        // Level 60: 111,672,425
        assertEquals(111_672_425L, SkillUtil.xpRequiredForLevel(60))

        // Level 39 and 40 XP
        val xp39 = SkillUtil.xpRequiredForLevel(39)
        val xp40 = SkillUtil.xpRequiredForLevel(40)
        assertTrue(xp40 > xp39)
        val diff39To40 = xp40 - xp39
        // XP from 39 to 40 is 2,200,000
        assertEquals(2_200_000L, diff39To40)
    }

    @Test
    fun testFormatSeconds() {
        assertEquals("0s", SkillEtaTracker.formatSeconds(0L))
        assertEquals("0s", SkillEtaTracker.formatSeconds(-5L))
        assertEquals("45s", SkillEtaTracker.formatSeconds(45L))
        assertEquals("1m 15s", SkillEtaTracker.formatSeconds(75L))
        assertEquals("15m 30s", SkillEtaTracker.formatSeconds(930L))
        assertEquals("1h 0m 0s", SkillEtaTracker.formatSeconds(3600L))
        assertEquals("2h 15m 32s", SkillEtaTracker.formatSeconds(8132L))
        assertEquals("24h 0m 0s", SkillEtaTracker.formatSeconds(86400L))
    }

    @Test
    fun testEtaCalculationMath() {
        // Remaining XP = 1,100,000 (halfway to level 40 from 39)
        // Rate = 550,000 XP/h = 152.777 XP/s
        // Expected ETA = 1,100,000 / (550,000 / 3600) = 7,200 seconds = 2 hours
        val remainingXp = 1_100_000L
        val ratePerHour = 550_000.0
        val ratePerSec = ratePerHour / 3600.0
        val seconds = remainingXp / ratePerSec
        assertEquals(7200.0, seconds, 0.001)

        val formatted = SkillEtaTracker.formatSeconds(seconds.toLong())
        assertEquals("2h 0m 0s", formatted)

        // After 125 seconds of active progress:
        val liveRemaining = seconds - 125.0
        assertEquals(7075.0, liveRemaining, 0.001)
        val formattedRemaining = SkillEtaTracker.formatSeconds(liveRemaining.toLong())
        assertEquals("1h 57m 55s", formattedRemaining)
    }

    @Test
    fun testProgressBarRenderableCreation() {
        val bar = SkillEtaTracker.createProgressBar(
            progress = 0.5,
            width = 140,
            height = 7,
            isComplete = false,
            isPausedState = false,
        )
        assertNotNull(bar)
        assertEquals(140, bar.width)
        assertEquals(7, bar.height)
    }
}

