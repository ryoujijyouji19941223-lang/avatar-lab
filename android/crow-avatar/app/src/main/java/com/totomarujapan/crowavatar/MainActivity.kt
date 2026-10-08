package com.totomarujapan.crowavatar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var avatar: AvatarView
    private lateinit var controls: LinearLayout
    private lateinit var status: TextView
    private lateinit var trackButton: Button
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var tracker: FaceTracker? = null
    private var provider: ProcessCameraProvider? = null
    private var tracking = false
    private var starting = false
    private var streamMode = false
    private var generation = 0
    private var resumeTracking = false
    private var trackingSince = 0L
    private var lastStatus = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        avatar = AvatarView(this).apply { setBackgroundColor(Color.rgb(7, 9, 13)) }
        controls = buildControls()
        val root = FrameLayout(this).apply {
            addView(avatar, FrameLayout.LayoutParams(-1, -1))
            addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            controls.setPadding(dp(8), dp(4), dp(8), dp(8) + bars.bottom)
            insets
        }
        avatar.setOnClickListener { if (streamMode) setStreamMode(false) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (streamMode) setStreamMode(false) else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }
    private fun buildControls(): LinearLayout {
        status = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f
            text = "Crow Avatar Work v0.4\n顔を正面にして開始。左目は画面右、右目は画面左です。"
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; textSize = 12f; setOnClickListener { action() }
        }
        trackButton = button("追従開始") { if (tracking || starting) stopTracking() else startTracking() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.argb(220, 18, 23, 32))
            addView(LinearLayout(this@MainActivity).apply {
                addView(trackButton, LinearLayout.LayoutParams(0,-2,1f))
                addView(button("正面を合わせる") { tracker?.recenter() }, LinearLayout.LayoutParams(0,-2,1f))
                addView(button("配信画面") { setStreamMode(true) }, LinearLayout.LayoutParams(0,-2,1f))
            })
            val presets = linkedMapOf("通常" to FacePose(), "左目" to FacePose(blinkLeft=1f),
                "右目" to FacePose(blinkRight=1f), "両目" to FacePose(blinkLeft=1f,blinkRight=1f),
                "口半開き" to FacePose(jaw=0.40f), "口全開" to FacePose(jaw=1f),
                "左向き" to FacePose(yaw=1f), "右向き" to FacePose(yaw=-1f),
                "上向き" to FacePose(pitch=1f), "下向き" to FacePose(pitch=-1f),
                "左傾き" to FacePose(roll=1f), "右傾き" to FacePose(roll=-1f))
            addView(HorizontalScrollView(this@MainActivity).apply {
                addView(LinearLayout(this@MainActivity).apply {
                    presets.forEach { (name, pose) -> addView(button(name) {
                        stopTracking(); avatar.preview(pose); status.text = "描画確認: $name（横にスワイプで他の動き）"
                    }) }
                })
            })
            addView(status)
        }
    }
    private fun startTracking() {
        if (tracking || starting || isDestroyed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1001); return
        }
        starting = true
        trackButton.text = "準備を停止"
        status.text = "顔認識モデルを準備中…"
        val token = ++generation
        cameraExecutor.execute {
            try {
                val created = FaceTracker(this) { pose -> runOnUiThread {
                    if (generation == token && tracking) {
                        avatar.updateFace(pose ?: FacePose())
                        val now = SystemClock.uptimeMillis()
                        if (now-lastStatus > 250L) {
                            lastStatus = now
                            status.text = if (pose == null) "顔を探しています…" else
                                "追従 %d秒  左目 %.2f / 右目 %.2f / 口 %.2f".format(
                                    (now-trackingSince)/1000, pose.blinkLeft, pose.blinkRight, pose.jaw)
                        }
                    }
                } }
                runOnUiThread {
                    if (generation != token || isDestroyed) {
                        if (cameraExecutor.isShutdown) created.close() else cameraExecutor.execute { created.close() }
                        return@runOnUiThread
                    }
                    tracker = created
                    val future = ProcessCameraProvider.getInstance(this)
                    future.addListener({
                        if (generation == token && !isDestroyed) {
                            try {
                                provider = future.get()
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                    .build()
                                analysis.setAnalyzer(cameraExecutor) { image ->
                                    try { created.detect(image) }
                                    catch (e: Exception) { runOnUiThread {
                                        if (generation == token) status.text = "解析エラー: ${e.message}"
                                    } }
                                }
                                provider?.unbindAll()
                                provider?.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                                starting = false; tracking = true; trackingSince = SystemClock.uptimeMillis()
                                avatar.setTracking(true); trackButton.text = "追従停止"
                                status.text = "前面カメラ起動。正面が自動で基準になります。"
                            } catch (e: Exception) { stopTracking(); status.text = "カメラ起動失敗: ${e.message}" }
                        }
                    }, ContextCompat.getMainExecutor(this))
                }
            } catch (e: Exception) { runOnUiThread {
                if (generation == token) { starting=false; trackButton.text="追従開始"; status.text="初期化失敗: ${e.message}" }
            } }
        }
    }
    private fun stopTracking() {
        ++generation; provider?.unbindAll()
        val old = tracker; tracker = null
        if (old != null) cameraExecutor.execute { old.close() }
        starting = false; tracking = false; avatar.setTracking(false)
        trackButton.text = "追従開始"; status.text = "追従停止"
    }
    private fun setStreamMode(enabled: Boolean) {
        streamMode = enabled; controls.visibility = if (enabled) View.GONE else View.VISIBLE
        WindowInsetsControllerCompat(window, avatar).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onStart() { super.onStart(); if (resumeTracking) { resumeTracking=false; startTracking() } }
    override fun onStop() { resumeTracking=tracking || starting; stopTracking(); super.onStop() }
    override fun onDestroy() { cameraExecutor.shutdown(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startTracking()
            else status.text = "顔追従にはカメラ許可が必要です。描画確認ボタンは使えます。"
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
