package com.kylecorry.trail_sense.tools.beacons.infrastructure.sort.mappers

import com.kylecorry.trail_sense.shared.grouping.mapping.GroupMapper
import com.kylecorry.trail_sense.shared.grouping.persistence.IGroupLoader
import com.kylecorry.trail_sense.tools.beacons.domain.IBeacon

class BeaconIdMapper(
    override val loader: IGroupLoader<IBeacon>
) : GroupMapper<IBeacon, Long, Long>() {

    override suspend fun getValue(item: IBeacon): Long {
        return item.id
    }

    override suspend fun aggregate(values: List<Long>): Long {
        return values.maxOrNull() ?: 0
    }

}