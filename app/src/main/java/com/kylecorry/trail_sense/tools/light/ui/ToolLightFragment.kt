package com.kylecorry.trail_sense.tools.light.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.observe
import com.kylecorry.sol.science.optics.Optics
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolLightBinding
import com.kylecorry.trail_sense.shared.DistanceUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.sensors.SensorService
import kotlin.math.max

class ToolLightFragment : BoundFragment<FragmentToolLightBinding>() {

    private val lightSensor by lazy { SensorService(requireContext()).getLightSensor() }
    private val formatService by lazy { FormatService.getInstance(requireContext()) }
    private var maxLux = 0f

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentToolLightBinding {
        return FragmentToolLightBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observe(lightSensor) { updateLight() }

        binding.resetBtn.setOnClickListener {
            maxLux = 0f
            updateLight()
        }

        binding.beamDistance.setOnValueChangeListener {
            maxLux = 0f
            if (it != null) {
                binding.lightChart.setDistanceUnits(it.units)
            }
            updateLight()
        }

        binding.beamDistance.units =
            formatService.sortDistanceUnits(DistanceUtils.hikingDistanceUnits)
    }

    private fun updateLight() {
        binding.lightTitle.title.text = formatService.formatLux(lightSensor.illuminance)
        maxLux = max(lightSensor.illuminance, maxLux)

        val distance = binding.beamDistance.value
        if (distance == null) {
            binding.lightTitle.subtitle.text = ""
            binding.beamDistanceText.text = ""
            binding.lightChart.setCandela(0f)
            return
        }

        val candela = Optics.luxToCandela(maxLux, distance)
        val beamDist = Optics.lightBeamDistance(candela).convertTo(distance.units)

        binding.lightTitle.subtitle.text = formatService.formatCandela(candela)
        binding.beamDistanceText.text =
            getString(R.string.beam_distance, formatService.formatDistance(beamDist))
        binding.lightChart.setCandela(candela)
    }
}