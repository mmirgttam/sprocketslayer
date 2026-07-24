package com.example.sprocketslayer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun hasRequiredBluetoothPermissions(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED)
}

fun bluetoothAdapter(context: Context): BluetoothAdapter? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    } else {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()
    }
}

@SuppressLint("MissingPermission")
private const val PREFS_NAME = "sprocket_slayer_prefs"
private const val PREF_LAST_PRINTER_ADDRESS = "last_printer_address"
private const val PREF_LAST_PRINTER_NAME = "last_printer_name"

fun rememberLastPrinter(context: Context, device: PairedPrinterDevice) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_LAST_PRINTER_ADDRESS, device.address)
        .putString(PREF_LAST_PRINTER_NAME, device.name)
        .apply()
}

fun getLastPrinterAddress(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_LAST_PRINTER_ADDRESS, null)

fun getLastPrinterName(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_LAST_PRINTER_NAME, null)

fun loadPairedDevices(context: Context): LoadDevicesResult {
    if (!hasRequiredBluetoothPermissions(context)) {
        return LoadDevicesResult(emptyList(), "Bluetooth permission is required.")
    }

    val adapter = bluetoothAdapter(context)
        ?: return LoadDevicesResult(emptyList(), "This device does not support Bluetooth.")
    if (!adapter.isEnabled) {
        return LoadDevicesResult(emptyList(), "Bluetooth is off. Turn it on in system settings.")
    }

    val lastAddress = getLastPrinterAddress(context)
    val paired = adapter.bondedDevices.orEmpty()
        .map { it.toPairedPrinterDevice(lastAddress) }
        .sortedWith(
            compareByDescending<PairedPrinterDevice> { it.isLastUsed }
                .thenByDescending { it.isLikelySprocket }
                .thenByDescending { it.hasSppUuid }
                .thenBy { it.name.lowercase() }
        )

    val message = when {
        paired.isEmpty() -> "No paired devices. Pair your printer in Android Bluetooth settings first."
        else -> "Found ${paired.size} paired Bluetooth device(s)."
    }
    return LoadDevicesResult(paired, message)
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.toPairedPrinterDevice(lastAddress: String?): PairedPrinterDevice {
    val safeName = name ?: "Unknown device"
    val hasSpp = uuids?.any { it.uuid == SPP_UUID } == true
    val likelySprocket = safeName.contains("sprocket", ignoreCase = true)
    return PairedPrinterDevice(
        name = safeName,
        address = address,
        isLikelySprocket = likelySprocket,
        hasSppUuid = hasSpp,
        isLastUsed = address == lastAddress,
    )
}
