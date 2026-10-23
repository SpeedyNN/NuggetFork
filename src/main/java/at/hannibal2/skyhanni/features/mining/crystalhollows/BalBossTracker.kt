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
import at.hannibal2.skyhanni.events.MobEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.entity.EntityClickEvent
import at.hannibal2.skyhanni.events.entity.EntityDeathEvent
import at.hannibal2.skyhanni.events.entity.EntityHurtEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketReceivedEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketSentEvent
import at.hannibal2.skyhanni.events.player.ClickAction
import at.hannibal2.skyhanni.events.player.PlayerInteractionEvent
import at.hannibal2.skyhanni.features.combat.damageindicator.DamageIndicatorManager
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.AllEntitiesGetter
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.cleanName
import at.hannibal2.skyhanni.utils.EntityUtils.getArmorInventory
import at.hannibal2.skyhanni.utils.EntityUtils.hasMaxHealth
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
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.HorizontalAlignment
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat.isLocalPlayer
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.container.VerticalContainerRenderable.Companion.vertical
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import java.awt.Color
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

    const val BAL_MAX_HP = 200
    var balCurrentHp: Int = BAL_MAX_HP
        private set

    private var currentBossHits: Int = 0
    private var pendingPlayerHits: Int = 0
    private var lastPlayerVolleySize: Int = 1
    private var lastOtherPlayerVolleySize: Int = 1
    private var lastPlayerAttackTime = SimpleTimeMark.farPast()
    private var lastPlayerShotTime = SimpleTimeMark.farPast()
    private var lastOtherPlayerAttackTime = SimpleTimeMark.farPast()
    private var lastBalDamageTime = SimpleTimeMark.farPast()
    private var lastRegisteredHitTime = SimpleTimeMark.farPast()
    private var lastBalDefeatedTime = SimpleTimeMark.farPast()
    private var lastBalSeenTime = SimpleTimeMark.farPast()

    private var cachedBalId: Int? = null

    private var display = listOf<Renderable>()
    private var bossBarDisplay = listOf<Renderable>()

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

    private val balHalfwayPattern by patternGroup.pattern(
        "bal.halfway",
        ".*(?:Halfway there! The boss is starting to become weaker|Halfway there!).*",
    )

    private val balNearlyTherePattern by patternGroup.pattern(
        "bal.nearlythere",
        ".*(?:Nearly there! The boss can't last much longer|Nearly there!).*",
    )

    init {
        // Main Tracker Overlay
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

        // Dedicated Boss Health Bar
        RenderDisplayHelper(
            condition = {
                if (bossBarDisplay.isEmpty()) {
                    updateBossBar()
                }
                isBossBarDisplayEnabled()
            },
            outsideInventory = true,
        ) {
            config.bossBarPosition.renderRenderables(
                bossBarDisplay,
                posLabel = "Bal Boss Health Bar",
            )
        }
    }

    private fun isDisplayEnabled(): Boolean {
        if (!config.enabled) return false
        if (config.onlyInKhazadDum && !inKhazadDum()) return false
        if (config.onlyInCrystalHollows && !IslandType.CRYSTAL_HOLLOWS.isInIsland()) return false
        return true
    }

    private fun isBossBarDisplayEnabled(): Boolean {
        if (!config.enabled || !config.showBossHealthBar) return false
        if (config.onlyInKhazadDum && !inKhazadDum()) return false
        if (config.onlyInCrystalHollows && !IslandType.CRYSTAL_HOLLOWS.isInIsland()) return false
        if (findBalEntity() != null) return true
        if (lastBalDefeatedTime.passedSince() < 8.seconds && inKhazadDum()) return true
        return inKhazadDum() && lastBalSeenTime.passedSince() < 25.seconds
    }

    private fun inKhazadDum(): Boolean {
        if (!IslandType.CRYSTAL_HOLLOWS.isInIsland()) return false
        val area = HypixelData.skyBlockArea.orEmpty()
        return area.contains("Khazad", ignoreCase = true) || area.contains("Magma Fields", ignoreCase = true)
    }

    fun isBal(entity: Entity): Boolean {
        if (cachedBalId != null && entity.id == cachedBalId) return true
        if (entity is MagmaCube) {
            val width = entity.boundingBox.maxX - entity.boundingBox.minX
            val height = entity.boundingBox.maxY - entity.boundingBox.minY
            if (entity.size >= 12 || width >= 3.5 || height >= 3.5) return true

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
        cachedBalId?.let { id ->
            val entity = MinecraftCompat.localWorldOrNull?.getEntity(id) as? MagmaCube
            if (entity != null && !entity.isRemoved && isBal(entity)) return entity
        }
        val bal = EntityUtils.getEntities<MagmaCube>()
            .filter { isBal(it) && !it.isRemoved }
            .maxByOrNull { it.size }

        if (bal != null) {
            if (cachedBalId != bal.id) {
                cachedBalId = bal.id
                // New Bal entity found! Reset HP to 200
                balCurrentHp = BAL_MAX_HP
                currentBossHits = 0
                pendingPlayerHits = 0
                update()
                updateBossBar()
            }
        }
        return bal
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

    private fun handlePlayerAttackOnBal() {
        lastPlayerVolleySize = 1
        pendingPlayerHits = (pendingPlayerHits + 1).coerceAtMost(20)
        lastPlayerAttackTime = SimpleTimeMark.now()
    }

    private fun handlePlayerShotAtBal() {
        val arrows = getOurArrowsPerShot()
        lastPlayerVolleySize = arrows
        pendingPlayerHits = (pendingPlayerHits + arrows).coerceAtMost(20)
        lastPlayerShotTime = SimpleTimeMark.now()
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
            handlePlayerAttackOnBal()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onPacketSent(event: PacketSentEvent) {
        if (!config.enabled) return
        when (val packet = event.packet) {
            is ServerboundInteractPacket -> {
                val entity = MinecraftCompat.localWorldOrNull?.getEntity(packet.entityId) ?: return
                if (isBal(entity) || isBalArmorStand(entity) || isTargetNearBal(entity)) {
                    handlePlayerAttackOnBal()
                }
            }
            is ServerboundAttackPacket -> {
                val entity = MinecraftCompat.localWorldOrNull?.getEntity(packet.entityId) ?: return
                if (isBal(entity) || isBalArmorStand(entity) || isTargetNearBal(entity)) {
                    handlePlayerAttackOnBal()
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
            is ClientboundRemoveEntitiesPacket -> {
                cachedBalId?.let { id ->
                    if (packet.entityIds.contains(id)) {
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

            // Check if Bal is dead or dying (death animation, deathTime > 0, shrinking, or removed)
            if (bal.deathTime > 0 || bal.isDeadOrDying || bal.isRemoved) {
                handleBalDefeated()
                return
            }

            // Track other players attacking in Khazad-dûm
            val otherPlayers = EntityUtils.getPlayerEntities().filter { !it.isLocalPlayer && it.distanceTo(bal) <= 35.0 }
            val attackingPlayers = otherPlayers.filter { it.swinging || it.attackAnim > 0 }
            if (attackingPlayers.isNotEmpty()) {
                lastOtherPlayerAttackTime = SimpleTimeMark.now()
                lastOtherPlayerVolleySize = attackingPlayers.maxOf { getPlayerArrowsPerShot(it) }
            }
            if (EntityUtils.getEntities<Arrow>().any { !it.owner.isLocalPlayer && it.distanceTo(bal) <= 30.0 }) {
                lastOtherPlayerAttackTime = SimpleTimeMark.now()
            }
        } else {
            // Bal entity is not currently in the world
            if (cachedBalId != null) {
                // If Bal was previously cached and is now gone/poofed, handle defeat!
                handleBalDefeated()
            }
        }

        // Expire stale pending hits if player hasn't attacked recently
        if (lastPlayerAttackTime.passedSince() > 800.milliseconds &&
            lastPlayerShotTime.passedSince() > 800.milliseconds
        ) {
            pendingPlayerHits = 0
        }
    }

    @OptIn(AllEntitiesGetter::class)
    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onEntityHurt(event: EntityHurtEvent<*>) {
        if (!config.enabled) return
        val bal = event.entity
        if (!isBal(bal)) return

        val timeSinceLastDamage = lastBalDamageTime.passedSince()
        lastBalDamageTime = SimpleTimeMark.now()

        // Expire stale pending hits (arrows that missed)
        if (lastPlayerAttackTime.passedSince() > 800.milliseconds &&
            lastPlayerShotTime.passedSince() > 800.milliseconds
        ) {
            pendingPlayerHits = 0
        }

        val arrowsNearBal = EntityUtils.getEntities<Arrow>().count { it.distanceTo(bal) <= 12.0 }
        val hasOurArrow = EntityUtils.getEntities<Arrow>().any { it.owner.isLocalPlayer && it.distanceTo(bal) <= 35.0 }

        // 1. Is this our local player's attack?
        val isOurHit = pendingPlayerHits > 0 ||
            lastPlayerAttackTime.passedSince() <= 400.milliseconds ||
            (hasOurArrow && lastPlayerShotTime.passedSince() <= 1200.milliseconds)

        // 2. Did another player in Khazad-dûm attack Bal?
        val otherPlayerAttacked = checkOtherPlayerAttack(bal)

        // 3. Rapid damage cannot be DoT (Venomous/Fire Aspect ticks at >= 500ms)
        val isRapidDamage = timeSinceLastDamage <= 400.milliseconds

        val isRealDamage = isOurHit || otherPlayerAttacked || isRapidDamage

        // Decrement Bal's HP for every valid damage packet
        if (isRealDamage) {
            val volleyDamage = when {
                isOurHit -> minOf(pendingPlayerHits, lastPlayerVolleySize).coerceAtLeast(1)
                otherPlayerAttacked -> lastOtherPlayerVolleySize.coerceAtLeast(1)
                else -> 1
            }
            val damage = maxOf(volleyDamage, minOf(5, arrowsNearBal)).coerceAtLeast(1)

            if (balCurrentHp > 0) {
                balCurrentHp = (balCurrentHp - damage).coerceAtLeast(0)
            }
            if (balCurrentHp == 0) {
                handleBalDefeated()
            }
            update()
            updateBossBar()
        }

        // Register hit for our local player
        if (isOurHit) {
            val hitsToRegister = if (pendingPlayerHits > 0) {
                val count = minOf(pendingPlayerHits, lastPlayerVolleySize).coerceAtLeast(1)
                pendingPlayerHits -= count
                count
            } else {
                1
            }
            repeat(hitsToRegister) {
                registerHit()
            }
        }
    }

    @OptIn(AllEntitiesGetter::class)
    private fun checkOtherPlayerAttack(bal: Entity): Boolean {
        if (lastOtherPlayerAttackTime.passedSince() <= 600.milliseconds) return true

        // Find other players in Khazad-dûm near Bal
        val otherPlayers = EntityUtils.getPlayerEntities().filter { !it.isLocalPlayer && it.distanceTo(bal) <= 35.0 }
        if (otherPlayers.isEmpty()) return false

        val attackingPlayers = otherPlayers.filter { it.swinging || it.attackAnim > 0 }
        if (attackingPlayers.isNotEmpty()) {
            lastOtherPlayerAttackTime = SimpleTimeMark.now()
            lastOtherPlayerVolleySize = attackingPlayers.maxOf { getPlayerArrowsPerShot(it) }
            return true
        }

        // Check if another player's arrow is flying near Bal
        if (EntityUtils.getEntities<Arrow>().any { !it.owner.isLocalPlayer && it.distanceTo(bal) <= 30.0 }) {
            lastOtherPlayerAttackTime = SimpleTimeMark.now()
            return true
        }

        // Check if a damage indicator armor stand spawned at Bal recently
        if (EntityUtils.getEntities<ArmorStand>().any {
            it.distanceTo(bal) <= 10.0 && it.tickCount <= 10 && DamageIndicatorManager.isDamageSplash(it)
        }) {
            lastOtherPlayerAttackTime = SimpleTimeMark.now()
            return true
        }

        return false
    }

    private fun registerHit() {
        sessionHits++
        hitsSinceLastPet++
        currentBossHits++
        totalHits++
        update()
    }

    private fun handleBalDefeated() {
        balCurrentHp = 0
        pendingPlayerHits = 0
        cachedBalId = null
        update()
        updateBossBar()

        if (lastBalDefeatedTime.passedSince() < 10.seconds) return
        lastBalDefeatedTime = SimpleTimeMark.now()

        val participated = currentBossHits > 0 ||
            lastPlayerAttackTime.passedSince() < 60.seconds ||
            lastPlayerShotTime.passedSince() < 60.seconds ||
            (inKhazadDum() && (sessionHits > 0 || currentBossHits > 0) && lastBalSeenTime.passedSince() < 60.seconds)

        cachedBalId = null

        if (participated) {
            bossesSinceLastPet++
            totalBosses++
            currentBossHits = 0
            update()
            updateBossBar()
            ChatUtils.chat("§a[SkyHanni] §6Bal defeated! §7Bosses since last pet: §e$bossesSinceLastPet")
        } else {
            currentBossHits = 0
            update()
            updateBossBar()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onEntityDeath(event: EntityDeathEvent<*>) {
        if (!config.enabled) return
        val entity = event.entity
        if (entity.id == cachedBalId || (entity is MagmaCube && isBal(entity))) {
            handleBalDefeated()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onMobDespawn(event: MobEvent.DeSpawn) {
        if (!config.enabled) return
        if (event.mob.baseEntity.id == cachedBalId && isBal(event.mob.baseEntity)) {
            handleBalDefeated()
        }
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

            ChatUtils.chat("§e[SkyHanni] §6§lRARE DROP! §r$rarityName Bal Pet§r§e! §7(Hits: §e${previousHits.addSeparators()}§7, Bosses: §e$previousBosses§7)")
            update()
            return
        }

        val cleanMsg = event.message.removeColor().trim()

        // 50% Milestone (100 HP / hits)
        if (balHalfwayPattern.matches(cleanMsg) ||
            cleanMsg.contains("Halfway there", ignoreCase = true) ||
            cleanMsg.contains("starting to become weaker", ignoreCase = true) ||
            (cleanMsg.contains("Bal", ignoreCase = true) && cleanMsg.contains("50%", ignoreCase = true))
        ) {
            if (balCurrentHp > 100) {
                balCurrentHp = 100
                update()
                updateBossBar()
                ChatUtils.debug("BalBossTracker: Synced Bal HP to 100 (50%) from chat")
            }
        }

        // 25% Milestone (50 HP / hits)
        if (balNearlyTherePattern.matches(cleanMsg) ||
            cleanMsg.contains("Nearly there", ignoreCase = true) ||
            cleanMsg.contains("can't last much longer", ignoreCase = true) ||
            cleanMsg.contains("can't take much more", ignoreCase = true) ||
            (cleanMsg.contains("Bal", ignoreCase = true) && cleanMsg.contains("25%", ignoreCase = true))
        ) {
            if (balCurrentHp > 50) {
                balCurrentHp = 50
                update()
                updateBossBar()
                ChatUtils.debug("BalBossTracker: Synced Bal HP to 50 (25%) from chat")
            }
        }

        // Defeat / Death Messages
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
            (cleanMsg.contains("has spawned", ignoreCase = true) || cleanMsg.contains("spawned!", ignoreCase = true) || cleanMsg.contains("awakened", ignoreCase = true))
        ) {
            balCurrentHp = BAL_MAX_HP
            currentBossHits = 0
            pendingPlayerHits = 0
            update()
            updateBossBar()
            ChatUtils.debug("BalBossTracker: Bal HP reset to 200 from spawn chat message")
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!config.enabled) return
        update()
        updateBossBar()
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
        ChatUtils.chat("§a[SkyHanni] Reset Bal session hits!")
    }

    fun resetBossHp(hp: Int = BAL_MAX_HP) {
        balCurrentHp = hp.coerceIn(0, BAL_MAX_HP)
        update()
        updateBossBar()
        ChatUtils.chat("§a[SkyHanni] Set Bal health to §e$balCurrentHp§7/§e$BAL_MAX_HP§a.")
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
        balCurrentHp = BAL_MAX_HP
        update()
        updateBossBar()
        ChatUtils.chat("§a[SkyHanni] Reset all Bal Boss Tracker statistics!")
    }

    private fun updateBossBar() {
        val hp = balCurrentHp.coerceIn(0, BAL_MAX_HP)
        val percent = hp.toDouble() / BAL_MAX_HP
        val colorCode = when {
            hp == 0 -> "§8"
            percent > 0.5 -> "§a"
            percent > 0.2 -> "§e"
            else -> "§c"
        }
        val percentInt = (percent * 100).roundToInt()

        val titleText = if (hp == 0) {
            "§c§lBal §7- §c§lDEFEATED §8(0/200 ❤)"
        } else {
            "§c§lBal §7- $colorCode$hp§7/§a200 ❤ §8($colorCode$percentInt%§8)"
        }
        val title = Renderable.text(titleText)
        val bar = Renderable.progressBar(
            percent = percent,
            width = 160,
            height = 6,
            startColor = Color(255, 60, 60),
            endColor = Color(60, 255, 60),
        )

        bossBarDisplay = listOf(
            Renderable.vertical(
                listOf(title, bar),
                spacing = 2,
                horizontalAlign = HorizontalAlignment.CENTER,
            ),
        )
    }

    private fun update() {
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
            add(Renderable.text("§6§lBal Boss Tracker"))

            if (config.showBossHealthInTracker) {
                val hp = balCurrentHp.coerceIn(0, BAL_MAX_HP)
                val percent = (hp.toDouble() / BAL_MAX_HP * 100).roundToInt()
                val hpColor = when {
                    hp == 0 -> "§8"
                    percent > 50 -> "§a"
                    percent > 20 -> "§e"
                    else -> "§c"
                }
                val hpText = if (hp == 0) "§cDEFEATED §8(0/200)" else "$hpColor$hp§7/§a200 §8($hpColor$percent%§8)"
                add(Renderable.text("§7Bal HP: $hpText"))
            }

            if (config.showSessionHits) {
                add(Renderable.text("§7Session Hits: §e${sessionHits.addSeparators()}"))
            }

            if (config.showHitsSinceLastPet) {
                val hitLine = Renderable.text(
                    "§7Hits Since Pet: §e${hitsSinceLastPet.addSeparators()} §8(§b$probTotalFormatted Any§8 · §6$probLegFormatted Leg§8)"
                )
                val hoverTooltip = listOf(
                    "§6§lBal Pet Drop Odds & Cumulative Chances",
                    "§7Hits since last pet: §e${hitsSinceLastPet.addSeparators()}",
                    "",
                    "§aCumulative Chances (so far):",
                    "§7• Any Pet: §b$probTotalFormatted",
                    "§7• Legendary: §6$probLegFormatted",
                    "§7• Epic: §5$probEpicFormatted",
                    "",
                    "§aPer-Hit Chances (with stats):",
                    "§7• Any Pet: §d1 in ${oneInNTotal.addSeparators()} §8(${"%.4f".format(Locale.US, effectiveTotalP * 100)}%)",
                    "§7• Legendary: §61 in ${oneInNLeg.addSeparators()} §8(${"%.4f".format(Locale.US, effectiveLegP * 100)}%)",
                    "§7• Epic: §51 in ${oneInNEpic.addSeparators()} §8(${"%.4f".format(Locale.US, effectiveEpicP * 100)}%)",
                    "",
                    "§aBase Rates:",
                    "§7• Epic: §50.0126% §8(1 in 7,937)",
                    "§7• Legendary: §60.0054% §8(1 in 18,519)",
                    "§7• Any Pet: §d0.0180% §8(1 in 5,556)",
                    "",
                    "§aStats Applied:",
                    "§7• Magic Find: §b+${mf.roundToInt()}",
                    "§7• Pet Luck: §d+${pl.roundToInt()}",
                    "§7• Stat Multiplier: §e${"%.2f".format(Locale.US, statMultiplier)}x",
                )
                add(Renderable.hoverTips(hitLine, hoverTooltip))
            }

            if (config.showRarityBreakdown) {
                add(Renderable.text("  §8├ §5Epic: §e1/${oneInNEpic.addSeparators()} §8(§5$probEpicFormatted§8)"))
                add(Renderable.text("  §8└ §6Leg: §e1/${oneInNLeg.addSeparators()} §8(§6$probLegFormatted§8)"))
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
            literal("hp") {
                arg("amount", BrigadierArguments.integer(0, BAL_MAX_HP)) { amountArg ->
                    callback {
                        val amount = getArg(amountArg)
                        resetBossHp(amount)
                    }
                }
                simpleCallback { resetBossHp() }
            }
            literal("bosses") {
                arg("amount", BrigadierArguments.integer(0)) { amountArg ->
                    callback {
                        val amount = getArg(amountArg)
                        bossesSinceLastPet = amount
                        totalBosses = maxOf(totalBosses, amount)
                        update()
                        ChatUtils.chat("§a[SkyHanni] Set Bal bosses since last pet to §e$amount§a.")
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
                        ChatUtils.chat("§a[SkyHanni] Set Bal hits since last pet to §e${amount.addSeparators()}§a.")
                    }
                }
            }
            simpleCallback {
                resetSessionHits()
                ChatUtils.clickableChat(
                    "§a[SkyHanni] Reset Bal session hits! §7Click here to reset ALL Bal stats, or use §e/shresetbaltracker bosses <num>§7.",
                    onClick = { resetAllStats() },
                    hover = "Click to reset all Bal statistics",
                )
            }
        }
    }
}
