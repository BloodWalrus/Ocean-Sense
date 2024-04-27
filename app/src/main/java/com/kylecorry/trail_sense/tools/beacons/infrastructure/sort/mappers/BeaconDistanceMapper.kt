package com.kylecorry.trail_sense.tools.beacons.infrastructure.sort.mappers

import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.shared.grouping.mapping.GroupMapper
import com.kylecorry.trail_sense.shared.grouping.persistence.IGroupLoader
import com.kylecorry.trail_sense.tools.beacons.domain.Beacon
import com.kylecorry.trail_sense.tools.beacons.domain.IBeacon

class BeaconDistanceMapper(
    override val loader: IGroupLoader<IBeacon>,
    private val locationProvider: () -> Coordinate
) : GroupMapper<IBeacon, Float, Float>() {

    override suspend fun getValue(item: IBeacon): Float {
        val center = (item as Beacon).coordinate
        return center.distanceTo(locationProvider.invoke())
    }

    override suspend fun aggregate(values: List<Float>): Float {
        return values.minOrNull() ?: Float.POSITIVE_INFINITY
    }

}