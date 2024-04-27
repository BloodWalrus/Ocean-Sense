package com.kylecorry.trail_sense.tools.tools.infrastructure

import android.widget.ImageButton
import com.kylecorry.andromeda.fragments.AndromedaFragment
import com.kylecorry.trail_sense.shared.QuickActionButton

data class ToolQuickAction(
    val id: Int,
    val name: String,
    val create: (button: ImageButton, fragment: AndromedaFragment) -> QuickActionButton
)