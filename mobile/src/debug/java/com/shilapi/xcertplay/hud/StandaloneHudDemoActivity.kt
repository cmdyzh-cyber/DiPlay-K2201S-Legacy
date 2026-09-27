package com.shilapi.xcertplay.hud

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest

/** Parked, finite probe of stock IPC. No shell, socket, SDK privilege or helper. */
class StandaloneHudDemoActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var showing = false
    private var started = 0L
    private var lastTurn = 0
    private var running = false
    private val target = ComponentName("com.byd.clusterdebug", "com.byd.clusterdebug.BroadcastReceiverCAN")
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                val elapsed = SystemClock.elapsedRealtime() - started
                if (elapsed >= 16_000) { clear("演示完成"); return }
                val turn = if (elapsed < 8_000) 1 else 2
                val distance = if (turn == 1) 500 else 800
                val road = if (turn == 1) "Muscat Road" else "Sultan Qaboos Street"
                transmit(BydStandalonePackets.guidance(if (turn == 1) 2 else 3, 0, distance, road)!!)
                if (turn != lastTurn) Log.i(TAG, "APP_GUIDANCE uid=${Process.myUid()} 转向=$turn 距离=$distance")
                lastTurn = turn
                status.text = (if (turn == 1) "左转 — 500 米" else "右转 — 800 米") + "\n" + road
                handler.postDelayed(this, 1_000)
            } catch (error: Exception) {
                Log.e(TAG, "演示失败", error)
                clear("发送失败")
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        status = TextView(this).apply { textSize = 32f; text = "独立 HUD 测试就绪" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(status)
            addView(TextView(this@StandaloneHudDemoActivity).apply {
                text = "停车测试：Muscat Road / 左转 500 米，随后 Sultan Qaboos Street / 右转 800 米；各显示 8 秒后清除。"
                textSize = 22f
            })
            addView(Button(this@StandaloneHudDemoActivity).apply {
                text = "开始 16 秒测试"
                setOnClickListener { startDemo() }
            })
            addView(Button(this@StandaloneHudDemoActivity).apply {
                text = "清除 HUD"
                setOnClickListener {
                    try { validateTarget(); showing = true; clear("button") }
                    catch (error: Exception) { status.text = "无法清除：${error.message}" }
                }
            })
        })
        try {
            validateTarget()
            Log.i(TAG, "PREFLIGHT_OK uid=${Process.myUid()} 原厂接收端已校验")
            if (intent.getBooleanExtra("run", false) && state == null) handler.post { startDemo() }
        } catch (error: Exception) {
            status.text = "测试不可用：${error.message}"
            Log.e(TAG, "预检失败", error)
        }
    }

    private fun validateTarget() {
        check(packageName == "com.shihab.diplay.hudtest" && Process.myUid() >= 10000)
        check(Build.FINGERPRINT == "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys") {
            "此测试仅限已验证的固件"
        }
        val info = packageManager.getPackageInfo(target.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(info.versionCodeCompat() == 10601004L) { "原厂接收端版本不同" }
        check(info.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        val certs = info.signingInfo!!.apkContentsSigners
        check(certs.size == 1 && MessageDigest.getInstance("SHA-256").digest(certs[0].toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) } ==
            "efe3ca8ada0d10c655c3df9910ad2ebc121a47d9a6358434eb24074309933efc")
        val receiver = packageManager.getReceiverInfo(target, 0)
        check(receiver.enabled && receiver.exported && receiver.permission.isNullOrEmpty())
    }

    private fun startDemo() {
        if (running) return
        try {
            validateTarget()

            BydNavigationOutputs.setDiagnosticHold(true)
            showing = true // Preserve cleanup even if a later operation fails.
            transmit(StandaloneHudPackets.start())
            started = SystemClock.elapsedRealtime()
            running = true
            lastTurn = 0
            Log.i(TAG, "APP_START uid=${Process.myUid()} 辅助=none")
            // Start and subsequent records target the same manifest receiver.
            handler.postDelayed(tick, 250)
        } catch (error: Exception) {
            Log.e(TAG, "独立模式预检/启动失败", error)
            status.text = "测试不可用：${error.message}"
            clear("启动失败")
        }
    }

    private fun transmit(packet: String) {
        sendBroadcast(Intent("byd.hud.NAVIGATION_DEMO").setComponent(target)
            .putExtra("normal", packet).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
    }

    private fun clear(reason: String) {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (showing) {
            try {
                transmit(StandaloneHudPackets.clear())
                // Broadcast delivery is not a hardware acknowledgement.
                Log.i(TAG, "APP_CLEAR_SENT uid=${Process.myUid()} 原因=$reason")
                showing = false
                status.text = "已发送清除 — 请查看挡风玻璃"
            } catch (error: Exception) {
                Log.e(TAG, "清除失败；请点按“清除 HUD”重试", error)
                status.text = "清除失败 — 请点按“清除 HUD”"
            }
        }

    }

    override fun onStop() { clear("界面已停止"); BydNavigationOutputs.setDiagnosticHold(false); super.onStop() }
    override fun onDestroy() { clear("界面已销毁"); super.onDestroy() }
    companion object { private const val TAG = "BYD-Standalone" }
}

/** PackageInfo.longVersionCode is API 28; the deprecated field covers the API 24 floor. */
@Suppress("DEPRECATION")
private fun PackageInfo.versionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
