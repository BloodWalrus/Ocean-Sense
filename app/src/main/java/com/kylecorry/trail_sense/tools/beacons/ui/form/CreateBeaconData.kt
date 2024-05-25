package com.kylecorry.trail_sense.tools.beacons.ui.form

import com.kylecorry.andromeda.core.specifications.Specification
import com.kylecorry.andromeda.core.system.GeoUri
import com.kylecorry.sol.science.geology.Geology
import com.kylecorry.sol.units.Bearing
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.shared.colors.fromColor
import com.kylecorry.trail_sense.tools.beacons.domain.Beacon
import com.kylecorry.trail_sense.tools.beacons.domain.BeaconIcon

data class CreateBeaconData(
    val id: Long = 0,
    val name: String = "",
    val coordinate: Coordinate? = null,
    val elevation: Distance? = null,
    val createAtDistance: Boolean = false,
    val distanceTo: Distance? = null,
    val bearingTo: Float? = null,
    val bearingIsTrueNorth: Boolean = false,
    val groupId: Long? = null,
    val color: AppColor = AppColor.Blue,
    val notes: String = "",
    val isVisible: Boolean = true,
    val icon: BeaconIcon? = null
) {

    fun toBeacon(
        isComplete: Specification<CreateBeaconData> = IsBeaconFormDataComplete()
    ): Beacon? {
        if (!isComplete.isSatisfiedBy(this)) return null

        val coordinate = if (createAtDistance) {
            val distanceTo = distanceTo?.meters()?.distance?.toDouble() ?: 0.0
            val bearingTo = bearingTo ?: 0f
            val declination = if (!bearingIsTrueNorth) Geology.getGeomagneticDeclination(
                coordinate!!,
                elevation?.meters()?.distance
            ) else 0f
            coordinate!!.plus(distanceTo, Bearing(bearingTo).withDeclination(declination))
        } else {
            coordinate!!
        }

        return Beacon(
            id,
            name,
            coordinate,
            isVisible,
            notes,
            groupId,
            elevation?.meters()?.distance,
            color = color.color,
            icon = icon
        )
    }

    companion object {
        val empty = CreateBeaconData()

        fun from(uri: GeoUri): CreateBeaconData {
            val name = uri.queryParameters.getOrDefault("label", "")
            val coordinate = uri.coordinate
            val elevation = uri.altitude ?: uri.queryParameters.getOrDefault(
                "ele",
                ""
            ).toFloatOrNull()
            val elevationDistance = elevation?.let { Distance.meters(elevation) }
            return CreateBeaconData(
                name = name,
                coordinate = coordinate,
                elevation = elevationDistance
            )
        }

        fun from(beacon: Beacon): CreateBeaconData {
            return CreateBeaconData(
                beacon.id,
                beacon.name,
                beacon.coordinate,
                beacon.elevation?.let { Distance.meters(it) },
                false,
                null,
                null,
                false,
                beacon.parentId,
                AppColor.entries.toTypedArray().fromColor(beacon.color) ?: AppColor.Blue,
                beacon.comment ?: "",
                beacon.visible,
                beacon.icon
            )
        }

    }
}
