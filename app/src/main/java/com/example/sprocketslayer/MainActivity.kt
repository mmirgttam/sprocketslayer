package com.example.sprocketslayer

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.example.sprocketslayer.ui.theme.SprocketSlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SprocketSlayerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasBluetoothPermission by remember { mutableStateOf(hasRequiredBluetoothPermissions(context)) }
    var devices by remember { mutableStateOf<List<PairedPrinterDevice>>(emptyList()) }
    var deviceListStatus by remember { mutableStateOf("Loading paired devices…") }
    var connectionState by remember { mutableStateOf<ConnectionUiState>(ConnectionUiState.Idle) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewStatus by remember { mutableStateOf("No image selected") }
    var printState by remember { mutableStateOf<PrintUiState>(PrintUiState.Idle) }

    val imageCropper = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            selectedImageUri = result.uriContent
            printState = PrintUiState.Idle
        } else {
            previewStatus = "Crop cancelled"
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageCropper.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions(
                        guidelines = CropImageView.Guidelines.ON,
                        fixAspectRatio = true,
                        aspectRatioX = SPROCKET_WIDTH,
                        aspectRatioY = SPROCKET_HEIGHT,
                        outputCompressFormat = Bitmap.CompressFormat.JPEG,
                        outputCompressQuality = JPEG_QUALITY,
                    )
                )
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasBluetoothPermission = hasRequiredBluetoothPermissions(context)
        if (hasBluetoothPermission) {
            val result = loadPairedDevices(context)
            devices = result.devices
            deviceListStatus = result.message
        } else {
            val denied = grants.filterValues { !it }.keys.joinToString()
            deviceListStatus = "Bluetooth permission denied: $denied"
        }
    }

    fun refreshDevices() {
        if (!hasBluetoothPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                    )
                )
            } else {
                hasBluetoothPermission = true
            }
            return
        }
        val result = loadPairedDevices(context)
        devices = result.devices
        deviceListStatus = result.message
    }

    fun connect(device: PairedPrinterDevice) {
        if (!hasBluetoothPermission) {
            deviceListStatus = "Bluetooth permission is required before connecting."
            return
        }
        connectionState = ConnectionUiState.Connecting(device.name)
        Thread {
            val result = runCatching { connectAndReadStatus(context, device.address) }
            (context as ComponentActivity).runOnUiThread {
                connectionState = result.fold(
                    onSuccess = {
                        rememberLastPrinter(context, device)
                        refreshDevices()
                        ConnectionUiState.Connected(device.copy(isLastUsed = true), it)
                    },
                    onFailure = { ConnectionUiState.Error(it.message ?: it.toString()) },
                )
            }
        }.start()
    }

    fun printSelectedImage() {
        val connected = connectionState as? ConnectionUiState.Connected ?: return
        val uri = selectedImageUri ?: return
        printState = PrintUiState.Preparing("selected image")
        Thread {
            val result = runCatching {
                val jpeg = loadCroppedJpeg(context, uri)
                printJpeg(context, connected.device.address, jpeg) { progress, detail ->
                    (context as ComponentActivity).runOnUiThread {
                        printState = PrintUiState.Printing(progress, detail)
                    }
                }
            }
            (context as ComponentActivity).runOnUiThread {
                printState = result.fold(
                    onSuccess = { PrintUiState.Complete(it) },
                    onFailure = { PrintUiState.Error(it.message ?: it.toString()) },
                )
            }
        }.start()
    }

    LaunchedEffect(hasBluetoothPermission) { refreshDevices() }

    LaunchedEffect(selectedImageUri) {
        val uri = selectedImageUri
        previewBitmap = null
        if (uri == null) {
            previewStatus = "No image selected"
        } else {
            previewStatus = "Preparing preview…"
            Thread {
                val result = runCatching { loadPreviewBitmap(context, uri) }
                (context as ComponentActivity).runOnUiThread {
                    result.fold(
                        onSuccess = {
                            previewBitmap = it
                            previewStatus = "Preview is cropped to $SPROCKET_WIDTH × $SPROCKET_HEIGHT"
                        },
                        onFailure = {
                            previewStatus = "Preview failed: ${it.message ?: it}"
                        },
                    )
                }
            }.start()
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewBitmap?.recycle() }
    }

    val connected = connectionState as? ConnectionUiState.Connected
    BackHandler(enabled = connected != null) {
        connectionState = ConnectionUiState.Idle
        printState = PrintUiState.Idle
        refreshDevices()
    }

    if (connected == null) {
        PrinterSelectionScreen(
            modifier = modifier,
            devices = devices,
            deviceListStatus = deviceListStatus,
            connectionState = connectionState,
            onRefresh = { refreshDevices() },
            onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            onConnect = { connect(it) },
        )
    } else {
        PrintScreen(
            modifier = modifier,
            connected = connected,
            selectedImageUri = selectedImageUri,
            previewBitmap = previewBitmap,
            previewStatus = previewStatus,
            printState = printState,
            onPickImage = { imagePicker.launch("image/*") },
            onPrint = { printSelectedImage() },
            onChangePrinter = {
                connectionState = ConnectionUiState.Idle
                printState = PrintUiState.Idle
                refreshDevices()
            },
        )
    }
}

@Composable
private fun BrandHeader(subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Sprocket Slayer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PrinterSelectionScreen(
    modifier: Modifier,
    devices: List<PairedPrinterDevice>,
    deviceListStatus: String,
    connectionState: ConnectionUiState,
    onRefresh: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConnect: (PairedPrinterDevice) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandHeader("Choose a paired Bluetooth printer to start.")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bluetooth printer", fontWeight = FontWeight.Bold)
                Text(deviceListStatus)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh) { Text("Refresh") }
                    OutlinedButton(onClick = onOpenBluetoothSettings) { Text("Bluetooth Settings") }
                }
                ConnectionStatusCard(connectionState)
            }
        }

        Text("Paired Bluetooth devices", fontWeight = FontWeight.Bold)
        devices.forEach { device ->
            PairedDeviceCard(device, onClick = { onConnect(device) })
        }
    }
}

@Composable
private fun PrintScreen(
    modifier: Modifier,
    connected: ConnectionUiState.Connected,
    selectedImageUri: Uri?,
    previewBitmap: Bitmap?,
    previewStatus: String,
    printState: PrintUiState,
    onPickImage: () -> Unit,
    onPrint: () -> Unit,
    onChangePrinter: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrandHeader("Crop, preview, and print your pocket photo.")
        ConnectedPrinterCard(connected = connected, onChangePrinter = onChangePrinter)
        ImagePreviewAndPrintCard(
            hasConnectedPrinter = true,
            selectedImageUri = selectedImageUri,
            previewBitmap = previewBitmap,
            previewStatus = previewStatus,
            printState = printState,
            onPickImage = onPickImage,
            onPrint = onPrint,
        )
    }
}

@Composable
private fun ConnectedPrinterCard(
    connected: ConnectionUiState.Connected,
    onChangePrinter: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Connected printer", fontWeight = FontWeight.Bold)
            Text(connected.device.name, style = MaterialTheme.typography.titleMedium)
            Text("Battery: ${connected.status.batteryLevel ?: "?"}%")
            Text("Printer: ${printStatusLabel(connected.status.printStatus)} • Queue: ${queueStatusLabel(connected.status.queueStatus)}")
            OutlinedButton(onClick = onChangePrinter) { Text("Change printer") }
        }
    }
}

@Composable
private fun ImagePreviewAndPrintCard(
    hasConnectedPrinter: Boolean,
    selectedImageUri: Uri?,
    previewBitmap: Bitmap?,
    previewStatus: String,
    printState: PrintUiState,
    onPickImage: () -> Unit,
    onPrint: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Image preview", fontWeight = FontWeight.Bold)
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Print preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(SPROCKET_WIDTH.toFloat() / SPROCKET_HEIGHT.toFloat()),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(SPROCKET_WIDTH.toFloat() / SPROCKET_HEIGHT.toFloat())
                ) {
                    Text(previewStatus, modifier = Modifier.padding(12.dp))
                }
            }
            Text(previewStatus)
            Text("Image: ${selectedImageUri?.lastPathSegment ?: "none selected"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickImage) { Text("Pick image") }
                Button(
                    onClick = onPrint,
                    enabled = hasConnectedPrinter && selectedImageUri != null && previewBitmap != null &&
                        printState !is PrintUiState.Printing && printState !is PrintUiState.Preparing,
                ) { Text("Print") }
            }
            when (printState) {
                PrintUiState.Idle -> Unit
                is PrintUiState.Preparing -> Text("Preparing ${printState.imageLabel}…")
                is PrintUiState.Printing -> {
                    LinearProgressIndicator(
                        progress = { printState.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Printing ${printState.progress}% — ${printState.detail}")
                }
                is PrintUiState.Complete -> Text("Print transfer complete. Job ${printState.jobId}.")
                is PrintUiState.Error -> Text("Print failed: ${printState.message}")
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: ConnectionUiState) {
    when (state) {
        ConnectionUiState.Idle -> Unit
        is ConnectionUiState.Connecting -> Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("Connecting to ${state.deviceName}…")
            }
        }
        is ConnectionUiState.Error -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Connection failed", fontWeight = FontWeight.Bold)
                Text(state.message)
            }
        }
        is ConnectionUiState.Connected -> Unit
    }
}

@Composable
private fun PairedDeviceCard(device: PairedPrinterDevice, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isLastUsed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(device.address)
            Text(
                text = buildList {
                    if (device.isLastUsed) add("Last used")
                    if (device.isLikelySprocket) add("Sprocket name match")
                    if (device.hasSppUuid) add("SPP available") else add("SPP not advertised/unknown")
                }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun printStatusLabel(value: Int?): String = when (value) {
    null -> "Unknown"
    1 -> "Ready"
    else -> "Status $value"
}

private fun queueStatusLabel(value: Int?): String = when (value) {
    null -> "Unknown"
    1 -> "Ready"
    else -> "Status $value"
}

@Preview(showBackground = true)
@Composable
fun PairedDeviceCardPreview() {
    SprocketSlayerTheme {
        PairedDeviceCard(
            PairedPrinterDevice(
                name = "Pocket Printer",
                address = "00:11:22:33:44:55",
                isLikelySprocket = true,
                hasSppUuid = true,
            )
        )
    }
}
