package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.data.TaskStatus

@StringRes
fun TaskStatus.labelRes(): Int = when (this) {
    TaskStatus.OPEN -> R.string.task_status_open
    TaskStatus.ONGOING -> R.string.task_status_ongoing
    TaskStatus.DONE -> R.string.task_status_done
}

/** Amber used for the "in progress" state everywhere, so it reads the same on any client colour. */
val OngoingAmber = Color(0xFFF59E0B)
