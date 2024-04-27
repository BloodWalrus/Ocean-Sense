package com.kylecorry.trail_sense.tools.paths.infrastructure

import com.kylecorry.andromeda.core.coroutines.onIO
import com.kylecorry.trail_sense.shared.grouping.filter.GroupFilter
import com.kylecorry.trail_sense.shared.grouping.persistence.ISearchableGroupLoader
import com.kylecorry.trail_sense.tools.paths.domain.IPath
import com.kylecorry.trail_sense.tools.paths.domain.IPathService
import com.kylecorry.trail_sense.tools.paths.domain.Path

class PathGroupLoader(pathService: IPathService) : ISearchableGroupLoader<IPath> {

    private val loader = pathService.loader()
    private val filter = GroupFilter(loader)

    override suspend fun load(search: String?, group: Long?): List<IPath> = onIO {
        if (search.isNullOrBlank()) {
            getPathsByGroup(group)
        } else {
            getPathsBySearch(search, group)
        }
    }

    private suspend fun getPathsBySearch(search: String, groupFilter: Long?) = onIO {
        filter.filter(groupFilter) {
            (it as Path).name?.contains(search, ignoreCase = true) == true
        }
    }

    private suspend fun getPathsByGroup(group: Long?) = onIO {
        loader.getChildren(group, 1)
    }

    override suspend fun getGroup(id: Long): IPath? {
        return loader.getGroup(id)
    }
}