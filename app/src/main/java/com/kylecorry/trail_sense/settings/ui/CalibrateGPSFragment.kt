package com.kylecorry.trail_sense.settings.ui

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.kylecorry.andromeda.core.system.Intents
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.andromeda.sense.location.GPS
import com.kylecorry.andromeda.sense.location.IGPS
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.sensors.CustomGPS
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.sensors.overrides.CachedGPS
import com.kylecorry.trail_sense.shared.sensors.overrides.OverrideGPS
import com.kylecorry.trail_sense.shared.views.CoordinatePreference


class CalibrateGPSFragment : AndromedaPreferenceFragment() {

    private val prefs by lazy { UserPreferences(requireContext()) }
    private val sensorService by lazy { SensorService(requireContext()) }
    private val throttle = Throttle(20)

    private lateinit var locationTxt: Preference
    private lateinit var autoLocationSwitch: SwitchPreferenceCompat
    private lateinit var permissionBtn: Preference
    private lateinit var locationOverridePref: CoordinatePreference
    private var clearCacheBtn: Preference? = null
    private val formatService by lazy { FormatService.getInstance(requireContext()) }

    private lateinit var gps: IGPS
    private lateinit var realGps: IGPS

    private var wasUsingRealGPS = false
    private var wasUsingCachedGPS = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.gps_calibration, rootKey)
        setIconColor(Resources.androidTextColorSecondary(requireContext()))
        wasUsingRealGPS = shouldUseRealGPS()
        wasUsingCachedGPS = shouldUseCachedGPS()
        gps = sensorService.getGPS()
        realGps = getRealGPS()
        bindPreferences()
    }

    private fun bindPreferences() {
        locationTxt = findPreference(getString(R.string.pref_holder_location))!!
        autoLocationSwitch = findPreference(getString(R.string.pref_auto_location))!!
        permissionBtn = findPreference(getString(R.string.pref_gps_request_permission))!!
        locationOverridePref = findPreference(getString(R.string.pref_gps_override))!!
        clearCacheBtn = preference(R.string.pref_gps_clear_cache)
        locationOverridePref.setGPS(realGps)
        locationOverridePref.setLocation(prefs.locationOverride)
        locationOverridePref.setTitle(getString(R.string.pref_gps_override_title))

        locationOverridePref.setOnLocationChangeListener {
            prefs.locationOverride = it ?: Coordinate.zero
            resetGPS()
            update()
        }

        autoLocationSwitch.setOnPreferenceClickListener {
            locationOverridePref.isEnabled = isLocationOverrideEnabled()
            resetGPS()
            update()
            true
        }

        permissionBtn.setOnPreferenceClickListener {
            val intent = Intents.appSettings(requireContext())
            getResult(intent) { _, _ ->
                // Do nothing
            }
            true
        }

        onClick(clearCacheBtn) {
            clearCache()
        }

        update()
    }

    override fun onResume() {
        super.onResume()
        if (gps.hasValidReading) {
            update()
        }
        startGPS()
    }

    override fun onPause() {
        super.onPause()
        stopGPS()
        locationOverridePref.pause()
    }

    private fun resetGPS() {
        stopGPS()
        gps = sensorService.getGPS()
        startGPS()
    }

    private fun startGPS() {
        gps.start(this::onLocationUpdate)
    }

    private fun stopGPS() {
        gps.stop(this::onLocationUpdate)
    }


    private fun onLocationUpdate(): Boolean {
        update()
        return true
    }

    private fun resetRealGPS() {
        locationOverridePref.pause()
        realGps = getRealGPS()
        locationOverridePref.setGPS(realGps)
    }

    private fun getRealGPS(): IGPS {
        return when {
            shouldUseRealGPS() -> {
                CustomGPS(requireContext())
            }
            shouldUseCachedGPS() -> {
                CachedGPS(requireContext())
            }
            else -> {
                OverrideGPS(requireContext())
            }
        }
    }

    private fun isLocationOverrideEnabled(): Boolean {
        // Either there are no other options for GPS or auto location is off
        return !isAutoGPSPreferenceEnabled() || !prefs.useAutoLocation
    }

    private fun isAutoGPSPreferenceEnabled(): Boolean {
        // Only disable when GPS permission is denied
        return sensorService.hasLocationPermission()
    }

    private fun shouldUseCachedGPS(): Boolean {
        // Permission is granted, but GPS is disabled
        return sensorService.hasLocationPermission() && !GPS.isAvailable(requireContext())
    }

    private fun shouldUseRealGPS(): Boolean {
        // When both permission is granted and GPS is enabled
        return GPS.isAvailable(requireContext())
    }

    private fun clearCache() {
        val cache = PreferencesSubsystem.getInstance(requireContext()).preferences
        cache.remove(CustomGPS.LAST_ALTITUDE)
        cache.remove(CustomGPS.LAST_UPDATE)
        cache.remove(CustomGPS.LAST_SPEED)
        cache.remove(CustomGPS.LAST_LONGITUDE)
        cache.remove(CustomGPS.LAST_LATITUDE)
    }

    private fun update() {
        if (throttle.isThrottled()) {
            return
        }

        val useReal = shouldUseRealGPS()
        val useCached = shouldUseCachedGPS()

        if (useReal != wasUsingRealGPS || useCached != wasUsingCachedGPS) {
            resetRealGPS()
            resetGPS()
            wasUsingCachedGPS = useCached
            wasUsingRealGPS = useReal
        }


        permissionBtn.isVisible = !isAutoGPSPreferenceEnabled()
        autoLocationSwitch.isEnabled = isAutoGPSPreferenceEnabled()
        locationOverridePref.isEnabled = isLocationOverrideEnabled()

        locationTxt.summary = formatService.formatLocation(gps.location)
    }


}