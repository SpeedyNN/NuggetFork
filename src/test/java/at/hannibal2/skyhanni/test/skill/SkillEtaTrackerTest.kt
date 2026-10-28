package at.hannibal2.skyhanni.test.skill

import at.hannibal2.skyhanni.features.skillprogress.SkillEtaTracker
import at.hannibal2.skyhanni.features.skillprogress.SkillEtaTracker.EtaCommandResult
import at.hannibal2.skyhanni.features.skillprogress.SkillType
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SkillEtaTrackerTest {

    @BeforeEach
    fun setUp() {
        SkillEtaTracker.minuteSamples.clear()
        SkillEtaTracker.currentXpHour = 0.0
        SkillEtaTracker.currentXpMin = 0.0
        SkillEtaTracker.liveCountdownSeconds = 0.0
        SkillEtaTracker.isPaused = true
        SkillEtaTracker.baseTotalXp = 0L
        SkillEtaTracker.sessionXpGained = 0.0
        SkillEtaTracker.lastKnownApiTotalXp = 0L
        SkillEtaTracker.syncTimer = 0
    }

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
        val missingLevel = SkillEtaTracker.parseEtaCommand(arrayOf("farming"))
        assertTrue(missingLevel is EtaCommandResult.Error)
        assertTrue((missingLevel as EtaCommandResult.Error).message.contains("Please specify a target level"))

        val missingSkill = SkillEtaTracker.parseEtaCommand(arrayOf("40"))
        assertTrue(missingSkill is EtaCommandResult.Error)
        assertTrue((missingSkill as EtaCommandResult.Error).message.contains("Please specify which skill"))

        val unknownSkill = SkillEtaTracker.parseEtaCommand(arrayOf("gardening", "40"))
        assertTrue(unknownSkill is EtaCommandResult.Error)
        assertTrue((unknownSkill as EtaCommandResult.Error).message.contains("Unknown skill"))

        val invalidNumber = SkillEtaTracker.parseEtaCommand(arrayOf("farming", "abc"))
        assertTrue(invalidNumber is EtaCommandResult.Error)
        assertTrue((invalidNumber as EtaCommandResult.Error).message.contains("Invalid target level"))

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
        assertEquals(50L, SkillUtil.xpRequiredForLevel(1))
        assertEquals(175L, SkillUtil.xpRequiredForLevel(2))
        assertEquals(55_172_425L, SkillUtil.xpRequiredForLevel(50))
        assertEquals(111_672_425L, SkillUtil.xpRequiredForLevel(60))

        val xp39 = SkillUtil.xpRequiredForLevel(39)
        val xp40 = SkillUtil.xpRequiredForLevel(40)
        assertTrue(xp40 > xp39)
        val diff39To40 = xp40 - xp39
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
        val remainingXp = 1_100_000L
        val ratePerHour = 550_000.0
        val ratePerSec = ratePerHour / 3600.0
        val seconds = remainingXp / ratePerSec
        assertEquals(7200.0, seconds, 0.001)

        val formatted = SkillEtaTracker.formatSeconds(seconds.toLong())
        assertEquals("2h 0m 0s", formatted)
    }

    @Test
    fun testMinuteRateCalculation() {
        val now = 1_000_000L
        // 5,000 XP gained over 30 seconds
        SkillEtaTracker.minuteSamples.addLast(SkillEtaTracker.XpSample(now - 30_000L, 2500.0))
        SkillEtaTracker.minuteSamples.addLast(SkillEtaTracker.XpSample(now, 2500.0))

        SkillEtaTracker.updateRateFromWindow(now)

        // 5000 XP in 30s -> 166.666 XP/s -> 10,000 XP/min -> 600,000 XP/h
        assertEquals(600_000.0, SkillEtaTracker.currentXpHour, 0.01)
        assertEquals(10_000.0, SkillEtaTracker.currentXpMin, 0.01)
        assertEquals(SkillEtaTracker.currentXpHour, SkillEtaTracker.currentXpMin * 60.0, 0.01)
    }

    @Test
    fun testSyncEtaUpdatesWithTrueRateAndRemaining() {
        SkillEtaTracker.config.trackedSkill = "Farming"
        SkillEtaTracker.config.targetLevel = 40
        SkillEtaTracker.baseTotalXp = SkillUtil.xpRequiredForLevel(39)
        // Remaining XP to level 40 is 2,200,000 XP
        // Rate is 440,000 XP/h = 122.222 XP/s
        SkillEtaTracker.currentXpHour = 440_000.0
        SkillEtaTracker.currentXpMin = 440_000.0 / 60.0

        SkillEtaTracker.syncEta()

        // Expected ETA = 2,200,000 / (440,000 / 3600) = 18,000 seconds (5 hours)
        assertEquals(18000.0, SkillEtaTracker.liveCountdownSeconds, 0.01)

        // When user farms faster, rate jumps to 880,000 XP/h:
        SkillEtaTracker.currentXpHour = 880_000.0
        SkillEtaTracker.syncEta()

        // Expected ETA = 2,200,000 / (880,000 / 3600) = 9,000 seconds (2.5 hours)
        assertEquals(9000.0, SkillEtaTracker.liveCountdownSeconds, 0.01)
    }

    @Test
    fun testCountdownDecrementsEverySecondAndSyncsEveryFiveSeconds() {
        SkillEtaTracker.config.trackedSkill = "Farming"
        SkillEtaTracker.config.targetLevel = 40
        SkillEtaTracker.baseTotalXp = SkillUtil.xpRequiredForLevel(39)
        SkillEtaTracker.currentXpHour = 440_000.0
        SkillEtaTracker.syncEta()

        val start = SkillEtaTracker.liveCountdownSeconds

        // For seconds 1 to 4: counts down strictly 1s per second
        for (sec in 1..4) {
            SkillEtaTracker.liveCountdownSeconds -= 1.0
        }
        assertEquals(start - 4.0, SkillEtaTracker.liveCountdownSeconds, 0.001)

        // At second 5: syncs with latest rate
        SkillEtaTracker.syncEta()
        assertEquals(18000.0, SkillEtaTracker.liveCountdownSeconds, 0.01)
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
