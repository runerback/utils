package com.runerback.openposeeditor.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import com.runerback.openposeeditor.export.OpenPoseExporter
import com.runerback.openposeeditor.skeleton.Skeleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class EditorGLRenderer(assetManager: AssetManager) : GLSurfaceView.Renderer {

    val poseRenderer = PoseRenderer(assetManager)
    var skeleton: Skeleton? = null
    var viewportCamera: Camera = Camera()
    var renderCamera: Camera = Camera()
    var selectedJointId: Int? = null

    private var width: Int = 1
    private var height: Int = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        poseRenderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        poseRenderer.resize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val skel = skeleton ?: return
        poseRenderer.render(viewportCamera, renderCamera, skel, selectedJointId, width, height)
    }

    fun export(
        surfaceView: GLSurfaceView,
        exportWidth: Int,
        exportHeight: Int,
        callback: (Bitmap, String) -> Unit,
    ) {
        surfaceView.queueEvent {
            val skel = skeleton ?: return@queueEvent
            val bitmap = poseRenderer.renderToBitmap(renderCamera, skel, exportWidth, exportHeight)
            val json = OpenPoseExporter().export(skel, renderCamera, exportWidth, exportHeight)
            Handler(Looper.getMainLooper()).post {
                callback(bitmap, json)
            }
        }
    }

    fun release() {
        poseRenderer.release()
    }
}
