package com.kylecorry.trail_sense.tools.augmented_reality.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.navigation.NavController
import com.kylecorry.andromeda.alerts.dialog
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.andromeda.core.time.TimerActionBehavior
import com.kylecorry.andromeda.core.ui.Colors.withAlpha
import com.kylecorry.andromeda.core.ui.setTextDistinct
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.fragments.observeFlow
import com.kylecorry.andromeda.sense.Sensors
import com.kylecorry.sol.science.astronomy.moon.MoonPhase
import com.kylecorry.sol.science.geology.CoordinateBounds
import com.kylecorry.sol.science.geology.Geofence
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentAugmentedRealityBinding
import com.kylecorry.trail_sense.tools.beacons.domain.Beacon
import com.kylecorry.trail_sense.tools.beacons.infrastructure.persistence.BeaconRepo
import com.kylecorry.trail_sense.tools.navigation.infrastructure.Navigator
import com.kylecorry.trail_sense.shared.CustomUiUtils.getCardinalDirectionColor
import com.kylecorry.trail_sense.shared.DistanceUtils.toRelativeDistance
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.Units
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.permissions.alertNoCameraPermission
import com.kylecorry.trail_sense.shared.permissions.requestCamera
import com.kylecorry.trail_sense.shared.withId
import com.kylecorry.trail_sense.tools.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.tools.augmented_reality.domain.calibration.ARCalibratorFactory
import com.kylecorry.trail_sense.tools.augmented_reality.ui.guide.ARGuide
import com.kylecorry.trail_sense.tools.augmented_reality.ui.guide.AstronomyARGuide
import com.kylecorry.trail_sense.tools.augmented_reality.ui.guide.NavigationARGuide
import com.kylecorry.trail_sense.tools.augmented_reality.ui.layers.ARAstronomyLayer
import com.kylecorry.trail_sense.tools.augmented_reality.ui.layers.ARBeaconLayer
import com.kylecorry.trail_sense.tools.augmented_reality.ui.layers.ARGridLayer
import com.kylecorry.trail_sense.tools.augmented_reality.ui.layers.ARPathLayer
import com.kylecorry.trail_sense.tools.maps.infrastructure.layers.PathLayerManager
import com.kylecorry.trail_sense.tools.navigation.ui.IMappablePath
import com.kylecorry.trail_sense.tools.paths.domain.Path
import java.time.ZonedDateTime
import kotlin.math.hypot

class AugmentedRealityFragment : BoundFragment<FragmentAugmentedRealityBinding>() {

    private var mode = ARMode.Normal

    private val userPrefs by lazy { UserPreferences(requireContext()) }
    private val beaconRepo by lazy {
        BeaconRepo.getInstance(requireContext())
    }

    private val formatter by lazy { FormatService.getInstance(requireContext()) }

    private var guide: ARGuide? = null

    private val beaconLayer by lazy {
        ARBeaconLayer(
            Distance.meters(userPrefs.augmentedReality.beaconViewDistance),
            onFocus = this::onBeaconFocused
        ) {
            if (navigator.getDestinationId() != it.id) {
                navigator.navigateTo(it)
            } else {
                navigator.cancelNavigation()
            }
            true
        }
    }

    private val astronomyLayer by lazy {
        ARAstronomyLayer(
            drawLines = true,
            drawBelowHorizon = false,
            onSunFocus = this::onSunFocused,
            onMoonFocus = this::onMoonFocused
        )
    }

    private val navigator by lazy { Navigator.getInstance(requireContext()) }

    private val gridLayer by lazy {
        ARGridLayer(
            30,
            northColor = Resources.getCardinalDirectionColor(requireContext()),
            horizonColor = Color.WHITE,
            labelColor = Color.WHITE,
            color = Color.WHITE.withAlpha(100),
            useTrueNorth = userPrefs.compass.useTrueNorth
        )
    }

    private val pathsLayer by lazy {
        ARPathLayer(
            userPrefs.augmentedReality.pathViewDistance,
            this::onPathFocused
        )
    }
    private var pathLayerManager: PathLayerManager? = null

    private var isCameraEnabled = true

    private var lastLocation = Coordinate.zero
    private val layerManagementUpdater = CoroutineTimer(actionBehavior = TimerActionBehavior.Skip) {
        if (!isBound) return@CoroutineTimer
        if (!userPrefs.augmentedReality.showPathLayer) return@CoroutineTimer
        if (binding.arView.location == lastLocation) return@CoroutineTimer
        lastLocation = binding.arView.location
        // This is only handling the path layer for now
        val viewDistance = Distance.meters(userPrefs.augmentedReality.pathViewDistance * 2f)
        pathLayerManager?.onBoundsChanged(
            CoordinateBounds.from(
                Geofence(
                    binding.arView.location,
                    viewDistance
                )
            )
        )
        pathLayerManager?.onLocationChanged(binding.arView.location, null)
    }

    // Calibration
    private val calibrationFactory = ARCalibratorFactory()
    private val astronomyService = AstronomyService()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Beacon layer setup (TODO: Move this to a layer manager)
        observeFlow(beaconRepo.getBeacons()) {
            beaconLayer.setBeacons(it)
        }
        observeFlow(navigator.destination) {
            beaconLayer.destination = it
        }

        binding.camera.setScaleType(PreviewView.ScaleType.FILL_CENTER)
        binding.camera.setShowTorch(false)

        binding.arView.bind(binding.camera)

        val modeId = requireArguments().getLong("mode", ARMode.Normal.id)
        val desiredMode = ARMode.entries.withId(modeId) ?: ARMode.Normal

        setMode(desiredMode)

        binding.cameraToggle.setOnClickListener {
            if (isCameraEnabled) {
                stopCamera()
            } else {
                startCamera()
            }
        }

        binding.calibrateBtn.setOnClickListener {
            startCalibration()
        }

        binding.calibrateBtn.setOnLongClickListener {
            dialog(getString(R.string.reset_calibration_question)) { cancelled ->
                if (!cancelled) {
                    binding.arView.resetCalibration()
                }
            }
            true
        }

        binding.confirmCalibrationButton.setOnClickListener {
            calibrate()
        }

        binding.cancelCalibrationButton.setOnClickListener {
            stopCalibration()
        }

        binding.arView.setOnFocusLostListener {
            binding.focusActionButton.isVisible = false
        }

        if (!Sensors.hasGyroscope(requireContext())) {
            ARNoGyroAlert(requireContext()).alert()
        }
    }

    override fun onResume() {
        super.onResume()

        pathLayerManager = PathLayerManager(requireContext(), pathsLayer)
        pathLayerManager?.start()
        layerManagementUpdater.interval(100)

        binding.arView.start()
        if (isCameraEnabled) {
            startCamera()
        } else {
            stopCamera()
        }

        guide?.start(binding.arView, binding.guidancePanel)
    }

    // TODO: Move this to the AR view
    private fun startCamera() {
        isCameraEnabled = true
        binding.cameraToggle.setImageResource(R.drawable.ic_camera)
        binding.arView.backgroundFillColor = Color.TRANSPARENT
        requestCamera {
            if (it) {
                binding.camera.isVisible = true
                binding.camera.start(
                    readFrames = false, shouldStabilizePreview = false
                )
            } else {
                binding.camera.isInvisible = true
                isCameraEnabled = false
                binding.cameraToggle.setImageResource(R.drawable.ic_camera_off)
                binding.arView.backgroundFillColor = Color.BLACK
                alertNoCameraPermission()
            }
        }
    }

    private fun stopCamera() {
        binding.cameraToggle.setImageResource(R.drawable.ic_camera_off)
        isCameraEnabled = false
        binding.arView.backgroundFillColor = Color.BLACK
        binding.camera.stop()
        binding.camera.isInvisible = true
    }

    override fun onPause() {
        super.onPause()
        binding.camera.stop()
        binding.arView.stop()
        guide?.stop(binding.arView, binding.guidancePanel)
        pathLayerManager?.stop()
        layerManagementUpdater.stop()
    }

    // TODO: Extract focus formatters
    private fun onSunFocused(time: ZonedDateTime): Boolean {
        binding.arView.focusText =
            getString(R.string.sun) + "\n" + formatter.formatRelativeDateTime(
                time,
                includeSeconds = false
            )
        return true
    }

    private fun onMoonFocused(time: ZonedDateTime, phase: MoonPhase): Boolean {
        binding.arView.focusText =
            getString(R.string.moon) + "\n" + formatter.formatRelativeDateTime(
                time,
                includeSeconds = false
            ) + "\n${formatter.formatMoonPhase(phase.phase)} (${
                formatter.formatPercentage(
                    phase.illumination
                )
            })"
        return true
    }

    private fun onPathFocused(path: IMappablePath): Boolean {
        binding.arView.focusText = path.name
        return true
    }

    private fun onBeaconFocused(beacon: Beacon): Boolean {
        val distance = hypot(
            binding.arView.location.distanceTo(beacon.coordinate),
            (beacon.elevation ?: binding.arView.altitude) - binding.arView.altitude
        )
        val userDistance = Distance.meters(distance).convertTo(userPrefs.baseDistanceUnits)
            .toRelativeDistance()
        val formattedDistance = formatter.formatDistance(
            userDistance,
            Units.getDecimalPlaces(userDistance.units),
            strict = false
        )
        binding.arView.focusText = beacon.name + "\n" + formattedDistance

        // If the beacon isn't the destination, show the navigate button
        if (navigator.getDestinationId() != beacon.id) {
            binding.focusActionButton.setTextDistinct(getString(R.string.navigate))
            binding.focusActionButton.setOnClickListener {
                navigator.navigateTo(beacon)
            }
            binding.focusActionButton.isVisible = true
        } else {
            binding.focusActionButton.isVisible = false
        }

        return true
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater, container: ViewGroup?
    ): FragmentAugmentedRealityBinding {
        return FragmentAugmentedRealityBinding.inflate(layoutInflater, container, false)
    }

    private fun setMode(mode: ARMode) {
        this.mode = mode
        when (mode) {
            ARMode.Normal -> {
                binding.arView.setLayers(
                    listOfNotNull(
                        gridLayer,
                        astronomyLayer,
                        if (userPrefs.augmentedReality.showPathLayer) pathsLayer else null,
                        beaconLayer
                    )
                )
                changeGuide(NavigationARGuide(navigator))
            }

            ARMode.Astronomy -> {
                binding.arView.setLayers(listOf(gridLayer, astronomyLayer))
                changeGuide(AstronomyARGuide(astronomyLayer) { setMode(ARMode.Normal) })
            }
        }
    }

    private fun changeGuide(guide: ARGuide?) {
        this.guide?.stop(binding.arView, binding.guidancePanel)
        this.guide = guide
        this.guide?.start(binding.arView, binding.guidancePanel)
    }

    private fun startCalibration() {
        binding.calibrationPanel.isVisible = true
        val isSunUp = astronomyService.isSunUp(binding.arView.location)
        dialog(
            getString(R.string.calibrate),
            getString(
                R.string.ar_calibration_instructions,
                if (isSunUp) getString(R.string.sun) else getString(R.string.moon)
            ),
        ) { cancelled ->
            if (cancelled) {
                stopCalibration()
            }
        }

    }

    private fun calibrate() {
        inBackground {
            val useGyro = userPrefs.augmentedReality.useGyroOnlyAfterCalibration
            val calibrator = if (astronomyService.isSunUp(binding.arView.location)) {
                calibrationFactory.getSunCalibrator(binding.arView.location)
            } else {
                calibrationFactory.getMoonCalibrator(binding.arView.location)
            }
            binding.arView.calibrate(calibrator, useGyro)
            stopCalibration()
        }
    }

    private fun stopCalibration() {
        binding.calibrationPanel.isVisible = false
    }

    companion object {
        fun open(navController: NavController, mode: ARMode = ARMode.Normal) {
            navController.navigate(
                R.id.augmentedRealityFragment, bundleOf(
                    "mode" to mode.id
                )
            )
        }
    }

}