package com.kylecorry.trail_sense.tools.paths.ui.commands

import android.content.Context
import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.andromeda.core.coroutines.onMain
import com.kylecorry.andromeda.pickers.CoroutinePickers
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.tools.paths.domain.IPathService
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.domain.PathMetadata
import com.kylecorry.trail_sense.tools.paths.infrastructure.persistence.IPathPreferences
import com.kylecorry.trail_sense.tools.paths.infrastructure.persistence.PathService


class CreatePathCommand(
    private val context: Context,
    private val pathService: IPathService = PathService.getInstance(context),
    private val pathPreferences: IPathPreferences = UserPreferences(context).navigation
) {

    suspend fun execute(parentId: Long?): Long? {
        val name = onMain {
            CoroutinePickers.text(
                context,
                context.getString(R.string.path),
                hint = context.getString(R.string.name)
            )
        } ?: return null

        return onIO {
            pathService.addPath(
                Path(
                    0,
                    name,
                    pathPreferences.defaultPathStyle,
                    PathMetadata.empty,
                    parentId = parentId
                )
            )
        }
    }
}