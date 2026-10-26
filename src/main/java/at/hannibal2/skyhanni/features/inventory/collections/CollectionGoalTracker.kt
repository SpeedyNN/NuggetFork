package at.hannibal2.skyhanni.features.inventory.collections

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.CollectionApi
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.data.GuiEditManager
import at.hannibal2.skyhanni.data.garden.CropCollectionApi.getCollection
import at.hannibal2.skyhanni.events.CollectionUpdateEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
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
object CollectionGoalTracker {

    private val config get() = SkyHanniMod.feature.inventory.collectionGoal

    var completedSoundPlayed = false
    private val completedGoals = mutableSetOf<String>()
    private var display = listOf<Renderable>()
    private var dummyDisplay = listOf<Renderable>()

    // Optimization state caches
    private var cachedItemStacks = mutableMapOf<String, SafeItemStack>()
    private var dummyBarWidth: Int = -1
    private var dummyBarHeight: Int = -1

    data class CollectionEntry(
        val key: String,
        val displayName: String,
        val internalName: NeuInternalName,
        val cropType: CropType? = null,
        val aliases: List<String> = emptyList(),
    )

    sealed class GoalCommandResult {
        data class SetGoal(val entry: CollectionEntry, val amount: Long) : GoalCommandResult()
        data class ClearGoal(val entry: CollectionEntry? = null) : GoalCommandResult()
        object ShowStatus : GoalCommandResult()
        data class Error(val message: String) : GoalCommandResult()
    }

    val ALL_COLLECTIONS: List<CollectionEntry> = listOf(
        // Farming - Crops
        CollectionEntry("wheat", "Wheat", "WHEAT".toInternalName(), CropType.WHEAT),
        CollectionEntry("carrot", "Carrot", "CARROT_ITEM".toInternalName(), CropType.CARROT),
        CollectionEntry("potato", "Potato", "POTATO_ITEM".toInternalName(), CropType.POTATO),
        CollectionEntry("pumpkin", "Pumpkin", "PUMPKIN".toInternalName(), CropType.PUMPKIN),
        CollectionEntry("melon", "Melon", "MELON".toInternalName(), CropType.MELON, listOf("melon_slice")),
        CollectionEntry("seeds", "Seeds", "SEEDS".toInternalName(), null, listOf("wheat_seeds")),
        CollectionEntry("red_mushroom", "Red Mushroom", "RED_MUSHROOM".toInternalName(), CropType.MUSHROOM, listOf("mushroom")),
        CollectionEntry("brown_mushroom", "Brown Mushroom", "BROWN_MUSHROOM".toInternalName(), CropType.MUSHROOM),
        CollectionEntry("cocoa_beans", "Cocoa Beans", "INK_SACK:3".toInternalName(), CropType.COCOA_BEANS, listOf("cocoa")),
        CollectionEntry("cactus", "Cactus", "CACTUS".toInternalName(), CropType.CACTUS),
        CollectionEntry("sugar_cane", "Sugar Cane", "SUGAR_CANE".toInternalName(), CropType.SUGAR_CANE, listOf("cane")),
        CollectionEntry("nether_wart", "Nether Wart", "NETHER_STALK".toInternalName(), CropType.NETHER_WART, listOf("wart")),

        // Farming - Animal Drops
        CollectionEntry("leather", "Leather", "LEATHER".toInternalName(), null, listOf("cow", "raw_beef", "beef")),
        CollectionEntry("raw_beef", "Raw Beef", "RAW_BEEF".toInternalName(), null, listOf("beef")),
        CollectionEntry("porkchop", "Raw Porkchop", "PORK".toInternalName(), null, listOf("pork", "raw_porkchop", "pig")),
        CollectionEntry("chicken", "Raw Chicken", "RAW_CHICKEN".toInternalName(), null, listOf("raw_chicken")),
        CollectionEntry("feather", "Feather", "FEATHER".toInternalName(), null, listOf("feathers")),
        CollectionEntry("mutton", "Raw Mutton", "MUTTON".toInternalName(), null, listOf("raw_mutton", "sheep")),
        CollectionEntry("rabbit", "Raw Rabbit", "RABBIT".toInternalName(), null, listOf("raw_rabbit")),
        CollectionEntry("rabbit_foot", "Rabbit's Foot", "RABBIT_FOOT".toInternalName(), null, listOf("rabbit_feet")),

        // Mining
        CollectionEntry("cobblestone", "Cobblestone", "COBBLESTONE".toInternalName()),
        CollectionEntry("coal", "Coal", "COAL".toInternalName()),
        CollectionEntry("iron_ingot", "Iron Ingot", "IRON_INGOT".toInternalName(), aliases = listOf("iron")),
        CollectionEntry("gold_ingot", "Gold Ingot", "GOLD_INGOT".toInternalName(), aliases = listOf("gold")),
        CollectionEntry("diamond", "Diamond", "DIAMOND".toInternalName()),
        CollectionEntry("lapis_lazuli", "Lapis Lazuli", "INK_SACK:4".toInternalName(), aliases = listOf("lapis")),
        CollectionEntry("redstone", "Redstone", "REDSTONE".toInternalName()),
        CollectionEntry("emerald", "Emerald", "EMERALD".toInternalName()),
        CollectionEntry("quartz", "Nether Quartz", "QUARTZ".toInternalName(), aliases = listOf("nether_quartz")),
        CollectionEntry("obsidian", "Obsidian", "OBSIDIAN".toInternalName()),
        CollectionEntry("glowstone_dust", "Glowstone Dust", "GLOWSTONE_DUST".toInternalName(), aliases = listOf("glowstone")),
        CollectionEntry("gravel", "Gravel", "GRAVEL".toInternalName()),
        CollectionEntry("ice", "Ice", "ICE".toInternalName()),
        CollectionEntry("netherrack", "Netherrack", "NETHERRACK".toInternalName()),
        CollectionEntry("sand", "Sand", "SAND".toInternalName()),
        CollectionEntry("red_sand", "Red Sand", "SAND:1".toInternalName(), aliases = listOf("red_sand")),
        CollectionEntry("mycelium", "Mycelium", "MYCEL".toInternalName(), aliases = listOf("mycelium")),
        CollectionEntry("end_stone", "End Stone", "ENDER_STONE".toInternalName()),
        CollectionEntry("mithril", "Mithril", "MITHRIL_ORE".toInternalName()),
        CollectionEntry("hard_stone", "Hard Stone", "HARD_STONE".toInternalName()),
        CollectionEntry("gemstone", "Gemstone", "ROUGH_AMBER_GEM".toInternalName(), aliases = listOf("rough_gemstone")),
        CollectionEntry("titanium", "Titanium", "TITANIUM_ORE".toInternalName()),
        CollectionEntry("glacite", "Glacite", "GLACITE".toInternalName()),
        CollectionEntry("tungsten", "Tungsten", "TUNGSTEN".toInternalName()),
        CollectionEntry("umber", "Umber", "UMBER".toInternalName()),

        // Combat
        CollectionEntry("rotten_flesh", "Rotten Flesh", "ROTTEN_FLESH".toInternalName()),
        CollectionEntry("bone", "Bone", "BONE".toInternalName()),
        CollectionEntry("string", "String", "STRING".toInternalName()),
        CollectionEntry("spider_eye", "Spider Eye", "SPIDER_EYE".toInternalName()),
        CollectionEntry("gunpowder", "Gunpowder", "SULPHUR".toInternalName(), aliases = listOf("sulphur")),
        CollectionEntry("ender_pearl", "Ender Pearl", "ENDER_PEARL".toInternalName()),
        CollectionEntry("ghast_tear", "Ghast Tear", "GHAST_TEAR".toInternalName()),
        CollectionEntry("slimeball", "Slimeball", "SLIME_BALL".toInternalName(), aliases = listOf("slime_ball", "slime")),
        CollectionEntry("blaze_rod", "Blaze Rod", "BLAZE_ROD".toInternalName()),
        CollectionEntry("magma_cream", "Magma Cream", "MAGMA_CREAM".toInternalName()),
        CollectionEntry("chili_pepper", "Chili Pepper", "CHILI_PEPPER".toInternalName()),

        // Foraging
        CollectionEntry("oak_wood", "Oak Wood", "LOG".toInternalName(), aliases = listOf("oak", "oak_log")),
        CollectionEntry("spruce_wood", "Spruce Wood", "LOG:1".toInternalName(), aliases = listOf("spruce", "spruce_log")),
        CollectionEntry("birch_wood", "Birch Wood", "LOG:2".toInternalName(), aliases = listOf("birch", "birch_log")),
        CollectionEntry("jungle_wood", "Jungle Wood", "LOG:3".toInternalName(), aliases = listOf("jungle", "jungle_log")),
        CollectionEntry("acacia_wood", "Acacia Wood", "LOG_2".toInternalName(), aliases = listOf("acacia", "acacia_log")),
        CollectionEntry("dark_oak_wood", "Dark Oak Wood", "LOG_2:1".toInternalName(), aliases = listOf("dark_oak", "dark_oak_log")),

        // Fishing
        CollectionEntry("raw_fish", "Raw Fish", "RAW_FISH".toInternalName(), aliases = listOf("fish")),
        CollectionEntry("raw_salmon", "Raw Salmon", "RAW_FISH:1".toInternalName(), aliases = listOf("salmon")),
        CollectionEntry("clownfish", "Clownfish", "RAW_FISH:2".toInternalName()),
        CollectionEntry("pufferfish", "Pufferfish", "RAW_FISH:3".toInternalName()),
        CollectionEntry("prismarine_shard", "Prismarine Shard", "PRISMARINE_SHARD".toInternalName()),
        CollectionEntry("prismarine_crystals", "Prismarine Crystals", "PRISMARINE_CRYSTALS".toInternalName()),
        CollectionEntry("clay", "Clay", "CLAY_BALL".toInternalName(), aliases = listOf("clay_ball")),
        CollectionEntry("water_lily", "Water Lily", "WATER_LILY".toInternalName(), aliases = listOf("lily_pad")),
        CollectionEntry("ink_sack", "Ink Sack", "INK_SACK".toInternalName(), aliases = listOf("ink")),
        CollectionEntry("sponge", "Sponge", "SPONGE".toInternalName()),
        CollectionEntry("magmafish", "Magmafish", "MAGMA_FISH".toInternalName(), aliases = listOf("magma_fish")),

        // Rift
        CollectionEntry("wilted_berberis", "Wilted Berberis", "WILTED_BERBERIS".toInternalName()),
        CollectionEntry("living_metal", "Living Metal", "LIVING_METAL".toInternalName()),
        CollectionEntry("hemovibe", "Hemovibe", "HEMOVIBE".toInternalName()),

        // Boss Collections
        CollectionEntry("bonzo", "Bonzo", "BONZO_FRAGMENT".toInternalName()),
        CollectionEntry("scarf", "Scarf", "SCARF_FRAGMENT".toInternalName()),
        CollectionEntry("professor", "Professor", "PROFESSOR_FRAGMENT".toInternalName()),
        CollectionEntry("thorn", "Thorn", "THORN_FRAGMENT".toInternalName()),
        CollectionEntry("livid", "Livid", "LIVID_FRAGMENT".toInternalName()),
        CollectionEntry("sadan", "Sadan", "SADAN_FRAGMENT".toInternalName()),
        CollectionEntry("necron", "Necron", "NECRON_HANDLE".toInternalName()),
    )

    // Pre-indexed lookup maps for O(1) matching
    private val byKey: Map<String, CollectionEntry> = ALL_COLLECTIONS.associateBy { it.key }
    private val byAlias: Map<String, CollectionEntry> = buildMap {
        for (entry in ALL_COLLECTIONS) {
            put(entry.key, entry)
            put(entry.displayName.lowercase(), entry)
            put(entry.displayName.lowercase().replace(" ", "_"), entry)
            for (alias in entry.aliases) {
                put(alias.lowercase(), entry)
                put(alias.lowercase().replace(" ", "_"), entry)
            }
        }
    }
    private val byInternalName: Map<String, CollectionEntry> = ALL_COLLECTIONS.associateBy { it.internalName.asString().uppercase() }

    private val cachedSuggestions: List<String> = buildList {
        for (entry in ALL_COLLECTIONS) {
            add(entry.key)
            if (entry.aliases.isNotEmpty()) {
                addAll(entry.aliases)
            }
        }
    }.distinct().sorted()

    private val collectionSuggestions = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        val remaining = builder.remainingLowerCase
        if (remaining.isEmpty()) {
            for (key in cachedSuggestions) {
                builder.suggest(key)
            }
        } else {
            for (item in cachedSuggestions) {
                if (item.startsWith(remaining)) {
                    builder.suggest(item)
                }
            }
            for (item in cachedSuggestions) {
                if (!item.startsWith(remaining) && item.contains(remaining)) {
                    builder.suggest(item)
                }
            }
        }
        builder.buildFuture()
    }

    private val activeGoalSuggestions = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        val remaining = builder.remainingLowerCase
        val candidates = config.getGoals().keys.map { it.lowercase() }
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
            inOwnInventory = false,
            condition = { SkyBlockUtils.inSkyBlock && config.enabled && (hasActiveGoal() || GuiEditManager.isInGui()) },
            onRender = {
                val toRender = if (hasActiveGoal()) display else dummyDisplay
                config.position.renderRenderables(toRender, extraSpace = 1, posLabel = "Collection Goal Tracker")
            },
        )
    }

    fun hasActiveGoal(): Boolean = config.getGoals().isNotEmpty()

    fun getAmount(entry: CollectionEntry): Long {
        var maxAmount = 0L

        // 1. Check CropCollectionApi if it's a Garden crop
        entry.cropType?.let { crop ->
            val gardenCropAmount = crop.getCollection()
            if (gardenCropAmount > maxAmount) {
                maxAmount = gardenCropAmount
            }
        }

        // 2. Check CollectionApi
        CollectionApi.getCollectionCounter(entry.internalName)?.let { counter ->
            if (counter > maxAmount) {
                maxAmount = counter
            }
        }

        // 3. Special aliases / multi-item fallback
        if (entry.key == "leather" || entry.key == "raw_beef") {
            CollectionApi.getCollectionCounter("LEATHER".toInternalName())?.let { if (it > maxAmount) maxAmount = it }
            CollectionApi.getCollectionCounter("RAW_BEEF".toInternalName())?.let { if (it > maxAmount) maxAmount = it }
        }
        if (entry.key == "mutton") {
            CollectionApi.getCollectionCounter("MUTTON".toInternalName())?.let { if (it > maxAmount) maxAmount = it }
            CollectionApi.getCollectionCounter("RAW_MUTTON".toInternalName())?.let { if (it > maxAmount) maxAmount = it }
        }

        return maxAmount
    }

    fun getCurrentAmount(): Long {
        val entry = resolveCollection(config.trackedCollectionKey) ?: return 0L
        return getAmount(entry)
    }

    fun setGoal(entry: CollectionEntry, amount: Long) {
        config.activeGoals[entry.key] = amount
        config.trackedCollectionKey = entry.key
        config.targetGoalAmount = amount
        completedGoals.remove(entry.key)
        completedSoundPlayed = false
        invalidateDisplayCache()
        update()
    }

    fun removeGoal(key: String) {
        val entry = byKey[key] ?: resolveCollection(key)
        config.activeGoals.remove(entry?.key ?: key)
        if (config.trackedCollectionKey.equals(key, ignoreCase = true) || (entry != null && config.trackedCollectionKey.equals(entry.key, ignoreCase = true))) {
            val remaining = config.activeGoals.entries.firstOrNull()
            if (remaining != null) {
                config.trackedCollectionKey = remaining.key
                config.targetGoalAmount = remaining.value
            } else {
                config.trackedCollectionKey = ""
                config.targetGoalAmount = 0L
            }
        }
        completedGoals.remove(entry?.key ?: key)
        invalidateDisplayCache()
        update()
        SoundUtils.playClickSound()
        ChatUtils.chat("§e[Collection Goal] §7Removed goal for §b${entry?.displayName ?: key}§7.")
    }

    fun clearGoal() {
        config.activeGoals.clear()
        config.trackedCollectionKey = ""
        config.targetGoalAmount = 0L
        completedGoals.clear()
        completedSoundPlayed = false
        invalidateDisplayCache()
        update()
    }

    private fun invalidateDisplayCache() {
        cachedItemStacks.clear()
    }

    fun resolveCollection(query: String): CollectionEntry? {
        val clean = query.trim().lowercase().replace(" ", "_").replace("-", "_")
        if (clean.isEmpty()) return null

        // 1. O(1) match by exact key, name, or alias
        byAlias[clean]?.let { return it }

        // 2. O(1) match by internal name
        val upper = query.trim().uppercase().replace(" ", "_")
        byInternalName[upper]?.let { return it }

        // 3. Fallback prefix search
        ALL_COLLECTIONS.firstOrNull { it.key.startsWith(clean) || it.displayName.lowercase().startsWith(clean) }?.let { return it }

        // 4. Fallback contains search
        ALL_COLLECTIONS.firstOrNull { it.key.contains(clean) || it.displayName.lowercase().contains(clean) }?.let { return it }

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
                    val entry = resolveCollection(heldItem.asString())
                    if (entry != null) {
                        return GoalCommandResult.SetGoal(entry, amount)
                    }
                }
                return GoalCommandResult.Error("Please specify a collection to track, or hold a collection item! Example: /setcollectiongoal wheat $first")
            }
            return GoalCommandResult.Error("Please specify a target amount! Example: /setcollectiongoal $first 100k")
        }

        // Support /setcollectiongoal clear <collection> or /setcollectiongoal remove <collection>
        if (args[0].equals("clear", ignoreCase = true) || args[0].equals("remove", ignoreCase = true) || args[0].equals("delete", ignoreCase = true)) {
            val query = args.drop(1).joinToString(" ").trim()
            val entry = resolveCollection(query) ?: return GoalCommandResult.Error("Could not find collection for '$query'!")
            return GoalCommandResult.ClearGoal(entry)
        }

        // Check if last argument is the amount: /setcollectiongoal dark oak wood 2m
        val lastAmount = args.last().formatLongOrNull()
        if (lastAmount != null && lastAmount > 0L) {
            val query = args.dropLast(1).joinToString(" ").trim()
            val entry = resolveCollection(query)
                ?: return GoalCommandResult.Error("Could not find collection for '$query'!")
            return GoalCommandResult.SetGoal(entry, lastAmount)
        }

        // Check if first argument is the amount: /setcollectiongoal 2m dark oak wood
        val firstAmount = args.first().formatLongOrNull()
        if (firstAmount != null && firstAmount > 0L) {
            val query = args.drop(1).joinToString(" ").trim()
            val entry = resolveCollection(query)
                ?: return GoalCommandResult.Error("Could not find collection for '$query'!")
            return GoalCommandResult.SetGoal(entry, firstAmount)
        }

        return GoalCommandResult.Error("Could not parse target amount! Example: /setcollectiongoal wheat 100k")
    }

    private fun handleGoalCommand(args: Array<String>) {
        if (args.isEmpty()) {
            val goals = config.getGoals()
            if (goals.isNotEmpty()) {
                ChatUtils.chat("§a[Collection Goal] Currently tracking ${goals.size} goal(s):")
                for ((key, target) in goals) {
                    val entry = resolveCollection(key)
                    val displayName = entry?.displayName ?: key
                    val current = if (entry != null) getAmount(entry) else 0L
                    val percent = (current.toDouble() / target).coerceAtLeast(0.0) * 100.0
                    ChatUtils.chat("  §7- §b$displayName§7: §a${current.addSeparators()} §7/ §e${target.addSeparators()} §b(%.1f%%)".format(percent))
                }
                ChatUtils.chat("§7Use §e/setcollectiongoal <collection> <amount> §7to add/update or §e/clearcollectiongoal [collection] §7to remove.")
            } else {
                ChatUtils.chat("§e[Collection Goal] No goals currently set. Usage: /setcollectiongoal <collection> <amount>")
            }
            return
        }

        val heldItem = runCatching { MinecraftCompat.localPlayerOrThrow.mainHandItem.getInternalNameOrNull() }.getOrNull()
        when (val result = parseGoalCommand(args, heldItem)) {
            is GoalCommandResult.SetGoal -> {
                setGoal(result.entry, result.amount)
                val current = getAmount(result.entry)
                ChatUtils.chat("§a[Collection Goal] Now tracking §b${result.entry.displayName} §7Goal: §a${result.amount.addSeparators()} §7(${current.addSeparators()} current)")
                if (current == 0L) {
                    ChatUtils.chat("§e[Collection Goal] §8(Tip: Open your SkyBlock Collection menu to sync your current progress!)")
                }
            }
            is GoalCommandResult.ClearGoal -> {
                if (result.entry != null) {
                    removeGoal(result.entry.key)
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
            ChatUtils.chat("§e[Collection Goal] No active goals to remove.")
            return
        }

        if (args.isNotEmpty()) {
            val query = args.joinToString(" ").trim()
            val entry = resolveCollection(query)
            if (entry != null && goals.containsKey(entry.key)) {
                removeGoal(entry.key)
                return
            } else if (entry != null) {
                ChatUtils.userError("Goal for ${entry.displayName} is not currently active!")
                return
            } else {
                ChatUtils.userError("Could not find collection for '$query'!")
                return
            }
        }

        if (goals.size == 1) {
            clearGoal()
            ChatUtils.chat("§a[Collection Goal] Goal cleared!")
            return
        }

        // Multiple goals active: list them with clickable remove buttons
        ChatUtils.chat("§e[Collection Goal] §7Active goals (click to remove):")
        for ((key, target) in goals) {
            val entry = resolveCollection(key)
            val displayName = entry?.displayName ?: key
            val current = if (entry != null) getAmount(entry) else 0L
            ChatUtils.clickableChat(
                "  §7- §b$displayName§7: §a${current.addSeparators()} §7/ §e${target.addSeparators()} §c[✕ Remove]",
                onClick = { removeGoal(key) },
                hover = "§cClick to remove this goal",
            )
        }
        ChatUtils.clickableChat(
            "§c[Remove All Goals]",
            onClick = {
                clearGoal()
                ChatUtils.chat("§a[Collection Goal] All goals cleared!")
            },
            hover = "§cClick to clear all collection goals",
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
            val fillColor = if (progress >= 1.0) 0xFF55FF55.toInt() else 0xFF3498DB.toInt()

            GuiRenderUtils.drawRect(0, 0, width, height, borderColor)
            GuiRenderUtils.drawRect(1, 1, width - 1, height - 1, bgColor)
            val fillWidth = ((width - 2) * clamped).toInt()
            if (fillWidth > 0) {
                GuiRenderUtils.drawRect(1, 1, 1 + fillWidth, height - 1, fillColor)
            }
        }
    }

    private fun buildSingleGoalDisplay(
        entry: CollectionEntry,
        itemStack: SafeItemStack,
        current: Long,
        target: Long,
    ): List<Renderable> {
        val progress = (current.toDouble() / target.coerceAtLeast(1L)).coerceAtLeast(0.0)
        val percentage = progress * 100.0
        val percentStr = "%.1f%%".format(percentage)
        val isComplete = current >= target

        val list = mutableListOf<Renderable>()

        val titleText = "§b${entry.displayName} §7Collection Goal"
        val removeButton = Renderable.clickable(
            Renderable.text("§c ✕"),
            onLeftClick = { removeGoal(entry.key) },
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
            onAnyClick = mapOf(RIGHT_MOUSE to { removeGoal(entry.key) }),
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

            for ((key, target) in goals) {
                val entry = resolveCollection(key) ?: continue
                val current = getAmount(entry)
                val itemStack = cachedItemStacks.getOrPut(entry.key) {
                    runCatching { entry.internalName.getItemStackOrNull() }.getOrNull() ?: SafeItemStack.EMPTY
                }
                if (fullList.isNotEmpty()) {
                    fullList.add(Renderable.placeholder(0, 4))
                }
                fullList.addAll(buildSingleGoalDisplay(entry, itemStack, current, target))

                if (current >= target && !completedGoals.contains(entry.key)) {
                    completedGoals.add(entry.key)
                    anyJustCompleted = true
                    ChatUtils.chat("§a[Collection Goal] §6Goal of ${target.addSeparators()} ${entry.displayName} reached!")
                } else if (current < target) {
                    completedGoals.remove(entry.key)
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
            val dummyEntry = ALL_COLLECTIONS.first { it.key == "wheat" }
            val dummyStack = runCatching { dummyEntry.internalName.getItemStackOrNull() }.getOrNull() ?: SafeItemStack.EMPTY
            dummyDisplay = buildSingleGoalDisplay(
                dummyEntry,
                dummyStack,
                250_000L,
                500_000L,
            )
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (event.repeatSeconds(1)) {
            update()
        }
    }

    @HandleEvent(CollectionUpdateEvent::class)
    fun onCollectionUpdate() {
        update()
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("setcollectiongoal") {
            description = "Set a target SkyBlock collection goal to track with a progress bar GUI"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("collectiongoal")
            simpleCallback {
                handleGoalCommand(emptyArray())
            }
            arg("collection", BrigadierArguments.string(), collectionSuggestions) { collArg ->
                arg("amount", BrigadierArguments.string()) { amountArg ->
                    callback {
                        handleGoalCommand(arrayOf(getArg(collArg), getArg(amountArg)))
                    }
                }
                arg("part2", BrigadierArguments.string()) { part2Arg ->
                    arg("amount", BrigadierArguments.string()) { amountArg ->
                        callback {
                            handleGoalCommand(arrayOf("${getArg(collArg)} ${getArg(part2Arg)}", getArg(amountArg)))
                        }
                    }
                }
                callback {
                    val coll = getArg(collArg)
                    if (coll.equals("clear", ignoreCase = true) || coll.equals("reset", ignoreCase = true)) {
                        handleClearCommand(emptyArray())
                    } else {
                        handleGoalCommand(arrayOf(coll))
                    }
                }
            }
        }

        event.registerBrigadier("clearcollectiongoal") {
            description = "Clear your current SkyBlock collection goal(s)"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("resetcollectiongoal")
            simpleCallback {
                handleClearCommand(emptyArray())
            }
            arg("collection", BrigadierArguments.string(), activeGoalSuggestions) { collArg ->
                callback {
                    handleClearCommand(arrayOf(getArg(collArg)))
                }
            }
        }
    }
}
