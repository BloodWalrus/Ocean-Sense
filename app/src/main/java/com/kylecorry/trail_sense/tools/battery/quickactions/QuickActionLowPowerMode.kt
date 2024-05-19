package com.kylecorry.trail_sense.tools.battery.quickactions

import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.QuickActionButton
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.tools.battery.infrastructure.LowPowerMode

class QuickActionLowPowerMode(button: ImageButton, fragment: Fragment) :
    QuickActionButton(button, fragment) {

    private val lowerPowerMode by lazy { LowPowerMode(context) }
    private val prefs by lazy { UserPreferences(context) }

    private fun update() {
        setState(lowerPowerMode.isEnabled())
    }

    override fun onCreate() {
        super.onCreate()
        setIcon(R.drawable.ic_tool_battery)
        update()
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    override fun onClick() {
        super.onClick()
        if (lowerPowerMode.isEnabled()) {
            prefs.power.userEnabledLowPower = false
            lowerPowerMode.disable(fragment.activity)
        } else {
            prefs.power.userEnabledLowPower = true
            lowerPowerMode.enable(fragment.activity)
        }
    }
}