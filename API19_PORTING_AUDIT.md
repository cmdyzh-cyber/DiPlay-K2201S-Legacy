# DiPlay -> K2201S Android 4.4.2 / API19 移植审计

目标：保留纽曼 K2201S 原 Android 4.4.2 系统，只实现 USB 有线 CarPlay。

## 已确认基线

- `main` 当前为 Android 7 fork，`mobile` 的 `minSdk=24`。
- 有线路径仍基于 Apple USB + USBMUX + Lockdown + iAP2。
- 第一阶段不移植无线、BYD HUD、Android Auto/Car App 集成。

## API19 关键差异

Android 4.4 已具备：

- UsbManager / UsbDevice / UsbInterface / UsbEndpoint
- UsbDeviceConnection.claimInterface
- controlTransfer / bulkTransfer
- MediaCodec / H.264 AVC decoder

但缺少 API21+ 的：

- UsbConfiguration
- UsbDevice.getConfiguration*
- UsbDeviceConnection.setConfiguration
- UsbInterface.getAlternateSetting
- UsbDeviceConnection.setInterface

因此不能只改 minSdk。

## 当前策略

1. 继续使用 Apple vendor request `0x52`, value `0`, index `4`。
2. 等待 iPhone USB re-enumeration。
3. API19 直接枚举当前 UsbDevice interfaces。
4. 如果 USBMUX `ff/fe/02` 已出现，则直接 claim。
5. 如果 USBMUX 不出现，再研究标准 USB `SET_CONFIGURATION / SET_INTERFACE` control transfer 或内核行为。

## 第二阶段兼容工作

- PendingIntent 新版 flag 做 API19 分支处理。
- MediaCodec API21+ buffer 方法改回 API16 兼容路径。
- Surface 切换在 API19 上重建 decoder。
- AudioTrack API23+ 调用逐项替换。
- Compose / Material3 / AndroidX UI 不作为 Legacy 基线。
- Legacy UI 使用原生 Activity / View / SurfaceView。

## 当前分支

`legacy/api19-k2201s`

当前新增：

- `legacyprobe/`
- `.github/workflows/legacy-api19-probe.yml`

下一步取决于真机 probe 结果。
