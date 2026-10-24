package at.hannibal2.skyhanni.features.garden.tracker

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.garden.FarmingProfitTrackerConfig.TrackedSource
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ItemAddManager
import at.hannibal2.skyhanni.events.ItemAddEvent
import at.hannibal2.skyhanni.events.SackChangeEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.garden.farming.CropClickEvent
import at.hannibal2.skyhanni.events.garden.farming.CropCollectionAddEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.features.garden.CropCollectionType
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.farming.HoeLevelDisplay
import at.hannibal2.skyhanni.features.garden.tracker.RareCropTracker.RareCropDropType
import at.hannibal2.skyhanni.features.inventory.SuperCraftFeatures
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NumberUtil.formatInt
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getHypixelEnchantments
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.containsKeys
import java.util.EnumMap
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object FarmingProfitTrackerCrops {

    private val toolExpCapsule = "TOOL_EXP_CAPSULE".toInternalName()
    private val sackCompactionTimeout = 60.seconds

    val CROP_SOURCES = listOf(
        TrackedSource.CROPS,
        TrackedSource.PESTS,
        TrackedSource.CROP_FEVER,
        TrackedSource.BLESSED,
    )

    // Wheat
    val WHEAT = "WHEAT".toInternalName()
    val HAY_BLOCK = "HAY_BLOCK".toInternalName()
    val ENCHANTED_HAY_BLOCK = "ENCHANTED_HAY_BLOCK".toInternalName()
    val ENCHANTED_HAY_BALE = "ENCHANTED_HAY_BALE".toInternalName()
    val TIGHTLY_TIED_HAY_BALE = "TIGHTLY_TIED_HAY_BALE".toInternalName()
    val ENCHANTED_BREAD = "ENCHANTED_BREAD".toInternalName()

    // Carrot
    val CARROT_ITEM = "CARROT_ITEM".toInternalName()
    val ENCHANTED_CARROT = "ENCHANTED_CARROT".toInternalName()
    val ENCHANTED_GOLDEN_CARROT = "ENCHANTED_GOLDEN_CARROT".toInternalName()

    // Potato
    val POTATO_ITEM = "POTATO_ITEM".toInternalName()
    val ENCHANTED_POTATO = "ENCHANTED_POTATO".toInternalName()
    val ENCHANTED_BAKED_POTATO = "ENCHANTED_BAKED_POTATO".toInternalName()

    // Nether Wart
    val NETHER_STALK = "NETHER_STALK".toInternalName()
    val ENCHANTED_NETHER_STALK = "ENCHANTED_NETHER_STALK".toInternalName()
    val MUTANT_NETHER_STALK = "MUTANT_NETHER_STALK".toInternalName()

    // Pumpkin
    val PUMPKIN = "PUMPKIN".toInternalName()
    val ENCHANTED_PUMPKIN = "ENCHANTED_PUMPKIN".toInternalName()
    val POLISHED_PUMPKIN = "POLISHED_PUMPKIN".toInternalName()

    // Melon
    val MELON = "MELON".toInternalName()
    val MELON_BLOCK = "MELON_BLOCK".toInternalName()
    val ENCHANTED_MELON = "ENCHANTED_MELON".toInternalName()
    val ENCHANTED_MELON_BLOCK = "ENCHANTED_MELON_BLOCK".toInternalName()
    val ENCHANTED_GLISTERING_MELON = "ENCHANTED_GLISTERING_MELON".toInternalName()

    // Cocoa Beans
    val COCOA = "INK_SACK:3".toInternalName()
    val COCOA_ALT = "INK_SACK-3".toInternalName()
    val ENCHANTED_COCOA = "ENCHANTED_COCOA".toInternalName()
    val ENCHANTED_COOKIE = "ENCHANTED_COOKIE".toInternalName()

    // Sugar Cane
    val SUGAR_CANE = "SUGAR_CANE".toInternalName()
    val ENCHANTED_SUGAR = "ENCHANTED_SUGAR".toInternalName()
    val ENCHANTED_PAPER = "ENCHANTED_PAPER".toInternalName()
    val ENCHANTED_SUGAR_CANE = "ENCHANTED_SUGAR_CANE".toInternalName()

    // Cactus
    val CACTUS = "CACTUS".toInternalName()
    val ENCHANTED_CACTUS_GREEN = "ENCHANTED_CACTUS_GREEN".toInternalName()
    val ENCHANTED_CACTUS = "ENCHANTED_CACTUS".toInternalName()

    // Mushroom (Red)
    val RED_MUSHROOM = "RED_MUSHROOM".toInternalName()
    val HUGE_MUSHROOM_2 = "HUGE_MUSHROOM_2".toInternalName()
    val ENCHANTED_RED_MUSHROOM = "ENCHANTED_RED_MUSHROOM".toInternalName()
    val ENCHANTED_HUGE_MUSHROOM_2 = "ENCHANTED_HUGE_MUSHROOM_2".toInternalName()

    // Mushroom (Brown)
    val BROWN_MUSHROOM = "BROWN_MUSHROOM".toInternalName()
    val HUGE_MUSHROOM_1 = "HUGE_MUSHROOM_1".toInternalName()
    val ENCHANTED_BROWN_MUSHROOM = "ENCHANTED_BROWN_MUSHROOM".toInternalName()
    val ENCHANTED_HUGE_MUSHROOM_1 = "ENCHANTED_HUGE_MUSHROOM_1".toInternalName()

    // Sunflower
    val DOUBLE_PLANT = "DOUBLE_PLANT".toInternalName()
    val ENCHANTED_SUNFLOWER = "ENCHANTED_SUNFLOWER".toInternalName()
    val COMPACTED_SUNFLOWER = "COMPACTED_SUNFLOWER".toInternalName()

    // Moonflower
    val MOONFLOWER = "MOONFLOWER".toInternalName()
    val ENCHANTED_MOONFLOWER = "ENCHANTED_MOONFLOWER".toInternalName()
    val COMPACTED_MOONFLOWER = "COMPACTED_MOONFLOWER".toInternalName()

    // Wild Rose
    val WILD_ROSE = "WILD_ROSE".toInternalName()
    val ENCHANTED_WILD_ROSE = "ENCHANTED_WILD_ROSE".toInternalName()
    val COMPACTED_WILD_ROSE = "COMPACTED_WILD_ROSE".toInternalName()

    internal data class CropTierDefinition(
        val crop: CropType,
        val rawMultiplier: Long,
        val validCandidates: Set<NeuInternalName> = emptySet(),
    )

    private val cropTierDefinitions: Map<NeuInternalName, CropTierDefinition> = buildMap {
        // Wheat
        put(WHEAT, CropTierDefinition(CropType.WHEAT, 1L))
        put(HAY_BLOCK, CropTierDefinition(CropType.WHEAT, 9L, setOf(WHEAT)))
        put(ENCHANTED_HAY_BLOCK, CropTierDefinition(CropType.WHEAT, 1296L, setOf(HAY_BLOCK, WHEAT)))
        put(ENCHANTED_HAY_BALE, CropTierDefinition(CropType.WHEAT, 1296L, setOf(HAY_BLOCK, WHEAT)))
        put(TIGHTLY_TIED_HAY_BALE, CropTierDefinition(CropType.WHEAT, 207360L, setOf(ENCHANTED_HAY_BLOCK, ENCHANTED_HAY_BALE, HAY_BLOCK, WHEAT)))
        put(ENCHANTED_BREAD, CropTierDefinition(CropType.WHEAT, 60L, setOf(WHEAT)))

        // Carrot
        put(CARROT_ITEM, CropTierDefinition(CropType.CARROT, 1L))
        put(ENCHANTED_CARROT, CropTierDefinition(CropType.CARROT, 160L, setOf(CARROT_ITEM)))
        put(ENCHANTED_GOLDEN_CARROT, CropTierDefinition(CropType.CARROT, 20480L, setOf(ENCHANTED_CARROT, CARROT_ITEM)))

        // Potato
        put(POTATO_ITEM, CropTierDefinition(CropType.POTATO, 1L))
        put(ENCHANTED_POTATO, CropTierDefinition(CropType.POTATO, 160L, setOf(POTATO_ITEM)))
        put(ENCHANTED_BAKED_POTATO, CropTierDefinition(CropType.POTATO, 25600L, setOf(ENCHANTED_POTATO, POTATO_ITEM)))

        // Nether Wart
        put(NETHER_STALK, CropTierDefinition(CropType.NETHER_WART, 1L))
        put(ENCHANTED_NETHER_STALK, CropTierDefinition(CropType.NETHER_WART, 160L, setOf(NETHER_STALK)))
        put(MUTANT_NETHER_STALK, CropTierDefinition(CropType.NETHER_WART, 25600L, setOf(ENCHANTED_NETHER_STALK, NETHER_STALK)))

        // Pumpkin
        put(PUMPKIN, CropTierDefinition(CropType.PUMPKIN, 1L))
        put(ENCHANTED_PUMPKIN, CropTierDefinition(CropType.PUMPKIN, 160L, setOf(PUMPKIN)))
        put(POLISHED_PUMPKIN, CropTierDefinition(CropType.PUMPKIN, 25600L, setOf(ENCHANTED_PUMPKIN, PUMPKIN)))

        // Melon
        put(MELON, CropTierDefinition(CropType.MELON, 1L))
        put(MELON_BLOCK, CropTierDefinition(CropType.MELON, 9L, setOf(MELON)))
        put(ENCHANTED_MELON, CropTierDefinition(CropType.MELON, 1440L, setOf(MELON_BLOCK, MELON)))
        put(ENCHANTED_MELON_BLOCK, CropTierDefinition(CropType.MELON, 230400L, setOf(ENCHANTED_MELON, MELON_BLOCK, MELON)))
        put(ENCHANTED_GLISTERING_MELON, CropTierDefinition(CropType.MELON, 1440L, setOf(ENCHANTED_MELON, MELON_BLOCK, MELON)))

        // Cocoa Beans
        put(COCOA, CropTierDefinition(CropType.COCOA_BEANS, 1L))
        put(COCOA_ALT, CropTierDefinition(CropType.COCOA_BEANS, 1L))
        put(ENCHANTED_COCOA, CropTierDefinition(CropType.COCOA_BEANS, 160L, setOf(COCOA, COCOA_ALT)))
        put(ENCHANTED_COOKIE, CropTierDefinition(CropType.COCOA_BEANS, 20480L, setOf(ENCHANTED_COCOA, COCOA, COCOA_ALT)))

        // Sugar Cane
        put(SUGAR_CANE, CropTierDefinition(CropType.SUGAR_CANE, 1L))
        put(ENCHANTED_SUGAR, CropTierDefinition(CropType.SUGAR_CANE, 160L, setOf(SUGAR_CANE)))
        put(ENCHANTED_PAPER, CropTierDefinition(CropType.SUGAR_CANE, 192L, setOf(SUGAR_CANE)))
        put(ENCHANTED_SUGAR_CANE, CropTierDefinition(CropType.SUGAR_CANE, 25600L, setOf(ENCHANTED_SUGAR, SUGAR_CANE)))

        // Cactus
        put(CACTUS, CropTierDefinition(CropType.CACTUS, 1L))
        put(ENCHANTED_CACTUS_GREEN, CropTierDefinition(CropType.CACTUS, 160L, setOf(CACTUS)))
        put(ENCHANTED_CACTUS, CropTierDefinition(CropType.CACTUS, 25600L, setOf(ENCHANTED_CACTUS_GREEN, CACTUS)))

        // Mushroom (Red)
        put(RED_MUSHROOM, CropTierDefinition(CropType.MUSHROOM, 1L))
        put(HUGE_MUSHROOM_2, CropTierDefinition(CropType.MUSHROOM, 9L, setOf(RED_MUSHROOM)))
        put(ENCHANTED_RED_MUSHROOM, CropTierDefinition(CropType.MUSHROOM, 160L, setOf(HUGE_MUSHROOM_2, RED_MUSHROOM)))
        put(ENCHANTED_HUGE_MUSHROOM_2, CropTierDefinition(CropType.MUSHROOM, 5120L, setOf(ENCHANTED_RED_MUSHROOM, HUGE_MUSHROOM_2, RED_MUSHROOM)))

        // Mushroom (Brown)
        put(BROWN_MUSHROOM, CropTierDefinition(CropType.MUSHROOM, 1L))
        put(HUGE_MUSHROOM_1, CropTierDefinition(CropType.MUSHROOM, 9L, setOf(BROWN_MUSHROOM)))
        put(ENCHANTED_BROWN_MUSHROOM, CropTierDefinition(CropType.MUSHROOM, 160L, setOf(HUGE_MUSHROOM_1, BROWN_MUSHROOM)))
        put(ENCHANTED_HUGE_MUSHROOM_1, CropTierDefinition(CropType.MUSHROOM, 5120L, setOf(ENCHANTED_BROWN_MUSHROOM, HUGE_MUSHROOM_1, BROWN_MUSHROOM)))

        // Sunflower
        put(DOUBLE_PLANT, CropTierDefinition(CropType.SUNFLOWER, 1L))
        put(ENCHANTED_SUNFLOWER, CropTierDefinition(CropType.SUNFLOWER, 160L, setOf(DOUBLE_PLANT)))
        put(COMPACTED_SUNFLOWER, CropTierDefinition(CropType.SUNFLOWER, 25600L, setOf(ENCHANTED_SUNFLOWER, DOUBLE_PLANT)))

        // Moonflower
        put(MOONFLOWER, CropTierDefinition(CropType.MOONFLOWER, 1L))
        put(ENCHANTED_MOONFLOWER, CropTierDefinition(CropType.MOONFLOWER, 160L, setOf(MOONFLOWER)))
        put(COMPACTED_MOONFLOWER, CropTierDefinition(CropType.MOONFLOWER, 25600L, setOf(ENCHANTED_MOONFLOWER, MOONFLOWER)))

        // Wild Rose
        put(WILD_ROSE, CropTierDefinition(CropType.WILD_ROSE, 1L))
        put(ENCHANTED_WILD_ROSE, CropTierDefinition(CropType.WILD_ROSE, 160L, setOf(WILD_ROSE)))
        put(COMPACTED_WILD_ROSE, CropTierDefinition(CropType.WILD_ROSE, 25600L, setOf(ENCHANTED_WILD_ROSE, WILD_ROSE)))
    }

    internal data class CropPrimitiveItem(
        val crop: CropType,
        val rawInternalName: NeuInternalName,
        val rawAmount: Long,
    )

    internal data class TrackedCandidate(
        val name: NeuInternalName,
        val multiplier: Long,
        var available: Long,
    )

    internal data class SourceTrackedCandidate(
        val source: TrackedSource,
        val name: NeuInternalName,
        val multiplier: Long,
        var available: Long,
    )

    /**
     * REGEX-TEST: BLESSED! You found an Enchanted Nether Wart!
     * REGEX-TEST: BLESSED! You found a Cropie!
     */
    private val blessedDropPattern by FarmingProfitTracker.patternGroup.pattern(
        "blessed.drop",
        "^BLESSED! You found an? (?<item>.+)!$",
    )

    private val blocksBrokenCache: MutableMap<CropType, Long> = EnumMap(CropType::class.java)
    private val pendingReplenishCosts: MutableMap<CropType, Long> = EnumMap(CropType::class.java)
    private val recentSpecialCropItems = mutableMapOf<NeuInternalName, Long>()

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    private fun onCropCollectionAdd(event: CropCollectionAddEvent) {
        val source = event.cropCollectionType.toTrackedSource() ?: return
        if (!FarmingProfitTracker.shouldTrack(source)) return
        trackCropAmount(event.crop, source, event.amount)
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    private fun onCropClick(event: CropClickEvent) {
        clearSessionStateIfSessionWasReset()
        blocksBrokenCache.addOrPut(event.crop, 1)
        queueReplenishCost(event)
        FarmingProfitTracker.markActivity()
        FarmingProfitTracker.firstUpdate()
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    private fun onTick(event: SkyHanniTickEvent) {
        if (!event.isMod(5) || blocksBrokenCache.isEmpty()) return
        val pending = EnumMap<CropType, Long>(blocksBrokenCache)
        blocksBrokenCache.clear()
        FarmingProfitTracker.modify { data ->
            pending.forEach { (crop, amount) ->
                data.addBlocksBroken(crop, amount)
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    private fun onChat(event: SkyHanniChatEvent.Allow) {
        checkRareCropDrop(event.cleanMessage)
        checkBlessedDrop(event.cleanMessage)
        checkCropFeverStart(event.cleanMessage)
        checkCropFeverDrop(event.cleanMessage)
        checkToolExpCapsule(event.cleanMessage)
        checkSuperCraft(event.cleanMessage)
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    private fun onItemAdd(event: ItemAddEvent) {
        when (event.source) {
            ItemAddManager.Source.COMMAND -> if (FarmingProfitTracker.config.enabled) {
                FarmingProfitTracker.run { event.addItemFromEvent() }
            }

            ItemAddManager.Source.ITEM_ADD,
            ItemAddManager.Source.SACKS,
            -> trackCropItemFromItemAdd(event)

            else -> Unit
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN, priority = HandleEvent.LOWEST)
    private fun onSackChange(event: SackChangeEvent) {
        if (event.sackChanges.isNotEmpty()) recentSpecialCropItems.clear()
    }

    internal fun addRareCropItem(internalName: NeuInternalName?, dropType: RareCropDropType) {
        internalName?.let { rememberSpecialCropItem(it, 1) }
        if (!FarmingProfitTracker.shouldTrack(TrackedSource.RARE_CROPS)) return
        if (internalName != null) {
            FarmingProfitTracker.addTrackedItem(TrackedSource.RARE_CROPS, internalName, 1L)
        }
        FarmingProfitTracker.modify {
            it.rareCropDrops.addOrPut(dropType, 1)
        }
        FarmingProfitTracker.markActivity()
    }

    internal fun rememberSpecialCropItem(internalName: NeuInternalName, amount: Long) {
        if (amount <= 0) return
        internalName.getCropTierDefinitionOrNull() ?: return
        recentSpecialCropItems.addOrPut(internalName, amount)
    }

    private fun checkRareCropDrop(message: String) {
        for (dropType in RareCropDropType.entries.filter { it.hasRareCropMessage }) {
            if (!dropType.chatPattern.matches(message)) continue
            val internalName = dropType.takeIf { it.isItemDrop }?.let {
                NeuInternalName.fromItemNameOrNull(it.dropName.removeColor())
            }
            addRareCropItem(internalName, dropType)
            return
        }
    }

    private fun checkBlessedDrop(message: String) {
        blessedDropPattern.matchMatcher(message) {
            val internalName = NeuInternalName.fromItemNameOrNull(group("item")) ?: return
            rememberSpecialCropItem(internalName, 1)
            if (!FarmingProfitTracker.shouldTrack(TrackedSource.BLESSED)) return@matchMatcher
            val cropDef = internalName.getCropTierDefinitionOrNull()
            val crop = cropDef?.crop ?: run {
                val primitiveStack = NeuItems.getPrimitiveMultiplier(internalName)
                CropType.getByNameOrNull(primitiveStack.internalName.itemNameWithoutColor)
            }
            val rawMultiplier = cropDef?.rawMultiplier ?: NeuItems.getPrimitiveMultiplier(internalName).amount.toLong()

            FarmingProfitTracker.addTrackedItem(TrackedSource.BLESSED, internalName, 1L)
            FarmingProfitTracker.modify {
                it.blessedDrops.addOrPut(internalName, 1)
                crop?.let { cropType ->
                    it.addCropAmount(cropType, TrackedSource.BLESSED, rawMultiplier)
                }
            }
            FarmingProfitTracker.markActivity()
        }
    }

    private fun checkCropFeverStart(message: String) {
        if (!FarmingProfitTracker.shouldTrack(TrackedSource.CROP_FEVER)) return
        if (!CropFeverTracker.isCropFeverStartMessage(message)) return
        val crop = GardenApi.getCurrentlyFarmedCrop() ?: GardenApi.lastBrokenCropType ?: return
        FarmingProfitTracker.modify {
            it.cropFevers.addOrPut(crop, 1)
        }
        FarmingProfitTracker.markActivity()
    }

    private fun checkCropFeverDrop(message: String) {
        CropFeverTracker.rngDrop.matchMatcher(message) {
            val rarity = RngDropEnum.getByNameOrNull(group("rarity")) ?: return
            val amount = group("amount").formatInt()
            val internalName = NeuInternalName.fromItemNameOrNull(group("crop")) ?: return
            rememberSpecialCropItem(internalName, amount.toLong())
            if (!FarmingProfitTracker.shouldTrack(TrackedSource.CROP_FEVER)) return@matchMatcher
            val cropDef = internalName.getCropTierDefinitionOrNull()
            val crop = GardenApi.getCurrentlyFarmedCrop() ?: cropDef?.crop ?: run {
                val primitiveStack = NeuItems.getPrimitiveMultiplier(internalName)
                CropType.getByNameOrNull(primitiveStack.internalName.itemNameWithoutColor)
            } ?: return

            val rawMultiplier = cropDef?.rawMultiplier ?: NeuItems.getPrimitiveMultiplier(internalName).amount.toLong()

            FarmingProfitTracker.addTrackedItem(TrackedSource.CROP_FEVER, internalName, amount.toLong())
            FarmingProfitTracker.modify {
                it.cropFeverDrops.addOrPut(rarity, 1)
                it.addCropAmount(crop, TrackedSource.CROP_FEVER, rawMultiplier * amount.toLong())
            }
            FarmingProfitTracker.markActivity()
        }
    }

    private fun checkToolExpCapsule(message: String) {
        if (!FarmingProfitTracker.shouldTrack(TrackedSource.CROPS)) return
        if (!HoeLevelDisplay.levelUpPattern.matches(message)) return
        FarmingProfitTracker.addTrackedItem(TrackedSource.CROPS, toolExpCapsule, 1L)
        FarmingProfitTracker.modify {
            it.toolExpCapsules++
        }
        FarmingProfitTracker.markActivity()
    }

    private fun trackCropAmount(crop: CropType, source: TrackedSource, amount: Long) {
        if (amount <= 0) return
        clearSessionStateIfSessionWasReset()
        val internalName = crop.getCropInternalName()
        FarmingProfitTracker.addTrackedItem(source, internalName, amount, message = false)
        if (source == TrackedSource.CROPS) applyPendingReplenishCost(crop)
        FarmingProfitTracker.modify {
            it.addCropAmount(crop, source, amount)
        }
        FarmingProfitTracker.markActivity()
    }

    private fun queueReplenishCost(event: CropClickEvent) {
        if (!FarmingProfitTracker.shouldTrack(TrackedSource.CROPS)) return
        val crop = event.crop
        if (!crop.replenish) return
        if (event.itemInHand?.getHypixelEnchantments()?.containsKeys("replenish") != true) return
        pendingReplenishCosts.addOrPut(crop, crop.multiplier.toLong())
    }

    private fun applyPendingReplenishCost(crop: CropType) {
        val amount = pendingReplenishCosts.remove(crop) ?: return
        removeTrackedRawCrop(crop.getCropInternalName(), amount)
    }

    private fun trackCropItemFromItemAdd(event: ItemAddEvent) {
        val anySourceEnabled = CROP_SOURCES.any { FarmingProfitTracker.shouldTrack(it) }
        if (!FarmingProfitTracker.config.enabled || !anySourceEnabled || event.amount <= 0) return
        val consumedSpecial = consumeRecentSpecialCropItem(event.internalName, event.amount.toLong())
        val amount = event.amount.toLong() - consumedSpecial
        if (amount > 0) {
            processCropCompaction(event.internalName, amount)
        }
    }

    private fun checkSuperCraft(message: String) {
        val crafted = SuperCraftFeatures.parseCraftedItem(message) ?: return
        processCropCompaction(crafted.internalName, crafted.amount.toLong())
    }

    internal fun processCropCompaction(internalName: NeuInternalName, amount: Long) {
        if (!FarmingProfitTracker.config.enabled || amount <= 0) return
        if (FarmingProfitTracker.lastFarmingActivity.passedSince() > sackCompactionTimeout) return
        clearSessionStateIfSessionWasReset()

        val cropDef = internalName.getCropTierDefinitionOrNull() ?: return
        if (cropDef.rawMultiplier <= 1 || cropDef.validCandidates.isEmpty()) return

        val eligibleSources = CROP_SOURCES.filter { FarmingProfitTracker.shouldTrack(it) }
        if (eligibleSources.isEmpty()) return

        var convertedTotal = 0L
        val craftedPerSource = mutableMapOf<TrackedSource, Long>()

        FarmingProfitTracker.modify { data ->
            val candidates = mutableListOf<SourceTrackedCandidate>()
            for (source in eligibleSources) {
                val bucketMap = data.bucketedItems[source] ?: continue
                for ((itemKey, trackedItem) in bucketMap) {
                    val count = trackedItem.totalAmount
                    if (count <= 0) continue
                    if (itemKey in cropDef.validCandidates) {
                        val candDef = itemKey.getCropTierDefinitionOrNull() ?: continue
                        if (candDef.crop == cropDef.crop && candDef.rawMultiplier < cropDef.rawMultiplier) {
                            candidates.add(SourceTrackedCandidate(source, itemKey, candDef.rawMultiplier, count))
                        }
                    }
                }
            }

            if (candidates.isEmpty()) return@modify

            val (craftable, deductions) = calculateMultiSourceCompactionDeductions(
                targetMultiplier = cropDef.rawMultiplier,
                amount = amount,
                candidates = candidates,
            )
            if (craftable <= 0) return@modify

            val rawConsumedBySource = mutableMapOf<TrackedSource, Long>()
            for ((key, toConsume) in deductions) {
                val (source, candidateName) = key
                var remaining = -toConsume
                while (remaining != 0L) {
                    val chunk = remaining.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                    data.addItem(source, candidateName, chunk, command = true)
                    remaining -= chunk
                }
                val candMultiplier = candidates.firstOrNull { it.source == source && it.name == candidateName }?.multiplier ?: 1L
                rawConsumedBySource.addOrPut(source, toConsume * candMultiplier)
            }

            var remainingToDistribute = craftable
            for ((source, rawConsumed) in rawConsumedBySource.entries.sortedByDescending { it.value }) {
                val sourceCrafted = (rawConsumed / cropDef.rawMultiplier).coerceAtMost(remainingToDistribute)
                if (sourceCrafted > 0) {
                    craftedPerSource.addOrPut(source, sourceCrafted)
                    remainingToDistribute -= sourceCrafted
                }
            }
            if (remainingToDistribute > 0) {
                val primarySource = rawConsumedBySource.maxByOrNull { it.value }?.key ?: TrackedSource.CROPS
                craftedPerSource.addOrPut(primarySource, remainingToDistribute)
            }

            convertedTotal = craftable
        }

        if (convertedTotal > 0) {
            for ((source, count) in craftedPerSource) {
                if (count > 0) {
                    FarmingProfitTracker.addTrackedItem(source, internalName, count, message = false)
                }
            }
            FarmingProfitTracker.markActivity()
        }
    }

    internal fun calculateMultiSourceCompactionDeductions(
        targetMultiplier: Long,
        amount: Long,
        candidates: List<SourceTrackedCandidate>,
    ): Pair<Long, Map<Pair<TrackedSource, NeuInternalName>, Long>> {
        if (targetMultiplier <= 1 || amount <= 0 || candidates.isEmpty()) {
            return 0L to emptyMap()
        }

        val sortedCandidates = candidates.sortedWith(
            compareByDescending<SourceTrackedCandidate> { it.multiplier }
                .thenBy { candidateSourcePriority(it.source) }
        )
        val totalRawAvailable = sortedCandidates.sumOf { it.available * it.multiplier }
        val craftable = minOf(amount, totalRawAvailable / targetMultiplier)
        if (craftable <= 0) return 0L to emptyMap()

        val deductions = mutableMapOf<Pair<TrackedSource, NeuInternalName>, Long>()
        var rawNeeded = craftable * targetMultiplier

        for (candidate in sortedCandidates) {
            if (rawNeeded <= 0) break
            val unitsNeeded = rawNeeded / candidate.multiplier
            val toConsume = minOf(candidate.available, unitsNeeded)
            if (toConsume > 0) {
                rawNeeded -= toConsume * candidate.multiplier
                deductions[candidate.source to candidate.name] = toConsume
            }
        }

        return craftable to deductions
    }

    private fun candidateSourcePriority(source: TrackedSource): Int = when (source) {
        TrackedSource.CROPS -> 1
        TrackedSource.PESTS -> 2
        TrackedSource.CROP_FEVER -> 3
        TrackedSource.BLESSED -> 4
        else -> 5
    }

    internal fun calculateCompactionDeductions(
        targetMultiplier: Long,
        amount: Long,
        candidates: List<TrackedCandidate>,
    ): Pair<Long, Map<NeuInternalName, Long>> {
        val multiCandidates = candidates.map {
            SourceTrackedCandidate(TrackedSource.CROPS, it.name, it.multiplier, it.available)
        }
        val (craftable, multiDeductions) = calculateMultiSourceCompactionDeductions(
            targetMultiplier,
            amount,
            multiCandidates,
        )
        val singleDeductions = multiDeductions.mapKeys { it.key.second }
        return craftable to singleDeductions
    }

    internal fun NeuInternalName.getCropTierDefinitionOrNull(): CropTierDefinition? =
        cropTierDefinitions[this] ?: run {
            val primitiveStack = NeuItems.getPrimitiveMultiplier(this)
            val crop = CropType.getByNameOrNull(primitiveStack.internalName.itemNameWithoutColor) ?: return null
            val candidates = if (primitiveStack.internalName != this) {
                setOf(primitiveStack.internalName)
            } else emptySet()
            CropTierDefinition(crop, primitiveStack.amount.toLong(), candidates)
        }

    internal fun NeuInternalName.getCropPrimitiveItemOrNull(): CropPrimitiveItem? {
        val def = getCropTierDefinitionOrNull() ?: return null
        return CropPrimitiveItem(def.crop, def.crop.getCropInternalName(), def.rawMultiplier)
    }

    private fun consumeRecentSpecialCropItem(internalName: NeuInternalName, amount: Long): Long {
        val stored = recentSpecialCropItems[internalName] ?: return 0L
        val consumed = minOf(stored, amount)
        val remaining = stored - consumed
        if (remaining > 0) recentSpecialCropItems[internalName] = remaining else recentSpecialCropItems.remove(internalName)
        return consumed
    }

    private fun removeTrackedRawCrop(rawInternalName: NeuInternalName, amount: Long) {
        if (amount <= 0) return
        FarmingProfitTracker.modify { data ->
            val rawAmount = minOf(amount, data.getTrackedCropItemAmount(rawInternalName))
            if (rawAmount <= 0) return@modify
            var remaining = -rawAmount
            while (remaining != 0L) {
                val chunk = remaining.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                data.addItem(TrackedSource.CROPS, rawInternalName, chunk, command = true)
                remaining -= chunk
            }
        }
    }

    private fun FarmingProfitTrackerData.getTrackedCropItemAmount(internalName: NeuInternalName): Long =
        bucketedItems[TrackedSource.CROPS]?.get(internalName)?.totalAmount?.coerceAtLeast(0L) ?: 0L

    private fun clearSessionStateIfSessionWasReset() {
        if (pendingReplenishCosts.isEmpty() && recentSpecialCropItems.isEmpty()) return
        if (FarmingProfitTracker.sessionDataHasNoFarmingData()) {
            pendingReplenishCosts.clear()
            recentSpecialCropItems.clear()
        }
    }

    internal fun CropType.getCropInternalName(): NeuInternalName = when (this) {
        CropType.WHEAT -> WHEAT
        CropType.CARROT -> CARROT_ITEM
        CropType.POTATO -> POTATO_ITEM
        CropType.NETHER_WART -> NETHER_STALK
        CropType.PUMPKIN -> PUMPKIN
        CropType.MELON -> MELON
        CropType.COCOA_BEANS -> COCOA
        CropType.SUGAR_CANE -> SUGAR_CANE
        CropType.CACTUS -> CACTUS
        CropType.MUSHROOM -> RED_MUSHROOM
        CropType.SUNFLOWER -> DOUBLE_PLANT
        CropType.MOONFLOWER -> MOONFLOWER
        CropType.WILD_ROSE -> WILD_ROSE
    }

    private fun CropCollectionType.toTrackedSource(): TrackedSource? = when (this) {
        CropCollectionType.BREAKING_CROPS -> TrackedSource.CROPS
        CropCollectionType.MOOSHROOM_COW -> TrackedSource.MOOSHROOM_COW
        CropCollectionType.GREENHOUSE,
        CropCollectionType.CROP_FEVER,
        CropCollectionType.PEST_BASE,
        CropCollectionType.PEST_RNG,
        CropCollectionType.UNKNOWN,
        -> null
    }
}
