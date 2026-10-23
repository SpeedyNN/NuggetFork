package at.hannibal2.skyhanni.config.features.mining.nucleus

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import at.hannibal2.skyhanni.features.mining.crystalhollows.BalBossTracker
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class BalBossTrackerConfig {
    @Expose
    @ConfigOption(name = "Enabled", desc = "Enable the Bal Boss & Pet Tracker.")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @Expose
    @ConfigOption(name = "Only in Crystal Hollows", desc = "Only display the tracker when inside the Crystal Hollows.")
    @ConfigEditorBoolean
    var onlyInCrystalHollows: Boolean = true

    @Expose
    @ConfigOption(name = "Only in Khazad-dûm", desc = "Only display the tracker when inside Khazad-dûm / Magma Fields.")
    @ConfigEditorBoolean
    var onlyInKhazadDum: Boolean = false

    @Expose
    @ConfigOption(name = "Show Session Hits", desc = "Display total registered hits on Bal for this session.")
    @ConfigEditorBoolean
    var showSessionHits: Boolean = true

    @Expose
    @ConfigOption(name = "Show Hits Since Last Pet", desc = "Display registered hits and cumulative probability since last Bal Pet drop.")
    @ConfigEditorBoolean
    var showHitsSinceLastPet: Boolean = true

    @Expose
    @ConfigOption(name = "Show Bosses Since Last Pet", desc = "Display defeated Bal bosses since last Bal Pet drop.")
    @ConfigEditorBoolean
    var showBossesSinceLastPet: Boolean = true

    @Expose
    @ConfigOption(name = "Show Pets Dropped", desc = "Display the total number of Bal pets dropped.")
    @ConfigEditorBoolean
    var showPetsDropped: Boolean = true

    @Expose
    @ConfigOption(name = "Show Drop Odds & Stats", desc = "Display current per-hit drop chance and relevant stats (Magic Find & Pet Luck).")
    @ConfigEditorBoolean
    var showDropOdds: Boolean = true

    @ConfigOption(name = "Reset Session Hits", desc = "Reset the session hit counter to 0.")
    @ConfigEditorButton(buttonText = "Reset")
    val resetSession: Runnable = Runnable { BalBossTracker.resetSessionHits() }

    @ConfigOption(name = "Reset All Bal Stats", desc = "Reset all Bal tracking statistics (hits since pet, bosses, pets dropped).")
    @ConfigEditorButton(buttonText = "Reset All")
    val resetAll: Runnable = Runnable { BalBossTracker.resetAllStats() }

    @Expose
    @ConfigLink(owner = BalBossTrackerConfig::class, field = "enabled")
    val position: Position = Position(20, 140)
}

