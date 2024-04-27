package com.kylecorry.trail_sense.settings.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.SwitchPreferenceCompat
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.permissions.RequestRemoveBatteryRestrictionCommand
import com.kylecorry.trail_sense.shared.permissions.requestBacktrackPermission
import com.kylecorry.trail_sense.shared.preferences.setupNotificationSetting
import com.kylecorry.trail_sense.tools.paths.infrastructure.services.BacktrackService
import com.kylecorry.trail_sense.tools.paths.infrastructure.subsystem.BacktrackSubsystem
import com.kylecorry.trail_sense.tools.paths.ui.commands.ChangeBacktrackFrequencyCommand
import java.time.Duration

class PathsSettingsFragment : AndromedaPreferenceFragment() {

    private var prefBacktrack: SwitchPreferenceCompat? = null
    private val formatService by lazy { FormatService.getInstance(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.paths_preferences, rootKey)
        prefBacktrack = switch(R.string.pref_backtrack_enabled)

        prefBacktrack?.isEnabled = !(prefs.isLowPowerModeOn && prefs.lowPowerModeDisablesBacktrack)

        prefBacktrack?.setOnPreferenceClickListener {
            val backtrack = BacktrackSubsystem.getInstance(requireContext())
            if (prefs.backtrackEnabled) {
                requestBacktrackPermission { success ->
                    if (success) {
                        inBackground {
                            backtrack.enable(true)
                            RequestRemoveBatteryRestrictionCommand(this@PathsSettingsFragment).execute()
                        }
                    } else {
                        backtrack.disable()
                        prefBacktrack?.isChecked = false
                    }
                }
            } else {
                backtrack.disable()
            }
            true
        }

        val prefBacktrackInterval = preference(R.string.pref_backtrack_interval)
        prefBacktrackInterval?.summary =
            formatService.formatDuration(prefs.backtrackRecordFrequency, includeSeconds = true)

        prefBacktrackInterval?.setOnPreferenceClickListener {
            ChangeBacktrackFrequencyCommand(requireContext(), lifecycleScope) {
                prefBacktrackInterval.summary =
                    formatService.formatDuration(it, includeSeconds = true)
            }.execute()
            true
        }

        val prefBacktrackPathColor = preference(R.string.pref_backtrack_path_color)
        prefBacktrackPathColor?.icon?.setTint(
            prefs.navigation.defaultPathColor.color
        )

        prefBacktrackPathColor?.setOnPreferenceClickListener {
            CustomUiUtils.pickColor(
                requireContext(),
                prefs.navigation.defaultPathColor,
                it.title.toString()
            ) {
                if (it != null) {
                    prefs.navigation.defaultPathColor = it
                    prefBacktrackPathColor.icon?.setTint(it.color)
                }
            }
            true
        }

        val backtrackHistory = preference(R.string.pref_backtrack_history_days)
        backtrackHistory?.summary =
            formatService.formatDays(prefs.navigation.backtrackHistory.toDays().toInt())
        backtrackHistory?.setOnPreferenceClickListener {
            Pickers.number(
                requireContext(),
                it.title.toString(),
                null,
                prefs.navigation.backtrackHistory.toDays().toInt(),
                allowDecimals = false,
                allowNegative = false,
                hint = getString(R.string.days)
            ) { days ->
                if (days != null) {
                    prefs.navigation.backtrackHistory = Duration.ofDays(days.toLong())
                    it.summary = formatService.formatDays(if (days.toInt() > 0) days.toInt() else 1)
                }
            }
            true
        }

        setupNotificationSetting(
            getString(R.string.pref_backtrack_notifications_link),
            BacktrackService.FOREGROUND_CHANNEL_ID,
            getString(R.string.backtrack)
        )
    }

}