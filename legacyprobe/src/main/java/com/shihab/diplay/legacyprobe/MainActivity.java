package com.shihab.diplay.legacyprobe;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int APPLE_VID = 0x05ac;
    private static final int USBMUX_CLASS = 0xff;
    private static final int USBMUX_SUBCLASS = 0xfe;
    private static final int USBMUX_PROTOCOL = 0x02;
    private static final int CARPLAY_REQ = 0x52;
    private static final int CARPLAY_INDEX = 0x0004;

    private UsbManager usbManager;
    private TextView logView;
    private String permissionAction;
    private String sourceDeviceName;
    private boolean waitingForReenumeration;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (permissionAction.equals(action)) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                log("USB permission=" + granted + " " + describe(device));
                if (granted && device != null) inspectDevice(device);
                return;
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action) && isApple(device)) {
                log("Apple attached: " + describe(device));
                if (waitingForReenumeration && !sameNode(device, sourceDeviceName)) {
                    waitingForReenumeration = false;
                }
                requestPermission(device);
                return;
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) && isApple(device)) {
                log("Apple detached: " + describe(device));
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionAction = getPackageName() + ".USB_PERMISSION";
        setContentView(buildUi());

        IntentFilter filter = new IntentFilter();
        filter.addAction(permissionAction);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(receiver, filter);

        log("DiPlay K2201S Legacy USB Probe v0.2");
        log("Android=" + Build.VERSION.RELEASE + " API=" + Build.VERSION.SDK_INT);
        log("ABI=" + Build.CPU_ABI + " ABI2=" + Build.CPU_ABI2);
        log("USB host=" + getPackageManager().hasSystemFeature("android.hardware.usb.host"));
        probeH264();
        scan();
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (RuntimeException ignored) {}
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(10, 10, 10, 10);

        Button scan = new Button(this);
        scan.setText("1 扫描 iPhone");
        scan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scan(); }
        });
        root.addView(scan);

        Button permission = new Button(this);
        permission.setText("2 请求 USB 权限");
        permission.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                UsbDevice d = firstApple();
                if (d == null) log("FAIL: 未发现 Apple USB 设备");
                else requestPermission(d);
            }
        });
        root.addView(permission);

        Button transition = new Button(this);
        transition.setText("3 CarPlay USB 切换");
        transition.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { transitionToCarPlay(); }
        });
        root.addView(transition);

        logView = new TextView(this);
        logView.setTextSize(13f);
        logView.setTextIsSelectable(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void scan() {
        int count = 0;
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (!isApple(d)) continue;
            count++;
            log("Apple: " + describe(d) + " permission=" + usbManager.hasPermission(d));
            dumpInterfaces(d);
        }
        log("Apple USB device count=" + count);
        if (count == 0) {
            log("若 iPhone 已插入：检查车机 USB 数据口/Host/数据线。");
        }
    }

    private UsbDevice firstApple() {
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            if (isApple(d)) return d;
        }
        return null;
    }

    private void requestPermission(UsbDevice d) {
        if (usbManager.hasPermission(d)) {
            log("USB permission already granted");
            inspectDevice(d);
            return;
        }
        Intent i = new Intent(permissionAction).setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= 0x04000000; // FLAG_IMMUTABLE
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, flags);
        usbManager.requestPermission(d, pi);
        log("USB permission requested");
    }

    private void transitionToCarPlay() {
        final UsbDevice d = firstApple();
        if (d == null) {
            log("FAIL: 未发现 iPhone");
            return;
        }
        if (!usbManager.hasPermission(d)) {
            requestPermission(d);
            return;
        }

        sourceDeviceName = d.getDeviceName();
        waitingForReenumeration = true;
        log("发送 Apple 0x52 / index=4 ...");

        new Thread(new Runnable() {
            @Override public void run() {
                UsbDeviceConnection c = usbManager.openDevice(d);
                if (c == null) {
                    post("FAIL: openDevice=null");
                    waitingForReenumeration = false;
                    return;
                }
                try {
                    byte[] reply = new byte[1];
                    int n = c.controlTransfer(0xc0, CARPLAY_REQ, 0, CARPLAY_INDEX,
                            reply, reply.length, 1500);
                    post("0x52 transfer=" + n + (n == 1 ? " response=0x" + hex(reply[0]) : ""));
                    if (n == 1) {
                        post("PASS: 等待 iPhone detach/re-attach ...");
                    } else {
                        waitingForReenumeration = false;
                        post("FAIL: 0x52 未成功");
                    }
                } finally {
                    c.close();
                }
            }
        }, "carplay-transition").start();
    }

    private void inspectDevice(UsbDevice d) {
        log("Inspect: " + describe(d));
        dumpInterfaces(d);

        UsbInterface mux = findUsbMux(d);
        if (mux == null) {
            log("USBMUX: NOT FOUND");
            return;
        }

        UsbEndpoint[] pair = findBulkPair(mux);
        if (pair == null) {
            log("USBMUX: interface found, bulk pair missing");
            return;
        }

        log("USBMUX: FOUND iface=" + mux.getId()
                + " OUT=0x" + hex(pair[0].getAddress())
                + " IN=0x" + hex(pair[1].getAddress()));

        UsbDeviceConnection c = usbManager.openDevice(d);
        if (c == null) {
            log("FAIL: openDevice=null");
            return;
        }
        try {
            boolean claimed = c.claimInterface(mux, true);
            log("USBMUX claim=" + claimed);
            if (claimed) {
                log("PASS: API19 已能 claim iPhone USBMUX");
                c.releaseInterface(mux);
            }
        } finally {
            c.close();
        }
    }

    private UsbInterface findUsbMux(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            if (f.getInterfaceClass() == USBMUX_CLASS
                    && f.getInterfaceSubclass() == USBMUX_SUBCLASS
                    && f.getInterfaceProtocol() == USBMUX_PROTOCOL
                    && findBulkPair(f) != null) {
                return f;
            }
        }
        return null;
    }

    private UsbEndpoint[] findBulkPair(UsbInterface f) {
        UsbEndpoint in = null;
        UsbEndpoint out = null;
        for (int i = 0; i < f.getEndpointCount(); i++) {
            UsbEndpoint ep = f.getEndpoint(i);
            if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (ep.getDirection() == UsbConstants.USB_DIR_IN) in = ep;
            else out = ep;
        }
        return in != null && out != null ? new UsbEndpoint[]{out, in} : null;
    }

    private void dumpInterfaces(UsbDevice d) {
        log("interfaces=" + d.getInterfaceCount());
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface f = d.getInterface(i);
            log("  #" + i
                    + " id=" + f.getId()
                    + " class=0x" + hex(f.getInterfaceClass())
                    + " sub=0x" + hex(f.getInterfaceSubclass())
                    + " proto=0x" + hex(f.getInterfaceProtocol())
                    + " ep=" + f.getEndpointCount());
        }
    }

    private void probeH264() {
        try {
            int found = 0;
            for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if ("video/avc".equalsIgnoreCase(type)) {
                        log("H264 decoder=" + info.getName());
                        found++;
                        break;
                    }
                }
            }
            log(found > 0 ? "H264: PASS count=" + found : "H264: FAIL");
        } catch (RuntimeException e) {
            log("H264 probe error: " + e.getMessage());
        }
    }

    private boolean isApple(UsbDevice d) {
        return d != null && d.getVendorId() == APPLE_VID;
    }

    private boolean sameNode(UsbDevice d, String name) {
        return d != null && name != null && name.equals(d.getDeviceName());
    }

    private String describe(UsbDevice d) {
        if (d == null) return "null";
        return "name=" + d.getDeviceName()
                + " vid=0x" + hex(d.getVendorId())
                + " pid=0x" + hex(d.getProductId());
    }

    private static String hex(int v) {
        return String.format(Locale.US, "%02x", v & 0xff);
    }

    private void post(final String s) {
        runOnUiThread(new Runnable() {
            @Override public void run() { log(s); }
        });
    }

    private void log(String s) {
        if (logView == null) return;
        logView.append(s + "\n");
    }
}
