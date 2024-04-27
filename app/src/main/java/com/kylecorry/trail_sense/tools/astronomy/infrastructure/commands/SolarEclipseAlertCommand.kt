package com.kylecorry.trail_sense.tools.astronomy.infrastructure.commands

import android.content.Context
import android.util.Log
import com.kylecorry.andromeda.notify.Notify
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.main.NotificationChannels
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.commands.generic.Command
import com.kylecorry.trail_sense.shared.navigation.NavigationUtils
import com.kylecorry.trail_sense.tools.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.tools.astronomy.domain.Eclipse
import com.kylecorry.trail_sense.tools.astronomy.ui.format.EclipseFormatter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class SolarEclipseAlertCommand(private val context: Context) : Command<Coordinate> {
    override fun execute(location: Coordinate) {
        val prefs = UserPreferences(context)
        val shouldSend = prefs.astronomy.sendSolarEclipseAlerts

        if (!shouldSend) {
            return
        }

        val astronomyService = AstronomyService()
        val today = LocalDate.now()
        val todayEclipse = astronomyService.getSolarEclipse(location, today)
        val tomorrowEclipse =
            astronomyService.getSolarEclipse(location, today.plusDays(1))

        val eclipse = listOfNotNull(todayEclipse, tomorrowEclipse).firstOrNull {
            val timeUntilPeak = Duration.between(LocalDateTime.now(), it.peak)
            timeUntilPeak >= Duration.ZERO && timeUntilPeak <= Duration.ofDays(1)
        }

        if (eclipse == null) {
            Log.d(TAG, "No solar eclipse found for $today")
            return
        }

        val notification = Notify.status(
            context,
            AstronomyAlertCommand.NOTIFICATION_CHANNEL,
            context.getString(R.string.solar_eclipse),
            getEclipseDescription(context, eclipse),
            R.drawable.ic_astronomy,
            group = AstronomyAlertCommand.NOTIFICATION_GROUP_ASTRONOMY_ALERTS,
            intent = NavigationUtils.pendingIntent(context, R.id.action_astronomy),
            autoCancel = true
        )

        Notify.send(context, 273942, notification)
    }

    private fun getEclipseDescription(context: Context, eclipse: Eclipse): String {
        val formatService = FormatService.getInstance(context)

        val timeSpan = formatService.formatTimeSpan(
            eclipse.start,
            eclipse.end,
            true
        )

        val eclipseAmount = EclipseFormatter.type(context, eclipse)

        return "$timeSpan\n$eclipseAmount"
    }

    companion object {
        private const val TAG = "SolarEclipseAlertCommand"
    }

}