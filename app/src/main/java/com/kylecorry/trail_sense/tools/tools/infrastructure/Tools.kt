package com.kylecorry.trail_sense.tools.tools.infrastructure

import android.content.Context
import com.kylecorry.andromeda.core.capitalizeWords
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.luna.hooks.Hooks
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.settings.SettingsToolRegistration
import com.kylecorry.trail_sense.shared.quickactions.QuickActionOpenTool
import com.kylecorry.trail_sense.shared.views.QuickActionNone
import com.kylecorry.trail_sense.tools.astronomy.AstronomyToolRegistration
import com.kylecorry.trail_sense.tools.augmented_reality.AugmentedRealityToolRegistration
import com.kylecorry.trail_sense.tools.battery.BatteryToolRegistration
import com.kylecorry.trail_sense.tools.beacons.BeaconsToolRegistration
import com.kylecorry.trail_sense.tools.cliffheight.CliffHeightToolRegistration
import com.kylecorry.trail_sense.tools.climate.ClimateToolRegistration
import com.kylecorry.trail_sense.tools.clinometer.ClinometerToolRegistration
import com.kylecorry.trail_sense.tools.clock.ClockToolRegistration
import com.kylecorry.trail_sense.tools.clouds.CloudsToolRegistration
import com.kylecorry.trail_sense.tools.comms.messaging.LocalMessagingToolRegistration
import com.kylecorry.trail_sense.tools.convert.ConvertToolRegistration
import com.kylecorry.trail_sense.tools.diagnostics.DiagnosticsToolRegistration
import com.kylecorry.trail_sense.tools.experimentation.ExperimentationToolRegistration
import com.kylecorry.trail_sense.tools.flashlight.FlashlightToolRegistration
import com.kylecorry.trail_sense.tools.guide.UserGuideToolRegistration
import com.kylecorry.trail_sense.tools.level.BubbleLevelToolRegistration
import com.kylecorry.trail_sense.tools.light.LightMeterToolRegistration
import com.kylecorry.trail_sense.tools.lightning.LightningStrikeDistanceToolRegistration
import com.kylecorry.trail_sense.tools.maps.PhotoMapsToolRegistration
import com.kylecorry.trail_sense.tools.metaldetector.MetalDetectorToolRegistration
import com.kylecorry.trail_sense.tools.mirror.MirrorCameraToolRegistration
import com.kylecorry.trail_sense.tools.navigation.NavigationToolRegistration
import com.kylecorry.trail_sense.tools.notes.NotesToolRegistration
import com.kylecorry.trail_sense.tools.packs.PackingListsToolRegistration
import com.kylecorry.trail_sense.tools.paths.PathsToolRegistration
import com.kylecorry.trail_sense.tools.pedometer.PedometerToolRegistration
import com.kylecorry.trail_sense.tools.qr.QRCodeScannerToolRegistration
import com.kylecorry.trail_sense.tools.ruler.RulerToolRegistration
import com.kylecorry.trail_sense.tools.sensors.SensorsToolRegistration
import com.kylecorry.trail_sense.tools.solarpanel.SolarPanelAlignerToolRegistration
import com.kylecorry.trail_sense.tools.temperature_estimation.TemperatureEstimateToolRegistration
import com.kylecorry.trail_sense.tools.tides.TidesToolRegistration
import com.kylecorry.trail_sense.tools.tools.ui.sort.AlphabeticalToolSort
import com.kylecorry.trail_sense.tools.triangulate.TriangulateLocationToolRegistration
import com.kylecorry.trail_sense.tools.turn_back.TurnBackToolRegistration
import com.kylecorry.trail_sense.tools.waterpurification.WaterBoilTimerToolRegistration
import com.kylecorry.trail_sense.tools.weather.WeatherToolRegistration
import com.kylecorry.trail_sense.tools.whistle.WhistleToolRegistration
import com.kylecorry.trail_sense.tools.whitenoise.WhiteNoiseToolRegistration

object Tools {

    private val hooks = Hooks()
    private val registry = listOf(
        FlashlightToolRegistration,
        WhistleToolRegistration,
        RulerToolRegistration,
        PedometerToolRegistration,
        CliffHeightToolRegistration,
        NavigationToolRegistration,
        BeaconsToolRegistration,
        PhotoMapsToolRegistration,
        PathsToolRegistration,
        TriangulateLocationToolRegistration,
        ClinometerToolRegistration,
        BubbleLevelToolRegistration,
        ClockToolRegistration,
        AstronomyToolRegistration,
        WaterBoilTimerToolRegistration,
        TidesToolRegistration,
        BatteryToolRegistration,
        SolarPanelAlignerToolRegistration,
        LightMeterToolRegistration,
        WeatherToolRegistration,
        ClimateToolRegistration,
        TemperatureEstimateToolRegistration,
        CloudsToolRegistration,
        LightningStrikeDistanceToolRegistration,
        AugmentedRealityToolRegistration,
        ConvertToolRegistration,
        PackingListsToolRegistration,
        MetalDetectorToolRegistration,
        WhiteNoiseToolRegistration,
        NotesToolRegistration,
        QRCodeScannerToolRegistration,
        SensorsToolRegistration,
        DiagnosticsToolRegistration,
        SettingsToolRegistration,
        UserGuideToolRegistration,
        ExperimentationToolRegistration,
        MirrorCameraToolRegistration,
        TurnBackToolRegistration,
        LocalMessagingToolRegistration
    )

    fun isToolAvailable(context: Context, toolId: Long): Boolean {
        return getTool(context, toolId) != null
    }

    fun getTool(context: Context, toolId: Long): Tool? {
        return getTools(context).firstOrNull { it.id == toolId }
    }

    fun getTools(context: Context, availableOnly: Boolean = true): List<Tool> {
        val tools = hooks.memo("tools", Resources.getLocale(context).language) {
            registry.map { it.getTool(context) }
        }


        return tools.filter { !availableOnly || it.isAvailable(context) }
    }

    fun getQuickActions(context: Context): List<ToolQuickAction> {
        val none = ToolQuickAction(
            QUICK_ACTION_NONE,
            context.getString(R.string.none).capitalizeWords()
        ) { button, fragment ->
            QuickActionNone(button, fragment)
        }

        val tools = getTools(context)

        val sortedTools = AlphabeticalToolSort().sort(tools).flatMap { it.tools }

        val quickActions = tools
            .flatMap { it.quickActions }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .map { it.copy(name = it.name.capitalizeWords()) }

        val toolActions = sortedTools.map {
            ToolQuickAction(
                it.id.toInt() + TOOL_QUICK_ACTION_OFFSET, // Avoid overlap
                context.getString(R.string.tool_quick_action_name, it.name)
            ) { button, fragment -> QuickActionOpenTool(button, fragment, it.navAction, it.icon) }
        }

        return listOf(none) + quickActions + toolActions
    }

    const val TOOL_QUICK_ACTION_OFFSET = 1000

    // Tool IDs
    const val FLASHLIGHT = 1L
    const val WHISTLE = 2L
    const val RULER = 3L
    const val PEDOMETER = 4L
    const val CLIFF_HEIGHT = 5L
    const val NAVIGATION = 6L
    const val BEACONS = 7L
    const val PHOTO_MAPS = 8L
    const val PATHS = 9L
    const val TRIANGULATE_LOCATION = 10L
    const val CLINOMETER = 11L
    const val BUBBLE_LEVEL = 12L
    const val CLOCK = 13L
    const val ASTRONOMY = 14L
    const val WATER_BOIL_TIMER = 15L
    const val TIDES = 16L
    const val BATTERY = 17L
    const val SOLAR_PANEL_ALIGNER = 18L
    const val LIGHT_METER = 19L
    const val WEATHER = 20L
    const val CLIMATE = 21L
    const val TEMPERATURE_ESTIMATION = 22L
    const val CLOUDS = 23L
    const val LIGHTNING_STRIKE_DISTANCE = 24L
    const val AUGMENTED_REALITY = 25L
    const val CONVERT = 26L
    const val PACKING_LISTS = 27L
    const val METAL_DETECTOR = 28L
    const val WHITE_NOISE = 29L
    const val NOTES = 30L
    const val QR_CODE_SCANNER = 31L
    const val SENSORS = 32L
    const val DIAGNOSTICS = 33L
    const val SETTINGS = 34L
    const val USER_GUIDE = 35L
    const val EXPERIMENTATION = 36L
    const val MIRROR_CAMERA = 37L
    const val TURN_BACK = 38L
    const val LOCAL_MESSAGING = 39L

    // Quick Action IDs
    const val QUICK_ACTION_NONE = -1
    const val QUICK_ACTION_FLASHLIGHT = 1
    const val QUICK_ACTION_RULER = 5
    const val QUICK_ACTION_WHISTLE = 8
    const val QUICK_ACTION_WHITE_NOISE = 9
    const val QUICK_ACTION_LOW_POWER_MODE = 10
    const val QUICK_ACTION_SUNSET_ALERT = 13
    const val QUICK_ACTION_NIGHT_MODE = 14
    const val QUICK_ACTION_BACKTRACK = 15
    const val QUICK_ACTION_WEATHER_MONITOR = 16
    const val QUICK_ACTION_PEDOMETER = 17
    const val QUICK_ACTION_SCREEN_FLASHLIGHT = 18
    const val QUICK_ACTION_CREATE_NOTE = 19
    const val QUICK_ACTION_CREATE_BEACON = 20
    const val QUICK_ACTION_SCAN_CLOUD = 21
    const val QUICK_ACTION_OPEN_PHOTO_MAP = 22
}
