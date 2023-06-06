package com.kylecorry.trail_sense.astronomy.ui.items

import android.content.Context
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.coroutines.onDefault
import com.kylecorry.andromeda.core.math.DecimalFormatter
import com.kylecorry.ceres.list.ListItem
import com.kylecorry.ceres.list.ResourceListIcon
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.ui.format.EclipseFormatter
import java.time.LocalDate

class SolarEclipseListItemProducer(context: Context) : BaseAstroListItemProducer(context) {

    override suspend fun getListItem(
        date: LocalDate,
        location: Coordinate
    ): ListItem? = onDefault {
        val eclipse = astronomyService.getSolarEclipse(location, date) ?: return@onDefault null

        // Advanced
        val peakAltitude = astronomyService.getSunAltitude(location, eclipse.peak)
        val peakAzimuth = astronomyService.getSunAzimuth(location, eclipse.peak)

        listItem(
            5,
            context.getString(R.string.solar_eclipse),
            EclipseFormatter.type(context, eclipse),
            timeRange(eclipse.start, eclipse.end, date),
            ResourceListIcon(
                if (eclipse.isTotal) {
                    R.drawable.ic_total_solar_eclipse
                } else {
                    R.drawable.ic_partial_solar_eclipse
                }
            )
        ) {
            Alerts.dialog(
                context,
                context.getString(R.string.solar_eclipse),
                fields(
                    // Times
                    context.getString(R.string.times) to timeRange(
                        eclipse.start,
                        eclipse.end,
                        date
                    ),
                    context.getString(R.string.duration) to formatter.formatDuration(
                        eclipse.duration,
                        false
                    ),

                    // Visibility
                    context.getString(R.string.obscuration) to EclipseFormatter.type(
                        context,
                        eclipse
                    ),
                    context.getString(R.string.magnitude) to DecimalFormatter.format(
                        eclipse.magnitude,
                        2
                    ),

                    // Peak
                    context.getString(R.string.peak_time) to time(eclipse.peak, date),
                    context.getString(R.string.astronomy_altitude_peak) to formatter.formatDegrees(
                        peakAltitude
                    ),
                    context.getString(R.string.astronomy_direction_peak) to formatter.formatDirection(
                        peakAzimuth.direction
                    ),

                    ),
                cancelText = null
            )
        }
    }


}