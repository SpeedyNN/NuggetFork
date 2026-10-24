package at.hannibal2.skyhanni.features.mining.crystalhollows

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.model.SkyblockStat
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.entity.EntityClickEvent
import at.hannibal2.skyhanni.events.entity.EntityDeathEvent
import at.hannibal2.skyhanni.events.entity.EntityHurtEvent
import at.hannibal2.skyhanni.events.minecraft.WorldChangeEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketReceivedEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketSentEvent
import at.hannibal2.skyhanni.events.player.ClickAction
import at.hannibal2.skyhanni.events.player.PlayerInteractionEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.AllEntitiesGetter
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.cleanName
import at.hannibal2.skyhanni.utils.EntityUtils.getArmorInventory
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.cleanName as itemCleanName
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceTo
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.MobUtils.mob
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatPercentage
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RaycastUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.container.VerticalContainerRenderable.Companion.vertical
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import net.minecraft.world.entity.player.Player
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object BalBossTracker {

    private val config get() = SkyHanniMod.feature.mining.balBossTracker
    private val patternGroup = RepoPattern.group("mining.crystalhollows.baltracker")

    private val storage get() = ProfileStorageData.profileSpecific?.mining?.balBossTracker

    var hitsSinceLastPet: Long
        get() = storage?.hitsSinceLastPet ?: 0L
        set(value) {
            storage?.hitsSinceLastPet = value
        }

    var bossesSinceLastPet: Int
        get() = storage?.bossesSinceLastPet ?: 0
        set(value) {
            storage?.bossesSinceLastPet = value
        }

    var petsDropped: Int
        get() = storage?.petsDropped ?: 0
        set(value) {
            storage?.petsDropped = value
        }

    var epicPetsDropped: Int
        get() = storage?.epicPetsDropped ?: 0
        set(value) {
            storage?.epicPetsDropped = value
        }

    var legPetsDropped: Int
        get() = storage?.legPetsDropped ?: 0
        set(value) {
            storage?.legPetsDropped = value
        }

    var totalHits: Long
        get() = storage?.totalHits ?: 0L
        set(value) {
            storage?.totalHits = value
        }

    var totalBosses: Int
        get() = storage?.totalBosses ?: 0
        set(value) {
            storage?.totalBosses = value
        }

    var sessionHits: Long = 0L
        private set

    private var currentBossHits: Int = 0
    private var pendingMeleeHits: Int = 0
    private var pendingRangedHits: Int = 0
    private var lastMeleeAttackTime = SimpleTimeMark.farPast()
    private var lastShotTime = SimpleTimeMark.farPast()
    private var lastBalDefeatedTime = SimpleTimeMark.farPast()
    private var lastBalSeenTime = SimpleTimeMark.farPast()

    private var display = listOf<Renderable>()

    /**
     * Base Bal Pet drop rates from a registered hit:
     * - Epic: 0.0126% (1 in 7,936.5 hits)
     * - Legendary: 0.0054% (1 in 18,518.5 hits)
     * Combined base chance: 0.018% (1 in 5,555.55 hits)
     */
    const val BASE_EPIC_CHANCE = 0.000126
    const val BASE_LEG_CHANCE = 0.000054
    const val BASE_TOTAL_CHANCE = 0.00018

    @Suppress("MaxLineLength")
    private val balObtainedPattern by patternGroup.pattern(
        "bal.obtained",
        "(?:(?:§.)*\\[.*(?:§.)*\\+*(?:§.)*\\] )?(?<player>.*)§r§f §r§ehas obtained §r§a§r§7\\[Lvl 1\\] §r§(?<raritycolor>[65])Bal§r§e!",
    )

    init {
        RenderDisplayHelper(
            condition = {
                if (display.isEmpty()) {
                    update()
                }
                isDisplayEnabled()
            },
            outsideInventory = true,
        ) {
            config.position.renderRenderables(
                display,
                posLabel = "Bal Boss Tracker",
            )
        }
    }

    private fun isDisplayEnabled(): Boolean {
        if (!config.enabled) return false
        if (config.onlyInKhazadDum && !inKhazadDum()) return false
        if (config.onlyInCrystalHollows && !IslandType.CRYSTAL_HOLLOWS.isInIsland()) return false
        return true
    }

    private fun inKhazadDum(): Boolean {
        if (!IslandType.CRYSTAL_HOLLOWS.isInIsland()) return false
        val area = HypixelData.skyBlockArea.orEmpty()
        return area.contains("Khazad", ignoreCase = true) || area.contains("Magma Fields", ignoreCase = true)
    }

    fun isBal(entity: Entity): Boolean {
        if (entity is MagmaCube) {
            // Minion magma cubes are size 1 or 2; Bal is size 8 to 12
            if (entity.size >= 4) return true
            val width = entity.boundingBox.maxX - entity.boundingBox.minX
            val height = entity.boundingBox.maxY - entity.boundingBox.minY
            if (width >= 2.0 || height >= 2.0) return true

            if (hasNearbyBalArmorStand(entity)) return true

            val mobName = (entity as? LivingEntity)?.mob?.name
            if (mobName == "Bal" || entity.cleanName.contains("Bal", ignoreCase = true)) return true
        }
        return false
    }

    @OptIn(AllEntitiesGetter::class)
    private fun hasNearbyBalArmorStand(entity: Entity): Boolean {
        if (!IslandType.CRYSTAL_HOLLOWS.isInIsland()) return false
        return EntityUtils.getEntities<ArmorStand>().any { armorStand ->
            armorStand.distanceTo(entity) <= 8.0 && armorStand.cleanName.contains("Bal", ignoreCase = true)
        }
    }

    private fun isBalArmorStand(entity: Entity): Boolean {
        if (entity !is ArmorStand) return false
        if (entity.cleanName.contains("Bal", ignoreCase = true)) return true
        val bal = findBalEntity() ?: return false
        return entity.distanceTo(bal) <= 8.0
    }

    private fun isTargetNearBal(entity: Entity): Boolean {
        val bal = findBalEntity() ?: return false
        return entity.distanceTo(bal) <= 8.0
    }

    @OptIn(AllEntitiesGetter::class)
    fun findBalEntity(): MagmaCube? {
        if (!IslandType.CRYSTAL_HOLLOWS.isInIsland()) return null
        return EntityUtils.getEntities<MagmaCube>()
            .filter { !it.isRemoved && !it.isDeadOrDying && it.deathTime == 0 && isBal(it) }
            .maxByOrNull { it.size }
    }

    private fun isHoldingBow(): Boolean {
        val item = InventoryUtils.getItemInHand() ?: return false
        val internalName = item.getInternalNameOrNull()?.asString().orEmpty()
        val cleanName = item.itemCleanName
        return cleanName.contains("Bow", ignoreCase = true) ||
            cleanName.contains("Terminator", ignoreCase = true) ||
            internalName.contains("BOW") ||
            internalName.contains("TERMINATOR") ||
            internalName.contains("JUJU")
    }

    private fun getPlayerArrowsPerShot(player: Player): Int {
        val item = player.mainHandItem
        val name = item.hoverName.string
        val base = when {
            name.contains("Terminator", ignoreCase = true) -> 3
            name.contains("Runaan", ignoreCase = true) -> 3
            name.contains("Hurricane", ignoreCase = true) -> 5
            else -> 1
        }

        var terrorPieces = 0
        player.getArmorInventory()?.forEach { piece ->
            val clean = piece?.itemCleanName.orEmpty()
            val internal = piece?.getInternalNameOrNull()?.asString().orEmpty()
            if (clean.contains("Terror", ignoreCase = true) || internal.contains("TERROR")) {
                terrorPieces++
            }
        }
        val extraTerror = if (terrorPieces >= 2) 2 else 0
        return base + extraTerror
    }

    private fun getOurArrowsPerShot(): Int {
        val player = MinecraftCompat.localPlayerOrNull ?: return 1
        return getPlayerArrowsPerShot(player)
    }

    private fun isPlayerAimingAtBal(bal: Entity): Boolean {
        val eyePos = LocationUtils.playerEyeLocation()
        val balCenter = LorenzVec(
            (bal.boundingBox.minX + bal.boundingBox.maxX) / 2.0,
            (bal.boundingBox.minY + bal.boundingBox.maxY) / 2.0,
            (bal.boundingBox.minZ + bal.boundingBox.maxZ) / 2.0,
        )
        if (eyePos.distance(balCenter) > 75.0) return false

        val ray = RaycastUtils.createPlayerLookDirectionRay()
        val aabb = bal.boundingBox.inflate(3.0)
        if (RaycastUtils.intersectAABBWithRay(aabb, ray) != null) return true

        val distToRay = RaycastUtils.findDistanceToRay(ray, balCenter)
        return distToRay <= 10.0
    }

    private fun handlePlayerMeleeAttackOnBal() {
        if (lastMeleeAttackTime.passedSince() < 150.milliseconds) return
        lastMeleeAttackTime = SimpleTimeMark.now()
        pendingMeleeHits = 1
    }

    private fun handlePlayerShotAtBal() {
        if (lastShotTime.passedSince() < 150.milliseconds) return
        lastShotTime = SimpleTimeMark.now()
        pendingRangedHits = getOurArrowsPerShot()
    }

    private fun checkBowShotAtBal() {
        if (!isHoldingBow()) return
        val bal = findBalEntity()
        if (bal != null) {
            if (isPlayerAimingAtBal(bal)) {
                handlePlayerShotAtBal()
            }
        } else if (inKhazadDum() && lastBalSeenTime.passedSince() < 10.seconds) {
            handlePlayerShotAtBal()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onPlayerInteraction(event: PlayerInteractionEvent) {
        if (!config.enabled) return
        if (event.action == ClickAction.RIGHT_CLICK_AIR || event.action == ClickAction.RIGHT_CLICK_BLOCK) {
            checkBowShotAtBal()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onEntityClick(event: EntityClickEvent) {
        if (!config.enabled) return
        if (event.action != EntityClickEvent.ActionType.ATTACK) return

        val target = event.clickedEntity
        if (isBal(target) || isBalArmorStand(target) || isTargetNearBal(target)) {
            handlePlayerMeleeAttackOnBal()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onPacketSent(event: PacketSentEvent) {
        if (!config.enabled) return
        when (val packet = event.packet) {
            is ServerboundInteractPacket -> {
                val entity = MinecraftCompat.localWorldOrNull?.getEntity(packet.entityId) ?: return
                if (isBal(entity) || isBalArmorStand(entity) || isTargetNearBal(entity)) {
                    handlePlayerMeleeAttackOnBal()
                }
            }
            is ServerboundAttackPacket -> {
                val entity = MinecraftCompat.localWorldOrNull?.getEntity(packet.entityId) ?: return
                if (isBal(entity) || isBalArmorStand(entity) || isTargetNearBal(entity)) {
                    handlePlayerMeleeAttackOnBal()
                }
            }
            is ServerboundUseItemPacket -> {
                checkBowShotAtBal()
            }
            is ServerboundUseItemOnPacket -> {
                checkBowShotAtBal()
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (!config.enabled) return
        when (val packet = event.packet) {
            is ClientboundEntityEventPacket -> {
                val world = MinecraftCompat.localWorldOrNull ?: return
                val entity = packet.getEntity(world) ?: return
                if (isBal(entity)) {
                    // eventId 3 = death animation, 60 = poof
                    if (packet.eventId == 3.toByte() || packet.eventId == 60.toByte()) {
                        handleBalDefeated()
                    }
                }
            }
        }
    }

    @OptIn(AllEntitiesGetter::class)
    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onTick() {
        if (!config.enabled) return

        val bal = findBalEntity()
        if (bal != null) {
            lastBalSeenTime = SimpleTimeMark.now()
            if (bal.deathTime > 0 || bal.isDeadOrDying || bal.isRemoved) {
                handleBalDefeated()
                return
            }
        }

        // Expire stale pending hits
        if (pendingMeleeHits > 0 && lastMeleeAttackTime.passedSince() > 500.milliseconds) {
            pendingMeleeHits = 0
        }
        if (pendingRangedHits > 0 && lastShotTime.passedSince() > 1500.milliseconds) {
            pendingRangedHits = 0
        }
    }

    @OptIn(AllEntitiesGetter::class)
    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onEntityHurt(event: EntityHurtEvent<*>) {
        if (!config.enabled) return
        val bal = event.entity
        if (!isBal(bal)) return

        // 1. Local Player Melee Attack
        if (pendingMeleeHits > 0 && lastMeleeAttackTime.passedSince() <= 500.milliseconds) {
            pendingMeleeHits = 0
            registerHits(1)
            return
        }

        // 2. Local Player Ranged Attack
        if (pendingRangedHits > 0 && lastShotTime.passedSince() <= 1500.milliseconds) {
            val hits = pendingRangedHits
            pendingRangedHits = 0
            registerHits(hits)
            return
        }

        // Otherwise: Hurt caused by another player or passive DoT tick -> ignore for our tracker
    }

    private fun registerHits(count: Int) {
        sessionHits += count
        hitsSinceLastPet += count
        currentBossHits += count
        totalHits += count
        update()
    }

    private fun handleBalDefeated() {
        pendingMeleeHits = 0
        pendingRangedHits = 0

        if (lastBalDefeatedTime.passedSince() < 10.seconds) return
        lastBalDefeatedTime = SimpleTimeMark.now()

        val participated = currentBossHits > 0 ||
            lastMeleeAttackTime.passedSince() < 60.seconds ||
            lastShotTime.passedSince() < 60.seconds ||
            (inKhazadDum() && (sessionHits > 0 || currentBossHits > 0) && lastBalSeenTime.passedSince() < 60.seconds)

        if (participated) {
            bossesSinceLastPet++
            totalBosses++
            currentBossHits = 0
            update()
            runCatching {
                ChatUtils.chat("§a[SkyHanni] §6Bal defeated! §7Bosses since last pet: §e$bossesSinceLastPet")
            }
        } else {
            currentBossHits = 0
            update()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onEntityDeath(event: EntityDeathEvent<*>) {
        if (!config.enabled) return
        val entity = event.entity
        if (entity is MagmaCube && isBal(entity)) {
            handleBalDefeated()
        }
    }

    @HandleEvent
    fun onWorldChange(event: WorldChangeEvent) {
        pendingMeleeHits = 0
        pendingRangedHits = 0
        currentBossHits = 0
        update()
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        pendingMeleeHits = 0
        pendingRangedHits = 0
        currentBossHits = 0
        update()
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onChat(event: SkyHanniChatEvent.Allow) {
        if (!config.enabled) return

        balObtainedPattern.matchMatcher(event.message) {
            val player = group("player").removeColor().trim()
            if (!player.equals(PlayerUtils.getName(), ignoreCase = true)) return@matchMatcher

            val rarity = group("raritycolor")
            val rarityName = when (rarity) {
                "6" -> "§6Legendary"
                "5" -> "§5Epic"
                else -> "§dBal"
            }

            val previousHits = hitsSinceLastPet
            val previousBosses = bossesSinceLastPet

            petsDropped++
            if (rarity == "6") legPetsDropped++
            if (rarity == "5") epicPetsDropped++

            hitsSinceLastPet = 0L
            bossesSinceLastPet = 0

            runCatching {
                ChatUtils.chat("§e[SkyHanni] §6§lRARE DROP! §r$rarityName Bal Pet§r§e! §7(Hits: §e${previousHits.addSeparators()}§7, Bosses: §e$previousBosses§7)")
            }
            update()
            return
        }

        val cleanMsg = event.message.removeColor().trim()

        // Defeat Messages
        val isDefeatMsg = (cleanMsg.contains("Bal", ignoreCase = true) &&
            (cleanMsg.contains("defeated", ignoreCase = true) || cleanMsg.contains("slain", ignoreCase = true) || cleanMsg.contains("died", ignoreCase = true) || cleanMsg.contains("killed", ignoreCase = true))) ||
            cleanMsg.contains("BOSS SLAIN", ignoreCase = true) ||
            cleanMsg.contains("The boss has been defeated", ignoreCase = true) ||
            cleanMsg.contains("The boss was defeated", ignoreCase = true) ||
            cleanMsg.contains("The boss died", ignoreCase = true) ||
            (cleanMsg.contains("Topaz Crystal", ignoreCase = true) && (cleanMsg.contains("unlocked", ignoreCase = true) || cleanMsg.contains("claimed", ignoreCase = true)))

        if (isDefeatMsg) {
            handleBalDefeated()
        }

        // Spawn Messages
        if (cleanMsg.contains("Bal", ignoreCase = true) &&
            (cleanMsg.contains("has spawned", ignoreCase = true) ||
             cleanMsg.contains("spawned!", ignoreCase = true) ||
             cleanMsg.contains("has awakened", ignoreCase = true) ||
             cleanMsg.contains("awakened!", ignoreCase = true) ||
             cleanMsg.contains("has arisen", ignoreCase = true))
        ) {
            currentBossHits = 0
            pendingMeleeHits = 0
            pendingRangedHits = 0
            update()
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!config.enabled) return
        update()
    }

    fun calculateEffectiveChance(baseChance: Double = BASE_TOTAL_CHANCE): Double {
        val mf = SkyblockStat.MAGIC_FIND.lastKnownValue ?: 0.0
        val pl = SkyblockStat.PET_LUCK.lastKnownValue ?: 0.0
        val statMultiplier = 1.0 + (mf + pl) / 100.0
        return (baseChance * statMultiplier).coerceIn(0.0, 1.0)
    }

    fun calculateProbability(n: Long, p: Double): Double {
        if (n <= 0L || p <= 0.0) return 0.0
        if (p >= 1.0) return 1.0
        val notDrop = 1.0 - p
        return (1.0 - notDrop.pow(n.toDouble())).coerceIn(0.0, 1.0)
    }

    fun resetSessionHits() {
        sessionHits = 0L
        update()
        runCatching { ChatUtils.chat("§a[SkyHanni] Reset Bal session hits!") }
    }

    fun resetAllStats() {
        sessionHits = 0L
        hitsSinceLastPet = 0L
        bossesSinceLastPet = 0
        petsDropped = 0
        epicPetsDropped = 0
        legPetsDropped = 0
        totalHits = 0L
        totalBosses = 0
        currentBossHits = 0
        pendingMeleeHits = 0
        pendingRangedHits = 0
        update()
        runCatching { ChatUtils.chat("§a[SkyHanni] Reset all Bal Boss Tracker statistics!") }
    }

    private fun update() {
        if (runCatching { Minecraft.getInstance().font }.getOrNull() == null) return
        val mf = SkyblockStat.MAGIC_FIND.lastKnownValue ?: 0.0
        val pl = SkyblockStat.PET_LUCK.lastKnownValue ?: 0.0
        val statMultiplier = 1.0 + (mf + pl) / 100.0

        val effectiveTotalP = calculateEffectiveChance(BASE_TOTAL_CHANCE)
        val effectiveEpicP = calculateEffectiveChance(BASE_EPIC_CHANCE)
        val effectiveLegP = calculateEffectiveChance(BASE_LEG_CHANCE)

        val probTotal = calculateProbability(hitsSinceLastPet, effectiveTotalP)
        val probEpic = calculateProbability(hitsSinceLastPet, effectiveEpicP)
        val probLeg = calculateProbability(hitsSinceLastPet, effectiveLegP)

        val probTotalFormatted = probTotal.formatPercentage()
        val probEpicFormatted = probEpic.formatPercentage()
        val probLegFormatted = probLeg.formatPercentage()

        val oneInNTotal = if (effectiveTotalP > 0.0) (1.0 / effectiveTotalP).roundToLong() else 0L
        val oneInNEpic = if (effectiveEpicP > 0.0) (1.0 / effectiveEpicP).roundToLong() else 0L
        val oneInNLeg = if (effectiveLegP > 0.0) (1.0 / effectiveLegP).roundToLong() else 0L

        val lines = buildList {
            val titleText = Renderable.text("§6§lBal Boss Tracker §7(approx.)")
            add(
                Renderable.hoverTips(
                    titleText,
                    listOf(
                        "§6Bal Boss & Pet Tracker",
                        "§7Tracks approximate hits dealt to Bal.",
                        "§cNote: This counter is an approximation only",
                        "§cand can differ from actual server hits.",
                    ),
                ),
            )

            if (config.showSessionHits) {
                add(Renderable.text("§7Session Hits: §e${sessionHits.addSeparators()}"))
            }

            if (config.showHitsSinceLastPet) {
                val hitLine = Renderable.text(
                    "§7Hits Since Pet: §e${hitsSinceLastPet.addSeparators()} §8(§b$probTotalFormatted Any§8 · §6$probLegFormatted Leg§8)",
                )
                val hoverTooltip = listOf(
                    "§eCumulative Drop Probability (Hits: ${hitsSinceLastPet.addSeparators()})",
                    "§7Any Bal Pet: §b$probTotalFormatted §8(Base 0.018% / 1 in ${oneInNTotal.addSeparators()})",
                    "§5Epic Bal Pet: §d$probEpicFormatted §8(Base 0.0126% / 1 in ${oneInNEpic.addSeparators()})",
                    "§6Legendary Bal Pet: §e$probLegFormatted §8(Base 0.0054% / 1 in ${oneInNLeg.addSeparators()})",
                    "",
                    "§7Formula: §f1 - (1 - p)^n",
                    "§8(p = effective per-hit chance, n = hits since last pet)",
                    "",
                    "§c*Note: Hit count is an approximation due to",
                    "§cserver-side hit registration, spread & i-frames.",
                )
                add(Renderable.hoverTips(hitLine, hoverTooltip))
            }

            if (config.showRarityBreakdown) {
                add(Renderable.text(" §5Epic: §e$probEpicFormatted §8(1 in ${oneInNEpic.addSeparators()})"))
                add(Renderable.text(" §6Legendary: §e$probLegFormatted §8(1 in ${oneInNLeg.addSeparators()})"))
            }

            if (config.showBossesSinceLastPet) {
                add(Renderable.text("§7Bosses Since Pet: §e${bossesSinceLastPet.addSeparators()}"))
            }

            if (config.showPetsDropped) {
                val total = petsDropped
                val epic = epicPetsDropped
                val leg = legPetsDropped
                add(Renderable.text("§7Pets Dropped: §a${total.addSeparators()} §8(§5$epic§8/§6$leg§8)"))
            }

            if (config.showDropOdds) {
                val mfIcon = SkyblockStat.MAGIC_FIND.icon
                val plIcon = SkyblockStat.PET_LUCK.icon
                val chanceStr = if (oneInNTotal > 0) "1/${oneInNTotal.addSeparators()}" else "N/A"
                val multStr = "%.2f".format(Locale.US, statMultiplier)
                add(Renderable.text("§7Pet Chance: §d$chanceStr §8(§b$mfIcon${mf.roundToInt()} §d$plIcon${pl.roundToInt()} §8• §e${multStr}x§8)"))
            }
        }

        display = listOf(
            Renderable.vertical(
                lines,
                spacing = 2,
            ),
        )
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("shresetbaltracker") {
            description = "Resets or manages the Bal Boss Tracker stats"
            category = CommandCategory.USERS_RESET
            literal("session") {
                simpleCallback { resetSessionHits() }
            }
            literal("all") {
                simpleCallback { resetAllStats() }
            }
            literal("bosses") {
                arg("amount", BrigadierArguments.integer(0)) { amountArg ->
                    callback {
                        val amount = getArg(amountArg)
                        bossesSinceLastPet = amount
                        totalBosses = maxOf(totalBosses, amount)
                        update()
                        runCatching {
                            ChatUtils.chat("§a[SkyHanni] Set Bal bosses since last pet to §e$amount§a.")
                        }
                    }
                }
            }
            literal("hits") {
                arg("amount", BrigadierArguments.integer(0)) { amountArg ->
                    callback {
                        val amount = getArg(amountArg).toLong()
                        hitsSinceLastPet = amount
                        totalHits = maxOf(totalHits, amount)
                        update()
                        runCatching {
                            ChatUtils.chat("§a[SkyHanni] Set Bal hits since last pet to §e${amount.addSeparators()}§a.")
                        }
                    }
                }
            }
            simpleCallback {
                resetSessionHits()
                runCatching {
                    ChatUtils.clickableChat(
                        "§a[SkyHanni] Reset Bal session hits! §7Click here to reset ALL Bal stats, or use §e/shresetbaltracker bosses <num>§7.",
                        onClick = { resetAllStats() },
                        hover = "Click to reset all Bal statistics",
                    )
                }
            }
        }
    }
}
