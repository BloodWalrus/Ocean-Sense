package com.kylecorry.trail_sense.navigation.paths.infrastructure

import android.content.Context
import com.kylecorry.andromeda.core.coroutines.onDefault
import com.kylecorry.andromeda.notify.Notify
import com.kylecorry.trail_sense.navigation.paths.infrastructure.alerts.BacktrackAlerter
import com.kylecorry.trail_sense.navigation.paths.infrastructure.persistence.PathService
import com.kylecorry.trail_sense.navigation.paths.infrastructure.services.BacktrackService
import com.kylecorry.trail_sense.shared.UserPreferences
import kotlinx.coroutines.runBlocking

object BacktrackScheduler {

    suspend fun restart(context: Context) {
        val prefs = UserPreferences(context)
        if (prefs.backtrackEnabled) {
            stop(context)
            start(context, false)
        }
    }

    suspend fun start(context: Context, startNewPath: Boolean) = onDefault {
        if (startNewPath) {
            PathService.getInstance(context).endBacktrackPath()
        }

        if (!BacktrackIsAvailable().isSatisfiedBy(context)) {
            return@onDefault
        }

        BacktrackService.start(context)
    }

    fun stop(context: Context) {
        BacktrackService.stop(context)
        Notify.cancel(context, BacktrackAlerter.NOTIFICATION_ID)
    }

    fun isOn(context: Context): Boolean {
        return BacktrackIsEnabled().isSatisfiedBy(context)
    }

    fun isDisabled(context: Context): Boolean {
        return BacktrackIsAvailable().not().isSatisfiedBy(context)
    }
}