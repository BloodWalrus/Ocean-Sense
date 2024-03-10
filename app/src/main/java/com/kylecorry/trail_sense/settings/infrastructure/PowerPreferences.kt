package com.kylecorry.trail_sense.settings.infrastructure

import android.content.Context
import com.kylecorry.andromeda.preferences.BooleanPreference
import com.kylecorry.trail_sense.R

class PowerPreferences(context: Context) : PreferenceRepo(context) {

    val areTilesEnabled by BooleanPreference(cache, getString(R.string.pref_tiles_enabled), true)

    val autoLowPower by BooleanPreference(cache, getString(R.string.pref_auto_low_power), false)

    var userEnabledLowPower by BooleanPreference(cache, "pref_auto_low_power_user", false)

    var startOnBoot by BooleanPreference(
        cache,
        context.getString(R.string.pref_start_on_boot),
        true
    )
    var enableBatteryLog by BooleanPreference(
        cache,
        context.getString(R.string.pref_battery_log_enabled),
        true
    )

}