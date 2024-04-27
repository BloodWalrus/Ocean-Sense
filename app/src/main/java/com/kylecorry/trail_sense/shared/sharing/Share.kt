package com.kylecorry.trail_sense.shared.sharing

import androidx.fragment.app.Fragment
import com.kylecorry.andromeda.fragments.show
import com.kylecorry.sol.science.geography.CoordinateFormat
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.tools.navigation.infrastructure.share.LocationCopy
import com.kylecorry.trail_sense.tools.navigation.infrastructure.share.LocationGeoSender
import com.kylecorry.trail_sense.tools.navigation.infrastructure.share.LocationQRSender
import com.kylecorry.trail_sense.tools.navigation.infrastructure.share.LocationSharesheet

object Share {

    fun shareLocation(
        fragment: Fragment,
        location: Coordinate,
        format: CoordinateFormat = UserPreferences(fragment.requireContext()).navigation.coordinateFormat,
        title: String = fragment.getString(R.string.location)
    ) {
        val locationSenders = mapOf(
            ShareAction.Copy to LocationCopy(fragment.requireContext()),
            ShareAction.QR to LocationQRSender(fragment),
            ShareAction.Maps to LocationGeoSender(fragment.requireContext()),
            ShareAction.Send to LocationSharesheet(fragment.requireContext())
        )

        share(
            fragment,
            title,
            listOf(ShareAction.Copy, ShareAction.QR, ShareAction.Send, ShareAction.Maps)
        ) {
            it?.let {
                locationSenders[it]?.send(location, format)
            }
        }
    }

    fun share(
        fragment: Fragment,
        title: String,
        actions: List<ShareAction>,
        onAction: (action: ShareAction?) -> Unit
    ) {

        val titles = mapOf(
            ShareAction.Copy to fragment.getString(androidx.preference.R.string.copy),
            ShareAction.QR to fragment.getString(R.string.qr_code),
            ShareAction.Send to fragment.getString(R.string.share_action_send),
            ShareAction.Maps to fragment.getString(R.string.maps),
            ShareAction.File to fragment.getString(R.string.file)
        )

        val icons = mapOf(
            ShareAction.Copy to R.drawable.ic_copy,
            ShareAction.QR to R.drawable.ic_qr_code,
            ShareAction.Send to R.drawable.ic_send,
            ShareAction.Maps to R.drawable.maps,
            ShareAction.File to R.drawable.ic_file
        )

        val actionItems = actions.sortedBy { it.ordinal }.map {
            ActionItem(titles[it] ?: "", icons[it] ?: R.drawable.ic_send) {
                onAction(it)
            }
        }

        actions(fragment, title, actionItems) {
            onAction(null)
        }
    }

    fun actions(
        fragment: Fragment,
        title: String,
        actions: List<ActionItem>,
        noActionSelected: () -> Unit
    ) {
        var called = false

        val customOnAction = { action: ActionItem?, sheet: ActionSheet ->
            if (!called) {
                called = true
                if (action != null) {
                    sheet.dismiss()
                    action.action()
                } else {
                    noActionSelected()
                }
            }
        }
        val sheet = ActionSheet(title, actions, customOnAction)
        sheet.show(fragment)
    }

}

enum class ShareAction {
    Copy,
    QR,
    Maps,
    Send,
    File
}