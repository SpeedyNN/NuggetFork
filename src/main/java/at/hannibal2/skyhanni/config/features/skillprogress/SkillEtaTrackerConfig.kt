package at.hannibal2.skyhanni.config.features.skillprogress

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SkillEtaTrackerConfig {

    @Expose
    @ConfigOption(
        name = "Enabled",
        desc = "Show a live countdown GUI tracking ETA to reach your target skill level (/eta <skill> <level>)."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = true

    @Expose
    @ConfigLink(owner = SkillEtaTrackerConfig::class, field = "enabled")
    val position: Position = Position(10, 110)

    @Expose
    @ConfigOption(name = "Bar Width", desc = "Width of the skill ETA progress bar.")
    @ConfigEditorSlider(minValue = 80f, maxValue = 300f, minStep = 5f)
    var barWidth: Int = 140

    @Expose
    @ConfigOption(name = "Bar Height", desc = "Height of the skill ETA progress bar.")
    @ConfigEditorSlider(minValue = 4f, maxValue = 20f, minStep = 1f)
    var barHeight: Int = 7

    @Expose
    @ConfigOption(name = "Play Sound on Complete", desc = "Play a sound when you reach the target skill level.")
    @ConfigEditorBoolean
    var dingOnComplete: Boolean = true

    @Expose
    @ConfigOption(name = "Pause Timeout", desc = "Seconds of inactivity before the countdown timer pauses.")
    @ConfigEditorSlider(minValue = 2f, maxValue = 30f, minStep = 1f)
    var pauseTimeoutSeconds: Int = 5

    @Expose
    var trackedSkill: String = ""

    @Expose
    var targetLevel: Int = 0

    @Expose
    var startLevel: Int = 0

    @Expose
    var startTotalXp: Long = 0L
}

