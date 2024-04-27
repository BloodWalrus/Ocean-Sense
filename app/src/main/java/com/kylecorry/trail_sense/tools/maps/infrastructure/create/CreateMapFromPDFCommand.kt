package com.kylecorry.trail_sense.tools.maps.infrastructure.create

import android.content.Context
import android.net.Uri
import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.andromeda.pdf.GeospatialPDFParser
import com.kylecorry.andromeda.pdf.PDFRenderer
import com.kylecorry.sol.math.geometry.Size
import com.kylecorry.trail_sense.shared.io.FileSubsystem
import com.kylecorry.trail_sense.tools.maps.domain.MapCalibration
import com.kylecorry.trail_sense.tools.maps.domain.MapCalibrationPoint
import com.kylecorry.trail_sense.tools.maps.domain.MapMetadata
import com.kylecorry.trail_sense.tools.maps.domain.MapProjectionType
import com.kylecorry.trail_sense.tools.maps.domain.PercentCoordinate
import com.kylecorry.trail_sense.tools.maps.domain.PhotoMap
import com.kylecorry.trail_sense.tools.maps.infrastructure.IMapRepo
import java.io.IOException
import java.util.UUID

class CreateMapFromPDFCommand(private val context: Context, private val repo: IMapRepo, private val name: String) {

    private val files = FileSubsystem.getInstance(context)

    suspend fun execute(uri: Uri): PhotoMap? = onIO {
        val filename = "maps/" + UUID.randomUUID().toString() + ".webp"
        val calibrationPoints = mutableListOf<MapCalibrationPoint>()
        var projection = MapProjectionType.CylindricalEquidistant

        val parser = GeospatialPDFParser()
        val metadata = files.stream(uri)?.use {
            parser.parse(it)
        }
        val maxSize = 2048
        val (bp, scale) = PDFRenderer().toBitmap(context, uri, maxSize = maxSize)
            ?: return@onIO null

        if (metadata != null && metadata.points.size >= 4) {
            val points = listOf(metadata.points[1], metadata.points[3]).map {
                MapCalibrationPoint(
                    it.second,
                    PercentCoordinate(scale * it.first.x / bp.width, scale * it.first.y / bp.height)
                )
            }
            calibrationPoints.addAll(points)
        }

        val projectionName = metadata?.projection?.projection
        if (projectionName == null || projectionName.contains("mercator", true)) {
            projection = MapProjectionType.Mercator
        }

        try {
            files.save(filename, bp, recycleOnSave = true)
        } catch (e: IOException) {
            return@onIO null
        }

        val imageSize = files.imageSize(filename)
        val fileSize = files.size(filename)

        val map = PhotoMap(
            0,
            name,
            filename,
            MapCalibration(
                calibrationPoints.isNotEmpty(),
                calibrationPoints.isNotEmpty(),
                0f,
                calibrationPoints
            ),
            MapMetadata(
                Size(imageSize.width.toFloat(), imageSize.height.toFloat()),
                fileSize,
                projection = projection
            )
        )

        val id = repo.addMap(map)
        map.copy(id = id)
    }

}