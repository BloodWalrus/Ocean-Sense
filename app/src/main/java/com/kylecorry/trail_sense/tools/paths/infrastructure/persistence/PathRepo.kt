package com.kylecorry.trail_sense.tools.paths.infrastructure.persistence

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.kylecorry.trail_sense.main.persistence.AppDatabase
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.domain.PathGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PathRepo private constructor(context: Context) : IPathRepo {

    private val pathDao = AppDatabase.getInstance(context).pathDao()
    private val groupDao = AppDatabase.getInstance(context).pathGroupDao()

    override suspend fun add(value: Path): Long {
        return if (value.id != 0L) {
            pathDao.update(PathEntity.from(value))
            value.id
        } else {
            pathDao.insert(PathEntity.from(value))
        }
    }

    override suspend fun delete(value: Path) {
        pathDao.delete(PathEntity.from(value))
    }

    override suspend fun get(id: Long): Path? {
        return pathDao.get(id)?.toPath()
    }

    override fun getLive(id: Long): LiveData<Path?> {
        return pathDao.getLive(id).map { it?.toPath() }
    }

    override suspend fun getAll(): List<Path> {
        return pathDao.getAllSuspend().map { it.toPath() }
    }

    override fun getPaths(): Flow<List<Path>> {
        return pathDao.getAll().map { it.map { path -> path.toPath() } }
    }

    override suspend fun getPathsWithParent(parent: Long?): List<Path> {
        return pathDao.getAllInGroup(parent).map { it.toPath() }
    }

    override suspend fun addGroup(group: PathGroup): Long {
        return if (group.id != 0L) {
            groupDao.update(PathGroupEntity.from(group))
            group.id
        } else {
            groupDao.insert(PathGroupEntity.from(group))
        }
    }

    override suspend fun deleteGroup(group: PathGroup) {
        groupDao.delete(PathGroupEntity.from(group))
    }

    override suspend fun getGroupsWithParent(parent: Long?): List<PathGroup> {
        return groupDao.getAllWithParent(parent).map { it.toPathGroup() }
    }

    override suspend fun getGroup(id: Long): PathGroup? {
        return groupDao.get(id)?.toPathGroup()
    }

    companion object {
        private var instance: PathRepo? = null

        @Synchronized
        fun getInstance(context: Context): PathRepo {
            if (instance == null) {
                instance = PathRepo(context.applicationContext)
            }
            return instance!!
        }
    }
}