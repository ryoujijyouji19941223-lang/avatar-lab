package com.totomarujapan.crowavatar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var avatarView: AvatarView
    private lateinit var controls: LinearLayout
    private lateinit var status: TextView
    private lateinit var trackButton: Button
    private lateinit var streamButton: Button

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var faceTracker: FaceTracker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var tracking = false
    private var streamMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        avatarView = AvatarView(this).apply {
            setBackgroundColor(Color.rgb(7, 9, 13))
        }

        controls = buildControls()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                avatarView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                controls,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                )
            )
        }

        avatarView.setOnClickListener {
            if (streamMode) setStreamMode(false)
        }

        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    private fun buildControls(): LinearLayout {
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            text = "準備完了。まず「顔トラッキング開始」を押してください。"
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        trackButton = Button(this).apply {
            text = "顔トラッキング開始"
            setOnClickListener {
                if (tracking) stopTracking() else startTracking()
            }
        }

        streamButton = Button(this).apply {
            text = "配信画面にする"
            setOnClickListener { setStreamMode(true) }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(Color.argb(210, 18, 23, 32))

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(
                        trackButton,
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        streamButton,
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                }
            )
            addView(status)
        }
    }

    private fun startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }

        status.text = "顔認識モデルを初期化中…"

        try {
            faceTracker?.close()
            faceTracker = FaceTracker(
                context = this,
                onPose = { pose ->
                    runOnUiThread {
                        avatarView.updateFace(
                            pose.yaw,
                            pose.pitch,
                            pose.roll,
                            pose.jaw,
                            pose.blink
                        )
                        status.text =
                            "追跡中  左右 %.2f / 上下 %.2f / 傾き %.2f / 口 %.2f"
                                .format(pose.yaw, pose.pitch, pose.roll, pose.jaw)
                    }
                },
                onError = { message ->
                    runOnUiThread { status.text = "顔認識エラー: " + message }
                }
            )
        } catch (e: Exception) {
            status.text = "顔認識モデルの初期化に失敗: " + (e.message ?: "")
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { image ->
                    try {
                        faceTracker?.detect(image) ?: image.close()
                    } catch (e: Exception) {
                        image.close()
                        runOnUiThread {
                            status.text = "カメラ解析エラー: " + (e.message ?: "")
                        }
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )

                tracking = true
                avatarView.setTracking(true)
                trackButton.text = "顔トラッキング停止"
                status.text = "前面カメラ起動。顔を正面に入れてください。"
            } catch (e: Exception) {
                status.text = "カメラ起動失敗: " + (e.message ?: "")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopTracking() {
        cameraProvider?.unbindAll()
        faceTracker?.close()
        faceTracker = null
        tracking = false
        avatarView.setTracking(false)
        trackButton.text = "顔トラッキング開始"
        status.text = "顔トラッキング停止"
    }

    private fun setStreamMode(enabled: Boolean) {
        streamMode = enabled
        controls.visibility = if (enabled) View.GONE else View.VISIBLE

        val controller = window.insetsController
        if (enabled) {
            controller?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
            controller?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (streamMode) {
            setStreamMode(false)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        faceTracker?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            status.text =
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    "カメラ許可OK。顔トラッキングを開始できます。"
                } else {
                    "カメラ権限が必要です。"
                }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CAMERA = 1001
    }
}
