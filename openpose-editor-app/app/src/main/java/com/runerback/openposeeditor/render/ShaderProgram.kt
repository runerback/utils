package com.runerback.openposeeditor.render

import android.content.res.AssetManager
import android.opengl.GLES30
import android.util.Log

class ShaderProgram(
    assetManager: AssetManager,
    vertexPath: String,
    fragmentPath: String,
) {
    val programId: Int
    private val mvpMatrixLocation: Int
    private val colorLocation: Int
    private val alphaLocation: Int

    init {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, readAsset(assetManager, vertexPath))
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, readAsset(assetManager, fragmentPath))
        programId = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
        }

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw RuntimeException("Program link error: $error")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        mvpMatrixLocation = GLES30.glGetUniformLocation(programId, "uMvpMatrix")
        colorLocation = GLES30.glGetUniformLocation(programId, "uColor")
        alphaLocation = GLES30.glGetUniformLocation(programId, "uAlpha")
    }

    fun use() {
        GLES30.glUseProgram(programId)
        GLES30.glUniform1f(alphaLocation, 1.0f)
    }

    fun setMvpMatrix(matrix: FloatArray) {
        GLES30.glUniformMatrix4fv(mvpMatrixLocation, 1, false, matrix, 0)
    }

    fun setColor(r: Float, g: Float, b: Float) {
        GLES30.glUniform3f(colorLocation, r, g, b)
    }

    fun setAlpha(alpha: Float) {
        GLES30.glUniform1f(alphaLocation, alpha)
    }

    fun delete() {
        GLES30.glDeleteProgram(programId)
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compile error: $error")
        }
        return shader
    }

    private fun readAsset(assetManager: AssetManager, path: String): String {
        return assetManager.open(path).use { it.bufferedReader().readText() }
    }
}
