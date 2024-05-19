package com.kylecorry.trail_sense.tools.flashlight.quickactions

import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.QuickActionButton
import com.kylecorry.trail_sense.shared.navigateWithAnimation

class QuickActionScreenFlashlight(btn: ImageButton, fragment: Fragment) :
    QuickActionButton(btn, fragment) {

    override fun onCreate() {
        super.onCreate()
        setIcon(R.drawable.ic_screen_flashlight)
    }

    override fun onClick() {
        super.onClick()
        fragment.findNavController().navigateWithAnimation(R.id.fragmentToolScreenFlashlight)
    }
}