package com.shilapi.xcertplay.hud

import android.content.Context
import java.net.Socket
import java.net.InetSocketAddress
import android.os.SystemClock
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Test-only client of the user's explicitly ADB-started, navigation-only local bridge. */
object BydStarterBridge {
    private const val TAG = "BYD-Starter-Bridge"
    private const val MAGIC = 0x42594431
    private data class Frame(val turn: Int, val distance: Int, val receivedMs: Long)
    @Volatile private var latest: Frame? = null
    @Volatile private var demoStartMs: Long? = null
    @Volatile private var token: ByteArray? = null
    private var started = false
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var input: DataInputStream? = null
    private var retryAfterMs = 0L
    private var lastNotice: String? = null

    @Synchronized fun initialize(context: Context) {
        if (context.packageName !in setOf("com.andrerinas.headunitrevived.bydhudtest", "com.shihab.diplay.hudtest") || started) return
        val saved = context.getSharedPreferences("hud_starter", Context.MODE_PRIVATE).getString("token", null)
        token = decodeToken(saved)
        started = true
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "byd-starter-client").apply { isDaemon = true }
        }.scheduleWithFixedDelay(::tick, 0, 500, TimeUnit.MILLISECONDS)
    }

    @Synchronized fun configure(context: Context, secret: String) {
        check(context.packageName in setOf("com.andrerinas.headunitrevived.bydhudtest", "com.shihab.diplay.hudtest"))
        val parsed = requireNotNull(decodeToken(secret)) { "启动令牌无效" }
        context.getSharedPreferences("hud_starter", Context.MODE_PRIVATE).edit().putString("token", secret).apply()
        initialize(context)
        token = parsed
        Log.i(TAG, "已收到临时启动凭据")
    }

    private fun decodeToken(secret: String?): ByteArray? = secret?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        ?.let { value -> ByteArray(32) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }

    fun update(icon: Int, exit: Int, distance: Int) {
        val turn = BydFactoryTurnCode.map(icon, exit)
        latest = if (turn != null && distance in 0..16777214) Frame(turn, distance, SystemClock.elapsedRealtime()) else null
    }

    fun clear(cancelDemo: Boolean = true) { latest = null; if (cancelDemo) demoStartMs = null }

    /** Called only by the DUMP-protected debug receiver after the parked-demo request. */
    fun demonstrate(context: Context) {
        initialize(context)
        demoStartMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "已请求停车演示：左转 500 米、右转 800 米，然后清除")
    }

    private fun tick() {
        try {
            val now = SystemClock.elapsedRealtime()
            val demo = demoStartMs
            val frame = if (demo != null) {
                when {
                    now - demo < 8_000 -> Frame(1, 500, now)
                    now - demo < 16_000 -> Frame(2, 800, now)
                    now - demo < 20_000 -> null // Explicitly show cleanup before resuming phone guidance.
                    else -> { demoStartMs = null; null }
                }
            } else latest?.takeIf { now - it.receivedMs < 6_000 }
            if (frame == null) {
                if (socket != null) {
                    send(0, 0, 0)
                    notice("导航指引已清除")
                    close()
                }
                return
            }
            if (socket == null) {
                if (now < retryAfterMs) return
                val secret = token ?: return
                val next = Socket()
                try {
                    next.connect(InetSocketAddress("127.0.0.1", 42671), 1_000)
                    next.soTimeout = 1_500
                    output = DataOutputStream(next.outputStream).also { it.write(secret); it.flush() }
                    input = DataInputStream(next.inputStream)
                    socket = next
                } catch (error: Exception) { next.close(); throw error }
            }
            send(1, frame.turn, frame.distance)
            notice("已连接 ADB 启动器；导航写入已确认")
        } catch (error: Exception) {
            close() // EOF makes the helper clear any guidance it owns.
            retryAfterMs = SystemClock.elapsedRealtime() + 3_000
            notice("等待 ADB 启动器（${error.javaClass.simpleName}：${error.message}）")
        }
    }

    private fun send(operation: Int, turn: Int, distance: Int) {
        val out = checkNotNull(output)
        out.writeInt(MAGIC); out.writeInt(operation); out.writeInt(turn); out.writeInt(distance); out.flush()
        val result = checkNotNull(input).readInt()
        check(result == 0) { "启动器拒绝了命令：$result" }
    }

    private fun close() {
        try { socket?.close() } catch (_: Exception) { }
        socket = null; input = null; output = null
    }

    private fun notice(message: String) {
        if (message != lastNotice) { Log.i(TAG, message); lastNotice = message }
    }
}
