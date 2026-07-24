package com.example.sprocketslayer

import java.util.UUID

val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
const val SPROCKET_WIDTH = 1002
const val SPROCKET_HEIGHT = 640
const val JPEG_QUALITY = 90

data class PairedPrinterDevice(
    val name: String,
    val address: String,
    val isLikelySprocket: Boolean,
    val hasSppUuid: Boolean,
    val isLastUsed: Boolean = false,
)

data class PrinterStatus(
    val maxTargetMessageSize: Int,
    val security: Int,
    val systemFlags: Long?,
    val printStatus: Int?,
    val batteryLevel: Int?,
    val printProgress: Int?,
    val currentJob: Int?,
    val batteryStatus: Int?,
    val queueStatus: Int?,
    val numHosts: Int?,
)

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data class Connecting(val deviceName: String) : ConnectionUiState
    data class Connected(val device: PairedPrinterDevice, val status: PrinterStatus) : ConnectionUiState
    data class Error(val message: String) : ConnectionUiState
}

sealed interface PrintUiState {
    data object Idle : PrintUiState
    data class Preparing(val imageLabel: String) : PrintUiState
    data class Printing(val progress: Int, val detail: String) : PrintUiState
    data class Complete(val jobId: Int) : PrintUiState
    data class Error(val message: String) : PrintUiState
}

data class LoadDevicesResult(
    val devices: List<PairedPrinterDevice>,
    val message: String,
)
