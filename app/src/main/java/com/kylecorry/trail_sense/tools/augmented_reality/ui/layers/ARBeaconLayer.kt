package com.kylecorry.trail_sense.tools.augmented_reality.ui.layers

import android.graphics.Color
import com.kylecorry.andromeda.canvas.ICanvasDrawer
import com.kylecorry.andromeda.core.ui.Colors
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.tools.beacons.domain.Beacon
import com.kylecorry.trail_sense.tools.navigation.ui.DrawerBitmapLoader
import com.kylecorry.trail_sense.tools.augmented_reality.domain.position.GeographicARPoint
import com.kylecorry.trail_sense.tools.augmented_reality.ui.ARMarker
import com.kylecorry.trail_sense.tools.augmented_reality.ui.AugmentedRealityView
import com.kylecorry.trail_sense.tools.augmented_reality.ui.CanvasBitmap
import com.kylecorry.trail_sense.tools.augmented_reality.ui.CanvasCircle
import kotlin.math.hypot

class ARBeaconLayer(
    var maxVisibleDistance: Distance = Distance.kilometers(1f),
    private val beaconSize: Distance = Distance.meters(4f),
    private val onFocus: (beacon: Beacon) -> Boolean = { false },
    private val onClick: (beacon: Beacon) -> Boolean = { false }
) : ARLayer {

    private val beacons = mutableListOf<Beacon>()
    private val lock = Any()
    private var _loader: DrawerBitmapLoader? = null
    private var loadedImageSize = 24

    private val layer = ARMarkerLayer(8f, 48f)

    private var areBeaconsUpToDate = false

    var destination: Beacon? = null

    fun setBeacons(beacons: List<Beacon>) {
        synchronized(lock) {
            // TODO: Convert to markers
            this.beacons.clear()
            this.beacons.addAll(beacons)
            areBeaconsUpToDate = false
        }
    }

    protected fun finalize() {
        _loader?.clear()
        _loader = null
    }

    override suspend fun update(drawer: ICanvasDrawer, view: AugmentedRealityView) {
        if (_loader == null) {
            _loader = DrawerBitmapLoader(drawer)
            loadedImageSize = drawer.dp(24f).toInt()
        }

        val loader = _loader ?: return

        val beacons = synchronized(lock) {
            beacons.toList()
        }

        // TODO: Is this the responsibility of the layer or consumer?
        val visible = beacons.mapNotNull {
            if (it.id != destination?.id && !it.visible) {
                return@mapNotNull null
            }
            val distance = hypot(
                view.location.distanceTo(it.coordinate),
                (it.elevation ?: view.altitude) - view.altitude
            )
            if (it.id != destination?.id && distance > maxVisibleDistance.meters().distance) {
                return@mapNotNull null
            }
            it to distance
        }.sortedByDescending { it.second }

        // TODO: Avoid recreating markers every time - it will help if this didn't filter nearby beacons
//        if (!areBeaconsUpToDate) {
        // TODO: Change opacity if navigating
        layer.setMarkers(visible.flatMap {
            val beacon = it.first
            listOfNotNull(
                ARMarker(
                    GeographicARPoint(
                        beacon.coordinate,
                        beacon.elevation,
                        beaconSize.distance,
                    ),
                    CanvasCircle(beacon.color, Color.WHITE),
                    onFocusedFn = {
                        onFocus(beacon)
                    },
                    onClickFn = {
                        onClick(beacon)
                    }
                ),
                beacon.icon?.let { icon ->
                    val color = Colors.mostContrastingColor(Color.WHITE, Color.BLACK, beacon.color)
                    ARMarker(
                        GeographicARPoint(
                            beacon.coordinate,
                            beacon.elevation,
                            beaconSize.distance
                        ),
                        CanvasBitmap(
                            loader.load(icon.icon, loadedImageSize),
                            0.75f,
                            tint = color
                        ),
                        keepFacingUp = true
                    )
                }
            )
        })
        //            areBeaconsUpToDate = true
//        }


        layer.update(drawer, view)
    }

    override fun draw(drawer: ICanvasDrawer, view: AugmentedRealityView) {
        layer.draw(drawer, view)
    }

    override fun invalidate() {
        areBeaconsUpToDate = false
        layer.invalidate()
    }

    override fun onClick(
        drawer: ICanvasDrawer, view: AugmentedRealityView, pixel: PixelCoordinate
    ): Boolean {
        return layer.onClick(drawer, view, pixel)
    }

    override fun onFocus(drawer: ICanvasDrawer, view: AugmentedRealityView): Boolean {
        return layer.onFocus(drawer, view)
    }

}