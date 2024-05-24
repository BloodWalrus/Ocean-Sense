package com.kylecorry.trail_sense.tools.paths.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.coroutines.onDefault
import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.andromeda.core.coroutines.onMain
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.core.tryOrNothing
import com.kylecorry.andromeda.core.ui.Colors
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.fragments.observe
import com.kylecorry.andromeda.fragments.show
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.sol.math.Range
import com.kylecorry.sol.math.SolMath.roundPlaces
import com.kylecorry.sol.math.statistics.Statistics
import com.kylecorry.sol.science.geology.Geology
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentPathOverviewBinding
import com.kylecorry.trail_sense.databinding.ListItemWaypointBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils.getPrimaryMarkerColor
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.Units
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.debugging.DebugPathElevationsCommand
import com.kylecorry.trail_sense.shared.debugging.isDebug
import com.kylecorry.trail_sense.shared.declination.DeclinationFactory
import com.kylecorry.trail_sense.shared.extensions.range
import com.kylecorry.trail_sense.shared.extensions.withCancelableLoading
import com.kylecorry.trail_sense.shared.io.IOFactory
import com.kylecorry.trail_sense.shared.navigation.NavControllerAppNavigation
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.toRelativeDistance
import com.kylecorry.trail_sense.tools.beacons.infrastructure.BeaconNavigator
import com.kylecorry.trail_sense.tools.beacons.infrastructure.IBeaconNavigator
import com.kylecorry.trail_sense.tools.beacons.infrastructure.persistence.BeaconService
import com.kylecorry.trail_sense.tools.maps.infrastructure.layers.ILayerManager
import com.kylecorry.trail_sense.tools.maps.infrastructure.layers.MultiLayerManager
import com.kylecorry.trail_sense.tools.maps.infrastructure.layers.MyAccuracyLayerManager
import com.kylecorry.trail_sense.tools.maps.infrastructure.layers.MyLocationLayerManager
import com.kylecorry.trail_sense.tools.navigation.ui.MappableLocation
import com.kylecorry.trail_sense.tools.navigation.ui.MappablePath
import com.kylecorry.trail_sense.tools.navigation.ui.layers.BeaconLayer
import com.kylecorry.trail_sense.tools.navigation.ui.layers.MyAccuracyLayer
import com.kylecorry.trail_sense.tools.navigation.ui.layers.MyLocationLayer
import com.kylecorry.trail_sense.tools.navigation.ui.layers.PathLayer
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.domain.PathPoint
import com.kylecorry.trail_sense.tools.paths.domain.PathPointColoringStyle
import com.kylecorry.trail_sense.tools.paths.domain.beacon.IPathPointBeaconConverter
import com.kylecorry.trail_sense.tools.paths.domain.beacon.TemporaryPathPointBeaconConverter
import com.kylecorry.trail_sense.tools.paths.domain.factories.IPointDisplayFactory
import com.kylecorry.trail_sense.tools.paths.domain.hiking.HikingDifficulty
import com.kylecorry.trail_sense.tools.paths.domain.hiking.HikingService
import com.kylecorry.trail_sense.tools.paths.domain.point_finder.NearestPathLineNavigator
import com.kylecorry.trail_sense.tools.paths.domain.point_finder.NearestPathPointNavigator
import com.kylecorry.trail_sense.tools.paths.infrastructure.commands.BacktrackCommand
import com.kylecorry.trail_sense.tools.paths.infrastructure.persistence.PathService
import com.kylecorry.trail_sense.tools.paths.ui.commands.ChangePathColorCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.ChangePathLineStyleCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.ChangePointStyleCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.CreateBeaconFromPointCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.DeletePointCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.ExportPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.KeepPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.MoveIPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.NavigateToPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.NavigateToPointCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.RenamePathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.SimplifyPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.TogglePathVisibilityCommand
import kotlinx.coroutines.launch
import java.time.Duration


class PathOverviewFragment : BoundFragment<FragmentPathOverviewBinding>() {

    private val prefs by lazy { UserPreferences(requireContext()) }
    private val formatService by lazy { FormatService.getInstance(requireContext()) }
    private val throttle = Throttle(20)

    private val sensorService by lazy { SensorService(requireContext()) }
    private val gps by lazy { sensorService.getGPS() }
    private val compass by lazy { sensorService.getCompass() }
    private val hasCompass by lazy { sensorService.hasCompass() }
    private val declinationProvider by lazy {
        DeclinationFactory().getDeclinationStrategy(
            prefs,
            gps
        )
    }
    private var declination = 0f
    private val hikingService = HikingService()
    private val pathService by lazy { PathService.getInstance(requireContext()) }

    private var pointSheet: PathPointsListFragment? = null

    private lateinit var chart: PathElevationChart
    private var path: Path? = null
    private var waypoints: List<PathPoint> = emptyList()
    private var pathId: Long = 0L
    private var selectedPointId: Long? = null
    private var calculatedDuration = Duration.ZERO
    private var elevationGain = Distance.meters(0f)
    private var elevationLoss = Distance.meters(0f)
    private var elevationRange: Range<Distance>? = null
    private var slopes: List<Triple<PathPoint, PathPoint, Float>> = emptyList()
    private var difficulty = HikingDifficulty.Easy

    private val pathLayer = PathLayer()
    private val waypointLayer = BeaconLayer(8f) {
        if (selectedPointId != null) {
            deselectPoint()
            return@BeaconLayer true
        }
        val point = waypoints.firstOrNull { point -> point.id == it.id }
        if (point != null) {
            viewWaypoint(point)
            true
        } else {
            false
        }
    }
    private val myLocationLayer = MyLocationLayer()
    private val myAccuracyLayer = MyAccuracyLayer()

    private val paceFactor = 1.75f

    private var isFullscreen = false

    private val converter: IPathPointBeaconConverter by lazy {
        TemporaryPathPointBeaconConverter(
            getString(R.string.waypoint)
        )
    }

    private val beaconNavigator: IBeaconNavigator by lazy {
        BeaconNavigator(
            BeaconService(requireContext()),
            NavControllerAppNavigation(findNavController())
        )
    }

    private var layerManager: ILayerManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathId = requireArguments().getLong("path_id")
        pathLayer.setShouldRenderWithDrawLines(prefs.navigation.useFastPathRendering)
    }

    override fun onResume() {
        super.onResume()
        layerManager = MultiLayerManager(
            listOf(
                MyAccuracyLayerManager(
                    myAccuracyLayer,
                    Resources.getPrimaryMarkerColor(requireContext()),
                    25
                ),
                MyLocationLayerManager(
                    myLocationLayer,
                    Resources.getPrimaryMarkerColor(requireContext())
                )
            )
        )
        layerManager?.start()

        // Populate the last known location
        layerManager?.onLocationChanged(gps.location, gps.horizontalAccuracy)
    }

    override fun onPause() {
        super.onPause()
        layerManager?.stop()
        layerManager = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = PathElevationChart(binding.chart)

        binding.pathImage.isInteractive = true

        binding.pathImage.setOnTouchListener { v, event ->
            binding.root.isScrollable = event.action == MotionEvent.ACTION_UP
            false
        }

        binding.pathMapFullscreenToggle.setOnClickListener {
            isFullscreen = !isFullscreen
            binding.pathMapHolder.layoutParams = if (isFullscreen) {
                val legendHeight = Resources.dp(requireContext(), 72f).toInt()
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    binding.root.height - legendHeight
                )
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Resources.dp(requireContext(), 250f).toInt()
                ).also {
                    it.marginStart = Resources.dp(requireContext(), 16f).toInt()
                    it.marginEnd = Resources.dp(requireContext(), 16f).toInt()
                }
            }
            binding.pathMapFullscreenToggle.setImageResource(if (isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_recenter)
            val timer = CoroutineTimer {
                if (isBound) {
                    binding.root.scrollTo(0, binding.pathMapHolder.top)
                    binding.pathImage.recenter()
                }
            }
            timer.once(Duration.ofMillis(30))
        }

        binding.addPointBtn.setOnClickListener {
            inBackground {
                var wasSuccessful = false
                val job = launch {
                    BacktrackCommand(requireContext(), pathId).execute()
                    wasSuccessful = true
                }

                Alerts.withCancelableLoading(
                    requireContext(),
                    getString(R.string.loading),
                    onCancel = { job.cancel() }) {
                    job.join()
                    if (wasSuccessful) {
                        toast(getString(R.string.point_added))
                    }
                }
            }
        }

        binding.navigateBtn.setOnClickListener {
            navigateToNearestPathPoint()
        }

        binding.pathTitle.rightButton.setOnClickListener {
            showPathMenu()
        }

        binding.pathTitle.subtitle.setOnClickListener {
            movePath()
        }

        chart.setOnPointClickListener {
            viewWaypoint(it)
        }

        binding.pathElevationMin.setOnClickListener {
            val point = waypoints.filter { it.elevation != null }.minByOrNull { it.elevation!! }
            point?.let { viewWaypoint(it) }
        }

        binding.pathElevationMax.setOnClickListener {
            val point = waypoints.filter { it.elevation != null }.maxByOrNull { it.elevation!! }
            point?.let { viewWaypoint(it) }
        }

        observe(pathService.getLivePath(pathId)) {
            path = it
            updateParent()
            updateElevationPlot()
            updatePointStyleLegend()
            updatePathMap()
            onPathChanged()
        }

        observe(pathService.getWaypointsLive(pathId)) {
            onWaypointsChanged(it)
        }

        observe(gps) {
            updateDeclination()
            layerManager?.onLocationChanged(gps.location, gps.horizontalAccuracy)
            onPathChanged()
        }

        observe(compass) {
            layerManager?.onBearingChanged(compass.rawBearing)
        }

        waypointLayer.setOutlineColor(Color.TRANSPARENT)
        binding.pathImage.setLayers(
            listOf(pathLayer, waypointLayer, myAccuracyLayer, myLocationLayer)
        )

        binding.pathLineStyle.setOnClickListener {
            val path = path ?: return@setOnClickListener
            val command = ChangePathLineStyleCommand(requireContext(), this)
            command.execute(path)
        }

        binding.pathColor.setOnClickListener {
            val path = path ?: return@setOnClickListener
            val command = ChangePathColorCommand(requireContext(), this)
            command.execute(path)
        }

        binding.pathPointStyle.setOnClickListener {
            val path = path ?: return@setOnClickListener
            val command = ChangePointStyleCommand(requireContext(), this)
            command.execute(path)
        }

        if (!hasCompass) {
            myLocationLayer.setShowDirection(false)
        }
    }

    private fun onWaypointsChanged(waypoints: List<PathPoint>) {
        inBackground {
            onDefault {
                this@PathOverviewFragment.waypoints =
                    hikingService.correctElevations(waypoints.sortedBy { it.id }).reversed()
            }
            onIO {
                DebugPathElevationsCommand(
                    requireContext(),
                    waypoints,
                    this@PathOverviewFragment.waypoints
                ).execute()
            }
            onMain {
                val selected = selectedPointId
                if (selected != null && waypoints.find { it.id == selected } == null) {
                    deselectPoint()
                }
                pointSheet?.setPoints(this@PathOverviewFragment.waypoints)
            }
            updateElevationOverview()
            updateHikingStats()
            updatePathMap()
            updatePointStyleLegend()
            onPathChanged()
        }
    }

    private fun updateParent() {
        val path = path ?: return
        inBackground {
            val parent = onIO { pathService.getGroup(path.parentId) }
            if (isBound) {
                binding.pathTitle.subtitle.text = parent?.name ?: getString(R.string.no_group)
            }
        }
    }

    private fun updateElevationPlot() {
        chart.plot(
            waypoints.reversed(),
            path?.style?.color ?: prefs.navigation.defaultPathColor.color
        )
    }

    private suspend fun updateHikingStats() = onDefault {
        val reversed = waypoints.reversed()
        difficulty = hikingService.getHikingDifficulty(reversed)
        calculatedDuration =
            hikingService.getHikingDuration(reversed, paceFactor)
    }

    private fun movePath() {
        val path = path ?: return
        val command = MoveIPathCommand(requireContext(), pathService)
        inBackground {
            command.execute(path)
        }
    }

    private suspend fun updateElevationOverview() {
        onDefault {
            val path = waypoints.reversed()

            val gainLoss = hikingService.getElevationLossGain(path)

            val units = prefs.baseDistanceUnits
            elevationGain = gainLoss.second.convertTo(units)
            elevationLoss = gainLoss.first.convertTo(units)
            elevationRange =
                path.mapNotNull { it.elevation?.let { Distance.meters(it).convertTo(units) } }
                    .range()
            slopes = hikingService.getSlopes(path)
            slopes.forEach {
                it.first.slope = it.third
            }
            val first = slopes.lastOrNull()
            first?.first?.slope = first?.third ?: 0f
        }
        onMain {
            updateElevationPlot()
        }
    }

    private fun showPathMenu() {
        val path = path ?: return
        val actions = listOf(
            PathAction.Rename,
            PathAction.Keep,
            PathAction.ToggleVisibility,
            PathAction.Export,
            PathAction.Simplify,
            PathAction.ViewPoints
        )

        Pickers.menu(
            binding.pathTitle.rightButton, listOf(
                getString(R.string.rename),
                if (path.temporary) getString(R.string.keep_forever) else null,
                if (path.style.visible) getString(R.string.hide) else getString(
                    R.string.show
                ),
                getString(R.string.export),
                getString(R.string.simplify),
                getString(R.string.points)
            )
        ) {
            when (actions[it]) {
                PathAction.Export -> exportPath(path)
                PathAction.Rename -> renamePath(path)
                PathAction.Keep -> keepPath(path)
                PathAction.ToggleVisibility -> togglePathVisibility(path)
                PathAction.Simplify -> simplifyPath(path)
                PathAction.ViewPoints -> viewPoints()
                else -> {
                    // Do nothing
                }
            }
            true
        }
    }

    private fun simplifyPath(path: Path) {
        val command = SimplifyPathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun exportPath(path: Path) {
        val command = ExportPathCommand(
            requireContext(),
            this,
            IOFactory().createGpxService(this),
            pathService
        )
        command.execute(path)
    }

    private fun togglePathVisibility(path: Path) {
        val command = TogglePathVisibilityCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun renamePath(path: Path) {
        val command = RenamePathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun keepPath(path: Path) {
        val command = KeepPathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun updatePathMap() {
        val path = path ?: return
        if (!isBound) {
            return
        }
        binding.pathImage.bounds = Geology.getBounds(waypoints.map { it.coordinate })
        pathLayer.setPaths(
            listOf(
                MappablePath(
                    path.id,
                    waypoints.map {
                        MappableLocation(
                            it.id,
                            it.coordinate,
                            path.style.color,
                            null,
                            it.elevation
                        )
                    },
                    path.style.color,
                    path.style.line,
                    path.name
                )
            )
        )
    }

    private fun onPathChanged() {
        val path = path ?: return

        if (!isBound || throttle.isThrottled()) {
            return
        }

        binding.pathLineStyle.text = listOf(
            getString(R.string.solid),
            getString(R.string.dotted),
            getString(R.string.arrow),
            getString(R.string.dashed),
            getString(R.string.square),
            getString(R.string.diamond),
            getString(R.string.cross)
        )[path.style.line.ordinal]

        val distance =
            path.metadata.distance.convertTo(prefs.baseDistanceUnits).toRelativeDistance(prefs.useNauticalMiles)

        val start = path.metadata.duration?.start
        val end = path.metadata.duration?.end


        binding.pathTitle.title.text = PathNameFactory(requireContext()).getName(path)

        val duration = if (start != null && end != null && Duration.between(
                start,
                end
            ) > Duration.ofMinutes(1)
        ) {
            Duration.between(start, end)
        } else {
            calculatedDuration
        }

        binding.pathDuration.title = formatService.formatDuration(duration, false)
        binding.pathWaypoints.title = path.metadata.waypoints.toString()

        // Elevations
        binding.pathElevationGain.title = formatService.formatDistance(
            elevationGain,
            Units.getDecimalPlaces(elevationGain.units),
            false
        )
        binding.pathElevationLoss.title = formatService.formatDistance(
            elevationLoss,
            Units.getDecimalPlaces(elevationLoss.units),
            false
        )

        val elevationRange = elevationRange
        binding.pathElevationMin.isVisible = elevationRange != null
        binding.pathElevationMax.isVisible = elevationRange != null

        elevationRange?.let {
            binding.pathElevationMin.title = formatService.formatDistance(
                it.start,
                Units.getDecimalPlaces(it.start.units),
                false
            )

            binding.pathElevationMax.title = formatService.formatDistance(
                it.end,
                Units.getDecimalPlaces(it.end.units),
                false
            )
        }

        binding.pathDifficulty.title = formatService.formatHikingDifficulty(difficulty)

        binding.pathDistance.title =
            formatService.formatDistance(
                distance,
                Units.getDecimalPlaces(distance.units),
                false
            )

        Colors.setImageColor(binding.pathColor, path.style.color)

        updateWaypoints()

        updateDebugStats()
    }

    private fun updateWaypoints() {
        waypointLayer.setBeacons(
            waypoints.asBeacons(
                requireContext(),
                path?.style?.point ?: PathPointColoringStyle.None,
                selectedPointId
            )
        )
    }

    private fun updateDeclination() {
        inBackground {
            onIO {
                declination = declinationProvider.getDeclination()
                compass.declination = declination
            }
        }
    }

    private fun deselectPoint() {
        selectedPointId = null
        binding.pathSelectedPoint.isVisible = false
        chart.removeHighlight()
    }

    private fun updatePointStyleLegend() {

        val path = path ?: return

        val factory = getPointFactory()

        binding.pathPointStyle.text = listOf(
            getString(R.string.none),
            getString(R.string.cell_signal),
            getString(R.string.elevation),
            getString(R.string.time),
            getString(R.string.path_slope)
        )[path.style.point.ordinal]

        binding.pathLegend.colorScale = factory.createColorScale(waypoints)
        binding.pathLegend.labels = factory.createLabelMap(waypoints)
        binding.pathLegend.isVisible = path.style.point != PathPointColoringStyle.None
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPathOverviewBinding {
        return FragmentPathOverviewBinding.inflate(layoutInflater, container, false)
    }

    // Waypoints
    private fun drawWaypointListItem(itemBinding: ListItemWaypointBinding, item: PathPoint) {
        val itemStrategy = WaypointListItem(
            requireContext(),
            formatService,
            { createBeacon(it) },
            { deleteWaypoint(it) },
            { navigateToWaypoint(it) },
            { /* Do nothing */ }
        )

        itemStrategy.display(itemBinding, item)
    }

    private fun viewPoints() {
        binding.root.scrollTo(0, 0)
        pointSheet = PathPointsListFragment()
        pointSheet?.show(this)
        pointSheet?.onCreateBeaconListener = { createBeacon(it) }
        pointSheet?.onDeletePointListener = { deleteWaypoint(it) }
        pointSheet?.onNavigateToPointListener = { navigateToWaypoint(it) }
        pointSheet?.onViewPointListener = { viewWaypoint(it) }
        pointSheet?.setPoints(waypoints)
    }

    private fun viewWaypoint(point: PathPoint) {
        selectedPointId = if (selectedPointId == point.id) {
            null
        } else {
            point.id
        }

        binding.pathSelectedPoint.removeAllViews()

        if (selectedPointId != null) {
            binding.pathSelectedPoint.isVisible = true
            chart.highlight(point)
            val binding =
                ListItemWaypointBinding.inflate(layoutInflater, binding.pathSelectedPoint, true)
            drawWaypointListItem(binding, point)
        } else {
            deselectPoint()
        }

        onPathChanged()
    }

    private fun navigateToWaypoint(point: PathPoint) {
        val path = path ?: return
        val command = NavigateToPointCommand(
            this,
            converter,
            beaconNavigator
        )
        tryOrNothing {
            command.execute(path, point)
        }
    }

    private fun navigateToNearestPathPoint() {
        val path = path ?: return
        val points = waypoints
        val command = NavigateToPathCommand(
            if (prefs.navigation.onlyNavigateToPoints) NearestPathPointNavigator() else NearestPathLineNavigator(),
            gps,
            converter,
            beaconNavigator
        )

        toast(getString(R.string.navigating_to_nearest_path_point))

        inBackground {
            command.execute(path, points)
        }
    }

    private fun deleteWaypoint(point: PathPoint) {
        val path = path ?: return
        val command = DeletePointCommand(requireContext(), this)
        command.execute(path, point)
    }

    private fun createBeacon(point: PathPoint) {
        val path = path ?: return
        val command = CreateBeaconFromPointCommand(requireContext())
        command.execute(path, point)
    }

    private fun getPointFactory(): IPointDisplayFactory {
        return getPointFactory(requireContext(), path?.style?.point ?: PathPointColoringStyle.None)
    }

    private fun updateDebugStats() {
        if (!isDebug()) {
            return
        }

        binding.pathsTiming.isVisible = true

        inBackground {
            onDefault {
                val readings = waypoints
                    .filter { it.time != null }
                    .sortedBy { it.time }
                    .zipWithNext { a, b -> Duration.between(a.time, b.time).seconds / 60f }

                if (readings.isEmpty()) {
                    return@onDefault
                }

                val mean = Statistics.mean(readings).roundPlaces(2)
                val stdev = Statistics.stdev(readings, mean = mean).roundPlaces(2)
                val max = readings.maxOrNull()?.roundPlaces(2) ?: 0f
                val median = Statistics.median(readings).roundPlaces(2)
                val quantile75 = Statistics.quantile(readings, 0.75f).roundPlaces(2)
                val quantile90 = Statistics.quantile(readings, 0.9f).roundPlaces(2)
                onMain {
                    @Suppress("SetTextI18n")
                    binding.pathsTiming.text = "Mean: $mean\n" +
                            "Stdev: $stdev\n" +
                            "Max: $max\n" +
                            "Median: $median\n" +
                            "75th: $quantile75\n" +
                            "90th: $quantile90"
                }
            }
        }
    }
}