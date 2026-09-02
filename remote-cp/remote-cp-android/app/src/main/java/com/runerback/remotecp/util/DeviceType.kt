package com.runerback.remotecp.util

import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager

fun detectDeviceType(context: Context): String {
    val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return when {
        uiMode == Configuration.UI_MODE_TYPE_TELEVISION -> "Computer"
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> "Computer"
        context.resources.configuration.smallestScreenWidthDp >= 600 -> "Tablet"
        else -> "Phone"
    }
}
