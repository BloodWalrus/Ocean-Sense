package com.kylecorry.trail_sense.tools.augmented_reality.ui.layers

import android.graphics.Color
import com.kylecorry.andromeda.canvas.ICanvasDrawer
import com.kylecorry.andromeda.core.ui.Colors.withAlpha
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.luna.coroutines.CoroutineQueueRunner
import com.kylecorry.sol.math.Range
import com.kylecorry.sol.science.astronomy.Astronomy
import com.kylecorry.sol.science.astronomy.RiseSetTransitTimes
import com.kylecorry.sol.science.astronomy.SunTimesMode
import com.kylecorry.sol.science.astronomy.moon.MoonPhase
import com.kylecorry.sol.time.Time
import com.kylecorry.sol.time.Time.atEndOfDay
import com.kylecorry.sol.time.Time.atStartOfDay
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.tools.navigation.ui.DrawerBitmapLoader
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.tools.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.tools.astronomy.ui.MoonPhaseImageMapper
import com.kylecorry.trail_sense.tools.augmented_reality.domain.position.SphericalARPoint
import com.kylecorry.trail_sense.tools.augmented_reality.ui.ARLine
import com.kylecorry.trail_sense.tools.augmented_reality.ui.ARMarker
import com.kylecorry.trail_sense.tools.augmented_reality.ui.AugmentedRealityView
import com.kylecorry.trail_sense.tools.augmented_reality.ui.CanvasBitmap
import com.kylecorry.trail_sense.tools.augmented_reality.ui.CanvasCircle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class ARAstronomyLayer(
    private val drawLines: Boolean,
    private val drawBelowHorizon: Boolean,
    private val onSunFocus: (time: ZonedDateTime) -> Boolean,
    private val onMoonFocus: (time: ZonedDateTime, phase: MoonPhase) -> Boolean
) : ARLayer {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val runner = CoroutineQueueRunner()

    private val lineAlpha = 30
    private val lineThickness = 1f

    private val lineLayer = ARLineLayer(renderWithPaths = true)
    private val sunLayer = ARMarkerLayer()
    private val currentSunLayer = ARMarkerLayer()

    private val moonLayer = ARMarkerLayer()
    private val currentMoonLayer = ARMarkerLayer()

    private val astro = AstronomyService()

    private var bitmapLoader: DrawerBitmapLoader? = null

    private var lastUpdateTime = 0L
    private var lastUpdateLocation = Coordinate.zero

    private val updateFrequency = Duration.ofMinutes(1).toMillis()
    private val updateDistance = 1000f

    var timeOverride: ZonedDateTime? = null
        set(value) {
            field = value
            lastUpdateTime = 0L
        }

    override suspend fun update(drawer: ICanvasDrawer, view: AugmentedRealityView) {
        val location = view.location
        val time = System.currentTimeMillis()

        if (location.distanceTo(lastUpdateLocation) > updateDistance || time - lastUpdateTime > updateFrequency) {
            lastUpdateTime = time
            lastUpdateLocation = location
            updatePositions(drawer, location, timeOverride ?: ZonedDateTime.now())
        }

        if (drawLines) {
            lineLayer.update(drawer, view)
        }
        sunLayer.update(drawer, view)
        moonLayer.update(drawer, view)
        currentSunLayer.update(drawer, view)
        currentMoonLayer.update(drawer, view)
    }

    // TODO: Eventually use a clip path to clip the lines to the horizon
    override fun draw(drawer: ICanvasDrawer, view: AugmentedRealityView) {
        if (drawLines) {
            lineLayer.draw(drawer, view)
        }
        // NOTE: The moon renders in front of the sun, but the focus/click order prioritizes the sun
        sunLayer.draw(drawer, view)
        moonLayer.draw(drawer, view)

        // Only draw the current position when the time is today (this will change in the future)
        if (timeOverride == null || timeOverride?.toLocalDate() == LocalDate.now()) {
            currentSunLayer.draw(drawer, view)
            currentMoonLayer.draw(drawer, view)
        }
    }

    override fun invalidate() {
        lineLayer.invalidate()
        moonLayer.invalidate()
        sunLayer.invalidate()
        currentMoonLayer.invalidate()
        currentSunLayer.invalidate()
    }

    override fun onClick(
        drawer: ICanvasDrawer,
        view: AugmentedRealityView,
        pixel: PixelCoordinate
    ): Boolean {
        return currentSunLayer.onClick(drawer, view, pixel) ||
                currentMoonLayer.onClick(drawer, view, pixel) ||
                sunLayer.onClick(drawer, view, pixel) ||
                moonLayer.onClick(drawer, view, pixel)
    }

    override fun onFocus(drawer: ICanvasDrawer, view: AugmentedRealityView): Boolean {
        return currentSunLayer.onFocus(drawer, view) ||
                currentMoonLayer.onFocus(drawer, view) ||
                sunLayer.onFocus(drawer, view) ||
                moonLayer.onFocus(drawer, view)
    }

    private fun updatePositions(
        drawer: ICanvasDrawer,
        location: Coordinate,
        time: ZonedDateTime
    ) {
        scope.launch {
            runner.enqueue {
                if (bitmapLoader == null) {
                    bitmapLoader = DrawerBitmapLoader(drawer)
                }

                val granularity = Duration.ofMinutes(10)

                val moonBeforePathObject = CanvasCircle(
                    Color.WHITE.withAlpha(60)
                )

                val moonAfterPathObject = CanvasCircle(
                    Color.WHITE.withAlpha(200)
                )

                val sunBeforePathObject = CanvasCircle(
                    AppColor.Yellow.color.withAlpha(60)
                )

                val sunAfterPathObject = CanvasCircle(
                    AppColor.Yellow.color.withAlpha(200)
                )

                val moonPathTimes = getMoonTimes(location, time)
                val sunPathTimes = getSunTimes(location, time)

                val moonPositions = Time.getReadings(
                    moonPathTimes.start.minus(granularity),
                    moonPathTimes.end.plus(granularity),
                    granularity
                ) {
                    val obj = if (it.isBefore(time)) {
                        moonBeforePathObject
                    } else {
                        moonAfterPathObject
                    }

                    val phase = Astronomy.getMoonPhase(it)

                    ARMarker(
                        SphericalARPoint(
                            astro.getMoonAzimuth(location, it).value,
                            astro.getMoonAltitude(location, it),
                            isTrueNorth = true,
                            angularDiameter = 0.5f
                        ),
                        canvasObject = obj,
                        onFocusedFn = {
                            onMoonFocus(it, phase)
                        }
                    )
                }.map { it.value }

                val sunPositions = Time.getReadings(
                    sunPathTimes.start.minus(granularity),
                    sunPathTimes.end.plus(granularity),
                    granularity
                ) {

                    val obj = if (it.isBefore(time)) {
                        sunBeforePathObject
                    } else {
                        sunAfterPathObject
                    }

                    ARMarker(
                        SphericalARPoint(
                            astro.getSunAzimuth(location, it).value,
                            astro.getSunAltitude(location, it),
                            isTrueNorth = true,
                            angularDiameter = 0.5f
                        ),
                        canvasObject = obj,
                        onFocusedFn = {
                            onSunFocus(it)
                        }
                    )
                }.map { it.value }

                val moonAltitude = astro.getMoonAltitude(location)
                val moonAzimuth = astro.getMoonAzimuth(location).value

                val sunAltitude = astro.getSunAltitude(location)
                val sunAzimuth = astro.getSunAzimuth(location).value

                val phase = Astronomy.getMoonPhase(time)
                val moonIconId = MoonPhaseImageMapper().getPhaseImage(phase.phase)
                val moonImageSize = drawer.dp(24f).toInt()
                val moonBitmap = bitmapLoader?.load(moonIconId, moonImageSize)

                val moon = ARMarker(
                    SphericalARPoint(
                        moonAzimuth,
                        moonAltitude,
                        isTrueNorth = true,
                        angularDiameter = 2f
                    ),
                    canvasObject = moonBitmap?.let { CanvasBitmap(moonBitmap) }
                        ?: CanvasCircle(Color.WHITE),
                    onFocusedFn = {
                        onMoonFocus(time, phase)
                    }
                )

                val sun = ARMarker(
                    SphericalARPoint(
                        sunAzimuth,
                        sunAltitude,
                        isTrueNorth = true,
                        angularDiameter = 2f
                    ),
                    canvasObject = CanvasCircle(AppColor.Yellow.color),
                    onFocusedFn = {
                        onSunFocus(time)
                    }
                )

                val sunPointsToDraw = if (drawBelowHorizon) {
                    listOf(sunPositions)
                } else {
                    getMarkersAboveHorizon(sunPositions)
                }

                val moonPointsToDraw = if (drawBelowHorizon) {
                    listOf(moonPositions)
                } else {
                    getMarkersAboveHorizon(moonPositions)
                }


                // TODO: The line should be drawn to the horizon

                val sunLines = sunPointsToDraw.map { markers ->
                    ARLine(
                        markers.map { it.point },
                        AppColor.Yellow.color.withAlpha(lineAlpha),
                        lineThickness,
                        ARLine.ThicknessUnits.Angle,
                    )
                }

                val moonLines = moonPointsToDraw.map { markers ->
                    ARLine(
                        markers.map { it.point },
                        Color.WHITE.withAlpha(lineAlpha),
                        lineThickness,
                        ARLine.ThicknessUnits.Angle,
                    )
                }

                lineLayer.setLines(sunLines + moonLines)
                sunLayer.setMarkers(sunPointsToDraw.flatten())
                moonLayer.setMarkers(moonPointsToDraw.flatten())

                // The sun and moon can be drawn below the horizon
                currentSunLayer.setMarkers(listOf(sun))
                currentMoonLayer.setMarkers(listOf(moon))
            }
        }
    }

    private fun getMarkersAboveHorizon(points: List<ARMarker>): List<List<ARMarker>> {
        val lines = mutableListOf<List<ARMarker>>()
        var currentLine = mutableListOf<ARMarker>()
        points.forEach {
            val point = it.point as SphericalARPoint
            // TODO: This isn't efficient
            if (point.coordinate.elevation > 0) {
                currentLine.add(it)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    currentLine = mutableListOf()
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }

    // TODO: Move this to sol and handle when the object does not rise or set - and make it even more generic (take in a celestial locator)
    /**
     * Gets the times the object is above the horizon
     * @param location The location of the observer
     * @param time The current time
     * @param nextRiseOffset The duration before the next rise to switch to the next day's times
     * @param isUpPredicate A predicate to determine if the object is up
     * @param riseSetTransitTimesProducer A function to get the rise, set, and transit times for the object
     * @return The range of times the object is above the horizon
     */
    private fun getAboveHorizonTimes(
        location: Coordinate,
        time: ZonedDateTime,
        nextRiseOffset: Duration,
        isUpPredicate: (Coordinate, ZonedDateTime) -> Boolean,
        riseSetTransitTimesProducer: (Coordinate, LocalDate) -> RiseSetTransitTimes
    ): Range<ZonedDateTime> {
        // If it is up, use the last rise to the next set
        // If it is down and is less than nextRiseOffset from the next rise, use the next rise to the next set
        // If it is down and is greater than nextRiseOffset from the next rise, use the last rise to the last set
        val isUp = isUpPredicate(location, time)

        val yesterday = riseSetTransitTimesProducer(location, time.minusDays(1).toLocalDate())
        val today = riseSetTransitTimesProducer(location, time.toLocalDate())
        val tomorrow = riseSetTransitTimesProducer(location, time.plusDays(1).toLocalDate())

        val lastRise =
            Time.getClosestPastTime(time, listOfNotNull(yesterday.rise, today.rise, tomorrow.rise))
        val nextRise = Time.getClosestFutureTime(
            time,
            listOfNotNull(yesterday.rise, today.rise, tomorrow.rise)
        )
        val lastSet =
            Time.getClosestPastTime(time, listOfNotNull(yesterday.set, today.set, tomorrow.set))
        val nextSet =
            Time.getClosestFutureTime(time, listOfNotNull(yesterday.set, today.set, tomorrow.set))

        if (isUp) {
            return Range(lastRise ?: time.atStartOfDay(), nextSet ?: time.atEndOfDay())
        }

        if (nextRise == null || Duration.between(time, nextRise) > nextRiseOffset) {
            return Range(lastRise ?: time.atStartOfDay(), lastSet ?: time.atEndOfDay())
        }

        return Range(nextRise, nextSet ?: time.atEndOfDay())
    }

    private fun getMoonTimes(location: Coordinate, time: ZonedDateTime): Range<ZonedDateTime> {
        return getAboveHorizonTimes(
            location,
            time,
            Duration.ofHours(6),
            { loc, t -> astro.isMoonUp(loc, t) },
            { loc, date -> astro.getMoonTimes(loc, date) }
        )
    }

    private fun getSunTimes(location: Coordinate, time: ZonedDateTime): Range<ZonedDateTime> {
        return getAboveHorizonTimes(
            location,
            time,
            Duration.ofHours(6),
            { loc, t -> astro.isSunUp(loc, t) },
            { loc, date -> astro.getSunTimes(loc, SunTimesMode.Actual, date) }
        )
    }
}