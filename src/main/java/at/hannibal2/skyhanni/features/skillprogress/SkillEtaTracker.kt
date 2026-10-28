package at.hannibal2.skyhanni.features.skillprogress

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.SkillApi
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.data.GuiEditManager
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.SkillExpGainEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.KeyboardManager.RIGHT_MOUSE
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SoundUtils.playSound
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.container.HorizontalContainerRenderable.Companion.horizontal
import at.hannibal2.skyhanni.utils.renderables.primitives.ItemStackRenderable.Companion.item
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.gui.screens.ChatScreen
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object SkillEtaTracker {

    val config get() = SkyHanniMod.feature.skillProgress.skillEtaTracker

    data class XpSample(val timestamp: Long, val amount: Double)

    sealed class EtaCommandResult {
        data class SetGoal(val skill: SkillType, val targetLevel: Int) : EtaCommandResult()
        object ClearGoal : EtaCommandResult()
        object ShowStatus : EtaCommandResult()
        data class Error(val message: String) : EtaCommandResult()
    }

    val minuteSamples = ArrayDeque<XpSample>()
    var currentXpMin: Double = 0.0
    var currentXpHour: Double = 0.0
    var liveCountdownSeconds: Double = 0.0
    var isPaused: Boolean = true
    var lastXpGainTime = SimpleTimeMark.farPast()
    var hasAnnouncedCompletion: Boolean = false
    var baseTotalXp: Long = 0L
    var sessionXpGained: Double = 0.0

    private var display = listOf<Renderable>()
    private var dummyDisplay = listOf<Renderable>()
    private var dummyBarWidth: Int = -1
    private var dummyBarHeight: Int = -1

    private val skillAliases = mapOf(
        "farm" to SkillType.FARMING,
        "mine" to SkillType.MINING,
        "fight" to SkillType.COMBAT,
        "sword" to SkillType.COMBAT,
        "forage" to SkillType.FORAGING,
        "wood" to SkillType.FORAGING,
        "axe" to SkillType.FORAGING,
        "fish" to SkillType.FISHING,
        "rod" to SkillType.FISHING,
        "enchant" to SkillType.ENCHANTING,
        "ench" to SkillType.ENCHANTING,
        "alch" to SkillType.ALCHEMY,
        "brew" to SkillType.ALCHEMY,
        "brewing" to SkillType.ALCHEMY,
        "carp" to SkillType.CARPENTRY,
        "craft" to SkillType.CARPENTRY,
        "crafting" to SkillType.CARPENTRY,
        "tame" to SkillType.TAMING,
        "pet" to SkillType.TAMING,
        "pets" to SkillType.TAMING,
        "hunt" to SkillType.HUNTING,
    )

    private val skillSuggestions = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        val remaining = builder.remainingLowerCase
        val candidates = buildList {
            for (skill in SkillType.entries) {
                add(skill.displayName.lowercase())
            }
            add("clear")
            add("reset")
        }
        for (item in candidates) {
            if (remaining.isEmpty() || item.startsWith(remaining)) {
                builder.suggest(item)
            }
        }
        for (item in candidates) {
            if (remaining.isNotEmpty() && !item.startsWith(remaining) && item.contains(remaining)) {
                builder.suggest(item)
            }
        }
        builder.buildFuture()
    }

    init {
        RenderDisplayHelper(
            outsideInventory = true,
            inOwnInventory = true,
            condition = { SkyBlockUtils.inSkyBlock && config.enabled && (hasActiveGoal() || GuiEditManager.isInGui()) },
            onRender = {
                val toRender = if (hasActiveGoal()) display else dummyDisplay
                config.position.renderRenderables(toRender, extraSpace = 1, posLabel = "Skill ETA Tracker")
            },
        )
    }

    fun hasActiveGoal(): Boolean = config.trackedSkill.isNotEmpty() && config.targetLevel > 0

    fun getTrackedSkill(): SkillType? {
        val name = config.trackedSkill
        if (name.isEmpty()) return null
        return resolveSkill(name)
    }

    fun resolveSkill(query: String): SkillType? {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return null
        for (skill in SkillType.entries) {
            if (skill.displayName.equals(clean, ignoreCase = true) || skill.name.equals(clean, ignoreCase = true)) {
                return skill
            }
        }
        return skillAliases[clean]
    }

    fun getCurrentTotalXp(skill: SkillType): Long {
        val info = SkillUtil.getSkillInfo(skill)
        val infoXp = if (info != null) {
            if (info.totalXp > 0L) info.totalXp
            else {
                val lvl = if (info.overflowLevel > 0) info.overflowLevel else info.level
                if (lvl > 0) SkillUtil.xpRequiredForLevel(lvl) + info.currentXp
                else info.currentXp
            }
        } else 0L

        val tracked = baseTotalXp + sessionXpGained.toLong()
        return maxOf(infoXp, tracked)
    }

    fun getCurrentLevel(skill: SkillType): Int {
        val info = SkillUtil.getSkillInfo(skill) ?: return 0
        if (info.overflowLevel > 0) return info.overflowLevel
        return info.level
    }

    fun getTargetTotalXp(targetLevel: Int): Long = SkillUtil.xpRequiredForLevel(targetLevel)

    fun getRemainingXp(skill: SkillType, targetLevel: Int): Long {
        val targetXp = getTargetTotalXp(targetLevel)
        val currentXp = getCurrentTotalXp(skill)
        return (targetXp - currentXp).coerceAtLeast(0L)
    }

    fun getLiveSecondsRemaining(): Double {
        if (!hasActiveGoal()) return 0.0
        val skill = getTrackedSkill() ?: return 0.0
        if (getCurrentTotalXp(skill) >= getTargetTotalXp(config.targetLevel)) return 0.0
        return liveCountdownSeconds.coerceAtLeast(0.0)
    }

    fun formatSeconds(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0s"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun parseEtaCommand(args: Array<String>): EtaCommandResult {
        if (args.isEmpty()) {
            return EtaCommandResult.ShowStatus
        }

        if (args.size == 1) {
            val first = args[0].trim().lowercase()
            if (first in listOf("clear", "reset", "stop", "remove")) {
                return EtaCommandResult.ClearGoal
            }
            val skill = resolveSkill(first)
            if (skill != null) {
                return EtaCommandResult.Error("Please specify a target level! Example: /eta ${skill.displayName.lowercase()} 40")
            }
            val lvl = first.toIntOrNull()
            if (lvl != null) {
                return EtaCommandResult.Error("Please specify which skill to track! Example: /eta farming $lvl")
            }
            return EtaCommandResult.Error("Unknown skill: '${args[0]}'. Example: /eta farming 40")
        }

        val first = args[0].trim().lowercase()
        if (first in listOf("clear", "reset", "stop", "remove")) {
            return EtaCommandResult.ClearGoal
        }

        val skill = resolveSkill(args[0])
            ?: return EtaCommandResult.Error("Unknown skill: '${args[0]}'. Available skills: Farming, Mining, Combat, Foraging, Fishing, Enchanting, Alchemy, Carpentry, Taming, Hunting.")

        val targetLevel = args[1].toIntOrNull()
            ?: return EtaCommandResult.Error("Invalid target level '${args[1]}'. Please enter a valid number (e.g. 40).")

        if (targetLevel < 1 || targetLevel > 400) {
            return EtaCommandResult.Error("Target level must be between 1 and 400!")
        }

        return EtaCommandResult.SetGoal(skill, targetLevel)
    }

    fun handleEtaCommand(args: Array<String>) {
        when (val result = parseEtaCommand(args)) {
            is EtaCommandResult.SetGoal -> {
                val currentLvl = getCurrentLevel(result.skill)
                if (currentLvl > 0 && result.targetLevel <= currentLvl) {
                    ChatUtils.userError("Target level (${result.targetLevel}) must be greater than your current level ($currentLvl) in ${result.skill.displayName}!")
                    return
                }

                config.trackedSkill = result.skill.displayName
                config.targetLevel = result.targetLevel
                config.startLevel = currentLvl
                config.startTotalXp = getCurrentTotalXp(result.skill)
                config.enabled = true
                hasAnnouncedCompletion = false
                baseTotalXp = getCurrentTotalXp(result.skill)
                sessionXpGained = 0.0
                resetRate()
                updateDisplay()

                val currentStr = if (currentLvl > 0) "Current: §e$currentLvl" else "Current: §7Syncing..."
                val remaining = getRemainingXp(result.skill, result.targetLevel)

                ChatUtils.chat("§a[Skill ETA] Now tracking §b${result.skill.displayName} §8($currentStr §8➜ Target: §b${result.targetLevel}§8)§a!")
                ChatUtils.chat("§7Remaining XP: §e${remaining.addSeparators()} §7| Start gaining §b${result.skill.displayName} §7XP to see your live ETA.")
                if (currentLvl == 0) {
                    ChatUtils.chat("§e[Skill ETA] §8(Tip: Open your SkyBlock Skills menu to sync your current exact XP!)")
                }
            }

            is EtaCommandResult.ClearGoal -> {
                clearGoal()
                ChatUtils.chat("§a[Skill ETA] Active skill goal cleared.")
            }

            is EtaCommandResult.ShowStatus -> {
                if (hasActiveGoal()) {
                    val skill = getTrackedSkill()
                    if (skill == null) {
                        clearGoal()
                        ChatUtils.chat("§e[Skill ETA] No active goal. Usage: /eta <skill> <level>")
                        return
                    }
                    val target = config.targetLevel
                    val current = getCurrentLevel(skill)
                    val remaining = getRemainingXp(skill, target)
                    val rateHour = currentXpHour.toLong()
                    val rateMin = currentXpMin.toLong()
                    val liveSecs = getLiveSecondsRemaining().toLong()
                    val etaFormatted = if (rateHour > 0) formatSeconds(liveSecs) else "Waiting for XP..."
                    val pauseStatus = if (isPaused && rateHour > 0) " §c(PAUSED)" else ""

                    ChatUtils.chat("§a[Skill ETA] Currently tracking §b${skill.displayName} §8(Level $current ➜ $target)§7:")
                    ChatUtils.chat("  §7- Remaining XP: §e${remaining.addSeparators()}")
                    ChatUtils.chat("  §7- Current Rate: §e${if (rateHour > 0) "${rateHour.addSeparators()} XP/h (${rateMin.addSeparators()}/min)" else "N/A"}")
                    ChatUtils.chat("  §7- Live Countdown ETA: §b$etaFormatted$pauseStatus")
                    ChatUtils.clickableChat("§c[✕ Clear Active Goal]", onClick = {
                        clearGoal()
                        ChatUtils.chat("§a[Skill ETA] Active skill goal cleared.")
                    }, hover = "§cClick to clear this goal")
                } else {
                    ChatUtils.chat("§e[Skill ETA] No active goal set.")
                    ChatUtils.chat("§7Usage: §e/eta <skill> <targetLevel> §7(e.g. §b/eta farming 40§7)")
                    ChatUtils.chat("§7Clear: §e/eta clear")
                }
            }

            is EtaCommandResult.Error -> {
                ChatUtils.userError(result.message)
            }
        }
    }

    fun clearGoal() {
        config.trackedSkill = ""
        config.targetLevel = 0
        config.startLevel = 0
        config.startTotalXp = 0L
        hasAnnouncedCompletion = false
        baseTotalXp = 0L
        sessionXpGained = 0.0
        resetRate()
        display = emptyList()
        updateDisplay()
    }

    fun resetRate() {
        minuteSamples.clear()
        currentXpMin = 0.0
        currentXpHour = 0.0
        liveCountdownSeconds = 0.0
        isPaused = true
        lastXpGainTime = SimpleTimeMark.farPast()
    }

    fun updateRateFromWindow(now: Long = System.currentTimeMillis()) {
        while (minuteSamples.isNotEmpty() && now - minuteSamples.first().timestamp > 60_000L) {
            minuteSamples.removeFirst()
        }

        if (minuteSamples.size < 2) return

        val oldest = minuteSamples.first().timestamp
        val windowDurationSec = ((now - oldest) / 1000.0).coerceAtLeast(1.0)
        if (windowDurationSec < 3.0) return

        val totalGainedInWindow = minuteSamples.sumOf { it.amount }
        val xpPerSec = totalGainedInWindow / windowDurationSec
        val calculatedXpMin = xpPerSec * 60.0
        val calculatedXpHour = calculatedXpMin * 60.0

        if (currentXpHour <= 0.0) {
            currentXpHour = calculatedXpHour
            currentXpMin = calculatedXpMin
            val activeSkill = getTrackedSkill()
            if (activeSkill != null && config.targetLevel > 0) {
                val remaining = getRemainingXp(activeSkill, config.targetLevel)
                if (xpPerSec > 0.0) {
                    liveCountdownSeconds = (remaining / xpPerSec).coerceAtLeast(0.0)
                }
            }
        } else {
            val alpha = 0.12
            currentXpHour = currentXpHour * (1.0 - alpha) + calculatedXpHour * alpha
            currentXpMin = currentXpHour / 60.0
        }
    }

    fun createProgressBar(progress: Double, width: Int, height: Int, isComplete: Boolean, isPausedState: Boolean): Renderable =
        object : Renderable {
            override val width = width
            override val height = height
            override val horizontalAlign = RenderUtils.HorizontalAlignment.CENTER
            override val verticalAlign = RenderUtils.VerticalAlignment.TOP

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                val clamped = progress.coerceIn(0.0, 1.0)
                val borderColor = 0xAA000000.toInt()
                val bgColor = 0x88222222.toInt()
                val fillColor = when {
                    isComplete -> 0xFF55FF55.toInt() // Green
                    isPausedState -> 0xFFE67E22.toInt() // Orange
                    else -> 0xFF00C0FF.toInt() // Cyan
                }

                GuiRenderUtils.drawRect(0, 0, width, height, borderColor)
                GuiRenderUtils.drawRect(1, 1, width - 1, height - 1, bgColor)
                val fillWidth = ((width - 2) * clamped).toInt()
                if (fillWidth > 0) {
                    GuiRenderUtils.drawRect(1, 1, 1 + fillWidth, height - 1, fillColor)
                }
            }
        }

    private fun buildTrackerDisplay(
        skill: SkillType,
        targetLevel: Int,
        currentTotalXp: Long,
        currentLvl: Int,
        ratePerHour: Double,
        ratePerMin: Double,
        liveSecondsRemaining: Double,
        isPausedState: Boolean,
        isDummy: Boolean = false,
    ): List<Renderable> {
        val targetTotalXp = getTargetTotalXp(targetLevel)
        val isComplete = currentTotalXp >= targetTotalXp && targetTotalXp > 0L

        // Baseline XP calculation for progress bar
        val baseStart = when {
            config.startTotalXp in 1 until targetTotalXp -> config.startTotalXp
            currentLvl > 0 -> SkillUtil.xpRequiredForLevel(currentLvl)
            else -> 0L
        }
        val totalSpan = (targetTotalXp - baseStart).coerceAtLeast(1L)
        val progressSpan = (currentTotalXp - baseStart).coerceAtLeast(0L)
        val progress = if (isComplete) 1.0 else (progressSpan.toDouble() / totalSpan).coerceIn(0.0, 1.0)
        val percent = progress * 100.0

        val list = mutableListOf<Renderable>()

        // 1. Header with icon, skill name, level transition and remove button
        val titleText = "§b${skill.displayName} §e$currentLvl §8➜ §6$targetLevel"
        val headerElements = mutableListOf<Renderable>()
        val itemStack = runCatching { skill.item }.getOrNull()
        if (itemStack != null && !itemStack.isEmpty) {
            headerElements.add(Renderable.item({ itemStack }))
        }
        headerElements.add(Renderable.text(titleText, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER))

        val isInGui = runCatching { MinecraftCompat.screen?.let { it !is ChatScreen } }.getOrNull() ?: false
        if (isInGui && !isDummy) {
            val removeButton = Renderable.clickable(
                Renderable.text("§c ✕"),
                onLeftClick = {
                    clearGoal()
                    ChatUtils.chat("§a[Skill ETA] Active skill goal cleared.")
                },
                bypassChecks = true,
                tips = listOf("§cClick to remove this goal"),
            )
            headerElements.add(removeButton)
        }

        val header = Renderable.horizontal(
            headerElements,
            spacing = 2,
            horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
            verticalAlign = RenderUtils.VerticalAlignment.CENTER,
        )

        val headerClickable = if (!isDummy) {
            Renderable.clickable(
                header,
                onAnyClick = mapOf(RIGHT_MOUSE to {
                    clearGoal()
                    ChatUtils.chat("§a[Skill ETA] Active skill goal cleared.")
                }),
                bypassChecks = true,
                tips = if (isInGui) listOf("§eRight-click to remove this goal") else null,
            )
        } else header

        list.add(headerClickable)

        // 2. Progress Bar
        val bar = createProgressBar(progress, config.barWidth, config.barHeight, isComplete, isPausedState)
        val remainingXp = (targetTotalXp - currentTotalXp).coerceAtLeast(0L)
        val barTips = listOf(
            "§6Skill: §b${skill.displayName}",
            "§6Current Level: §e$currentLvl §8➜ §6Target: §e$targetLevel",
            "§6Current Total XP: §b${currentTotalXp.addSeparators()}",
            "§6Target Total XP: §b${targetTotalXp.addSeparators()}",
            "§6Remaining XP: §e${remainingXp.addSeparators()}",
            "§6Progress: §a${"%.1f%%".format(percent)}",
            "§6XP per Minute: §b${if (ratePerMin > 0) "${ratePerMin.toLong().addSeparators()} XP/min" else "N/A"}",
            "§6XP per Hour: §e${if (ratePerHour > 0) "${ratePerHour.toLong().addSeparators()} XP/h" else "N/A"}",
            "§6Estimated Time: §b${if (ratePerHour > 0) formatSeconds(liveSecondsRemaining.toLong()) else "Waiting for XP..."}",
        )
        list.add(Renderable.hoverTips(bar, barTips))

        // 3. XP Progression line
        val xpLine = if (isComplete) {
            "§a${targetTotalXp.addSeparators()} §7/ §e${targetTotalXp.addSeparators()} §6✔ COMPLETED!"
        } else {
            "§a${progressSpan.addSeparators()} §7/ §e${totalSpan.addSeparators()} §7(§b${"%.1f%%".format(percent)}§7)"
        }
        list.add(Renderable.text(xpLine, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER))

        // 4. Live Pace & Countdown line
        val etaFormatted = formatSeconds(liveSecondsRemaining.toLong())
        val paceLine = when {
            isComplete -> "§6✔ Level $targetLevel Reached!"
            ratePerHour <= 0.0 -> "§7Pace: §eCalculating... §7| ETA: §7Waiting for XP..."
            isPausedState -> "§7XP/h: §e${ratePerHour.toLong().addSeparators()} §7(§b${ratePerMin.toLong().addSeparators()}/min§7) §7| ETA: §cPaused ($etaFormatted)"
            else -> "§7XP/h: §e${ratePerHour.toLong().addSeparators()} §7(§b${ratePerMin.toLong().addSeparators()}/min§7) §7| ETA: §a$etaFormatted"
        }

        val paceRenderable = if (!isDummy) {
            Renderable.clickable(
                Renderable.text(paceLine, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER),
                onLeftClick = {
                    resetRate()
                    ChatUtils.chat("§a[Skill ETA] XP rate reset for ${skill.displayName}.")
                    updateDisplay()
                },
                tips = listOf("§eClick to reset XP rate calculation"),
            )
        } else {
            Renderable.text(paceLine, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER)
        }

        list.add(paceRenderable)

        return list
    }

    fun updateDisplay() {
        if (!config.enabled) {
            display = emptyList()
            return
        }

        val skill = getTrackedSkill()
        if (skill != null && config.targetLevel > 0) {
            val targetLevel = config.targetLevel
            val currentTotalXp = getCurrentTotalXp(skill)
            val currentLvl = getCurrentLevel(skill)
            val liveSecs = getLiveSecondsRemaining()

            display = buildTrackerDisplay(
                skill = skill,
                targetLevel = targetLevel,
                currentTotalXp = currentTotalXp,
                currentLvl = currentLvl,
                ratePerHour = currentXpHour,
                ratePerMin = currentXpMin,
                liveSecondsRemaining = liveSecs,
                isPausedState = isPaused,
                isDummy = false,
            )
        } else {
            display = emptyList()
        }

        if (dummyDisplay.isEmpty() || config.barWidth != dummyBarWidth || config.barHeight != dummyBarHeight) {
            dummyBarWidth = config.barWidth
            dummyBarHeight = config.barHeight
            val dummySkill = SkillType.FARMING
            val targetLvl = 40
            val targetXp = getTargetTotalXp(targetLvl)
            val startXp = SkillUtil.xpRequiredForLevel(39)
            val currentXp = startXp + ((targetXp - startXp) * 0.65).toLong()
            dummyDisplay = buildTrackerDisplay(
                skill = dummySkill,
                targetLevel = targetLvl,
                currentTotalXp = currentXp,
                currentLvl = 39,
                ratePerHour = 450_000.0,
                ratePerMin = 7_500.0,
                liveSecondsRemaining = 6160.0,
                isPausedState = false,
                isDummy = true,
            )
        }
    }

    @HandleEvent
    fun onSkillExpGain(event: SkillExpGainEvent) {
        val activeSkill = getTrackedSkill() ?: return
        if (event.skill != activeSkill) return

        val now = System.currentTimeMillis()
        lastXpGainTime = SimpleTimeMark.now()
        isPaused = false

        sessionXpGained += event.gained
        if (event.totalXp != null && event.totalXp > 0) {
            baseTotalXp = event.totalXp.toLong()
            sessionXpGained = 0.0
        }

        minuteSamples.addLast(XpSample(now, event.gained))

        // Keep 60 seconds of samples for 1-minute rate calculation
        while (minuteSamples.isNotEmpty() && now - minuteSamples.first().timestamp > 60_000L) {
            minuteSamples.removeFirst()
        }

        updateRateFromWindow(now)
        updateDisplay()
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!hasActiveGoal()) return
        val activeSkill = getTrackedSkill() ?: return

        val now = System.currentTimeMillis()

        // 1. Pause check
        if (lastXpGainTime.passedSince() > config.pauseTimeoutSeconds.seconds) {
            if (!isPaused) {
                isPaused = true
                updateDisplay()
            }
        } else {
            isPaused = false

            // Update rate from 1-minute window
            updateRateFromWindow(now)

            // 2. LIVE COUNTDOWN: Strictly decrement by 1 second each second of real time!
            if (liveCountdownSeconds > 0.0) {
                liveCountdownSeconds = (liveCountdownSeconds - 1.0).coerceAtLeast(0.0)
            }

            // 3. Gentle bounded drift correction towards true target ETA
            if (currentXpHour > 0.0) {
                val remainingXp = getRemainingXp(activeSkill, config.targetLevel)
                val ratePerSec = currentXpHour / 3600.0
                if (ratePerSec > 0.0) {
                    val targetEtaSeconds = remainingXp / ratePerSec
                    if (liveCountdownSeconds <= 0.0) {
                        liveCountdownSeconds = targetEtaSeconds
                    } else {
                        val diff = targetEtaSeconds - liveCountdownSeconds
                        val adjustment = (diff * 0.02).coerceIn(-0.1, 0.1)
                        liveCountdownSeconds = (liveCountdownSeconds + adjustment).coerceAtLeast(0.0)
                    }
                }
            }

            updateDisplay()
        }

        // 4. Completion check
        val targetXp = getTargetTotalXp(config.targetLevel)
        val currentXp = getCurrentTotalXp(activeSkill)
        if (currentXp >= targetXp && targetXp > 0L && !hasAnnouncedCompletion) {
            hasAnnouncedCompletion = true
            ChatUtils.chat("§6[Skill ETA] §aCongratulations! You reached §b${activeSkill.displayName} ${config.targetLevel}§a!")
            if (config.dingOnComplete) {
                SoundUtils.createSound("entity.player.levelup", 1f, 1f, isWarning = false).playSound()
            }
            updateDisplay()
        }
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("eta") {
            description = "Track estimated time to reach a target skill level with live countdown"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("skilleta")
            simpleCallback {
                handleEtaCommand(emptyArray())
            }
            arg("skill", BrigadierArguments.string(), skillSuggestions) { skillArg ->
                arg("level", BrigadierArguments.integer(1, 400)) { levelArg ->
                    callback {
                        handleEtaCommand(arrayOf(getArg(skillArg), getArg(levelArg).toString()))
                    }
                }
                callback {
                    handleEtaCommand(arrayOf(getArg(skillArg)))
                }
            }
        }
    }
}
