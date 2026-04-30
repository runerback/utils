package com.runerback.screenrecorder.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.runerback.screenrecorder.MainActivity
import com.runerback.screenrecorder.data.RecordingStateRepository
import com.runerback.screenrecorder.data.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingControlsService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var statusTextView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var exitButton: Button
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingResultCode: Int? = null
    private var pendingResultData: Intent? = null
    private var exitAfterStop = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WindowManager::class.java)
        overlayView = createOverlayView()
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 96
        }
        windowManager?.addView(overlayView, layoutParams)
        RecordingStateRepository.setToolboxVisible(true)
        serviceScope.launch {
            RecordingStateRepository.uiState.collectLatest { uiState ->
                statusTextView.text = when (uiState.status) {
                    RecordingStatus.IDLE -> "Ready"
                    RecordingStatus.PREPARING -> "Preparing"
                    RecordingStatus.RECORDING -> "Recording"
                    RecordingStatus.STOPPING -> "Stopping"
                }
                startButton.isEnabled = uiState.status == RecordingStatus.IDLE
                stopButton.isEnabled = uiState.status == RecordingStatus.PREPARING ||
                    uiState.status == RecordingStatus.RECORDING
                exitButton.isEnabled = uiState.status != RecordingStatus.STOPPING
                if (exitAfterStop && uiState.status == RecordingStatus.IDLE) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW, null -> {
                if (intent?.hasExtra(EXTRA_RESULT_CODE) == true) {
                    pendingResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, pendingResultCode ?: 0)
                }
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)?.let { resultData ->
                    pendingResultData = resultData
                }
                exitAfterStop = false
                RecordingStateRepository.setToolboxVisible(true)
            }

            ACTION_EXIT -> handleExit()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        pendingResultCode = null
        pendingResultData = null
        exitAfterStop = false
        RecordingStateRepository.setToolboxVisible(false)
        super.onDestroy()
    }

    private fun createOverlayView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(0xCC202124.toInt())
            }

            statusTextView = TextView(context).apply {
                text = "Ready"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                setPadding(0, 0, 0, 12)
                setOnTouchListener(createDragTouchListener())
            }
            addView(statusTextView)

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    startButton = Button(context).apply {
                        text = "Start"
                        setOnClickListener { handleStart() }
                    }
                    stopButton = Button(context).apply {
                        text = "Stop"
                        setOnClickListener { RecordingCommands.stop(context) }
                    }
                    exitButton = Button(context).apply {
                        text = "Exit"
                        setOnClickListener { handleExit() }
                    }
                    addView(startButton)
                    addView(stopButton)
                    addView(exitButton)
                },
            )
        }
    }

    private fun handleStart() {
        when (RecordingStateRepository.uiState.value.status) {
            RecordingStatus.PREPARING,
            RecordingStatus.RECORDING,
            RecordingStatus.STOPPING,
            -> return

            RecordingStatus.IDLE -> {
                val resultCode = pendingResultCode
                val resultData = pendingResultData
                if (resultCode != null && resultData != null) {
                    pendingResultCode = null
                    pendingResultData = null
                    RecordingCommands.start(this, resultCode, resultData)
                } else {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            action = MainActivity.ACTION_REQUEST_CAPTURE
                            putExtra(MainActivity.EXTRA_AUTO_START_AFTER_PERMISSION, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        },
                    )
                }
            }
        }
    }

    private fun handleExit() {
        val status = RecordingStateRepository.uiState.value.status
        pendingResultCode = null
        pendingResultData = null
        if (status == RecordingStatus.IDLE) {
            stopSelf()
        } else {
            exitAfterStop = true
            RecordingCommands.stop(this)
        }
    }

    private fun createDragTouchListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    windowManager?.updateViewLayout(overlayView, layoutParams)
                    true
                }

                else -> false
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.runerback.screenrecorder.action.SHOW_TOOLBOX"
        const val ACTION_EXIT = "com.runerback.screenrecorder.action.EXIT_TOOLBOX"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
