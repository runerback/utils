package com.runerback.screenrecorder.util

import java.util.Locale

fun formatElapsedClock(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatDurationText(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0L -> String.format(
            Locale.getDefault(),
            "%d hr %02d min %02d sec",
            hours,
            minutes,
            seconds,
        )
        minutes > 0L -> String.format(
            Locale.getDefault(),
            "%d min %02d sec",
            minutes,
            seconds,
        )
        else -> String.format(Locale.getDefault(), "%d sec", seconds)
    }
}
