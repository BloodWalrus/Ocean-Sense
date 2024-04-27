package com.kylecorry.trail_sense.tools.notes

import android.content.Context
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.tools.notes.quickactions.QuickActionCreateNote
import com.kylecorry.trail_sense.tools.tools.infrastructure.ToolRegistration
import com.kylecorry.trail_sense.tools.tools.ui.Tool
import com.kylecorry.trail_sense.tools.tools.ui.ToolCategory
import com.kylecorry.trail_sense.tools.tools.ui.ToolQuickAction
import com.kylecorry.trail_sense.tools.tools.ui.Tools

object NotesToolRegistration : ToolRegistration {
    override fun getTool(context: Context): Tool {
        return Tool(
            Tools.NOTES,
            context.getString(R.string.tool_notes_title),
            R.drawable.ic_tool_notes,
            R.id.fragmentToolNotes,
            ToolCategory.Other,
            guideId = R.raw.guide_tool_notes,
            quickActions = listOf(
                ToolQuickAction(
                    Tools.QUICK_ACTION_CREATE_NOTE,
                    context.getString(R.string.note),
                    ::QuickActionCreateNote
                )
            ),
            additionalNavigationIds = listOf(
                R.id.fragmentToolNotesCreate
            )
        )
    }
}