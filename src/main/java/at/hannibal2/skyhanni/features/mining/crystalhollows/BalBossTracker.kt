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
import at.hannibal2.skyhanni.events.minecraft.packet.PacketSentEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.AllEntitiesGetter
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.cleanName
import at.hannibal2.skyhanni.utils.EntityUtils.hasMaxHealth
import at.hannibal2.skyhanni.utils.LocationUtils.distanceTo
import at.hannibal2.skyhanni.utils.MobUtils.mob
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatPercentage
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat.isLocalPlayer
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.container.VerticalContainerRenderable.Companion.vertical
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import net.minecraft.world.entity.projectile.arrow.Arrow
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
    private var lastPlayerAttackTime = SimpleTimeMark.farPast()
    private var lastBalDamageTime = SimpleTimeMark.farPast()
    private var lastRegisteredHitTime = SimpleTimeMark.farPast()
    private var lastBalDefeatedTime = SimpleTimeMark.farPast()
    private var lastBalSeenTime = SimpleTimeMark.farPast()

    private var cachedBalId: Int? = null

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
        if (cachedBalId != null && entity.id == cachedBalId) return true
        if (entity is MagmaCube) {
            val mobName = (entity as? LivingEntity)?.mob?.name
            if (mobName == "Bal") {
                cachedBalId = entity.id
                return true
            }
            if (entity.cleanName.contains("Bal", ignoreCase = true)) {
                cachedBalId = entity.id
                return true
            }
            if (inKhazadDum()) {
                cachedBalId = entity.id
                return true
            }
            if (hasNearbyBalArmorStand(entity)) {
                cachedBalId = entity.id
                return true
            }
            if (IslandType.CRYSTAL_HOLLOWS.isInIsland() && !entity.hasMaxHealth(35_000)) {
                cachedBalId = entity.id
                return true
            }
        }
        return false
    }

    @OptIn(AllEntitiesGetter::class)
    private fun hasNearbyBalArmorStand(entity: Entity): Boolean {
        if (!IslandType.CRYSTAL_HOLLOWS.isInIsland()) return false
        return EntityUtils.getEntities<ArmorStand>().any { armorStand ->
            armorStand.distanceTo(entity) <= 6.0 && armorStand.cleanName.contains("Bal", ignoreCase = true)
        }
    }

    private fun isBalArmorStand(entity: Entity): Boolean {
        if (entity !is ArmorStand) return false
        if (entity.cleanName.contains("Bal", ignoreCase = true)) return true
        val bal = findBalEntity() ?: return false
        return entity.distanceTo(bal) <= 6.0
    }

    private fun isTargetNearBal(entity: Entity): Boolean {
        val bal = findBalEntity() ?: return false
        return entity.distanceTo(bal) <= 6.0
    }

    @OptIn(AllEntitiesGetter::class)
    fun findBalEntity(): MagmaCube? {
        if (!IslandType.CRYSTAL_HOLLOWS.isInIsland()) return null
        cachedBalId?.let { id ->
            val entity = MinecraftCompat.localWorldOrNull?.getEntity(id) as? MagmaCube
            if (entity != null && !entity.isRemoved) return entity
        }
        val bal = EntityUtils.getEntities<MagmaCube>().firstOrNull { isBal(it) }
        if (bal != null) {
            cachedBalId = bal.id
        }
        return bal
    }

    private fun handlePlayerAttackOnBal() {
        lastPlayerAttackTime = SimpleTimeMark.now()
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
        val packetEntityId = when (val packet = event.packet) {
            is ServerboundInteractPacket -> packet.entityId
            is ServerboundAttackPacket -> packet.entityId
            else -> return
        }
        val entity = MinecraftCompat.localWorldOrNull?.getEntity(packetEntityId) ?: return
        if (isBal(entity) || isBalArmorStand(entity) || isTargetNearBal(entity)) {
            handlePlayerAttackOnBal()
        }
    }

    @OptIn(AllEntitiesGetter::class)
    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onTick() {
        if (!config.enabled) return

        val bal = findBalEntity()
        if (bal != null) {
            lastBalSeenTime = SimpleTimeMark.now()
            cachedBalId = bal.id

            if (bal.isDeadOrDying || bal.isRemoved) {
                handleBalDefeated()
                return
            }

            val balBox = bal.boundingBox.inflate(0.5)
            for (arrow in EntityUtils.getEntities<Arrow>()) {
                if (!arrow.owner.isLocalPlayer) continue
                if (arrow.isRemoved) continue

                val arrowBox = arrow.boundingBox
                if (arrowBox.intersects(balBox) || balBox.contains(arrow.position())) {
                    handlePlayerAttackOnBal()
                    break
                }
            }
        } else {
            if (cachedBalId != null && lastBalSeenTime.passedSince() >= 1.seconds) {
                handleBalDefeated()
            }
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onEntityHurt(event: EntityHurtEvent<*>) {
        if (!config.enabled) return
        if (!isBal(event.entity)) return

        lastBalDamageTime = SimpleTimeMark.now()

        if (lastPlayerAttackTime.passedSince() <= 1200.milliseconds) {
            if (lastRegisteredHitTime.passedSince() >= 380.milliseconds) {
                lastRegisteredHitTime = SimpleTimeMark.now()
                lastPlayerAttackTime = SimpleTimeMark.farPast()
                registerHit()
            }
        }
    }

    private fun registerHit() {
        sessionHits++
        hitsSinceLastPet++
        currentBossHits++
        totalHits++
        update()
    }

    private fun handleBalDefeated() {
        if (lastBalDefeatedTime.passedSince() < 15.seconds) return

        val participated = currentBossHits > 0 ||
            lastPlayerAttackTime.passedSince() < 60.seconds ||
            (inKhazadDum() && (sessionHits > 0 || currentBossHits > 0) && lastBalSeenTime.passedSince() < 60.seconds)

        if (participated) {
            lastBalDefeatedTime = SimpleTimeMark.now()
            bossesSinceLastPet++
            totalBosses++
            currentBossHits = 0
            cachedBalId = null
            update()
            ChatUtils.chat("§a[SkyHanni] §6Bal defeated! §7Bosses since last pet: §e$bossesSinceLastPet")
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onEntityDeath(event: EntityDeathEvent<*>) {
        if (!config.enabled) return
        if (isBal(event.entity) || event.entity.id == cachedBalId) {
            handleBalDefeated()
        }
    }

    @HandleEvent(onlyOnIsland = IslandType.CRYSTAL_HOLLOWS)
    fun onMobDespawn(event: MobEvent.DeSpawn) {
        if (!config.enabled) return
        if (event.mob.name == "Bal" || event.mob.baseEntity.id == cachedBalId) {
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
        if (cleanMsg.contains("Bal", ignoreCase = true) &&
            (cleanMsg.contains("defeated", ignoreCase = true) || cleanMsg.contains("slain", ignoreCase = true) || cleanMsg.contains("died", ignoreCase = true))
        ) {
            handleBalDefeated()
        }
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!config.enabled) return
        update()
    }

    fun calculateEffectiveChance(): Double {
        val mf = SkyblockStat.MAGIC_FIND.lastKnownValue ?: 0.0
        val pl = SkyblockStat.PET_LUCK.lastKnownValue ?: 0.0
        val statMultiplier = 1.0 + (mf + pl) / 100.0
        return (BASE_TOTAL_CHANCE * statMultiplier).coerceIn(0.0, 1.0)
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
        update()
        ChatUtils.chat("§a[SkyHanni] Reset all Bal Boss Tracker statistics!")
    }

    private fun update() {
        val mf = SkyblockStat.MAGIC_FIND.lastKnownValue ?: 0.0
        val pl = SkyblockStat.PET_LUCK.lastKnownValue ?: 0.0
        val effectiveP = calculateEffectiveChance()
        val prob = calculateProbability(hitsSinceLastPet, effectiveP)
        val probFormatted = prob.formatPercentage()
        val oneInN = if (effectiveP > 0.0) (1.0 / effectiveP).roundToLong() else 0L

        val lines = buildList {
            add(Renderable.text("§6§lBal Boss Tracker"))

            if (config.showSessionHits) {
                add(Renderable.text("§7Session Hits: §e${sessionHits.addSeparators()}"))
            }

            if (config.showHitsSinceLastPet) {
                add(Renderable.text("§7Hits Since Pet: §e${hitsSinceLastPet.addSeparators()} §8(§b$probFormatted§8)"))
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
                val chanceStr = if (oneInN > 0) "1/${oneInN.addSeparators()}" else "N/A"
                add(Renderable.text("§7Pet Chance: §d$chanceStr §8(§b$mfIcon${mf.roundToInt()} §d$plIcon${pl.roundToInt()}§8)"))
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
