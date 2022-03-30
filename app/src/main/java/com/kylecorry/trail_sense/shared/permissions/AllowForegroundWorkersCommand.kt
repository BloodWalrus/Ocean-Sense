package com.kylecorry.trail_sense.shared.permissions

import android.content.Context
import com.kylecorry.trail_sense.main.BackgroundWorkerService
import com.kylecorry.trail_sense.shared.commands.Command

class AllowForegroundWorkersCommand(private val context: Context) : Command {

    override fun execute() {
        if (IsBatteryUnoptimized().isSatisfiedBy(context)) {
            BackgroundWorkerService.stop(context)
            return
        }

        if (IsBatteryExemptionRequired().isSatisfiedBy(context)) {
            BackgroundWorkerService.start(context)
        } else {
            BackgroundWorkerService.stop(context)
        }
    }
}