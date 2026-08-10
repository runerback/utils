package com.runerback.openposeeditor.export

import com.runerback.openposeeditor.render.Camera
import com.runerback.openposeeditor.skeleton.Skeleton

interface KeypointExporter {
    fun export(skeleton: Skeleton, camera: Camera, width: Int, height: Int): String
}
