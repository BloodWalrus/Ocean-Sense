package com.kylecorry.trail_sense.tools.beacons.infrastructure.commands

import android.content.Context
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.andromeda.core.coroutines.onMain
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.tools.beacons.domain.Beacon
import com.kylecorry.trail_sense.tools.beacons.infrastructure.BeaconPickers
import com.kylecorry.trail_sense.tools.beacons.infrastructure.persistence.BeaconService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TODO: Consolidate with move beacon group
class MoveBeaconCommand(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: BeaconService,
    private val onMoved: () -> Unit
) {

    fun execute(beacon: Beacon) {
        scope.launch {
            val result = BeaconPickers.pickGroup(
                context,
                null,
                context.getString(R.string.move),
                initialGroup = beacon.parentId
            )
            if (result.first) {
                return@launch
            }
            val groupId = result.second?.id
            val groupName = onIO {
                service.add(beacon.copy(parentId = groupId))
                result.second?.name ?: context.getString(R.string.no_group)
            }

            onMain {
                Alerts.toast(
                    context,
                    context.getString(R.string.moved_to, groupName)
                )
                onMoved()
            }

        }
    }

}