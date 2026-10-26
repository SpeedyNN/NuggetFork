package at.hannibal2.skyhanni.api

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ItemAddManager
import at.hannibal2.skyhanni.events.CollectionUpdateEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.ItemAddEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.features.dungeon.DungeonApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.InventoryDetector
import at.hannibal2.skyhanni.utils.ItemUtils.cleanName
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLoreComponent
import at.hannibal2.skyhanni.utils.ItemUtils.itemNameWithoutColor
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NeuItems.getItemStackOrNull
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.RegexUtils.anyMatches
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.addOrPut
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object CollectionApi {
    private val patternGroup = RepoPattern.group("data.collection.api")

    /**
     * REGEX-TEST: Farming Collections
     * REGEX-TEST: Carrot Collection
     */
    private val collectionInventoryPattern by patternGroup.pattern(
        "collections",
        ".+ Collections?",
    )

    /**
     * WRAPPED-REGEX-TEST: "                          43,649/50k"
     * REGEX-TEST: Total collected: 277,252
     */
    private val counterPattern by patternGroup.pattern(
        "counter.new",
        ".* (?<amount>[\\d,]*)(?:/.*)?",
    )

    /**
     * REGEX-TEST: Total collected: 261,390
     * REGEX-TEST: Total Collected: 2,012,418
     */
    private val singleCounterPattern by patternGroup.pattern(
        "singlecounter.new",
        "Total [c|C]ollected: (?<amount>.*)",
    )

    /**
     * REGEX-TEST: [MVP+] oxsss: 1.9M
     * REGEX-TEST: [VIP] oxsss: 0
     * REGEX-TEST: oxsss: 0
     */
    val playerCounterPattern by patternGroup.pattern(
        "playercounter.new",
        "(?:\\[[^]]+(?:\\+)?] |)(?<name>[^§]{2,16}): (?<amount>.+)",
    )

    /**
     * REGEX-TEST: Progress to Raw Chicken IX: 25.7§6%
     * REGEX-TEST: Total Collected: 1,917,287
     */
    val collectionNotMaxedPattern by patternGroup.pattern(
        "collections.notmaxed.new",
        "Progress to .+|Total Collected: .+",
    )

    /**
     * REGEX-TEST: §7Progress to Nether Wart I: §e46§6%
     */
    private val collectionTier0Pattern by patternGroup.pattern(
        "tierzero",
        "§7Progress to .* I: .*",
    )

    val collectionInventory = InventoryDetector { collectionInventoryPattern }
    val collectionValue = mutableMapOf<NeuInternalName, Long>()
    val collectionByName = mutableMapOf<String, Long>()

    // TODO repo
    private val incorrectCollectionNames = mapOf(
        "MUSHROOM_COLLECTION".toInternalName() to "RED_MUSHROOM".toInternalName(),
    )

    @HandleEvent
    fun onProfileJoin(event: ProfileJoinEvent) {
        collectionValue.clear()
        collectionByName.clear()
    }

    fun saveCollection(internalName: NeuInternalName, displayName: String, counter: Long) {
        val raw = internalName.asString().uppercase()
        if (internalName != NeuInternalName.NONE && raw.isNotEmpty()) {
            collectionValue[internalName] = counter
            val withDash = raw.replace(':', '-').toInternalName()
            collectionValue[withDash] = counter
            val withColon = raw.replace('-', ':').toInternalName()
            collectionValue[withColon] = counter
        }

        val cleanName = displayName.trim().lowercase().removeSuffix(" collection").trim()
        if (cleanName.isNotEmpty()) {
            collectionByName[cleanName] = counter
            collectionByName[cleanName.replace(" ", "_")] = counter
            collectionByName[cleanName.replace("_", " ")] = counter
            val upperName = cleanName.uppercase().replace(" ", "_").toInternalName()
            collectionValue[upperName] = counter
        }
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        val inventoryName = event.inventoryName
        if (!collectionInventory.isInside()) return

        if (inventoryName.endsWith("n")) {
            val stack = event.inventoryItems[4] ?: return
            val lore = stack.getLoreComponent()

            singleCounterPattern.firstMatcher(lore.map { it.string }) {
                val counter = group("amount").formatLong()
                val internalName = stack.getInternalName().getCorrectedName()
                val menuName = inventoryName.removeSuffix(" Collection").trim()
                saveCollection(internalName, menuName, counter)
            }

            CollectionUpdateEvent.post()
        }

        if (inventoryName.endsWith("s") && !DungeonApi.bossCollectionsInventoryPattern.matches(event.inventoryName)) {
            for ((_, stack) in event.inventoryItems) {
                val name = stack.cleanName
                if ("Collections" in name) continue

                val lore = stack.getLoreComponent()
                if (lore.none { it.string.contains("Click to view!") }) continue

                val internalName = stack.getInternalName().getCorrectedName()

                val isCoop = playerCounterPattern.anyMatches(lore.map { it.string })
                val isNotMaxed = collectionNotMaxedPattern.anyMatches(lore.map { it.string })

                if (!isCoop || isNotMaxed) {
                    val found = singleCounterPattern.firstMatcher(lore.map { it.string }) {
                        val counter = group("amount").formatLong()
                        saveCollection(internalName, name, counter)
                        true
                    } ?: false
                    if (!found) {
                        counterPattern.firstMatcher(lore.map { it.string }) {
                            val counter = group("amount").formatLong()
                            saveCollection(internalName, name, counter)
                        }
                    }
                } else {
                    val coopIndex = lore.indexOfFirst { it.string == "Co-op Contributions:" }
                    if (coopIndex == -1) continue

                    var totalCollected = 0L
                    lore.drop(coopIndex).forEach { line ->
                        if (line.string.isBlank()) return@forEach

                        playerCounterPattern.matchMatcher(line) {
                            totalCollected += group("amount").formatLong()
                        }
                    }

                    saveCollection(internalName, name, totalCollected)
                }
            }
            CollectionUpdateEvent.post()
        }
    }

    @HandleEvent
    fun onItemAdd(event: ItemAddEvent) {
        if (event.source == ItemAddManager.Source.COMMAND) return
        val internalName = event.internalName
        val amount = NeuItems.getPrimitiveMultiplier(internalName).amount
        if (amount > 1) return

        // TODO add support for replenish (higher collection than actual items in inv)
        if (internalName.getItemStackOrNull() == null) {
            ChatUtils.debug("CollectionAPI.addFromInventory: item is null for '$internalName'")
            return
        }
        val current = getCollectionCounter(internalName) ?: 0L
        saveCollection(internalName, internalName.itemNameWithoutColor, current + event.amount.toLong())
        CollectionUpdateEvent.post()
    }

    fun isCollectionTier0(lore: List<String>) = lore.any { collectionTier0Pattern.matches(it) }
    fun NeuInternalName.getCorrectedName() = incorrectCollectionNames.getOrElse(this) { this }

    fun getCollectionCounter(internalName: NeuInternalName): Long? {
        collectionValue[internalName]?.let { return it }
        val raw = internalName.asString().uppercase()
        val withDash = raw.replace(':', '-').toInternalName()
        collectionValue[withDash]?.let { return it }
        val withColon = raw.replace('-', ':').toInternalName()
        collectionValue[withColon]?.let { return it }
        val nameKey = raw.lowercase().replace("_", " ")
        collectionByName[nameKey]?.let { return it }
        collectionByName[raw.lowercase()]?.let { return it }
        return null
    }

    fun getCollectionCounterByName(name: String): Long? {
        val clean = name.trim().lowercase().removeSuffix(" collection").trim()
        collectionByName[clean]?.let { return it }
        collectionByName[clean.replace(" ", "_")]?.let { return it }
        collectionByName[clean.replace("_", " ")]?.let { return it }
        val upperInternal = clean.uppercase().replace(" ", "_").toInternalName()
        return getCollectionCounter(upperInternal)
    }
}
