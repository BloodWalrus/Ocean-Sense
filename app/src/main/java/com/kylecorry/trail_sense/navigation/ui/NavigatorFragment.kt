package com.kylecorry.trail_sense.navigation.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.navigation.domain.Beacon
import com.kylecorry.trail_sense.navigation.domain.LocationMath
import com.kylecorry.trail_sense.navigation.domain.NavigationService
import com.kylecorry.trail_sense.navigation.domain.compass.DeclinationCalculator
import com.kylecorry.trail_sense.navigation.infrastructure.BeaconDB
import com.kylecorry.trail_sense.navigation.infrastructure.LocationSharesheet
import com.kylecorry.trail_sense.navigation.infrastructure.NavigationPreferences
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.doTransaction
import com.kylecorry.trail_sense.shared.sensors.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.roundToInt


class NavigatorFragment(initialDestination: Beacon? = null) : Fragment() {

    private lateinit var compass: ICompass
    private lateinit var gps: IGPS
    private var destination: Beacon? = initialDestination
    private lateinit var altimeter: IAltimeter

    private var units = UserPreferences.DistanceUnits.Meters
    private var useTrueNorth = false
    private var altimeterMode = NavigationPreferences.AltimeterMode.GPS
    private var isRulerSetup = false
    private var areRulerTextViewsAligned = false

    // UI Fields
    private lateinit var azimuthTxt: TextView
    private lateinit var directionTxt: TextView
    private lateinit var locationTxt: TextView
    private lateinit var navigationTxt: TextView
    private lateinit var beaconBtn: FloatingActionButton
    private lateinit var altitudeTxt: TextView
    private lateinit var compassView: CompassView
    private lateinit var userPrefs: UserPreferences
    private lateinit var prefs: NavigationPreferences
    private lateinit var ruler: ConstraintLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_navigator, container, false)

        userPrefs = UserPreferences(context!!)
        prefs = userPrefs.navigation

        compass = if (prefs.useExperimentalCompass) {
            Compass2(context!!)
        } else {
            Compass(context!!)
        }
        gps = GPS(context!!)

        val altimeterMode = prefs.altimeter

        altimeter = when (altimeterMode) {
            NavigationPreferences.AltimeterMode.GPS -> {
                FusedAltimeter(gps, Barometer(context!!))
            }
            NavigationPreferences.AltimeterMode.Barometer -> {
                Barometer(context!!)
            }
            else -> {
                NullBarometer()
            }
        }

        // Assign the UI fields
        azimuthTxt = view.findViewById(R.id.compass_azimuth)
        directionTxt = view.findViewById(R.id.compass_direction)
        locationTxt = view.findViewById(R.id.location)
        navigationTxt = view.findViewById(R.id.navigation)
        beaconBtn = view.findViewById(R.id.beaconBtn)
        altitudeTxt = view.findViewById(R.id.altitude)
        ruler = view.findViewById(R.id.ruler)

        compassView = CompassView(
            view.findViewById(R.id.needle),
            view.findViewById(R.id.destination_star),
            view.findViewById(R.id.azimuth_indicator)
        )

        locationTxt.setOnLongClickListener {
            val sender = LocationSharesheet(context!!)
            sender.send(gps.location)
            true
        }

        beaconBtn.setOnClickListener {
            // Open the navigation select screen
            // Allows user to choose destination from list or add a destination to the list
            if (destination == null) {
                fragmentManager?.doTransaction {
                    this.addToBackStack(null)
                    this.replace(
                        R.id.fragment_holder,
                        BeaconListFragment(
                            BeaconDB(
                                context!!
                            ), gps
                        )
                    )
                }
            } else {
                destination = null
                updateNavigator()
            }

        }
        return view
    }

    override fun onResume() {
        super.onResume()
        compass.start(this::onCompassUpdate)
        gps.start(this::onLocationUpdate)
        altimeter.start(this::onAltitudeUpdate)

        val hasGPS = SensorChecker(context!!).hasGPS()

        if (!hasGPS){
            beaconBtn.hide()
        } else {
            beaconBtn.show()
        }

        useTrueNorth = prefs.useTrueNorth
        altimeterMode = prefs.altimeter
        units = userPrefs.distanceUnits

        if (useTrueNorth) {
            compass.declination = DeclinationCalculator().calculate(gps.location, gps.altitude)
        } else {
            compass.declination = 0f
        }

        // Update the UI
        updateNavigator()
        updateCompassUI()
        updateLocationUI()
    }

    override fun onPause() {
        super.onPause()
        // Stop the low level sensors
        compass.stop(this::onCompassUpdate)
        gps.stop(this::onLocationUpdate)
        altimeter.stop(this::onAltitudeUpdate)
    }

    private fun onCompassUpdate(): Boolean {
        updateCompassUI()
        return true
    }

    private fun onAltitudeUpdate(): Boolean {
        updateLocationUI()
        return true
    }

    private fun onLocationUpdate(): Boolean {
        updateLocationUI()
        return destination != null
    }

    private fun setupRuler() {
        val dpi = resources.displayMetrics.densityDpi
        val height =
            ruler.height / dpi.toDouble() * if (userPrefs.distanceUnits == UserPreferences.DistanceUnits.Meters) 2.54 else 1.0

        if (height == 0.0 || context == null) {
            return
        }

        if (!isRulerSetup) {

            val theme = context!!.theme
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            val arr = context!!.obtainStyledAttributes(typedValue.data, IntArray(1) {
                android.R.attr.textColorPrimary
            })
            val primaryColor = arr.getColor(0, -1)
            arr.recycle()

            ruler.visibility = View.INVISIBLE
            for (i in 0..ceil(height).toInt() * 8) {
                val inches = i / 8.0
                val tv = TextView(context)
                val bar = ImageView(context)
                bar.setBackgroundColor(primaryColor)
                val layoutParams = ConstraintLayout.LayoutParams(1, 4)
                bar.layoutParams = layoutParams
                when {
                    inches % 1.0 == 0.0 -> {
                        bar.layoutParams.width = 48
                        tv.text = inches.toInt().toString()
                    }
                    inches % 0.5 == 0.0 -> {
                        bar.layoutParams.width = 36
                    }
                    inches % 0.25 == 0.0 -> {
                        bar.layoutParams.width = 24
                    }
                    else -> {
                        bar.layoutParams.width = 12
                    }
                }
                bar.y =
                    ruler.height * (inches / height).toFloat() + resources.getDimensionPixelSize(R.dimen.ruler_top)
                if (!tv.text.isNullOrBlank()) {
                    tv.setTextColor(primaryColor)
                    ruler.addView(tv)
                    tv.y = bar.y
                    tv.x =
                        bar.layoutParams.width.toFloat() + resources.getDimensionPixelSize(R.dimen.ruler_label)
                }

                ruler.addView(bar)
            }
        } else if (!areRulerTextViewsAligned) {
            for (view in ruler.children) {
                if (view.height != 0) {
                    areRulerTextViewsAligned = true
                }
                view.y -= view.height / 2f
            }
        }

        isRulerSetup = true

        if (areRulerTextViewsAligned) {
            ruler.visibility = View.VISIBLE
        }
    }

    /**
     * Update the navigator
     */
    private fun updateNavigator() {
        if (destination != null) {
            // Navigating
            gps.start(this::onLocationUpdate)
            beaconBtn.setImageDrawable(context?.getDrawable(R.drawable.ic_cancel))
            updateNavigationUI()
        } else {
            // Not navigating
            beaconBtn.setImageDrawable(context?.getDrawable(R.drawable.ic_beacon))
            updateNavigationUI()
        }
    }

    /**
     * Update the compass
     */
    private fun updateCompassUI() {
        // Update the text boxes
        val azimuth = (compass.bearing.value.roundToInt() % 360).toString().padStart(3, ' ')
        val direction =
            compass.bearing.direction.symbol.toUpperCase(Locale.getDefault()).padEnd(2, ' ')
        azimuthTxt.text = "${azimuth}°"
        directionTxt.text = direction

        // Rotate the compass
        compassView.setAzimuth(compass.bearing.value)

        // Update the navigation
        updateNavigationUI()
        setupRuler()
    }

    /**
     * Update the navigation
     */
    private fun updateNavigationUI() {
        // Determine if the navigator is navigating
        if (destination == null) {
            // Hide the navigation indicators
            compassView.hideBeacon()
            navigationTxt.text = ""
            return
        }

        val declination = DeclinationCalculator()
            .calculate(gps.location, gps.altitude)

        // Retrieve the current location and azimuth
        val location = gps.location

        destination?.apply {
            val vector = NavigationService().navigate(location, this.coordinate)
            val bearing =
                if (!useTrueNorth) vector.direction.withDeclination(-declination) else vector.direction

            compassView.showBeacon(bearing.value)
            navigationTxt.text =
                "${this.name}    (${bearing.value.roundToInt()}°)\n${LocationMath.distanceToReadableString(
                    vector.distance,
                    units
                )}"
        }
    }

    /**
     * Update the current location
     */
    private fun updateLocationUI() {

        // Update the declination value
        compass.declination =
            if (useTrueNorth) DeclinationCalculator().calculate(gps.location, gps.altitude) else 0f


        val location = gps.location

        // Update the latitude, longitude display
        locationTxt.text = location.getFormattedString()
        locationTxt.setTextIsSelectable(true)

        altitudeTxt.text = getAltitudeString(altimeter.altitude, units)

        // Update the navigation display
        updateNavigationUI()
    }

    private fun getAltitudeString(altitude: Float, units: UserPreferences.DistanceUnits): String {
        return if (units == UserPreferences.DistanceUnits.Meters) {
            "${altitude.roundToInt()} m"
        } else {
            "${LocationMath.convertToBaseUnit(altitude, units).roundToInt()} ft"
        }
    }

}
