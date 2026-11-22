package com.pockettaco.salsa

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Android calls this whenever a Bluetooth device connects, even if the
 * launcher isn't open. If it's the Pocket Taco, bring the launcher to the front.
 *
 * Android blocks apps from popping up on their own unless the user has given
 * them "Appear on top" permission, which Settings asks for.
 */
class TacoConnectReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val settings = Settings(context)

        val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return

        if (!hasBluetoothPermission(context)) return
        val name = (device.name ?: "").lowercase()
        val isTaco = "pocket" in name || "gamesir" in name
        val isGamepad = device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL
        if (!isTaco && !(settings.anyGamepadCounts && isGamepad)) return
        // Play the intro when the launcher comes up for this attach.
        com.pockettaco.salsa.ui.SplashState.attachedAt = android.os.SystemClock.elapsedRealtime()
        if (!settings.openOnConnect) return

        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }
}

fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

fun canAppearOnTop(context: Context): Boolean = android.provider.Settings.canDrawOverlays(context)
