package ai.parayoo.demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class FloatingBubbleService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private lateinit var windowManager: WindowManager
    private lateinit var bubble: TextView
    private var resultCard: LinearLayout? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var recorder: WavRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!::bubble.isInitialized) showBubble()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Parayoo floating bubble",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Parayoo bubble is ready")
            .setContentText("Tap the bubble to record Malayalam")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showBubble() {
        bubble = TextView(this).apply {
            gravity = Gravity.CENTER
            text = "🎙"
            textSize = 28f
            setTextColor(Color.WHITE)
            background = ovalBackground("#1565C0")
            contentDescription = "Parayoo record bubble"
            setOnTouchListener(BubbleTouchListener())
        }
        bubbleParams = overlayParams(72, 72).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 260
        }
        windowManager.addView(bubble, bubbleParams)
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downX = 0
        private var downY = 0
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX.toInt()
                    downY = event.rawY.toInt()
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX.toInt() - downX
                    val deltaY = event.rawY.toInt() - downY
                    moved = moved || kotlin.math.abs(deltaX) > 12 || kotlin.math.abs(deltaY) > 12
                    bubbleParams.x = startX - deltaX
                    bubbleParams.y = startY + deltaY
                    windowManager.updateViewLayout(bubble, bubbleParams)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onBubbleTapped()
                    return true
                }
            }
            return false
        }
    }

    private fun onBubbleTapped() {
        if (recorder?.isRecording() == true) {
            stopAndTransliterate()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        dismissResult()
        val wavRecorder = WavRecorder(cacheDir)
        if (!wavRecorder.start()) {
            showResult("Could not start microphone.", false)
            return
        }
        recorder = wavRecorder
        bubble.text = "■"
        bubble.background = ovalBackground("#D32F2F")
        mainHandler.postDelayed(autoStop, MAX_RECORDING_MS)
    }

    private val autoStop = Runnable {
        if (recorder?.isRecording() == true) stopAndTransliterate()
    }

    private fun stopAndTransliterate() {
        mainHandler.removeCallbacks(autoStop)
        val audioFile = recorder?.stop()
        recorder = null
        bubble.text = "…"
        bubble.background = ovalBackground("#F59E0B")
        if (audioFile == null) {
            showResult("No audio captured. Try again.", false)
            resetBubble()
            return
        }
        if (BuildConfig.SARVAM_API_KEY.isBlank()) {
            showResult("Missing Sarvam API key. Rebuild the app.", false)
            resetBubble()
            return
        }

        networkExecutor.execute {
            try {
                val result = SarvamClient.transliterate(audioFile)
                mainHandler.post {
                    showResult(result, true)
                    resetBubble()
                }
            } catch (error: Exception) {
                mainHandler.post {
                    showResult("Could not convert. ${error.message ?: "Try again."}", false)
                    resetBubble()
                }
            }
        }
    }

    private fun resetBubble() {
        bubble.text = "🎙"
        bubble.background = ovalBackground("#1565C0")
    }

    private fun showResult(message: String, canCopy: Boolean) {
        dismissResult()
        val text = TextView(this).apply {
            this.text = message
            setTextColor(Color.WHITE)
            textSize = 17f
        }
        val copy = TextView(this).apply {
            this.text = if (canCopy) "COPY" else "CLOSE"
            setTextColor(Color.rgb(147, 197, 253))
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                if (canCopy) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Parayoo Manglish", message))
                    Toast.makeText(this@FloatingBubbleService, "Copied", Toast.LENGTH_SHORT).show()
                }
                dismissResult()
            }
        }
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(8), dp(4))
            background = roundedBackground("#1F2937")
            addView(text)
            addView(copy, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
            })
        }
        val resultParams = overlayParams(280, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = bubbleParams.x
            y = bubbleParams.y + dp(80)
        }
        windowManager.addView(resultCard, resultParams)
        mainHandler.postDelayed({ dismissResult() }, RESULT_DISMISS_MS)
    }

    private fun dismissResult() {
        resultCard?.let { card ->
            runCatching { windowManager.removeView(card) }
        }
        resultCard = null
    }

    private fun overlayParams(widthDp: Int, heightDp: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            dp(widthDp),
            if (heightDp == WindowManager.LayoutParams.WRAP_CONTENT) heightDp else dp(heightDp),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )

    private fun ovalBackground(color: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(color))
    }

    private fun roundedBackground(color: String) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(Color.parseColor(color))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoStop)
        recorder?.release()
        dismissResult()
        if (::bubble.isInitialized) runCatching { windowManager.removeView(bubble) }
        networkExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "parayoo_bubble"
        const val NOTIFICATION_ID = 11
        const val MAX_RECORDING_MS = 15_000L
        const val RESULT_DISMISS_MS = 10_000L
    }
}
