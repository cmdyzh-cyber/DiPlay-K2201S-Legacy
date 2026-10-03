# K2201S / Android 4.4.2 DiPlay Legacy USB Probe v0.2

这不是完整 CarPlay 成品，而是 API19 移植的真机 USB 探针。

## 测试目标

1. 确认 Android 4.4.2 / API19 能识别 iPhone USB 设备。
2. 确认车机可获得 USB permission。
3. 发送 DiPlay 当前有线路径使用的 Apple vendor request `0x52 / index=4`。
4. 观察 iPhone 是否 detach / re-attach。
5. 检查重新枚举后是否出现 USBMUX `ff/fe/02` 接口。
6. 尝试 `claimInterface` USBMUX。
7. 列出系统 H.264/AVC decoder。

## 真机测试顺序

1. 安装 APK 并启动。
2. 用确定可传数据的 USB 线连接 iPhone 和纽曼数据 USB 口。
3. 点 **1 扫描 iPhone**。
4. 点 **2 请求 USB 权限**，若系统弹窗则允许。
5. 点 **3 CarPlay USB 切换**。
6. 记录屏幕日志。

最关键的目标日志：

```
Apple USB device count=1
USB permission=true
0x52 transfer=1
Apple detached
Apple attached
USBMUX: FOUND
USBMUX claim=true
PASS: API19 已能 claim iPhone USBMUX
```

如果 `USBMUX claim=true` 成立，就继续 V0.3，把现有 DiPlay 的 USBMUX / Lockdown / iAP2 有线栈接进 API19 compatibility layer。

## GitHub Actions

仓库分支 `legacy/api19-k2201s` 已配置：

`.github/workflows/legacy-api19-probe.yml`

每次修改 probe 代码都会自动构建 debug APK，并上传 artifact：

`K2201S-API19-USB-Probe-V0.2`

## 安全边界

- 不刷车机固件。
- 不写系统分区。
- 不包含 MFi/accessory 私钥或证书。
- 此 probe 不会启动完整 CarPlay session。
