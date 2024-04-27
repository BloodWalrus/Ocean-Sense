package com.kylecorry.trail_sense.tools.tides.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.CoroutineAlerts
import com.kylecorry.andromeda.core.coroutines.onMain
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.andromeda.core.ui.setCompoundDrawables
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.sol.science.oceanography.Tide
import com.kylecorry.sol.science.oceanography.TideType
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentTideBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.tools.tides.domain.TideService
import com.kylecorry.trail_sense.tools.tides.domain.TideTable
import com.kylecorry.trail_sense.tools.tides.domain.commands.CurrentTideCommand
import com.kylecorry.trail_sense.tools.tides.domain.commands.DailyTideCommand
import com.kylecorry.trail_sense.tools.tides.domain.commands.LoadTideTableCommand
import com.kylecorry.trail_sense.tools.tides.ui.mappers.TideListItemMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate


class TidesFragment : BoundFragment<FragmentTideBinding>() {

    private val formatService by lazy { FormatService.getInstance(requireContext()) }
    private val tideService = TideService()
    private var table: TideTable? = null
    private lateinit var chart: TideChart
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val units by lazy { prefs.baseDistanceUnits }

    private var current: CurrentTideData? = null
    private var daily: DailyTideData? = null
    private val mapper by lazy { TideListItemMapper(requireContext()) }
    private val currentTideCommand by lazy { CurrentTideCommand(tideService) }
    private val dailyTideCommand by lazy { DailyTideCommand(tideService) }
    private val loadTideCommand by lazy { LoadTideTableCommand(requireContext()) }

    private var currentRefreshTimer = CoroutineTimer {
        inBackground { refreshCurrent() }
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentTideBinding {
        return FragmentTideBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = TideChart(binding.chart)

        binding.tideTitle.rightButton.setOnClickListener {
            findNavController().navigate(R.id.action_tides_to_tideList)
        }

        binding.loading.isVisible = true

        binding.tideListDate.setOnDateChangeListener {
            onDisplayDateChanged()
        }

        scheduleUpdates(20)
    }

    private fun onTideLoaded() {
        if (!isBound) return
        val tide = table ?: return
        binding.tideTitle.subtitle.text = tide.name
            ?: if (tide.location != null) formatService.formatLocation(tide.location) else getString(
                android.R.string.untitled
            )
        inBackground {
            refreshDaily()
            refreshCurrent()
        }
    }

    private fun onDisplayDateChanged() {
        if (!isBound) return
        inBackground {
            refreshDaily()
        }
    }

    private fun updateDaily() {
        if (!isBound) return
        val daily = daily ?: return
        chart.plot(daily.waterLevels, daily.waterLevelRange)
        updateTideList(daily.tides)
    }


    private fun updateTideList(tides: List<Tide>) {
        val updatedTides = tides.map {
            val isEstimated = this.table?.tides?.firstOrNull { t -> t.time == it.time } == null

            if (isEstimated) {
                it.copy(height = null)
            } else {
                it
            }
        }

        binding.tideList.setItems(updatedTides, mapper)
    }

    private fun updateCurrent() {
        if (!isBound) return
        val displayDate = binding.tideListDate.date
        val current = current ?: return
        val daily = daily ?: return

        val name = getTideTypeName(current.type)
        val waterLevel = if (current.waterLevel != null) {
            val height = Distance.meters(current.waterLevel).convertTo(units)
            " (${formatService.formatDistance(height, 2, true)})"
        } else {
            ""
        }
        @SuppressLint("SetTextI18n")
        binding.tideTitle.title.text = name + waterLevel
        val currentLevel = daily.waterLevels.minByOrNull {
            Duration.between(Instant.now(), it.time).abs()
        }
        if (currentLevel != null && displayDate == LocalDate.now()) {
            chart.highlight(currentLevel, daily.waterLevelRange)
        } else {
            chart.removeHighlight()
        }
        binding.tideTitle.title.setCompoundDrawables(
            Resources.dp(requireContext(), 24f).toInt(),
            left = if (current.rising) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
        )
        CustomUiUtils.setImageColor(
            binding.tideTitle.title,
            Resources.androidTextColorSecondary(requireContext())
        )
    }

    private fun loadTideTable() {
        if (!isBound) return
        binding.loading.isVisible = true
        binding.tideList.setItems(emptyList())
        inBackground {
            table = loadTideCommand.execute()
            onMain {
                binding.loading.isVisible = false
                if (table == null) {
                    val cancelled = CoroutineAlerts.dialog(
                        requireContext(),
                        getString(R.string.no_tides),
                        getString(R.string.calibrate_new_tide)
                    )
                    if (!cancelled) {
                        findNavController().navigate(R.id.action_tides_to_tideList)
                    }
                } else {
                    onTideLoaded()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentRefreshTimer.interval(Duration.ofMinutes(1))
        binding.tideListDate.date = LocalDate.now()
        ShowTideDisclaimerCommand(this) {
            loadTideTable()
        }.execute()
    }

    override fun onPause() {
        super.onPause()
        currentRefreshTimer.stop()
    }

    override fun onUpdate() {
        super.onUpdate()
        updateCurrent()
    }

    private suspend fun refreshCurrent() {
        current = getCurrentTideData()
        onMain {
            updateCurrent()
        }
    }

    private suspend fun refreshDaily() {
        daily = getDailyTideData(binding.tideListDate.date)
        onMain {
            updateDaily()
        }
    }

    private suspend fun getCurrentTideData(): CurrentTideData? {
        return table?.let { currentTideCommand.execute(it) }
    }

    private suspend fun getDailyTideData(date: LocalDate): DailyTideData? {
        return table?.let { dailyTideCommand.execute(it, date) }
    }

    private fun getTideTypeName(tideType: TideType?): String {
        return when (tideType) {
            TideType.High -> getString(R.string.high_tide)
            TideType.Low -> getString(R.string.low_tide)
            null -> getString(R.string.half_tide)
        }
    }
}