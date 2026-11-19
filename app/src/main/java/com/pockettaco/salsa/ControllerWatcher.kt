package com.pockettaco.salsa

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watches for the Pocket Taco (or any gamepad, if the user allows it).
 *
 * Android tells apps the moment a gamepad connects or disconnects through
 * InputManager, so no Bluetooth permission is needed. In Android mode the
 * Taco pairs as "Gamesir -Pocket 1"; in G-Touch mode as "GameSir-Pocket 1_G".
 */
class ControllerWatcher(context: Context, private val settings: Settings) :
    InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(InputManager::class.java)
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName

    fun start() {
        inputManager.registerInputDeviceListener(this, null)
        refresh()
    }

    fun stop() = inputManager.unregisterInputDeviceListener(this)

    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    fun refresh() {
        val match = InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .firstOrNull { isTaco(it) }
        _deviceName.value = match?.name
        _connected.value = match != null || settings.forceTacoMode
    }

    private fun isTaco(device: InputDevice): Boolean {
        if (device.isVirtual) return false
        val isGamepad = device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        if (!isGamepad) return false
        if (settings.anyGamepadCounts) return true
        val name = device.name.lowercase()
        return "pocket" in name || "gamesir" in name
    }
}
