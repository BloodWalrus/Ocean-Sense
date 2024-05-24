package com.kylecorry.trail_sense.shared

import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits

fun Distance.toRelativeDistance(useNauticalMiles: Boolean, threshold: Float = 1000f): Distance {
    val metric = units.isMetric()
    val baseDistance =
        if (metric) convertTo(DistanceUnits.Meters) else convertTo(DistanceUnits.Feet)
    val newUnits = if (baseDistance.distance >= threshold) {
        if (useNauticalMiles) {
            DistanceUnits.NauticalMiles
        } else if (metric) {
            DistanceUnits.Kilometers
        } else {
            DistanceUnits.Miles
        }
    } else {
        if (metric) DistanceUnits.Meters else DistanceUnits.Feet
    }
    return convertTo(newUnits)
}

fun DistanceUnits.isMetric(): Boolean {
    return listOf(
        DistanceUnits.Kilometers,
        DistanceUnits.Meters,
        DistanceUnits.Centimeters
    ).contains(this)
}

fun DistanceUnits.isLarge(): Boolean {
    // If it is greater than 100 meters per unit, then it is large
    return meters > 100f
}