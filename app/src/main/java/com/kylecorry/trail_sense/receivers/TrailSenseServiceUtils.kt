package com.kylecorry.trail_sense.receivers

import android.content.Context
import android.os.Build
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.tiles.TileManager
import com.kylecorry.trail_sense.tools.tools.infrastructure.Tools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TrailSenseServiceUtils {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun restartServices(context: Context, isInBackground: Boolean = false) {
        val appContext = context.applicationContext
        coroutineScope.launch {
            if (!isInBackground) {
                ServiceRestartAlerter(appContext).dismiss()
            }

            val tools = Tools.getTools(appContext, false)
            tools.flatMap { it.services }.forEach {
                it.restart(appContext)
            }

            TileManager().setTilesEnabled(
                appContext,
                UserPreferences(appContext).power.areTilesEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            )
        }
    }

    /**
     * Temporarily stops all services (will restart when the app is opened again)
     */
    fun stopServices(context: Context) {
        val appContext = context.applicationContext

        val tools = Tools.getTools(appContext, false)
        tools.flatMap { it.services }.forEach {
            it.stop(appContext)
        }

        TileManager().setTilesEnabled(appContext, false)
    }

}