package com.kylecorry.trail_sense.tools.paths.domain.point_finder

import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.shared.specifications.InGeofenceSpecification
import com.kylecorry.trail_sense.tools.paths.domain.PathPoint

class NextPathPointNavigator :
    IPathPointNavigator {
    override suspend fun getNextPoint(path: List<PathPoint>, location: Coordinate): PathPoint? {
        // TODO: This doesn't take into consideration which points you've already reached - if the path is a out and back type, it will not work properly
        val line = NearestPathLineCalculator().calculate(location, path) ?: return null
        return if (isAtPoint(location, line.second)) {
            line.second
        } else {
            val idx = path.indexOf(line.second)
            path.getOrNull(idx + 1)
        }
    }

    private fun isAtPoint(location: Coordinate, point: PathPoint): Boolean {
        val specification = InGeofenceSpecification(
            point.coordinate, Distance.meters(
                AT_LOCATION_RADIUS
            )
        )
        return specification.isSatisfiedBy(location)
    }

    companion object {
        private const val AT_LOCATION_RADIUS = 30f
    }

}