package com.kylecorry.trail_sense.tools.pedometer.tiles

import android.os.Build
import androidx.annotation.RequiresApi
import com.kylecorry.andromeda.core.topics.generic.ITopic
import com.kylecorry.andromeda.core.topics.generic.map
import com.kylecorry.andromeda.core.topics.generic.replay
import com.kylecorry.trail_sense.shared.DistanceUtils.toRelativeDistance
import com.kylecorry.trail_sense.shared.FeatureState
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.Units
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.tiles.TopicTile
import com.kylecorry.trail_sense.tools.pedometer.infrastructure.subsystem.PedometerSubsystem

@RequiresApi(Build.VERSION_CODES.N)
class PedometerTile : TopicTile() {

    private val pedometer by lazy { PedometerSubsystem.getInstance(this) }
    private val formatter by lazy { FormatService.getInstance(this) }
    private val prefs by lazy { UserPreferences(this) }

    override val stateTopic: ITopic<FeatureState>
        get() = pedometer.state.replay()

    override val subtitleTopic: ITopic<String>
        get() = pedometer.distance.map {
            val converted = it.convertTo(prefs.baseDistanceUnits).toRelativeDistance(prefs.useNauticalMiles)
            formatter.formatDistance(converted, Units.getDecimalPlaces(converted.units))
        }.replay()

    override fun stop() {
        pedometer.disable()
    }

    override fun start() {
        startForegroundService {
            pedometer.enable()
        }
    }

}