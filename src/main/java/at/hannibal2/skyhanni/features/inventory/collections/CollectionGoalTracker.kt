package at.hannibal2.skyhanni.features.inventory.collections

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.CollectionApi
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.data.GuiEditManager
import at.hannibal2.skyhanni.data.garden.CropCollectionApi
import at.hannibal2.skyhanni.events.CollectionUpdateEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.garden.farming.CropCollectionAddEvent
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems
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
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

@SkyHanniModule
object CollectionGoalTracker {

    private val config get() = SkyHanniMod.feature.inventory.collectionGoal

    var completedSoundPlayed = false
    private var display = listOf<Renderable>()
    private var dummyDisplay = listOf<Renderable>()

    data class CollectionEntry(
        val key: String,
        val displayName: String,
        val internalName: NeuInternalName,
        val cropType: CropType? = null,
        val aliases: List<String> = emptyList(),
    )

    sealed class GoalCommandResult {
        data class SetGoal(val entry: CollectionEntry, val amount: Long) : GoalCommandResult()
        object ClearGoal : GoalCommandResult()
        object ShowStatus : GoalCommandResult()
        data class Error(val message: String) : GoalCommandResult()
    }

    val ALL_COLLECTIONS: List<CollectionEntry> = listOf(
        // Farming
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

    private val collectionSuggestions = ALL_COLLECTIONS.map { it.key }

    val collectionSuggestionProvider = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        val remaining = builder.remainingLowerCase
        for (key in collectionSuggestions) {
            if (key.startsWith(remaining) || key.contains(remaining)) {
                builder.suggest(key)
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
                config.position.renderRenderables(toRender, extraSpace = 1, posLabel = "Collection Goal Tracker")
            },
        )
    }

    fun hasActiveGoal(): Boolean = config.trackedCollectionKey.isNotEmpty() && config.targetGoalAmount > 0L

    fun getActiveCollectionEntry(): CollectionEntry? =
        ALL_COLLECTIONS.firstOrNull { it.key == config.trackedCollectionKey }

    fun getCurrentAmount(): Long {
        val entry = getActiveCollectionEntry() ?: return 0L
        return getCollectionAmount(entry)
    }

    fun getCollectionAmount(entry: CollectionEntry): Long {
        var maxAmount = 0L

        // 1. Check CropCollectionApi if it is a crop
        entry.cropType?.let { crop ->
            val gardenCropAmount = CropCollectionApi.run { crop.getCollection() }
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

        return maxAmount
    }

    fun setGoal(entry: CollectionEntry, amount: Long) {
        config.trackedCollectionKey = entry.key
        config.targetGoalAmount = amount
        completedSoundPlayed = false
        update()
    }

    fun clearGoal() {
        config.trackedCollectionKey = ""
        config.targetGoalAmount = 0L
        completedSoundPlayed = false
        update()
    }

    fun resolveCollection(query: String): CollectionEntry? {
        val clean = query.trim().lowercase().replace(" ", "_").replace("-", "_")
        if (clean.isEmpty()) return null

        // 1. Exact key match
        ALL_COLLECTIONS.firstOrNull { it.key == clean }?.let { return it }

        // 2. Exact display name match
        ALL_COLLECTIONS.firstOrNull { it.displayName.equals(query.trim(), ignoreCase = true) }?.let { return it }

        // 3. Alias match
        ALL_COLLECTIONS.firstOrNull { clean in it.aliases }?.let { return it }

        // 4. Exact internal name match
        val upper = query.trim().uppercase().replace(" ", "_")
        ALL_COLLECTIONS.firstOrNull { it.internalName.asString() == upper }?.let { return it }

        // 5. Starts with match
        ALL_COLLECTIONS.firstOrNull { it.key.startsWith(clean) || it.displayName.lowercase().startsWith(clean) }?.let { return it }

        // 6. Contains match
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
                return GoalCommandResult.ClearGoal
            }
            val amount = first.formatLongOrNull()
            if (amount != null && amount > 0L) {
                if (heldItem != null) {
                    val baseInternalName = NeuItems.getPrimitiveMultiplier(heldItem).internalName
                    val resolved = ALL_COLLECTIONS.firstOrNull { it.internalName == heldItem || it.internalName == baseInternalName }
                    if (resolved != null) {
                        return GoalCommandResult.SetGoal(resolved, amount)
                    }
                }
                return GoalCommandResult.Error("Please specify a collection to track, or hold a collection item! Example: /setcollectiongoal wheat $first")
            }
            return GoalCommandResult.Error("Please specify a target amount! Example: /setcollectiongoal $first 100k")
        }

        // Try last argument as amount: /setcollectiongoal sugar_cane 100k
        val lastAmount = args.last().formatLongOrNull()
        if (lastAmount != null && lastAmount > 0L) {
            val query = args.dropLast(1).joinToString(" ").trim()
            val collection = resolveCollection(query)
                ?: return GoalCommandResult.Error("Could not find collection for '$query'! Type /setcollectiongoal to see suggestions.")
            return GoalCommandResult.SetGoal(collection, lastAmount)
        }

        // Try first argument as amount: /setcollectiongoal 100k sugar_cane
        val firstAmount = args.first().formatLongOrNull()
        if (firstAmount != null && firstAmount > 0L) {
            val query = args.drop(1).joinToString(" ").trim()
            val collection = resolveCollection(query)
                ?: return GoalCommandResult.Error("Could not find collection for '$query'! Type /setcollectiongoal to see suggestions.")
            return GoalCommandResult.SetGoal(collection, firstAmount)
        }

        return GoalCommandResult.Error("Could not parse target amount! Example: /setcollectiongoal wheat 100k")
    }

    private fun handleGoalCommand(args: Array<String>) {
        if (args.isEmpty()) {
            val entry = getActiveCollectionEntry()
            if (entry != null && config.targetGoalAmount > 0L) {
                val current = getCurrentAmount()
                val target = config.targetGoalAmount
                val percent = (current.toDouble() / target).coerceAtLeast(0.0) * 100.0
                ChatUtils.chat("§a[Collection Goal] Currently tracking §e${entry.displayName} Collection§7: §a${current.addSeparators()} §7/ §e${target.addSeparators()} §b(%.1f%%)".format(percent))
                ChatUtils.chat("§7Use §e/setcollectiongoal <collection> <amount> §7to update or §e/clearcollectiongoal §7to clear.")
            } else {
                ChatUtils.chat("§e[Collection Goal] No goal currently set. Usage: /setcollectiongoal <collection> <amount> or hold a collection item and type /setcollectiongoal <amount>")
            }
            return
        }

        val heldItem = runCatching { MinecraftCompat.localPlayerOrThrow.mainHandItem.getInternalNameOrNull() }.getOrNull()
        when (val result = parseGoalCommand(args, heldItem)) {
            is GoalCommandResult.SetGoal -> {
                setGoal(result.entry, result.amount)
                val current = getCurrentAmount()
                ChatUtils.chat("§a[Collection Goal] Now tracking §e${result.amount.addSeparators()}x ${result.entry.displayName} Collection §a(${current.addSeparators()} current collection)")
            }
            is GoalCommandResult.ClearGoal -> {
                clearGoal()
                ChatUtils.chat("§a[Collection Goal] Goal cleared!")
            }
            is GoalCommandResult.ShowStatus -> {
                handleGoalCommand(emptyArray())
            }
            is GoalCommandResult.Error -> {
                ChatUtils.userError(result.message)
            }
        }
    }

    private fun handleClearCommand() {
        clearGoal()
        ChatUtils.chat("§a[Collection Goal] Goal cleared!")
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

    private fun buildDisplay(entry: CollectionEntry, current: Long, target: Long): List<Renderable> {
        val progress = (current.toDouble() / target.coerceAtLeast(1L)).coerceAtLeast(0.0)
        val percentage = progress * 100.0
        val percentStr = "%.1f%%".format(percentage)
        val isComplete = current >= target

        val list = mutableListOf<Renderable>()

        val itemStack = runCatching { entry.internalName.getItemStackOrNull() }.getOrNull() ?: SafeItemStack.EMPTY
        val titleText = "§e${entry.displayName} §7Collection"

        val header = if (!itemStack.isEmpty) {
            Renderable.horizontal(
                Renderable.item({ itemStack }),
                Renderable.text(titleText, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER),
                spacing = 2,
                horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
                verticalAlign = RenderUtils.VerticalAlignment.CENTER,
            )
        } else {
            Renderable.text(titleText, horizontalAlign = RenderUtils.HorizontalAlignment.CENTER)
        }
        list.add(header)

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
        val entry = getActiveCollectionEntry()
        val target = config.targetGoalAmount

        if (entry != null && target > 0L) {
            val current = getCollectionAmount(entry)
            display = buildDisplay(entry, current, target)

            if (current >= target) {
                if (!completedSoundPlayed) {
                    completedSoundPlayed = true
                    if (config.dingOnComplete) {
                        SoundUtils.plingSound.playSound()
                    }
                    ChatUtils.chat("§a[Collection Goal] §6Goal of ${target.addSeparators()}x ${entry.displayName} Collection reached!")
                }
            } else {
                completedSoundPlayed = false
            }
        } else {
            display = emptyList()
            completedSoundPlayed = false
        }

        dummyDisplay = buildDisplay(
            ALL_COLLECTIONS.first { it.key == "wheat" },
            50_000L,
            100_000L,
        )
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

    @HandleEvent(CropCollectionAddEvent::class)
    fun onCropCollectionAdd() {
        update()
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("setcollectiongoal") {
            description = "Set a target collection goal to track with a progress bar GUI"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("collectiongoal")
            simpleCallback {
                handleGoalCommand(emptyArray())
            }
            arg("collection", BrigadierArguments.string(), collectionSuggestionProvider) { collectionArg ->
                arg("amount", BrigadierArguments.string()) { amountArg ->
                    callback {
                        handleGoalCommand(arrayOf(getArg(collectionArg), getArg(amountArg)))
                    }
                }
                arg("part2", BrigadierArguments.string()) { part2Arg ->
                    arg("amount", BrigadierArguments.string()) { amountArg ->
                        callback {
                            handleGoalCommand(arrayOf("${getArg(collectionArg)}_${getArg(part2Arg)}", getArg(amountArg)))
                        }
                    }
                }
                callback {
                    val col = getArg(collectionArg)
                    if (col.equals("clear", ignoreCase = true) || col.equals("reset", ignoreCase = true)) {
                        handleClearCommand()
                    } else {
                        handleGoalCommand(arrayOf(col))
                    }
                }
            }
        }

        event.registerBrigadier("clearcollectiongoal") {
            description = "Clear your current collection goal"
            category = CommandCategory.USERS_ACTIVE
            aliases = listOf("resetcollectiongoal")
            legacyCallbackArgs { handleClearCommand() }
        }
    }
}

