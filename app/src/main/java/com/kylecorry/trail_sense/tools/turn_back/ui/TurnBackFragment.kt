package com.kylecorry.trail_sense.tools.turn_back.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.topics.generic.asLiveData
import com.kylecorry.andromeda.core.ui.setTextDistinct
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.fragments.observe
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.sense.readAll
import com.kylecorry.sol.science.astronomy.SunTimesMode
import com.kylecorry.sol.time.Time.toZonedDateTime
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolTurnBackBinding
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.extensions.withCancelableLoading
import com.kylecorry.trail_sense.shared.hooks.HookTriggers
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.tools.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.tools.turn_back.infrastructure.receivers.TurnBackAlarmReceiver
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

class TurnBackFragment : BoundFragment<FragmentToolTurnBackBinding>() {

    private val prefs by lazy { UserPreferences(requireContext()) }
    private val sharedPrefs by lazy { PreferencesSubsystem.getInstance(requireContext()) }
    private val formatter by lazy { FormatService.getInstance(requireContext()) }
    private val triggers = HookTriggers()
    private val astronomy = AstronomyService()

    private var returnTime by state<Instant?>(null)
    private var turnBackTime by state<Instant?>(null)

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentToolTurnBackBinding {
        return FragmentToolTurnBackBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.edittext.setOnClickListener {
            Pickers.time(
                requireContext(),
                prefs.use24HourTime,
                returnTime?.toZonedDateTime()?.toLocalTime() ?: LocalTime.now()
            ) {
                if (it != null) {
                    val newReturnTime = if (it < LocalTime.now()) {
                        ZonedDateTime.now().plusDays(1).with(it)
                    } else {
                        ZonedDateTime.now().with(it)
                    }

                    setReturnTime(newReturnTime)
                }
            }
        }

        binding.sunsetButton.setOnClickListener {
            inBackground {
                var wasSuccessful = false
                var sunsetTime: ZonedDateTime? = null
                val job = launch {
                    val sensors = SensorService(requireContext())
                    val gps = sensors.getGPS()
                    readAll(listOf(gps), onlyIfInvalid = true)
                    sunsetTime = astronomy.getNextSunset(gps.location, SunTimesMode.Actual)
                        ?.toZonedDateTime()
                    wasSuccessful = true
                }


                Alerts.withCancelableLoading(requireContext(),
                    getString(R.string.loading),
                    onCancel = { job.cancel() }) {
                    job.join()
                    if (wasSuccessful) {
                        setReturnTime(sunsetTime ?: return@withCancelableLoading)
                    }
                }
            }
        }

        binding.cancelButton.setOnClickListener {
            sharedPrefs.preferences.remove(PREF_TURN_BACK_TIME)
            sharedPrefs.preferences.remove(PREF_TURN_BACK_RETURN_TIME)
            TurnBackAlarmReceiver.stop(requireContext())
        }

        sharedPrefs.preferences.onChange.asLiveData()
            .observe(viewLifecycleOwner) {
                if (it == PREF_TURN_BACK_TIME) {
                    turnBackTime = sharedPrefs.preferences.getInstant(PREF_TURN_BACK_TIME)
                } else if (it == PREF_TURN_BACK_RETURN_TIME) {
                    returnTime = sharedPrefs.preferences.getInstant(PREF_TURN_BACK_RETURN_TIME)
                }
            }

        scheduleUpdates(INTERVAL_1_FPS)
    }

    private fun setReturnTime(time: ZonedDateTime) {
        val instant = time.toInstant()
        val newTurnBackTime =
            Instant.now()
                .plus(Duration.between(Instant.now(), instant).dividedBy(2))

        sharedPrefs.preferences.putInstant(PREF_TURN_BACK_TIME, newTurnBackTime)
        sharedPrefs.preferences.putInstant(PREF_TURN_BACK_RETURN_TIME, instant)
        TurnBackAlarmReceiver.enable(this, true)
    }

    override fun onResume() {
        super.onResume()
        returnTime = sharedPrefs.preferences.getInstant(PREF_TURN_BACK_RETURN_TIME)
        turnBackTime = sharedPrefs.preferences.getInstant(PREF_TURN_BACK_TIME)
    }

    override fun onUpdate() {
        super.onUpdate()

        val formattedTurnBackTime = memo("turn_back_time", turnBackTime) {
            turnBackTime?.let { formatter.formatTime(it, includeSeconds = false) }
        }

        val formattedReturnTime = memo("return_time", returnTime) {
            returnTime?.let { formatter.formatTime(it, includeSeconds = false) }
        }

        val formattedRemainingTime =
            memo(
                "remaining_time",
                turnBackTime,
                returnTime,
                triggers.frequency("remaining_time", Duration.ofSeconds(1))
            ) {
                var remaining = turnBackTime?.let { Duration.between(Instant.now(), it) }
                if (remaining?.isNegative == true) {
                    remaining = Duration.ZERO
                }
                remaining?.let {
                    formatter.formatDuration(
                        remaining,
                        includeSeconds = remaining < Duration.ofMinutes(1)
                    )
                }
            }

        effect("instructions", formattedTurnBackTime, formattedRemainingTime, formattedReturnTime) {
            binding.instructions.setTextDistinct(
                if (formattedRemainingTime != null) {
                    getString(
                        R.string.turn_back_instructions,
                        formattedTurnBackTime,
                        formattedRemainingTime,
                        formattedReturnTime
                    )
                } else {
                    getString(R.string.time_not_set)
                }
            )
        }

        effect("edittext", formattedReturnTime) {
            binding.edittext.setText(formattedReturnTime)
        }

        effect("cancel_button", returnTime) {
            binding.cancelButton.isVisible = returnTime != null
        }
    }

    companion object {
        const val PREF_TURN_BACK_TIME = "pref_turn_back_time"
        const val PREF_TURN_BACK_RETURN_TIME = "pref_turn_back_return_time"
    }
}