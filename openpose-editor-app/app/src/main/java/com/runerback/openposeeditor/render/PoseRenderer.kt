package com.runerback.openposeeditor.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import com.runerback.openposeeditor.skeleton.Keypoint
import com.runerback.openposeeditor.skeleton.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PoseRenderer(private val assetManager: AssetManager) {

    private var shader: ShaderProgram? = null
    private var jointMesh: Mesh? = null
    private var boneMesh: Mesh? = null
    private var cameraFrustumMesh: Mesh? = null

    private val modelMatrix = Matrix4f()
    private val mvpMatrix = Matrix4f()
    private val floatArray = FloatArray(16)

    private var framebuffer: Int = 0
    private var renderTexture: Int = 0
    private var depthRenderbuffer: Int = 0
    private var renderWidth: Int = 0
    private var renderHeight: Int = 0

    fun init() {
        GLES30.glClearColor(0.12f, 0.12f, 0.12f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        shader = ShaderProgram(assetManager, "shaders/skeleton.vert", "shaders/skeleton.frag")
        jointMesh = createColoredMesh(PrimitiveMeshes.createSphere(0.025f, 10, 12))
        boneMesh = createColoredMesh(PrimitiveMeshes.createCylinder(0.008f, 1f, 10))
        cameraFrustumMesh = createColoredMesh(PrimitiveMeshes.createCameraFrustum(), GLES30.GL_LINES)
    }

    fun resize(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    fun render(
        viewportCamera: Camera,
        renderCamera: Camera?,
        skeleton: Skeleton,
        selectedJointId: Int?,
        width: Int,
        height: Int,
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawSkeleton(viewportCamera, skeleton, selectedJointId, width, height)
        renderCamera?.let { drawCameraIndicator(viewportCamera, it, width, height) }
    }

    fun renderToBitmap(camera: Camera, skeleton: Skeleton, width: Int, height: Int): Bitmap {
        ensureFramebuffer(width, height)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        drawSkeleton(camera, skeleton, selectedJointId = null, width, height)

        val buffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return flipBitmapVertically(bitmap)
    }

    private fun drawSkeleton(camera: Camera, skeleton: Skeleton, selectedJointId: Int?, width: Int, height: Int) {
        val shader = shader ?: return
        val vpMatrix = camera.getViewProjectionMatrix(width, height)

        shader.use()

        val positions = skeleton.computeWorldPositions()

        // Draw joints
        val jointMesh = jointMesh ?: return
        val cameraDistance = camera.position.distance(camera.target)
        val jointScale = (cameraDistance * 0.25f).coerceAtLeast(0.01f)
        var selectedKeypoint: Keypoint? = null
        var selectedPosition: Vector3f? = null
        for (kp in skeleton.keypoints) {
            if (!kp.enabled) continue
            val pos = positions[kp.id] ?: continue
            if (kp.id == selectedJointId) {
                selectedKeypoint = kp
                selectedPosition = pos
                continue
            }
            drawJoint(jointMesh, pos, colorToFloats(kp.color), jointScale, false, vpMatrix)
        }
        selectedKeypoint?.let { kp ->
            selectedPosition?.let { pos ->
                drawJoint(jointMesh, pos, colorToFloats(android.graphics.Color.rgb(255, 255, 255)), jointScale, true, vpMatrix)
            }
        }

        // Draw bones
        val boneMesh = boneMesh ?: return
        for (bone in skeleton.bones) {
            val fromKp = skeleton.keypointById(bone.fromId) ?: continue
            val toKp = skeleton.keypointById(bone.toId) ?: continue
            if (!fromKp.enabled || !toKp.enabled) continue
            val fromPos = positions[bone.fromId] ?: continue
            val toPos = positions[bone.toId] ?: continue
            val color = colorToFloats(bone.color)
            drawBone(boneMesh, fromPos, toPos, vpMatrix, color)
        }
    }

    private fun drawCameraIndicator(viewportCamera: Camera, renderCamera: Camera, width: Int, height: Int) {
        val shader = shader ?: return
        val mesh = cameraFrustumMesh ?: return
        val vpMatrix = viewportCamera.getViewProjectionMatrix(width, height)
        val cameraColor = colorToFloats(android.graphics.Color.rgb(255, 255, 0))

        val dir = Vector3f(renderCamera.target).sub(renderCamera.position)
        if (dir.lengthSquared() < 0.0001f) return
        dir.normalize()

        val rotation = Quaternionf().rotationTo(Vector3f(0f, 0f, -1f), dir)
        modelMatrix.identity()
            .translate(renderCamera.position)
            .rotate(rotation)

        vpMatrix.mul(modelMatrix, mvpMatrix)
        shader.setMvpMatrix(mvpMatrix.get(floatArray))
        shader.setColor(cameraColor[0], cameraColor[1], cameraColor[2])
        shader.setAlpha(0.4f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        mesh.draw()
        GLES30.glDisable(GLES30.GL_BLEND)
        shader.setAlpha(1.0f)
    }

    private fun drawBone(mesh: Mesh, from: Vector3f, to: Vector3f, vpMatrix: Matrix4f, color: FloatArray) {
        val direction = Vector3f(to).sub(from)
        val length = direction.length()
        if (length < 0.0001f) return

        val mid = Vector3f(from).add(to).mul(0.5f)
        val yAxis = Vector3f(0f, 1f, 0f)
        val rotation = Quaternionf().rotationTo(yAxis, direction.normalize())

        modelMatrix.identity()
            .translate(mid)
            .rotate(rotation)
            .scale(1f, length, 1f)

        vpMatrix.mul(modelMatrix, mvpMatrix)
        shader?.setMvpMatrix(mvpMatrix.get(floatArray))
        drawMeshWithColor(mesh, color)
    }

    private fun drawJoint(
        mesh: Mesh,
        pos: Vector3f,
        color: FloatArray,
        baseScale: Float,
        selected: Boolean,
        vpMatrix: Matrix4f,
    ) {
        modelMatrix.identity()
            .translate(pos)
            .scale(baseScale)
        if (selected) {
            modelMatrix.scale(1.2f)
        }
        vpMatrix.mul(modelMatrix, mvpMatrix)
        shader?.setMvpMatrix(mvpMatrix.get(floatArray))
        drawMeshWithColor(mesh, color)
    }

    private fun drawMeshWithColor(mesh: Mesh, color: FloatArray) {
        shader?.setColor(color[0], color[1], color[2])
        mesh.draw()
    }

    private fun createColoredMesh(data: PrimitiveMeshes.MeshData, mode: Int = GLES30.GL_TRIANGLES): Mesh {
        return Mesh(data.vertices, data.indices, 3, 0, -1, mode)
    }

    private fun colorToFloats(color: Int): FloatArray {
        return floatArrayOf(
            ((color shr 16) and 0xFF) / 255f,
            ((color shr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
        )
    }

    private fun ensureFramebuffer(width: Int, height: Int) {
        if (width == renderWidth && height == renderHeight && framebuffer != 0) return

        if (framebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderTexture), 0)
            GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRenderbuffer), 0)
        }

        val fb = IntArray(1)
        val tex = IntArray(1)
        val rb = IntArray(1)
        GLES30.glGenFramebuffers(1, fb, 0)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glGenRenderbuffers(1, rb, 0)

        framebuffer = fb[0]
        renderTexture = tex[0]
        depthRenderbuffer = rb[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderTexture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRenderbuffer)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, width, height)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, renderTexture, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depthRenderbuffer)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        renderWidth = width
        renderHeight = height
    }

    private fun flipBitmapVertically(bitmap: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun release() {
        shader?.delete()
        jointMesh?.delete()
        boneMesh?.delete()
        cameraFrustumMesh?.delete()
        if (framebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES30.glDeleteTextures(1, intArrayOf(renderTexture), 0)
            GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRenderbuffer), 0)
        }
    }
}
