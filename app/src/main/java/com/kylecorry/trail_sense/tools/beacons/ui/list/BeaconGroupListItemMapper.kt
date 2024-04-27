package com.kylecorry.trail_sense.tools.beacons.ui.list

import android.content.Context
import com.kylecorry.andromeda.views.list.ListItem
import com.kylecorry.andromeda.views.list.ListItemMapper
import com.kylecorry.andromeda.views.list.ListMenuItem
import com.kylecorry.andromeda.views.list.ResourceListIcon
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.tools.beacons.domain.BeaconGroup

class BeaconGroupListItemMapper(
    private val context: Context,
    private val actionHandler: (BeaconGroup, BeaconGroupAction) -> Unit
) : ListItemMapper<BeaconGroup> {
    override fun map(value: BeaconGroup): ListItem {
        return toListItem(value, context) { actionHandler(value, it) }
    }

    private fun toListItem(
        group: BeaconGroup,
        context: Context,
        action: (BeaconGroupAction) -> Unit
    ): ListItem {
        return ListItem(
            -group.id, // Negative to distinguish it from beacons
            title = group.name,
            icon = ResourceListIcon(R.drawable.ic_beacon_group, AppColor.Gray.color),
            subtitle = context.resources.getQuantityString(
                R.plurals.beacon_group_summary,
                group.count,
                group.count
            ),
            menu = getMenu(context, action)
        ) {
            action(BeaconGroupAction.Open)
        }
    }

    private fun getMenu(
        context: Context,
        action: (BeaconGroupAction) -> Unit
    ): List<ListMenuItem> {
        return listOf(
            ListMenuItem(context.getString(R.string.rename)) { action(BeaconGroupAction.Edit) },
            ListMenuItem(context.getString(R.string.move_to)) { action(BeaconGroupAction.Move) },
            ListMenuItem(context.getString(R.string.delete)) { action(BeaconGroupAction.Delete) },
        )
    }
}