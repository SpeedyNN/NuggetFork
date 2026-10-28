package at.hannibal2.skyhanni.features.inventory.sacks

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.data.GuiEditManager
import at.hannibal2.skyhanni.data.SackApi
import at.hannibal2.skyhanni.events.SackChangeEvent
import at.hannibal2.skyhanni.events.SackDataUpdateEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.garden.tracker.FarmingProfitTrackerCrops
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.ItemUtils.matchesItemName
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.KeyboardManager.RIGHT_MOUSE
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStackOrNull
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatLongOrNull
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SafeItemStack
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SoundUtils.playSound
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.container.HorizontalContainerRenderable.Companion.horizontal
import at.hannibal2.skyhanni.utils.renderables.primitives.ItemStackRenderable.Companion.item
import at.hannibal2.skyhanni.utils.renderables.primitives.placeholder
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.gui.screens.ChatScreen

@SkyHanniModule
object SackGoalTracker {

    private val config get() = SkyHanniMod.feature.inventory.sackGoal

    var completedSoundPlayed = false
    private val completedGoals = mutableSetOf<NeuInternalName>()
    private var display = listOf<Renderable>()
    private var dummyDisplay = listOf<Renderable>()

    // Optimization state caches
    private var cachedItemStacks = mutableMapOf<NeuInternalName, SafeItemStack>()
    private var dummyBarWidth: Int = -1
    private var dummyBarHeight: Int = -1

    private var cachedSackSuggestions: List<String> = emptyList()
    private var lastSackListSize: Int = -1

    private var cachedAllSackItems: Set<NeuInternalName> = emptySet()
    private var lastSacksSize: Int = -1

    sealed class GoalCommandResult {
        data class SetGoal(val internalName: NeuInternalName, val amount: Long) : GoalCommandResult()
        data class ClearGoal(val internalName: NeuInternalName? = null) : GoalCommandResult()
        object ShowStatus : GoalCommandResult()
        data class Error(val message: String) : GoalCommandResult()
    }

    private val KNOWN_CROP_ALIASES = mapOf(
        // Wheat
        "wheat" to FarmingProfitTrackerCrops.WHEAT,
        "enchanted wheat" to FarmingProfitTrackerCrops.ENCHANTED_WHEAT,
        "enchanted hay bale" to FarmingProfitTrackerCrops.ENCHANTED_HAY_BALE,
        "enchanted hay block" to FarmingProfitTrackerCrops.ENCHANTED_HAY_BLOCK,
        "hay bale" to FarmingProfitTrackerCrops.ENCHANTED_HAY_BALE,
        "hay block" to FarmingProfitTrackerCrops.HAY_BLOCK,
        "tightly tied hay bale" to FarmingProfitTrackerCrops.TIGHTLY_TIED_HAY_BALE,
        "enchanted bread" to FarmingProfitTrackerCrops.ENCHANTED_BREAD,
        // Seeds
        "seeds" to FarmingProfitTrackerCrops.SEEDS,
        "wheat seeds" to FarmingProfitTrackerCrops.SEEDS,
        "enchanted seeds" to FarmingProfitTrackerCrops.ENCHANTED_SEEDS,
        "box of seeds" to FarmingProfitTrackerCrops.BOX_OF_SEEDS,
        // Carrot
        "carrot" to FarmingProfitTrackerCrops.CARROT_ITEM,
        "enchanted carrot" to FarmingProfitTrackerCrops.ENCHANTED_CARROT,
        "enchanted golden carrot" to FarmingProfitTrackerCrops.ENCHANTED_GOLDEN_CARROT,
        // Potato
        "potato" to FarmingProfitTrackerCrops.POTATO_ITEM,
        "enchanted potato" to FarmingProfitTrackerCrops.ENCHANTED_POTATO,
        "enchanted baked potato" to FarmingProfitTrackerCrops.ENCHANTED_BAKED_POTATO,
        // Nether Wart
        "nether wart" to FarmingProfitTrackerCrops.NETHER_STALK,
        "wart" to FarmingProfitTrackerCrops.NETHER_STALK,
        "enchanted nether wart" to FarmingProfitTrackerCrops.ENCHANTED_NETHER_STALK,
        "mutant nether wart" to FarmingProfitTrackerCrops.MUTANT_NETHER_STALK,
        // Pumpkin
        "pumpkin" to FarmingProfitTrackerCrops.PUMPKIN,
        "enchanted pumpkin" to FarmingProfitTrackerCrops.ENCHANTED_PUMPKIN,
        "polished pumpkin" to FarmingProfitTrackerCrops.POLISHED_PUMPKIN,
        // Melon
        "melon" to FarmingProfitTrackerCrops.MELON,
        "melon slice" to FarmingProfitTrackerCrops.MELON,
        "enchanted melon" to FarmingProfitTrackerCrops.ENCHANTED_MELON,
        "enchanted melon slice" to FarmingProfitTrackerCrops.ENCHANTED_MELON,
        "enchanted melon block" to FarmingProfitTrackerCrops.ENCHANTED_MELON_BLOCK,
        "melon block" to FarmingProfitTrackerCrops.MELON_BLOCK,
        // Cocoa Beans
        "cocoa" to FarmingProfitTrackerCrops.COCOA,
        "cocoa beans" to FarmingProfitTrackerCrops.COCOA,
        "enchanted cocoa" to FarmingProfitTrackerCrops.ENCHANTED_COCOA,
        "enchanted cocoa beans" to FarmingProfitTrackerCrops.ENCHANTED_COCOA,
        "enchanted cookie" to FarmingProfitTrackerCrops.ENCHANTED_COOKIE,
        // Sugar Cane
        "sugar cane" to FarmingProfitTrackerCrops.SUGAR_CANE,
        "cane" to FarmingProfitTrackerCrops.SUGAR_CANE,
        "enchanted sugar" to FarmingProfitTrackerCrops.ENCHANTED_SUGAR,
        "enchanted paper" to FarmingProfitTrackerCrops.ENCHANTED_PAPER,
        "enchanted sugar cane" to FarmingProfitTrackerCrops.ENCHANTED_SUGAR_CANE,
        // Cactus
        "cactus" to FarmingProfitTrackerCrops.CACTUS,
        "enchanted cactus green" to FarmingProfitTrackerCrops.ENCHANTED_CACTUS_GREEN,
        "enchanted cactus" to FarmingProfitTrackerCrops.ENCHANTED_CACTUS,
        // Mushroom
        "red mushroom" to FarmingProfitTrackerCrops.RED_MUSHROOM,
        "enchanted red mushroom" to FarmingProfitTrackerCrops.ENCHANTED_RED_MUSHROOM,
        "enchanted red mushroom block" to FarmingProfitTrackerCrops.ENCHANTED_HUGE_MUSHROOM_2,
        "brown mushroom" to FarmingProfitTrackerCrops.BROWN_MUSHROOM,
        "enchanted brown mushroom" to FarmingProfitTrackerCrops.ENCHANTED_BROWN_MUSHROOM,
        "enchanted brown mushroom block" to FarmingProfitTrackerCrops.ENCHANTED_HUGE_MUSHROOM_1,
        // Flowers
        "sunflower" to FarmingProfitTrackerCrops.DOUBLE_PLANT,
        "enchanted sunflower" to FarmingProfitTrackerCrops.ENCHANTED_SUNFLOWER,
        "compacted sunflower" to FarmingProfitTrackerCrops.COMPACTED_SUNFLOWER,
        "moonflower" to FarmingProfitTrackerCrops.MOONFLOWER,
        "enchanted moonflower" to FarmingProfitTrackerCrops.ENCHANTED_MOONFLOWER,
        "compacted moonflower" to FarmingProfitTrackerCrops.COMPACTED_MOONFLOWER,
        "wild rose" to FarmingProfitTrackerCrops.WILD_ROSE,
        "enchanted wild rose" to FarmingProfitTrackerCrops.ENCHANTED_WILD_ROSE,
        "compacted wild rose" to FarmingProfitTrackerCrops.COMPACTED_WILD_ROSE,
    )

    private fun getSackSuggestions(): List<String> {
        val currentSize = SackApi.sackListInternalNames.size
        if (cachedSackSuggestions.isEmpty() || currentSize != lastSackListSize) {
            lastSackListSize = currentSize
            cachedSackSuggestions = (KNOWN_CROP_ALIASES.keys + SackApi.sackListInternalNames.map { it.lowercase().replace(" ", "_") }).distinct().sorted()
        }
        return cachedSackSuggestions
    }

    private fun getAllSackItems(): Set<NeuInternalName> {
        val size = SackApi.sacks.size
        if (cachedAllSackItems.isEmpty() || size != lastSacksSize) {
            lastSacksSize = size
            cachedAllSackItems = SackApi.sacks.values.flatten().toSet()
        }
        return cachedAllSackItems
    }

    private val sackSuggestions = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        val remaining = builder.remainingLowerCase
        val candidates = getSackSuggestions()
        if (remaining.isEmpty()) {
            for (key in candidates) {
                builder.suggest(key)
            }
        } else {
            for (item in candidates) {
                if (item.startsWith(remaining)) {
                    builder.suggest(item)
                }
            }
            for (item in candidates) {
                if (!item.startsWith(remaining) && item.contains(remaining)) {
                    builder.suggest(item)
                }
            }
        }
        builder.buildFuture()
    }

    private val activeGoalSuggestions = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        val remaining = builder.remainingLowerCase
        val candidates = config.getGoals().keys.map { it.asString().lowercase() }
        for (item in candidates) {
            if (remaining.isEmpty() || item.contains(remaining)) {
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
                config.position.renderRenderables(toRender, extraSpace = 1, posLabel = "Sack Goal Tracker")
            },
        )
    }

    fun hasActiveGoal(): Boolean = config.getGoals().isNotEmpty()

    fun getAmount(internalName: NeuInternalName): Long =
        SackApi.run { internalName.getAmountInSacks().toLong() }

    fun getCurrentAmount(): Long {
        val internalName = config.trackedItemInternalName
        if (internalName.isEmpty()) return 0L
        return getAmount(internalName.toInternalName())
    }

    fun setGoal(internalName: NeuInternalName, amount: Long) {
        config.activeGoals[internalName.asString()] = amount
        config.trackedItemInternalName = internalName.asString()
        config.targetGoalAmount = amount
        completedGoals.remove(internalName)
        completedSoundPlayed = false
        invalidateDisplayCache()
        update()
    }

    fun removeGoal(internalName: NeuInternalName) {
        config.activeGoals.remove(internalName.asString())
        if (config.trackedItemInternalName.equals(internalName.asString(), ignoreCase = true)) {
            val remaining = config.activeGoals.entries.firstOrNull()
            if (remaining != null) {
                config.trackedItemInternalName = remaining.key
                config.targetGoalAmount = remaining.value
            } else {
                config.trackedItemInternalName = ""
                config.targetGoalAmount = 0L
            }
        }
        completedGoals.remove(internalName)
        invalidateDisplayCache()
        update()
        SoundUtils.playClickSound()
        ChatUtils.chat("§e[Sack Goal] §7Removed goal for §b${internalName.repoItemName}§7.")
    }

    fun clearGoal() {
        config.activeGoals.clear()
        config.trackedItemInternalName = ""
        config.targetGoalAmount = 0L
        completedGoals.clear()
        completedSoundPlayed = false
        invalidateDisplayCache()
        update()
    }

    private fun invalidateDisplayCache() {
        cachedItemStacks.clear()
    }

    fun resolveTrackedItem(query: String): NeuInternalName? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        KNOWN_CROP_ALIASES[lower]?.let { return it }
        val lowerUnderscore = lower.replace(" ", "_")
        KNOWN_CROP_ALIASES[lowerUnderscore]?.let { return it }

        val upper = trimmed.uppercase()
        val upperUnderscore = trimmed.replace(" ", "_").uppercase()
        val allSackItems = getAllSackItems()

        // Exact internal name match in sacks or sack list
        allSackItems.firstOrNull { it.asString().equals(upper, ignoreCase = true) || it.asString().equals(upperUnderscore, ignoreCase = true) }?.let { return it }
        if (SackApi.sackListInternalNames.contains(upper)) return upper.toInternalName()
        if (SackApi.sackListInternalNames.contains(upperUnderscore)) return upperUnderscore.toInternalName()

        // Exact display name matches
        allSackItems.firstOrNull { it.matchesItemName(trimmed) }?.let { return it }
        allSackItems.firstOrNull { it.itemNameWithoutColor.equals(trimmed, ignoreCase = true) }?.let { return it }

        // Enchanted prefix check
        val enchantedCandidate = "ENCHANTED_$upperUnderscore"
        allSackItems.firstOrNull { it.asString().equals(enchantedCandidate, ignoreCase = true) }?.let { return it }

        // Partial matches on name or internal name
        allSackItems.firstOrNull { it.itemNameWithoutColor.startsWith(trimmed, ignoreCase = true) }?.let { return it }
        allSackItems.firstOrNull { it.itemNameWithoutColor.contains(trimmed, ignoreCase = true) }?.let { return it }
        allSackItems.firstOrNull { it.asString().contains(upperUnderscore, ignoreCase = true) }?.let { return it }

        // Direct valid internal name fallback
        if (upperUnderscore.matches(Regex("^[A-Z0-9_:-]+$"))) {
            return upperUnderscore.toInternalName()
        }

        return null
    }

    fun parseGoalCommand(args: Array<String>, heldItem: NeuInternalName? = null): GoalCommandResult {
        if (args.isEmpty()) {
            return GoalCommandResult.ShowStatus
        }

        if (args.size == 1) {
            val first = args[0]
            if (first.equals("clear", ignoreCase = true) || first.equals("reset", ignoreCase = true)) {
                return GoalCommandResult.ClearGoal(null)
            }
            val amount = first.formatLongOrNull()
            if (amount != null && amount > 0L) {
                if (heldItem != null) {
                    return GoalCommandResult.SetGoal(heldItem, amount)
                }
                return GoalCommandResult.Error("Please specify an item to track, or hold a sack item! Example: /setsackgoal melon $first")
            }
            return GoalCommandResult.Error("Please specify a target amount! Example: /setsackgoal $first 100k")
        }

        // Support /setsackgoal clear <item> or /setsackgoal remove <item>
        if (args[0].equals("clear", ignoreCase = true) || args[0].equals("remove", ignoreCase = true) || args[0].equals("delete", ignoreCase = true)) {
            val query = args.drop(1).joinToString(" ").trim()
            val item = resolveTrackedItem(query) ?: return GoalCommandResult.Error("Could not find sack item for '$query'!")
            return GoalCommandResult.ClearGoal(item)
        }

        // Check if last argument is the amount: /setsackgoal enchanted melon 100k
        val lastAmount = args.last().formatLongOrNull()
        if (lastAmount != null && lastAmount > 0L) {
            val query = args.dropLast(1).joinToString(" ").trim()
            val item = resolveTrackedItem(query)
                ?: return GoalCommandResult.Error("Could not find sack item for '$query'!")
            return GoalCommandResult.SetGoal(item, lastAmount)
        }

        // Check if first argument is the amount: /setsackgoal 100k enchanted melon
        val firstAmount = args.first().formatLongOrNull()
        if (firstAmount != null && firstAmount > 0L) {
            val query = args.drop(1).joinToString(" ").trim()
            val item = resolveTrackedItem(query)
                ?: return GoalCommandResult.Error("Could not find sack item for '$query'!")
            return GoalCommandResult.SetGoal(item, firstAmount)
        }

        return GoalCommandResult.Error("Could not parse target amount! Example: /setsackgoal melon 100k")
    }

    private fun handleGoalCommand(args: Array<String>) {
        if (args.isEmpty()) {
            val goals = config.getGoals()
            if (goals.isNotEmpty()) {
                ChatUtils.chat("§a[Sack Goal] Currently tracking ${goals.size} goal(s):")
                for ((item, target) in goals) {
                    val current = getAmount(item)
                    val percent = (current.toDouble() / target).coerceAtLeast(0.0) * 100.0
                    ChatUtils.chat("  §7- §e${item.repoItemName}§7: §a${current.addSeparators()} §7/ §e${target.addSeparators()} §b(%.1f%%)".format(percent))
                }
                ChatUtils.chat("§7Use §e/setsackgoal <item> <amount> §7to add/update or §e/clearsackgoal [item] §7to remove.")
            } else {
                ChatUtils.chat("§e[Sack Goal] No goals currently set. Usage: /setsackgoal <item> <amount> or hold an item and type /setsackgoal <amount>")
            }
            return
        }

        val heldItem = runCatching { MinecraftCompat.localPlayerOrThrow.mainHandItem.getInternalNameOrNull() }.getOrNull()
        when (val result = parseGoalCommand(args, heldItem)) {
            is GoalCommandResult.SetGoal -> {
                setGoal(result.internalName, result.amount)
                val current = getAmount(result.internalName)
                ChatUtils.chat("§a[Sack Goal] Now tracking §e${result.amount.addSeparators()}x ${result.internalName.repoItemName} §a(${current.addSeparators()} currently in sacks)")
            }
            is GoalCommandResult.ClearGoal -> {
                if (result.internalName != null) {
                    removeGoal(result.internalName)
                } else {
                    handleClearCommand(emptyArray())
                }
            }
            is GoalCommandResult.ShowStatus -> {
                handleGoalCommand(emptyArray())
            }
            is GoalCommandResult.Error -> {
                ChatUtils.userError(result.message)
            }
        }
    }

    private fun handleClearCommand(args: Array<String> = emptyArray()) {
        val goals = config.getGoals()
        if (goals.isEmpty()) {
            ChatUtils.chat("§e[Sack Goal] No active goals to remove.")
            return
        }

        if (args.isNotEmpty()) {
            val query = args.joinToString(" ").trim()
            val item = resolveTrackedItem(query)
            if (item != null && goals.containsKey(item)) {
                removeGoal(item)
                return
            } else if (item != null) {
                ChatUtils.userError("Goal for ${item.repoItemName} is not currently active!")
                return
            } else {
                ChatUtils.userError("Could not find sack item for '$query'!")
                return
            }
        }

        if (goals.size == 1) {
            clearGoal()
            ChatUtils.chat("§a[Sack Goal] Goal cleared!")
            return
        }

        // Multiple goals active: list them with clickable remove buttons
        ChatUtils.chat("§e[Sack Goal] §7Active goals (click to remove):")
        for ((item, target) in goals) {
            val current = getAmount(item)
            ChatUtils.clickableChat(
                "  §7- §e${item.repoItemName}§7: §a${current.addSeparators()} §7/ §e${target.addSeparators()} §c[✕ Remove]",
                onClick = { removeGoal(item) },
                hover = "§cClick to remove this goal",
            )
        }
        ChatUtils.clickableChat(
            "§c[Remove All Goals]",
            onClick = {
                clearGoal()
                ChatUtils.chat("§a[Sack Goal] All goals cleared!")
            },
            hover = "§cClick to clear all sack goals",
        )
    }

    fun createProgressBar(progress: Double, width: Int, height: Int): Renderable = object : Renderable {
        override val width = width
        override val height = height
        override val horizontalAlign = RenderUtils.HorizontalAlignment.CENTER
        override val verticalAlign = RenderUtils.VerticalAlignment.TOP

        override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
            val clamped = progress.coerceIn(0.0, 1.0)
            val borderColor = 0xAA000000.toInt()
            val bgColor = 0x88222222.toInt()
            val fillColor = if (progress >= 1.0) 0xFF55FF55.toInt() else 0xFF2ECC71.toInt()

            GuiRenderUtils.drawRect(0, 0, width, height, borderColor)
            GuiRenderUtils.drawRect(1, 1, width - 1, height - 1, bgColor)
            val fillWidth = ((width - 2) * clamped).toInt()
            if (fillWidth > 0) {
                GuiRenderUtils.drawRect(1, 1, 1 + fillWidth, height - 1, fillColor)
            }
        }
    }

    private fun buildSingleGoalDisplay(
        internalName: NeuInternalName,
        itemStack: SafeItemStack,
        current: Long,
        target: Long,
    ): List<Renderable> {
        val progress = (current.toDouble() / target.coerceAtLeast(1L)).coerceAtLeast(0.0)
        val percentage = progress * 100.0
        val percentStr = "%.1f%%".format(percentage)
        val isComplete = current >= target

        val list = mutableListOf<Renderable>()

        val titleText = "§e${internalName.repoItemName} §7Goal"
        val removeButton = Renderable.clickable(
            Renderable.text("§c ✕"),
            onLeftClick = { removeGoal(internalName) },
            bypassChecks = true,
            tips = listOf("§cClick to remove this goal"),
        )

        val headerElements = mutableListOf<Renderable>()
        if (!itemStack.isEmpty) {
            headerElements.add(Renderable.item({ itemStack }))
        }
        headerElements.add(Renderable.text(titleText, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER))

        val isInGui = MinecraftCompat.screen?.let { it !is ChatScreen } ?: false
        if (isInGui) {
            headerElements.add(removeButton)
        }

        val header = Renderable.horizontal(
            headerElements,
            spacing = 2,
            horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
            verticalAlign = RenderUtils.VerticalAlignment.CENTER,
        )

        val clickableHeader = Renderable.clickable(
            header,
            onAnyClick = mapOf(RIGHT_MOUSE to { removeGoal(internalName) }),
            bypassChecks = true,
            tips = if (isInGui) listOf("§eRight-click to remove this goal") else null,
        )
        list.add(clickableHeader)

        list.add(createProgressBar(progress, config.barWidth, config.barHeight))

        val countText = if (isComplete) {
            "§a${current.addSeparators()} §7/ §e${target.addSeparators()} §6✔ COMPLETED!"
        } else {
            "§a${current.addSeparators()} §7/ §e${target.addSeparators()} §b($percentStr)"
        }
        list.add(Renderable.text(countText, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER))

        return list
    }

    fun update() {
        if (!config.enabled) return

        val goals = config.getGoals()
        if (goals.isNotEmpty()) {
            val fullList = mutableListOf<Renderable>()
            var anyJustCompleted = false

            for ((internalName, target) in goals) {
                val current = getAmount(internalName)
                val itemStack = cachedItemStacks.getOrPut(internalName) {
                    runCatching { internalName.getItemStackOrNull() }.getOrNull() ?: SafeItemStack.EMPTY
                }
                if (fullList.isNotEmpty()) {
                    fullList.add(Renderable.placeholder(0, 4))
                }
                fullList.addAll(buildSingleGoalDisplay(internalName, itemStack, current, target))

                if (current >= target && !completedGoals.contains(internalName)) {
                    completedGoals.add(internalName)
                    anyJustCompleted = true
                    ChatUtils.chat("§a[Sack Goal] §6Goal of ${target.addSeparators()}x ${internalName.repoItemName} reached!")
                } else if (current < target) {
                    completedGoals.remove(internalName)
                }
            }

            if (anyJustCompleted && config.dingOnComplete) {
                SoundUtils.plingSound.playSound()
            }
            display = fullList
        } else {
            if (display.isNotEmpty()) {
                display = emptyList()
            }
            completedGoals.clear()
            completedSoundPlayed = false
        }

        if (dummyDisplay.isEmpty() || config.barWidth != dummyBarWidth || config.barHeight != dummyBarHeight) {
            dummyBarWidth = config.barWidth
            dummyBarHeight = config.barHeight
            val dummyItem = "ENCHANTED_MELON".toInternalName()
            val dummyStack = runCatching { dummyItem.getItemStackOrNull() }.getOrNull() ?: SafeItemStack.EMPTY
            dummyDisplay = buildSingleGoalDisplay(
                dummyItem,
                dummyStack,
                75_000L,
                150_000L,
            )
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (event.repeatSeconds(1)) {
            update()
        }
    }

    @HandleEvent(SackChangeEvent::class)
    fun onSackChange() {
        update()
    }

    @HandleEvent(SackDataUpdateEvent::class)
    fun onSackDataUpdate() {
        update()
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("setsackgoal") {
            description = "Set a target sack collection goal to track with a progress bar GUI"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("sackgoal")
            simpleCallback {
                handleGoalCommand(emptyArray())
            }
            arg("item", BrigadierArguments.string(), sackSuggestions) { itemArg ->
                arg("amount", BrigadierArguments.string()) { amountArg ->
                    callback {
                        handleGoalCommand(arrayOf(getArg(itemArg), getArg(amountArg)))
                    }
                }
                arg("part2", BrigadierArguments.string()) { part2Arg ->
                    arg("amount", BrigadierArguments.string()) { amountArg ->
                        callback {
                            handleGoalCommand(arrayOf("${getArg(itemArg)} ${getArg(part2Arg)}", getArg(amountArg)))
                        }
                    }
                }
                callback {
                    val item = getArg(itemArg)
                    if (item.equals("clear", ignoreCase = true) || item.equals("reset", ignoreCase = true)) {
                        handleClearCommand(emptyArray())
                    } else {
                        handleGoalCommand(arrayOf(item))
                    }
                }
            }
        }

        event.registerBrigadier("clearsackgoal") {
            description = "Clear your current sack collection goal(s)"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("resetsackgoal")
            simpleCallback {
                handleClearCommand(emptyArray())
            }
            arg("item", BrigadierArguments.string(), activeGoalSuggestions) { itemArg ->
                callback {
                    handleClearCommand(arrayOf(getArg(itemArg)))
                }
            }
        }
    }
}
