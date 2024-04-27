package com.kylecorry.trail_sense.tools.paths.domain.waypointcolors

import android.util.Range
import com.kylecorry.sol.math.SolMath.clamp
import com.kylecorry.sol.math.SolMath.norm
import com.kylecorry.trail_sense.shared.scales.IColorScale
import com.kylecorry.trail_sense.tools.paths.domain.PathPoint

class AltitudePointColoringStrategy(
    private val altitudeRange: Range<Float>,
    private val colorScale: IColorScale
) : IPointColoringStrategy {
    override fun getColor(point: PathPoint): Int? {
        val altitude = point.elevation ?: return null
        val ratio = clamp(norm(altitude, altitudeRange.lower, altitudeRange.upper), 0f, 1f)
        return colorScale.getColor(ratio)
    }
}