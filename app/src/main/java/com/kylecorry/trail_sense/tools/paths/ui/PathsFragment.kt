package com.kylecorry.trail_sense.tools.paths.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.topics.generic.asLiveData
import com.kylecorry.andromeda.core.topics.generic.replay
import com.kylecorry.andromeda.core.tryOrNothing
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.fragments.observeFlow
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentPathsBinding
import com.kylecorry.trail_sense.shared.FeatureState
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.extensions.onBackPressed
import com.kylecorry.trail_sense.shared.grouping.lists.GroupListManager
import com.kylecorry.trail_sense.shared.grouping.lists.bind
import com.kylecorry.trail_sense.shared.io.IOFactory
import com.kylecorry.trail_sense.shared.permissions.RequestRemoveBatteryRestrictionCommand
import com.kylecorry.trail_sense.shared.permissions.requestBacktrackPermission
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.tools.paths.domain.IPath
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.domain.PathGroup
import com.kylecorry.trail_sense.tools.paths.domain.pathsort.ClosestPathSortStrategy
import com.kylecorry.trail_sense.tools.paths.domain.pathsort.LongestPathSortStrategy
import com.kylecorry.trail_sense.tools.paths.domain.pathsort.MostRecentPathSortStrategy
import com.kylecorry.trail_sense.tools.paths.domain.pathsort.NamePathSortStrategy
import com.kylecorry.trail_sense.tools.paths.domain.pathsort.ShortestPathSortStrategy
import com.kylecorry.trail_sense.tools.paths.infrastructure.PathGroupLoader
import com.kylecorry.trail_sense.tools.paths.infrastructure.persistence.PathService
import com.kylecorry.trail_sense.tools.paths.infrastructure.subsystem.BacktrackSubsystem
import com.kylecorry.trail_sense.tools.paths.ui.commands.ChangeBacktrackFrequencyCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.CreatePathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.CreatePathGroupCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.DeletePathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.DeletePathGroupGroupCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.ExportPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.ImportPathsCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.KeepPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.MergePathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.MoveIPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.RenamePathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.RenamePathGroupGroupCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.SimplifyPathCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.TogglePathVisibilityCommand
import com.kylecorry.trail_sense.tools.paths.ui.commands.ViewPathCommand

class PathsFragment : BoundFragment<FragmentPathsBinding>() {

    private val backtrack by lazy { BacktrackSubsystem.getInstance(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val pathService by lazy {
        PathService.getInstance(requireContext())
    }

    private val gps by lazy {
        SensorService(requireContext()).getGPS()
    }

    private val gpxService by lazy {
        IOFactory().createGpxService(this)
    }

    private var sort = PathSortMethod.MostRecent

    private val listMapper by lazy {
        IPathListItemMapper(
            requireContext(),
            this::handleAction,
            this::handleGroupAction
        )
    }

    private val pathLoader by lazy { PathGroupLoader(pathService) }
    private lateinit var manager: GroupListManager<IPath>

    private var lastRoot: IPath? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pathsList.emptyView = binding.waypointsEmptyText
        manager = GroupListManager(
            lifecycleScope,
            pathLoader,
            lastRoot,
            this::sortPaths
        )

        manager.bind(binding.searchbox)
        manager.bind(binding.pathsList, binding.pathsTitle.title, listMapper) {
            (it as PathGroup?)?.name ?: getString(R.string.paths)
        }

        sort = prefs.navigation.pathSort

        // TODO: See if it is possible to get notified of changes without loading all paths
        observeFlow(pathService.getPaths()) {
            manager.refresh()
        }

        onBackPressed {
            if (!manager.up()) {
                remove()
                findNavController().navigateUp()
            }
        }


        binding.pathsTitle.rightButton.setOnClickListener {
            val defaultSort = prefs.navigation.pathSort
            Pickers.menu(
                it,
                listOf(
                    getString(R.string.sort_by, getSortString(defaultSort)),
                    getString(R.string.export)
                )
            ) { selected ->
                when (selected) {
                    0 -> changeSort()
                    1 -> exportCurrentGroup()
                }
                true
            }
        }

        binding.backtrackPlayBar.setOnSubtitleClickListener {
            ChangeBacktrackFrequencyCommand(requireContext(), lifecycleScope) { onUpdate() }.execute()
        }

        backtrack.state.replay().asLiveData().observe(viewLifecycleOwner) { updateStatusBar() }

        backtrack.frequency.replay().asLiveData().observe(viewLifecycleOwner) { updateStatusBar() }

        binding.backtrackPlayBar.setOnPlayButtonClickListener {
            when (backtrack.getState()) {
                FeatureState.On -> backtrack.disable()
                FeatureState.Off -> {
                    requestBacktrackPermission { success ->
                        if (success) {
                            inBackground {
                                backtrack.enable(true)
                                RequestRemoveBatteryRestrictionCommand(this@PathsFragment).execute()
                            }
                        }
                    }
                }
                FeatureState.Unavailable -> toast(getString(R.string.backtrack_disabled_low_power_toast))
            }
        }

        setupCreateMenu()
    }

    override fun onPause() {
        super.onPause()
        tryOrNothing {
            lastRoot = manager.root
        }
    }

    private fun updateStatusBar() {
        binding.backtrackPlayBar.setState(
            backtrack.getState(),
            backtrack.getFrequency()
        )
    }

    private fun setupCreateMenu() {
        binding.addMenu.setOverlay(binding.overlayMask)
        binding.addMenu.fab = binding.addBtn
        binding.addMenu.hideOnMenuOptionSelected = true
        binding.addMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_import_path_gpx -> importPaths()
                R.id.action_create_path_group -> createGroup()
                R.id.action_create_path -> createPath()
            }
            true
        }
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPathsBinding {
        return FragmentPathsBinding.inflate(layoutInflater, container, false)
    }


    private fun changeSort() {
        val sortOptions = PathSortMethod.values()
        Pickers.item(
            requireContext(),
            getString(R.string.sort),
            sortOptions.map { getSortString(it) },
            sortOptions.indexOf(prefs.navigation.pathSort)
        ) { newSort ->
            if (newSort != null) {
                prefs.navigation.pathSort = sortOptions[newSort]
                sort = sortOptions[newSort]
                onSortChanged()
            }
        }
    }

    private fun getSortString(sortMethod: PathSortMethod): String {
        return when (sortMethod) {
            PathSortMethod.MostRecent -> getString(R.string.most_recent)
            PathSortMethod.Longest -> getString(R.string.longest)
            PathSortMethod.Shortest -> getString(R.string.shortest)
            PathSortMethod.Closest -> getString(R.string.closest)
            PathSortMethod.Name -> getString(R.string.name)
        }
    }

    private fun onSortChanged() {
        manager.refresh(true)
    }

    private fun handleGroupAction(group: PathGroup, action: PathGroupAction) {
        when (action) {
            PathGroupAction.Delete -> deleteGroup(group)
            PathGroupAction.Rename -> renameGroup(group)
            PathGroupAction.Open -> manager.open(group.id)
            PathGroupAction.Move -> movePath(group)
            PathGroupAction.Export -> exportPath(group)
        }
    }

    private fun handleAction(path: Path, action: PathAction) {
        when (action) {
            PathAction.Export -> exportPath(path)
            PathAction.Delete -> deletePath(path)
            PathAction.Merge -> merge(path)
            PathAction.Show -> showPath(path)
            PathAction.Rename -> renamePath(path)
            PathAction.Keep -> keepPath(path)
            PathAction.ToggleVisibility -> togglePathVisibility(path)
            PathAction.Simplify -> simplifyPath(path)
            PathAction.Move -> movePath(path)
            else -> {}
        }
    }

    private fun simplifyPath(path: Path) {
        val command = SimplifyPathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private suspend fun sortPaths(paths: List<IPath>): List<IPath> {
        val strategy = when (sort) {
            PathSortMethod.MostRecent -> MostRecentPathSortStrategy(pathService)
            PathSortMethod.Longest -> LongestPathSortStrategy(pathService)
            PathSortMethod.Shortest -> ShortestPathSortStrategy(pathService)
            PathSortMethod.Closest -> ClosestPathSortStrategy(gps.location, pathService)
            PathSortMethod.Name -> NamePathSortStrategy()
        }
        return strategy.sort(paths)
    }

    private fun togglePathVisibility(path: Path) {
        val command = TogglePathVisibilityCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun renamePath(path: Path) {
        val command = RenamePathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun keepPath(path: Path) {
        val command = KeepPathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun showPath(path: Path) {
        val command = ViewPathCommand(findNavController())
        command.execute(path)
    }

    private fun showPath(id: Long) {
        val command = ViewPathCommand(findNavController())
        command.execute(id)
    }

    private fun importPaths() {
        val command = ImportPathsCommand(
            requireContext(),
            this,
            gpxService,
            pathService,
            prefs.navigation
        )
        command.execute(manager.root?.id)
    }

    private fun exportPath(path: IPath?) {
        val command = ExportPathCommand(requireContext(), this, gpxService, pathService)
        command.execute(path)
    }

    private fun exportCurrentGroup() {
        exportPath(manager.root)
    }

    private fun deletePath(path: Path) {
        val command = DeletePathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun merge(path: Path) {
        val command = MergePathCommand(requireContext(), this, pathService)
        command.execute(path)
    }

    private fun createPath() {
        val command = CreatePathCommand(requireContext(), pathService, prefs.navigation)
        inBackground {
            command.execute(manager.root?.id)?.let {
                showPath(it)
            }
        }
    }

    // Groups
    private fun deleteGroup(group: PathGroup) {
        val command = DeletePathGroupGroupCommand(requireContext(), pathService)
        inBackground {
            command.execute(group)
            manager.refresh()
        }
    }

    private fun renameGroup(group: PathGroup) {
        val command = RenamePathGroupGroupCommand(requireContext(), pathService)
        inBackground {
            command.execute(group)
            manager.refresh()
        }
    }

    private fun createGroup() {
        val command = CreatePathGroupCommand(requireContext(), pathService)
        inBackground {
            command.execute(manager.root?.id)
            manager.refresh()
        }
    }

    private fun movePath(path: IPath) {
        val command = MoveIPathCommand(requireContext(), pathService)
        inBackground {
            val newGroup = command.execute(path)
            if (newGroup?.id != path.parentId) {
                toast(getString(R.string.moved_to, newGroup?.name ?: getString(R.string.no_group)))
                manager.refresh()
            }
        }
    }

}