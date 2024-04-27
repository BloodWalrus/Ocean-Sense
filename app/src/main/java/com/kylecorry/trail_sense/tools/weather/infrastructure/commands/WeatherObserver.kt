package com.kylecorry.trail_sense.tools.weather.infrastructure.commands

import android.content.Context
import com.kylecorry.andromeda.core.coroutines.onDefault
import com.kylecorry.andromeda.sense.location.IGPS
import com.kylecorry.andromeda.sense.readAll
import com.kylecorry.sol.math.SolMath.real
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.sensors.altimeter.AltimeterWrapper
import com.kylecorry.trail_sense.shared.sensors.thermometer.HistoricThermometer
import com.kylecorry.trail_sense.tools.weather.domain.RawWeatherObservation
import java.time.Duration
import java.time.Instant

internal class WeatherObserver(
    private val context: Context,
    private val timeout: Duration = Duration.ofSeconds(10)
) : IWeatherObserver {

    private val sensorService by lazy { SensorService(context) }
    private val altimeter by lazy { sensorService.getAltimeter(preferGPS = true) }
    private val altimeterAsGPS by lazy { sensorService.getGPSFromAltimeter(altimeter) }
    private val gps: IGPS by lazy {
        altimeterAsGPS ?: sensorService.getGPS()
    }
    private val barometer by lazy { sensorService.getBarometer() }
    private val thermometer by lazy { sensorService.getThermometer() }
    private val hygrometer by lazy { sensorService.getHygrometer() }

    override suspend fun getWeatherObservation(): Reading<RawWeatherObservation>? = onDefault {
        readAll(
            listOfNotNull(
                altimeter,
                if (altimeterAsGPS != gps) gps else null,
                barometer,
                thermometer,
                hygrometer
            ),
            timeout,
            forceStopOnCompletion = true
        )

        // Read the thermometer one last time - historic thermometer depends on updated location/elevation reading
        if (thermometer is HistoricThermometer) {
            readAll(listOf(thermometer), Duration.ofSeconds(1), forceStopOnCompletion = true)
        }

        if (barometer.pressure == 0f) {
            return@onDefault null
        }

        Reading(
            RawWeatherObservation(
                0,
                barometer.pressure.real(1013f),
                altimeter.altitude.real(),
                thermometer.temperature.real(16f),
                if (altimeter is AltimeterWrapper) (altimeter as AltimeterWrapper).altitudeAccuracy else null,
                hygrometer.humidity,
                gps.location
            ),
            Instant.now()
        )
    }

}