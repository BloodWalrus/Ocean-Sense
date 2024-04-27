package com.kylecorry.trail_sense.tools.paths.infrastructure.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kylecorry.sol.math.Range
import com.kylecorry.sol.science.geology.CoordinateBounds
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.shared.data.Identifiable
import com.kylecorry.trail_sense.tools.paths.domain.LineStyle
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.domain.PathMetadata
import com.kylecorry.trail_sense.tools.paths.domain.PathPointColoringStyle
import com.kylecorry.trail_sense.tools.paths.domain.PathStyle
import java.time.Instant

@Entity(tableName = "paths")
data class PathEntity(
    @ColumnInfo(name = "name") val name: String?,
    // Style
    @ColumnInfo(name = "lineStyle") val lineStyle: LineStyle,
    @ColumnInfo(name = "pointStyle") val pointStyle: PathPointColoringStyle,
    @ColumnInfo(name = "color") val color: AppColor,
    @ColumnInfo(name = "visible") val visible: Boolean,
    // Saved
    @ColumnInfo(name = "temporary") val temporary: Boolean = false,
    // Metadata
    @ColumnInfo(name = "distance") val distance: Float,
    @ColumnInfo(name = "numWaypoints") val numWaypoints: Int,
    @ColumnInfo(name = "startTime") val startTime: Long?,
    @ColumnInfo(name = "endTime") val endTime: Long?,
    // Bounds
    @ColumnInfo(name = "north") val north: Double,
    @ColumnInfo(name = "east") val east: Double,
    @ColumnInfo(name = "south") val south: Double,
    @ColumnInfo(name = "west") val west: Double,
    // Parent
    @ColumnInfo(name = "parentId") val parentId: Long?
) : Identifiable {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    override var id: Long = 0L

    fun toPath(): Path {
        return Path(
            id,
            name,
            PathStyle(
                lineStyle,
                pointStyle,
                color.color,
                visible
            ),
            PathMetadata(
                Distance.meters(distance),
                numWaypoints,
                if (startTime != null && endTime != null) Range(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)) else null,
                CoordinateBounds(north, east, south, west)
            ),
            temporary,
            parentId = parentId
        )
    }

    companion object {
        fun from(path: Path): PathEntity {
            return PathEntity(
                path.name,
                path.style.line,
                path.style.point,
                AppColor.values().firstOrNull { it.color == path.style.color } ?: AppColor.Gray,
                path.style.visible,
                path.temporary,
                path.metadata.distance.meters().distance,
                path.metadata.waypoints,
                path.metadata.duration?.start?.toEpochMilli(),
                path.metadata.duration?.end?.toEpochMilli(),
                path.metadata.bounds.north,
                path.metadata.bounds.east,
                path.metadata.bounds.south,
                path.metadata.bounds.west,
                path.parentId
            ).also {
                it.id = path.id
            }
        }
    }


}