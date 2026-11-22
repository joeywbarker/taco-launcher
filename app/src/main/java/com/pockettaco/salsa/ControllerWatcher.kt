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
    /** Every input device Android sees, shown in Settings to help spot the Taco. */
    private val _allDevices = MutableStateFlow<List<String>>(emptyList())
    val allDevices: StateFlow<List<String>> = _allDevices

    /** Called when the Taco is attached while the launcher is on screen. */
    var onAttached: (() -> Unit)? = null
    private var listening = false

    fun start() {
        inputManager.registerInputDeviceListener(this, null)
        listening = false   // what we find now may have happened while we weren't looking
        refresh()
        listening = true
    }

    fun stop() { listening = false; inputManager.unregisterInputDeviceListener(this) }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    fun refresh() {
        val match = InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .firstOrNull { isTaco(it) }
        val was = _deviceName.value
        _deviceName.value = match?.name
        if (listening && was == null && match != null) onAttached?.invoke()
        _allDevices.value = InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual }
            .map { it.name }
        _connected.value = match != null || settings.forceTacoMode
    }

    private fun isTaco(device: InputDevice): Boolean {
        if (device.isVirtual) return false
        // In G-Touch mode the Taco can show up as a keyboard or touch device
        // rather than a gamepad, so match the name first, whatever kind it is.
        val name = device.name.lowercase()
        if ("pocket" in name || "gamesir" in name) return true
        if (!settings.anyGamepadCounts) return false
        val s = device.sources
        return s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }
}
