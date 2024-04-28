package com.kylecorry.trail_sense.tools.turn_back.infrastructure.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.kylecorry.andromeda.background.IOneTimeTaskScheduler
import com.kylecorry.andromeda.background.OneTimeTaskSchedulerFactory
import com.kylecorry.andromeda.fragments.IPermissionRequester
import com.kylecorry.andromeda.notify.Notify
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.permissions.RequestBackgroundLocationCommand
import com.kylecorry.trail_sense.shared.permissions.requestScheduleExactAlarms
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.tools.astronomy.infrastructure.commands.SunsetAlarmCommand
import com.kylecorry.trail_sense.tools.turn_back.ui.TurnBackFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class TurnBackAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val prefs = PreferencesSubsystem.getInstance(context)

        val returnTime =
            prefs.preferences.getInstant(TurnBackFragment.PREF_TURN_BACK_RETURN_TIME) ?: return

        val formattedReturnTime =
            FormatService.getInstance(context).formatTime(returnTime, includeSeconds = false)

        val notification = Notify.alert(
            context,
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.turn_back_notification_title),
            context.getString(R.string.turn_back_notification_description, formattedReturnTime),
            R.drawable.ic_undo,
            group = NOTIFIATION_GROUP,
            autoCancel = true
        )
        Notify.send(
            context,
            TURN_BACK_NOTIFICATION_ID,
            notification
        )

        // Clear the times
        prefs.preferences.remove(TurnBackFragment.PREF_TURN_BACK_TIME)
        prefs.preferences.remove(TurnBackFragment.PREF_TURN_BACK_RETURN_TIME)
    }

    companion object {

        private const val PI_ID = 238094
        private const val TURN_BACK_NOTIFICATION_ID = 2390423
        const val NOTIFICATION_CHANNEL_ID = "Turn back"
        private const val NOTIFIATION_GROUP = "trail_sense_turn_back"

        fun scheduler(context: Context): IOneTimeTaskScheduler {
            return OneTimeTaskSchedulerFactory(context).exact(
                TurnBackAlarmReceiver::class.java,
                PI_ID
            )
        }

        fun start(context: Context) {
            val prefs = PreferencesSubsystem.getInstance(context)
            val turnBackTime = prefs.preferences.getInstant(
                TurnBackFragment.PREF_TURN_BACK_TIME
            ) ?: return

            val scheduler = scheduler(context)
            scheduler.once(turnBackTime)
        }

        fun stop(context: Context) {
            val scheduler = scheduler(context)
            scheduler.cancel()
        }

        // TODO: Extract this out of the receiver
        /**
         * Enable sunset alerts and request permissions if needed
         */
        fun <T> enable(
            fragment: T,
            shouldRequestPermissions: Boolean
        ) where T : Fragment, T : IPermissionRequester {
            if (shouldRequestPermissions) {
                fragment.requestScheduleExactAlarms {
                    start(fragment.requireContext())
                    RequestBackgroundLocationCommand(fragment).execute()
                }
            } else {
                start(fragment.requireContext())
            }
        }
    }
}