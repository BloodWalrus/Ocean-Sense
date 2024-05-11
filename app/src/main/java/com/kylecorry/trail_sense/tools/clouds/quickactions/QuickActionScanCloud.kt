package com.kylecorry.trail_sense.tools.clouds.quickactions

import android.widget.ImageButton
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.QuickActionButton
import com.kylecorry.trail_sense.shared.navigateWithAnimation

class QuickActionScanCloud(button: ImageButton, fragment: Fragment) :
    QuickActionButton(button, fragment) {

    override fun onCreate() {
        super.onCreate()
        button.setImageResource(R.drawable.cloud_scanner)
        CustomUiUtils.setButtonState(button, false)

        button.setOnClickListener {
            fragment.findNavController().navigateWithAnimation(
                R.id.cloudFragment,
                bundleOf("open_scanner" to true),
            )
        }

        button.setOnLongClickListener {
            fragment.findNavController().navigateWithAnimation(
                R.id.cloudFragment
            )
            true
        }
    }


}