package com.kylecorry.trail_sense.tools.clock.infrastructure

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kylecorry.andromeda.notify.Notify
import com.kylecorry.trail_sense.main.NotificationChannels
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.navigation.NavigationUtils

class NextMinuteBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val time = intent?.getStringExtra(EXTRA_TIME)
        val notification = Notify.alert(
            context,
            CHANNEL_ID,
            context.getString(R.string.clock_sync_notification, time),
            null,
            R.drawable.ic_tool_clock,
            group = NOTIFICATION_GROUP_CLOCK,
            intent = NavigationUtils.pendingIntent(context, R.id.toolClockFragment)
        )
        Notify.send(context, NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "ClockSync"
        private const val EXTRA_TIME = "extra_time"
        private const val PENDING_INTENT_ID = 632854823
        private const val NOTIFICATION_ID = 49852323
        private const val NOTIFICATION_GROUP_CLOCK = "trail_sense_clock"

        fun intent(context: Context, timeString: String): Intent {
            val i = Intent(context, NextMinuteBroadcastReceiver::class.java)
            i.putExtra(EXTRA_TIME, timeString)
            return i
        }

        fun pendingIntent(context: Context, timeString: String): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                PENDING_INTENT_ID,
                intent(context, timeString),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}