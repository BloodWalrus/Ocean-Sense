package com.kylecorry.trail_sense.weather.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.kylecorry.andromeda.alerts.dialog
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.topics.asLiveData
import com.kylecorry.andromeda.core.topics.generic.asLiveData
import com.kylecorry.andromeda.core.topics.generic.replay
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.sense.Sensors
import com.kylecorry.sol.science.meteorology.Meteorology
import com.kylecorry.sol.science.meteorology.WeatherFront
import com.kylecorry.sol.science.meteorology.clouds.CloudGenus
import com.kylecorry.sol.units.*
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.ActivityWeatherBinding
import com.kylecorry.trail_sense.quickactions.WeatherQuickActionBinder
import com.kylecorry.trail_sense.shared.*
import com.kylecorry.trail_sense.shared.CustomUiUtils.setCompoundDrawables
import com.kylecorry.trail_sense.shared.alerts.ResettableLoadingIndicator
import com.kylecorry.trail_sense.shared.alerts.SnackbarLoadingIndicator
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.shared.extensions.*
import com.kylecorry.trail_sense.shared.permissions.RequestRemoveBatteryRestrictionCommand
import com.kylecorry.trail_sense.weather.domain.isHigh
import com.kylecorry.trail_sense.weather.domain.isLow
import com.kylecorry.trail_sense.weather.infrastructure.CurrentWeather
import com.kylecorry.trail_sense.weather.infrastructure.WeatherLogger
import com.kylecorry.trail_sense.weather.infrastructure.WeatherObservation
import com.kylecorry.trail_sense.weather.infrastructure.clouds.CloudDetailsService
import com.kylecorry.trail_sense.weather.infrastructure.commands.ChangeWeatherFrequencyCommand
import com.kylecorry.trail_sense.weather.infrastructure.subsystem.WeatherSubsystem
import com.kylecorry.trail_sense.weather.ui.clouds.CloudDetailsModal
import java.time.Duration
import java.time.Instant

class WeatherFragment : BoundFragment<ActivityWeatherBinding>() {

    private var useSeaLevelPressure = false
    private var units = PressureUnits.Hpa

    private val prefs by lazy { UserPreferences(requireContext()) }
    private val temperatureUnits by lazy { prefs.temperatureUnits }

    private lateinit var chart: PressureChart

    private val formatService by lazy { FormatService(requireContext()) }

    private var history: List<WeatherObservation> = listOf()
    private var rawHistory: List<Reading<Pressure>> = listOf()
    private val mapper = WeatherListItemMapper()

    private val weatherSubsystem by lazy { WeatherSubsystem.getInstance(requireContext()) }
    private var weather: CurrentWeather? = null
    private val loadingIndicator by lazy {
        ResettableLoadingIndicator(
            SnackbarLoadingIndicator(
                this,
                binding.weatherPlayBar,
                getString(R.string.updating_weather)
            )
        )
    }

    private val logger by lazy {
        WeatherLogger(
            requireContext(),
            Duration.ofSeconds(30),
            Duration.ofMillis(500),
            loadingIndicator
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        WeatherQuickActionBinder(
            this,
            binding,
            prefs.weather
        ).bind()

        chart = PressureChart(binding.chart) { timeAgo, pressure ->
            if (timeAgo == null || pressure == null) {
                binding.pressureMarker.isInvisible = true
            } else {
                val formatted = formatService.formatPressure(
                    Pressure(pressure, units),
                    Units.getDecimalPlaces(units)
                )
                binding.pressureMarker.text = getString(
                    R.string.pressure_reading_time_ago,
                    formatted,
                    formatService.formatDuration(timeAgo, false)
                )
                binding.pressureMarker.isVisible = true
            }
        }

        weatherSubsystem.weatherChanged.asLiveData().observe(viewLifecycleOwner) {
            updateWeather()
        }

        weatherSubsystem.weatherMonitorState.replay()
            .asLiveData().observe(viewLifecycleOwner) {
                updateStatusBar()
            }

        weatherSubsystem.weatherMonitorFrequency.replay()
            .asLiveData().observe(viewLifecycleOwner) {
                updateStatusBar()
            }


        binding.weatherPlayBar.setOnSubtitleClickListener {
            ChangeWeatherFrequencyCommand(requireContext()) { onUpdate() }.execute()
        }

        binding.weatherPlayBar.setOnPlayButtonClickListener {
            when (weatherSubsystem.weatherMonitorState.getOrNull()) {
                FeatureState.Unavailable -> toast(getString(R.string.weather_monitoring_disabled))
                FeatureState.On -> weatherSubsystem.disableMonitor()
                FeatureState.Off -> {
                    weatherSubsystem.enableMonitor()
                    RequestRemoveBatteryRestrictionCommand(requireContext()).execute()
                }
                null -> {}
            }
        }
    }

    private fun updateStatusBar() {
        binding.weatherPlayBar.setState(
            weatherSubsystem.weatherMonitorState.getOrNull() ?: FeatureState.Off,
            weatherSubsystem.weatherMonitorFrequency.getOrNull()
        )
    }

    override fun onResume() {
        super.onResume()
        loadingIndicator.reset()
        logger.start()
        useSeaLevelPressure = prefs.weather.useSeaLevelPressure
        units = prefs.pressureUnits

        updateWeather()
    }

    override fun onPause() {
        super.onPause()
        logger.stop()
        loadingIndicator.hide()
    }

    private fun updateList() {
        if (!isBound) return
        val weather = weather ?: return
        val observation = weather.observation ?: return

        val pressure = formatService.formatPressure(
            observation.pressure.convertTo(units),
            Units.getDecimalPlaces(units)
        )

        val tendency = getString(
            R.string.pressure_tendency_format_2, formatService.formatPressure(
                Pressure.hpa(weather.pressureTendency.amount).convertTo(units),
                Units.getDecimalPlaces(units) + 1
            )
        )
        val tendencyIcon =
            PressureCharacteristicImageMapper().getImageResource(weather.pressureTendency.characteristic)


        val color = Resources.androidTextColorSecondary(requireContext())

        // TODO: Extract these to fields like astronomy
        val items = listOfNotNull(
            WeatherListItem(
                1,
                R.drawable.ic_barometer,
                getString(R.string.pressure),
                pressure,
                color
            ),
            WeatherListItem(
                2,
                tendencyIcon,
                getString(R.string.pressure_tendency),
                tendency,
                color
            ),
            getPressureSystemListItem(observation.pressure),
            getWeatherFrontListItem(weather.prediction.front),
            getTemperatureListItem(observation.temperature),
            getHumidityListItem(observation.humidity),
            getCloudListItem(weather.clouds)
        )

        binding.weatherList.setItems(items, mapper)
    }

    private fun getWeatherFrontListItem(front: WeatherFront?): WeatherListItem? {
        front ?: return null

        val frontName: String
        val icon: Int
        when (front) {
            WeatherFront.Warm -> {
                frontName = getString(R.string.weather_warm_front)
                icon = R.drawable.ic_warm_weather_front
            }
            WeatherFront.Cold -> {
                frontName = getString(R.string.weather_cold_front)
                icon = R.drawable.ic_cold_weather_front
            }
        }

        return WeatherListItem(
            7,
            icon,
            getString(R.string.weather_front),
            frontName
        )
    }

    private fun getHumidityListItem(humidity: Float?): WeatherListItem? {
        return if (Sensors.hasHygrometer(requireContext()) && humidity != null) {
            val value = formatService.formatPercentage(humidity)
            WeatherListItem(
                4,
                R.drawable.ic_category_water,
                getString(R.string.humidity),
                value,
                AppColor.Blue.color
            ) { showHumidityChart() }
        } else {
            null
        }
    }

    private fun getTemperatureListItem(temperature: Temperature): WeatherListItem {
        val value = formatService.formatTemperature(
            temperature.convertTo(temperatureUnits)
        )
        val color = when {
            temperature.temperature <= 15f -> AppColor.Blue.color
            temperature.temperature >= 25f -> AppColor.Red.color
            else -> Resources.androidTextColorSecondary(requireContext())
        }
        return WeatherListItem(
            3,
            R.drawable.thermometer,
            getString(R.string.temperature),
            value,
            color
        ) { showTemperatureChart() }
    }

    private fun getPressureSystemListItem(pressure: Pressure): WeatherListItem? {
        val name: String
        val description: String
        val icon: Int
        if (pressure.isHigh()) {
            name = getString(R.string.high_pressure)
            description = getString(R.string.high_pressure_system_description)
            icon = R.drawable.ic_high_pressure_system
        } else if (pressure.isLow()) {
            name = getString(R.string.low_pressure)
            description = getString(R.string.low_pressure_system_description)
            icon = R.drawable.ic_low_pressure_system
        } else {
            return null
        }

        return WeatherListItem(
            6,
            icon,
            getString(R.string.pressure_system),
            name
        ) {
            dialog(name, description, cancelText = null)
        }
    }

    private fun getCloudListItem(cloud: Reading<CloudGenus?>?): WeatherListItem? {
        cloud ?: return null
        val cloudDetailsService = CloudDetailsService(requireContext())
        val name = cloudDetailsService.getCloudName(cloud.value)
        return WeatherListItem(
            5,
            R.drawable.cloudy,
            getString(R.string.clouds),
            name
        ) {
            CloudDetailsModal(requireContext()).show(cloud.value)
        }
    }

    private fun updateWeather() {
        inBackground {
            onIO {
                history = weatherSubsystem.getHistory().filter {
                    Duration.between(it.time, Instant.now()) <= prefs.weather.pressureHistory
                }

                loadRawWeatherReadings()

                weather = weatherSubsystem.getWeather()
            }
            onMain {
                update()
            }
        }
    }

    private suspend fun loadRawWeatherReadings() {
        if (isDebug()) {
            if (prefs.weather.useSeaLevelPressure) {
                val showMeanShiftedReadings = prefs.weather.showMeanShiftedReadings
                val raw = weatherSubsystem.getRawHistory().filter {
                    Duration.between(
                        it.time,
                        Instant.now()
                    ) <= prefs.weather.pressureHistory
                }
                val averageAltitude =
                    if (!showMeanShiftedReadings) {
                        0f
                    } else {
                        raw.map { it.value.altitude }.average().toFloat()
                    }
                rawHistory = raw.map {
                    if (showMeanShiftedReadings) {
                        val seaLevel = Meteorology.getSeaLevelPressure(
                            Pressure.hpa(it.value.pressure),
                            Distance.meters(averageAltitude)
                        )
                        Reading(seaLevel, it.time)
                    } else {
                        Reading(it.value.seaLevel(false), it.time)
                    }
                }
            }
        }
    }

    private fun update() {
        if (!isBound) return
        displayPressureChart(history, rawHistory)
        updateList()
        inBackground {
            updateForecast()
        }
    }

    private fun displayPressureChart(
        readings: List<WeatherObservation>,
        rawReadings: List<Reading<Pressure>>
    ) {
        val displayReadings = readings.map { it.pressureReading() }
        if (displayReadings.isNotEmpty()) {
            chart.plot(displayReadings, rawReadings.ifEmpty { null })
        }
    }

    private suspend fun updateForecast() {
        if (!isBound) return
        val weather = weather ?: return
        val prediction = weather.prediction
        onMain {
            binding.weatherTitle.title.text = formatService.formatWeather(prediction.primaryHourly)
            binding.weatherTitle.title.setOnClickListener {
                if (prediction.hourly.isNotEmpty()) {
                    val conditions = prediction.hourly.joinToString("\n") {
                        formatService.formatWeather(it)
                    }
                    dialog(getString(R.string.weather), conditions, cancelText = null)
                }
            }
            binding.weatherTitle.title.setCompoundDrawables(
                size = Resources.dp(requireContext(), 24f).toInt(),
                left = formatService.getWeatherImage(prediction.primaryHourly)
            )
            val speed = formatService.formatWeatherSpeed(weather.prediction.hourlyArrival)
                .lowercase()
            val then = getString(
                R.string.then_weather,
                formatService.formatWeather(prediction.primaryDaily).lowercase()
            )
            binding.weatherTitle.subtitle.text = if (speed.isNotEmpty() && prediction.primaryDaily == null) {
                speed
            } else if (speed.isNotEmpty()) {
                "$speed, $then"
            } else {
                then
            }
        }
    }

    private fun showHumidityChart() {
        val readings =
            history.filter { it.humidity != null }.map { Reading(it.humidity!!, it.time) }
        if (readings.size < 2) {
            return
        }
        val readingDuration = Duration.between(readings.first().time, Instant.now())
        CustomUiUtils.showLineChart(
            this, getString(
                R.string.humidity_history,
                formatService.formatDuration(readingDuration, true)
            )
        ) {
            val chart = HumidityChart(it)
            chart.plot(readings)
        }
    }

    private fun showTemperatureChart() {
        val readings = history.map {
            Reading(it.temperature.convertTo(temperatureUnits).temperature, it.time)
        }
        if (readings.size < 2) {
            return
        }
        val readingDuration = Duration.between(readings.first().time, Instant.now())
        CustomUiUtils.showLineChart(
            this, getString(
                R.string.temperature_history,
                formatService.formatDuration(readingDuration, true)
            )
        ) {
            val chart = TemperatureChart(it)
            chart.plot(readings)
        }
    }


    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): ActivityWeatherBinding {
        return ActivityWeatherBinding.inflate(layoutInflater, container, false)
    }

}
