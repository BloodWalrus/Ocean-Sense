package com.kylecorry.trail_sense.shared.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.andromeda.sense.location.IGPS
import com.kylecorry.sol.science.geography.CoordinateFormatter.parse
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.tools.beacons.domain.Beacon
import com.kylecorry.trail_sense.tools.beacons.infrastructure.BeaconPickers
import com.kylecorry.trail_sense.tools.beacons.infrastructure.persistence.BeaconService
import com.kylecorry.trail_sense.tools.beacons.infrastructure.sort.ClosestBeaconSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration

class CoordinateInputView(context: Context?, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    private val formatService by lazy { FormatService.getInstance(getContext()) }
    private val sensorService by lazy { SensorService(getContext()) }
    lateinit var gps: IGPS

    private val errorHandler = CoroutineTimer {
        locationEdit.error = getContext().getString(R.string.coordinate_input_invalid_location)
    }

    var coordinate: Coordinate?
        get() = _coordinate
        set(value) {
            _coordinate = value
            if (value == null) {
                locationEdit.setText("")
            } else {
                val formatted = formatService.formatLocation(value)
                locationEdit.setText(formatted)
            }
        }
    private var _coordinate: Coordinate? = null

    private var changeListener: ((coordinate: Coordinate?) -> Unit)? = null
    private var beaconListener: ((beacon: Beacon) -> Unit)? = null
    private var autofillListener: (() -> Unit)? = null

    private lateinit var locationEdit: EditText
    private lateinit var gpsBtn: ImageButton
    private lateinit var beaconBtn: ImageButton
    private lateinit var helpBtn: ImageButton
    private lateinit var gpsLoadingIndicator: ProgressBar

    init {
        context?.let {
            inflate(it, R.layout.view_coordinate_input, this)
            gps = sensorService.getGPS()
            locationEdit = findViewById(R.id.utm)
            gpsLoadingIndicator = findViewById(R.id.gps_loading)
            helpBtn = findViewById(R.id.coordinate_input_help_btn)
            gpsBtn = findViewById(R.id.gps_btn)
            beaconBtn = findViewById(R.id.beacon_btn)

            CustomUiUtils.setButtonState(gpsBtn, true)
            CustomUiUtils.setButtonState(beaconBtn, true)

            gpsBtn.visibility = View.VISIBLE
            gpsLoadingIndicator.visibility = View.GONE

            locationEdit.addTextChangedListener {
                onChange()
            }

            helpBtn.setOnClickListener {
                Alerts.dialog(
                    getContext(),
                    getContext().getString(R.string.location_input_help_title),
                    getContext().getString(R.string.location_input_help),
                    cancelText = null
                )
            }

            beaconBtn.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val beacon = BeaconPickers.pickBeacon(
                        context,
                        sort = ClosestBeaconSort(BeaconService(context), gps::location)
                    ) ?: return@launch
                    coordinate = beacon.coordinate
                    beaconListener?.invoke(beacon)
                }
            }

            gpsBtn.setOnClickListener {
                autofillListener?.invoke()
                autofill()
            }
        }
    }

    private fun onGPSUpdate(): Boolean {
        coordinate = gps.location
        gpsBtn.visibility = View.VISIBLE
        gpsLoadingIndicator.visibility = View.GONE
        beaconBtn.isEnabled = true
        locationEdit.isEnabled = true
        return false
    }

    private fun onChange() {
        val locationText = locationEdit.text.toString()
        _coordinate = Coordinate.parse(locationText)
        errorHandler.stop()
        if (_coordinate == null && locationText.isNotEmpty()) {
            errorHandler.once(Duration.ofSeconds(2))
        } else {
            locationEdit.error = null
        }
        changeListener?.invoke(_coordinate)
    }

    fun pause() {
        gps.stop(this::onGPSUpdate)
        gpsBtn.visibility = View.VISIBLE
        gpsLoadingIndicator.visibility = View.GONE
        locationEdit.isEnabled = true
        errorHandler.stop()
    }

    fun autofill() {
        if (!gpsBtn.isVisible) return
        gpsBtn.visibility = View.GONE
        gpsLoadingIndicator.visibility = View.VISIBLE
        beaconBtn.isEnabled = false
        locationEdit.isEnabled = false
        gps.start(this::onGPSUpdate)
    }

    fun setOnAutoLocationClickListener(listener: (() -> Unit)?) {
        autofillListener = listener
    }

    fun setOnCoordinateChangeListener(listener: ((coordinate: Coordinate?) -> Unit)?) {
        changeListener = listener
    }

    fun setOnBeaconSelectedListener(listener: ((beacon: Beacon) -> Unit)?) {
        beaconListener = listener
    }

}