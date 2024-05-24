package com.kylecorry.trail_sense.tools.lightning.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.andromeda.core.ui.setCompoundDrawables
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.sol.science.meteorology.Meteorology
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.DistanceUnits
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolLightningBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.DistanceUtils.toRelativeDistance
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.Units
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.colors.AppColor
import com.kylecorry.trail_sense.tools.lightning.domain.LightningStrike
import com.kylecorry.trail_sense.tools.lightning.infrastructure.persistence.LightningRepo
import java.time.Instant

class FragmentToolLightning : BoundFragment<FragmentToolLightningBinding>() {
    private val formatService by lazy { FormatService.getInstance(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val repo by lazy { LightningRepo.getInstance(requireContext()) }

    private lateinit var units: DistanceUnits

    private var lightningTime: Instant? = null
    private var strike: Reading<LightningStrike>? = null
    private var lastStrike: Reading<LightningStrike>? = null

    private val timer = CoroutineTimer {
        updateUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lightningTitle.subtitle.setCompoundDrawables(
            Resources.dp(requireContext(), 24f).toInt(),
            left = R.drawable.ic_alert
        )

        CustomUiUtils.setImageColor(binding.lightningTitle.subtitle, AppColor.Red.color)

        binding.lightningTitle.subtitle.isVisible = false

        binding.startBtn.setOnClickListener {
            if (lightningTime == null) {
                onLightning()
            } else {
                onThunder()
            }
        }
    }

    private fun onLightning() {
        lightningTime = Instant.now()
        binding.startBtn.setImageResource(R.drawable.ic_thunder)
        binding.startBtn.setText(getString(R.string.thunder))
        binding.startBtn.setState(true)
    }

    private fun onThunder() {
        lightningTime?.let { record(it) }
        reset(false)
    }

    private fun reset(clearDistance: Boolean) {
        lightningTime = null
        if (clearDistance) {
            binding.lightningTitle.title.text = ""
            binding.lightningTitle.subtitle.isVisible = false
        }
        binding.startBtn.setImageResource(R.drawable.ic_torch_on)
        binding.startBtn.setText(getString(R.string.lightning))
        binding.startBtn.setState(false)
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentToolLightningBinding {
        return FragmentToolLightningBinding.inflate(layoutInflater, container, false)
    }

    override fun onResume() {
        super.onResume()
        units = prefs.baseDistanceUnits
        reset(true)
        loadLastStrike()
        timer.interval(20)
    }

    override fun onPause() {
        super.onPause()
        timer.stop()
    }

    private fun loadLastStrike() {
        inBackground {
            lastStrike = repo.getLast()
        }
    }

    private fun updateUI() {
        // Distance of current strike
        val current = lightningTime?.let { getDistance(it) } ?: strike?.value?.distance
        current?.let {
            val distance = it
                .convertTo(units)
                .toRelativeDistance(prefs.useNauticalMiles)
            binding.lightningTitle.title.text = formatService.formatDistance(
                distance,
                Units.getDecimalPlaces(distance.units),
                false
            )
            binding.lightningTitle.subtitle.isVisible =
                Meteorology.isLightningStrikeDangerous(distance)
        }

        // Distance of previous strike
        lastStrike?.let {
            val distance = it.value.distance
                .convertTo(units)
                .toRelativeDistance(prefs.useNauticalMiles)

            val lastTime = getString(
                R.string.last_lightning_strike_at, formatService.formatTime(
                    it.time,
                    includeSeconds = false
                )
            )

            val lastDistance = formatService.formatDistance(
                distance,
                Units.getDecimalPlaces(distance.units),
                false
            )

            binding.previousStrike.text =
                getString(R.string.dash_separated_pair, lastTime, lastDistance)
        }
    }

    private fun record(lightning: Instant) {
        val distance = getDistance(lightning)
        inBackground {
            val currentStrike = Reading(LightningStrike(0, distance), Instant.now())
            strike?.let { lastStrike = it }
            strike = currentStrike
            repo.add(currentStrike)
        }
    }

    private fun getDistance(lightning: Instant): Distance {
        return Distance.meters(Meteorology.getLightningStrikeDistance(lightning, Instant.now()))
    }

}