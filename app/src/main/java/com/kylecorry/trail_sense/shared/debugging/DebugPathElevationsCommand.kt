package com.kylecorry.trail_sense.shared.debugging

import android.content.Context
import com.kylecorry.andromeda.csv.CSVConvert
import com.kylecorry.trail_sense.shared.io.FileSubsystem
import com.kylecorry.trail_sense.tools.paths.domain.PathPoint
import com.kylecorry.trail_sense.tools.paths.domain.hiking.HikingService

class DebugPathElevationsCommand(
    private val context: Context,
    private val raw: List<PathPoint>,
    private val smoothed: List<PathPoint>
) : DebugCommand() {

    private val hikingService = HikingService()

    override fun executeDebug() {
        val distances = hikingService.getDistances(smoothed.map { it.coordinate })
        val header = listOf(listOf("distance", "raw", "smoothed"))
        val data = header + distances.zip(raw.sortedByDescending { it.id }
            .zip(smoothed)).map {
            listOf(it.first, it.second.first.elevation, it.second.second.elevation)
        }

        FileSubsystem.getInstance(context).writeDebug(
            "path_elevations.csv",
            CSVConvert.toCSV(data)
        )
    }
}