package com.kylecorry.trail_sense.tools.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.setMargins
import androidx.gridlayout.widget.GridLayout
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.dialog
import com.kylecorry.andromeda.core.capitalizeWords
import com.kylecorry.andromeda.core.coroutines.onDefault
import com.kylecorry.andromeda.core.coroutines.onMain
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.ui.Colors
import com.kylecorry.andromeda.core.ui.setCompoundDrawables
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.list.ListView
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.ceres.list.ListItem
import com.kylecorry.ceres.list.ResourceListIcon
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolsBinding
import com.kylecorry.trail_sense.databinding.ListItemToolBinding
import com.kylecorry.trail_sense.quickactions.ToolsQuickActionBinder
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.extensions.setOnQueryTextListener
import com.kylecorry.trail_sense.tools.guide.infrastructure.UserGuideUtils
import com.kylecorry.trail_sense.tools.ui.sort.AlphabeticalToolSort
import com.kylecorry.trail_sense.tools.ui.sort.CategorizedTools
import com.kylecorry.trail_sense.tools.ui.sort.ToolSortFactory
import com.kylecorry.trail_sense.tools.ui.sort.ToolSortType

class ToolsFragment : BoundFragment<FragmentToolsBinding>() {

    private val tools by lazy { Tools.getTools(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }

    private val pinnedToolManager by lazy { PinnedToolManager(prefs) }

    private val toolSortFactory by lazy { ToolSortFactory(requireContext()) }

    private val pinnedSorter = AlphabeticalToolSort()

    private lateinit var toolListView: ListView<Tool>

    override fun generateBinding(
        layoutInflater: LayoutInflater, container: ViewGroup?
    ): FragmentToolsBinding {
        return FragmentToolsBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolListView = ListView(binding.tools, R.layout.list_item_tool){ view, tool ->
            // TODO: Have this be a tool list item instead
            val binding = ListItemToolBinding.bind(view)
            binding.title.text = tool.name
            binding.icon.setImageResource(tool.icon)
            Colors.setImageColor(binding.icon, Resources.androidTextColorPrimary(requireContext()))

            binding.root.setBackgroundResource(R.drawable.rounded_rectangle)
            binding.root.backgroundTintList = ColorStateList.valueOf(
                Resources.getAndroidColorAttr(
                    requireContext(),
                    android.R.attr.colorBackgroundFloating
                )
            )

            // Set margin
            val margin = Resources.dp(requireContext(), 8f).toInt()
            val params = binding.root.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(margin)
            params.height = Resources.dp(requireContext(), 64f).toInt()

            binding.root.elevation = 2f

            binding.root.setOnClickListener {
                findNavController().navigate(tool.navAction)
            }

            binding.root.setOnLongClickListener {
                Pickers.menu(
                    view, listOf(
                        if (tool.isExperimental) getString(R.string.experimental) else null,
                        if (tool.description != null) getString(R.string.pref_category_about) else null,
                        if (pinnedToolManager.isPinned(tool.id)) {
                            getString(R.string.unpin)
                        } else {
                            getString(R.string.pin)
                        },
                        if (tool.guideId != null) getString(R.string.tool_user_guide_title) else null,
                    )
                ) { selectedIdx ->
                    when (selectedIdx) {
                        1 -> dialog(tool.name, tool.description, cancelText = null)
                        2 -> {
                            if (pinnedToolManager.isPinned(tool.id)) {
                                pinnedToolManager.unpin(tool.id)
                            } else {
                                pinnedToolManager.pin(tool.id)
                            }
                            updatePinnedTools()
                        }

                        3 -> {
                            UserGuideUtils.showGuide(this, tool.guideId!!)
                        }
                    }
                    true
                }
                true
            }
        }

        updatePinnedTools()
        updateTools()

        updateQuickActions()

        binding.settingsBtn.setOnClickListener {
            findNavController().navigate(R.id.action_settings)
        }

        binding.searchbox.setOnQueryTextListener { _, _ ->
            updateTools()
            true
        }

//        binding.pinnedEditBtn.setOnClickListener {
//            // Sort alphabetically, but if the tool is already pinned, put it first
//            val sorted = tools.sortedBy { tool ->
//                if (pinnedToolManager.isPinned(tool.id)) {
//                    "0${tool.name}"
//                } else {
//                    tool.name
//                }
//            }
//            val toolNames = sorted.map { it.name }
//            val defaultSelected = sorted.mapIndexedNotNull { index, tool ->
//                if (pinnedToolManager.isPinned(tool.id)) {
//                    index
//                } else {
//                    null
//                }
//            }
//
//            Pickers.items(
//                requireContext(), getString(R.string.pinned), toolNames, defaultSelected
//            ) { selected ->
//                if (selected != null) {
//                    pinnedToolManager.setPinnedToolIds(selected.map { sorted[it].id })
//                }
//
//                updatePinnedTools()
//            }
//        }
//
//        binding.sortBtn.setOnClickListener {
//            changeToolSort()
//        }

        CustomUiUtils.oneTimeToast(
            requireContext(),
            getString(R.string.tool_long_press_hint_toast),
            "tools_long_press_notice_shown",
            short = false
        )

    }

    // TODO: Add a way to customize this
    private fun updateQuickActions() {
        ToolsQuickActionBinder(this, binding).bind()
    }

    private fun changeToolSort() {
        val sortTypes = ToolSortType.values()
        val sortTypeNames = mapOf(
            ToolSortType.Name to getString(R.string.name),
            ToolSortType.Category to getString(R.string.category)
        )

        Pickers.item(
            requireContext(),
            getString(R.string.sort),
            sortTypes.map { sortTypeNames[it] ?: "" },
            sortTypes.indexOf(prefs.toolSort)
        ) { selectedIdx ->
            if (selectedIdx != null) {
                prefs.toolSort = sortTypes[selectedIdx]
                updateTools()
            }
        }
    }

    private fun updateTools() {
        val filter = binding.searchbox.query

        // Hide pinned when searching
//        if (filter.isNullOrBlank()) {
//            binding.pinned.isVisible = true
//            binding.pinnedTitle.isVisible = true
//            binding.pinnedEditBtn.isVisible = true
//        } else {
//            binding.pinned.isVisible = false
//            binding.pinnedTitle.isVisible = false
//            binding.pinnedEditBtn.isVisible = false
//        }

        val tools = if (filter.isNullOrBlank()) {
            this.tools
        } else {
            this.tools.filter {
                it.name.contains(filter, true) || it.description?.contains(filter, true) == true
            }
        }

        val sorter = toolSortFactory.getToolSort(prefs.toolSort)
//        populateTools(sorter.sort(tools), binding.tools)

//        val items = tools.map {
//            ListItem(
//                it.id,
//                it.name,
//                icon = ResourceListIcon(it.icon, Resources.androidTextColorPrimary(requireContext()))
//            ){
//                findNavController().navigate(it.navAction)
//            }
//        }

//        binding.tools.setItems(items)

        toolListView.setData(tools)
    }

    private fun updatePinnedTools() {
//        val pinned = tools.filter {
//            pinnedToolManager.isPinned(it.id)
//        }
//
//        binding.pinned.isVisible = pinned.isNotEmpty()
//
//        populateTools(pinnedSorter.sort(pinned), binding.pinned)
    }

    private fun populateTools(categories: List<CategorizedTools>, grid: GridLayout) {
        inBackground {
            val viewsToAdd = mutableListOf<View>()

            onDefault {
                if (categories.size == 1) {
                    categories.first().tools.forEach {
                        viewsToAdd.add(createToolButton(it))
                    }
                } else {
                    categories.forEach {
                        viewsToAdd.add(createToolCategoryHeader(it.categoryName))
                        it.tools.forEach {
                            viewsToAdd.add(createToolButton(it))
                        }
                    }
                }
            }

            onMain {
                grid.removeAllViews()
                viewsToAdd.forEach {
                    grid.addView(it)
                }
            }

        }
    }

    private fun createToolCategoryHeader(name: String?): View {
        // TODO: Move this to the class level
        val headerMargins = Resources.dp(requireContext(), 8f).toInt()

        val gridColumnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2, 1f)
        val gridRowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)

        val header = TextView(requireContext())
        header.text = name?.capitalizeWords()
        header.textSize = 14f
        header.setTextColor(
            Resources.getAndroidColorAttr(
                requireContext(), android.R.attr.colorPrimary
            )
        )
        // Bold
        header.paint.isFakeBoldText = true
        header.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = gridColumnSpec
            rowSpec = gridRowSpec
            setMargins(headerMargins, headerMargins * 2, headerMargins, headerMargins)
        }
        header.gravity = Gravity.CENTER_VERTICAL

        return header
    }

    private fun createToolButton(tool: Tool): View {
        val buttonMargins = Resources.dp(requireContext(), 8f).toInt()
        val gridColumnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        val gridRowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)

        val button = ToolButton(requireContext())
        button.text = tool.name.capitalizeWords()
        button.setIconResource(tool.icon)
        button.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = gridColumnSpec
            rowSpec = gridRowSpec
            setMargins(buttonMargins)
        }

        button.setOnClickListener { _ ->
            findNavController().navigate(tool.navAction)
        }

        button.setOnLongClickListener { view ->
            Pickers.menu(
                view, listOf(
                    if (tool.isExperimental) getString(R.string.experimental) else null,
                    if (tool.description != null) getString(R.string.pref_category_about) else null,
                    if (pinnedToolManager.isPinned(tool.id)) {
                        getString(R.string.unpin)
                    } else {
                        getString(R.string.pin)
                    },
                    if (tool.guideId != null) getString(R.string.tool_user_guide_title) else null,
                )
            ) { selectedIdx ->
                when (selectedIdx) {
                    1 -> dialog(tool.name, tool.description, cancelText = null)
                    2 -> {
                        if (pinnedToolManager.isPinned(tool.id)) {
                            pinnedToolManager.unpin(tool.id)
                        } else {
                            pinnedToolManager.pin(tool.id)
                        }
                        updatePinnedTools()
                    }

                    3 -> {
                        UserGuideUtils.showGuide(this, tool.guideId!!)
                    }
                }
                true
            }
            true
        }

        return button
    }

}