package at.hannibal2.skyhanni.config.features.inventory.sacks

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SackGoalConfig {

    @Expose
    @ConfigOption(name = "Enabled", desc = "Show a progress bar GUI tracking progress toward a sack item goal.")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @Expose
    @ConfigLink(owner = SackGoalConfig::class, field = "enabled")
    val position: Position = Position(10, 100)

    @Expose
    @ConfigOption(name = "Bar Width", desc = "Width of the sack goal progress bar.")
    @ConfigEditorSlider(minValue = 80f, maxValue = 300f, minStep = 5f)
    var barWidth: Int = 140

    @Expose
    @ConfigOption(name = "Bar Height", desc = "Height of the sack goal progress bar.")
    @ConfigEditorSlider(minValue = 4f, maxValue = 20f, minStep = 1f)
    var barHeight: Int = 7

    @Expose
    @ConfigOption(name = "Play Sound on Complete", desc = "Play a pling sound when your sack goal is reached.")
    @ConfigEditorBoolean
    var dingOnComplete: Boolean = true

    @Expose
    var trackedItemInternalName: String = ""

    @Expose
    var targetGoalAmount: Long = 0L
}

