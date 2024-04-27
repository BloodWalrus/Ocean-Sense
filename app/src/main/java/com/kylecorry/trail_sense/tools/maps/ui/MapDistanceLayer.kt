package com.kylecorry.trail_sense.tools.maps.ui

import android.graphics.Color
import androidx.annotation.ColorInt
import com.kylecorry.andromeda.canvas.ICanvasDrawer
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.tools.beacons.domain.Beacon
import com.kylecorry.trail_sense.tools.navigation.ui.MappablePath
import com.kylecorry.trail_sense.tools.navigation.ui.layers.BeaconLayer
import com.kylecorry.trail_sense.tools.navigation.ui.layers.ILayer
import com.kylecorry.trail_sense.tools.navigation.ui.layers.IMapView
import com.kylecorry.trail_sense.tools.navigation.ui.layers.PathLayer
import com.kylecorry.trail_sense.tools.paths.domain.LineStyle

class MapDistanceLayer(private val onPathChanged: (points: List<Coordinate>) -> Unit = {}) :
    ILayer {

    private val pointLayer = BeaconLayer {
        if (!isEnabled) {
            return@BeaconLayer false
        }
        add(it.coordinate)
        true
    }
    private val pathLayer = PathLayer()
    private var points = mutableListOf<Coordinate>()

    init {
        pathLayer.setShouldRenderWithDrawLines(true)
    }

    var isEnabled = true
        set(value) {
            field = value
            clear()
        }

    private var pathColor: Int = Color.BLACK

    fun setPathColor(@ColorInt color: Int) {
        pathColor = color
        updateLayers()
    }

    fun setOutlineColor(@ColorInt color: Int) {
        pointLayer.setOutlineColor(color)
    }

    fun add(location: Coordinate) {
        if (location == points.lastOrNull()) {
            return
        }
        points.add(location)
        onPathChanged(points.toList())
        updateLayers()
    }

    fun undo() {
        if (points.isNotEmpty()) {
            points.removeLast()
            onPathChanged(points.toList())
            updateLayers()
        }
    }

    fun clear() {
        points.clear()
        onPathChanged(points.toList())
        updateLayers()
    }

    fun getPoints(): List<Coordinate> {
        return points
    }

    override fun draw(drawer: ICanvasDrawer, map: IMapView) {
        if (!isEnabled) {
            return
        }

        pathLayer.draw(drawer, map)
        pointLayer.draw(drawer, map)
    }

    override fun invalidate() {
        pointLayer.invalidate()
        pathLayer.invalidate()
    }

    override fun onClick(drawer: ICanvasDrawer, map: IMapView, pixel: PixelCoordinate): Boolean {
        if (!isEnabled) {
            return false
        }

        val wasPointClicked = pointLayer.onClick(drawer, map, pixel)

        if (wasPointClicked) {
            return true
        }

        add(map.toCoordinate(pixel))
        return true
    }

    private fun updateLayers() {
        pointLayer.setBeacons(getBeacons())
        pathLayer.setPaths(listOf(getPath()))
    }

    private fun getPath(): MappablePath {
        return MappablePath(
            0,
            getBeacons(),
            pathColor,
            LineStyle.Solid
        )
    }

    private fun getBeacons(): List<Beacon> {
        return points.mapIndexed { index, coordinate ->
            Beacon(
                index.toLong(),
                "",
                coordinate,
                color = pathColor,
                temporary = true
            )
        }
    }
}