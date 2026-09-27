package com.shilapi.xcertplay.hud

import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException

/** Temporary HUD-test output using the installed factory SDK and the app's own identity. */
internal class BydFactoryNavigationOutput(private val context: Context) {
    private var device: Any? = null
    private var disabled = false
    private var showing = false
    private var logged = false

    fun update(icon: Int, exit: Int, distance: Int) {
        if (disabled || !context.packageName.endsWith(".bydhudtest") && !context.packageName.endsWith(".hudtest")) return
        val turn = BydFactoryTurnCode.map(icon, exit) ?: run { clear(); return }
        if (distance !in 0..16777214) { clear(); return }
        try {
            val sdk = device ?: Class.forName("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice")
                .getMethod("getInstance", Context::class.java).invoke(null, context).also { device = it }
            if (!showing) {
                checkResult("start", sdk.javaClass.getMethod("sendAutoNaviStatus", Int::class.javaPrimitiveType).invoke(sdk, 2))
                showing = true
            }
            val idsClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds\$Instrument")
            val ids = arrayOf("INSTRUMENT_FRONT_CROSSING_DISTANCE_SET", "INSTRUMENT_GUIDE_INFO_SIMPLE_SET", "INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET")
                .map { idsClass.getField(it).getInt(null) }.toIntArray()
            val valueClass = Class.forName("android.hardware.bydauto.BYDAutoEventValue")
            val value = valueClass.getConstructor().newInstance()
            valueClass.getField("intArrayValue").set(value, intArrayOf(distance, turn, turn))
            checkResult("guidance", sdk.javaClass.getMethod("set", IntArray::class.java, valueClass).invoke(sdk, ids, value))
            if (!logged) {
                Log.i(TAG, "原厂导航已接受 转向=$turn 距离=$distance；HUD 可见输出未确认")
                logged = true
            }
        } catch (error: Exception) {
            // If start succeeded but guidance failed, end our own navigation state once.
            clear()
            disabled = true
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            Log.w(TAG, "原厂导航已停用：${cause.javaClass.simpleName}：${cause.message}")
        }
    }

    fun clear() {
        if (!showing) return
        showing = false
        logged = false
        try {
            val sdk = device ?: return
            checkResult("end", sdk.javaClass.getMethod("sendAutoNaviStatus", Int::class.javaPrimitiveType).invoke(sdk, 1))
            Log.i(TAG, "原厂导航已结束")
        } catch (error: Exception) {
            disabled = true
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            Log.w(TAG, "原厂导航清理失败：${cause.message}")
        }
    }

    private fun checkResult(operation: String, result: Any?) {
        check(result == 0) { "$operation 返回 $result" }
    }

    companion object { private const val TAG = "BYD-Factory-Navi" }
}
