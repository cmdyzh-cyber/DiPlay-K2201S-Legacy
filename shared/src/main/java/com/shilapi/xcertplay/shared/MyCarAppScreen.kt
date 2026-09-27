package com.shilapi.xcertplay.shared

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

class MyCarAppScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("硬件传输未配置。板载 I2C 需要 /dev/i2c-N 路径以及系统/SELinux 权限；CH341 需要部署对应的 VID/PID 配置。")
            .setHeaderAction(Action.APP_ICON)
            .setTitle("xcertplay 硬件状态")
            .build()
    }
}
