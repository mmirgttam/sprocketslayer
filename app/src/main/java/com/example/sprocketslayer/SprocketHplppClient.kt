package com.example.sprocketslayer

import android.annotation.SuppressLint
import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@SuppressLint("MissingPermission")
fun connectAndReadStatus(context: Context, address: String): PrinterStatus {
    val adapter = bluetoothAdapter(context) ?: error("Bluetooth not supported")
    val device = adapter.getRemoteDevice(address)
    adapter.cancelDiscovery()

    device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket ->
        socket.connect()
        val input = socket.inputStream
        val output = socket.outputStream

        val conn = hplppRequest(input, output, byteArrayOf(0x24, 0x00, 0x10))
        require(conn.firstOrNull() == 0x25.toByte()) { "Unexpected connection response: ${conn.toHex()}" }
        val maxTarget = conn.readUShortLE(1)
        val security = conn[3].toInt() and 0xff

        val status = hplppRequest(input, output, byteArrayOf(0x08, 1, 2, 3, 4, 5, 6, 7, 9))
        require(status.firstOrNull() == 0x09.toByte()) { "Unexpected status response: ${status.toHex()}" }
        return parsePrinterStatus(status, maxTarget, security)
    }
}

@SuppressLint("MissingPermission")
fun printJpeg(
    context: Context,
    address: String,
    jpeg: ByteArray,
    onProgress: (progress: Int, detail: String) -> Unit,
): Int {
    val adapter = bluetoothAdapter(context) ?: error("Bluetooth not supported")
    val device = adapter.getRemoteDevice(address)
    adapter.cancelDiscovery()

    device.createRfcommSocketToServiceRecord(SPP_UUID).use { socket ->
        socket.connect()
        val input = socket.inputStream
        val output = socket.outputStream

        val conn = hplppRequest(input, output, byteArrayOf(0x24, 0x00, 0x10))
        require(conn.firstOrNull() == 0x25.toByte()) { "Unexpected connection response: ${conn.toHex()}" }
        val maxTarget = conn.readUShortLE(1)
        val chunkSize = maxTarget - 2 // command + fileHandle are the FILE_WRITE_REQ header

        val start = hplppRequest(input, output, byteArrayOf(0x0c, 0x01) + intLe(jpeg.size))
        require(start.firstOrNull() == 0x0d.toByte()) { "Unexpected print start response: ${start.toHex()}" }
        val fileHandle = start[1]
        val jobId = start.readUShortLE(2)

        var pos = 0
        repeat(2_000) {
            val end = (pos + chunkSize).coerceAtMost(jpeg.size)
            val chunk = jpeg.copyOfRange(pos, end)
            val rsp = hplppRequest(input, output, byteArrayOf(0x0e, fileHandle) + chunk)
            require(rsp.firstOrNull() == 0x0f.toByte()) { "Unexpected file write response: ${rsp.toHex()}" }
            val status = rsp[2].toInt() and 0xff
            val received = rsp.readUIntLE(3).toInt()
            val total = rsp.readUIntLE(7).toInt()
            val progress = if (total > 0) (received * 100 / total).coerceIn(0, 100) else 0
            onProgress(progress, "$received/$total bytes")

            when (status) {
                1 -> pos = received
                2 -> return jobId
                3 -> error("Printer cancelled the transfer")
                4 -> error("Printer failed the transfer")
                else -> error("Unknown FILE_WRITE_RSP status $status: ${rsp.toHex()}")
            }
        }
        error("Print transfer did not complete")
    }
}

private fun hplppRequest(input: InputStream, output: OutputStream, body: ByteArray): ByteArray {
    output.write(hplppEnvelope(body))
    output.flush()
    val rsp = readHplppBody(input)
    if (rsp.firstOrNull() == 0x01.toByte()) error("Printer returned HPLPP error: ${rsp.toHex()}")
    return rsp
}

private fun hplppEnvelope(body: ByteArray): ByteArray {
    val prefix = byteArrayOf('H'.code.toByte(), 'P'.code.toByte(), '+'.code.toByte())
    return when {
        body.size < 256 -> prefix + byteArrayOf(1, body.size.toByte()) + body
        body.size < 65_536 -> prefix + byteArrayOf(2) + shortLe(body.size) + body
        else -> prefix + byteArrayOf(3) + intLe(body.size) + body
    }
}

private fun readHplppBody(input: InputStream): ByteArray {
    fun readExact(count: Int): ByteArray {
        val out = ByteArray(count)
        var pos = 0
        while (pos < count) {
            val read = input.read(out, pos, count - pos)
            if (read < 0) error("Socket closed while reading")
            pos += read
        }
        return out
    }

    var a = input.read()
    var b = input.read()
    var c = input.read()
    while (!(a == 'H'.code && b == 'P'.code && c == '+'.code)) {
        if (c < 0) error("Socket closed before HP+ header")
        a = b
        b = c
        c = input.read()
    }

    val lengthType = readExact(1)[0].toInt() and 0xff
    val length = when (lengthType) {
        1 -> readExact(1)[0].toInt() and 0xff
        2 -> readExact(2).readUShortLE(0)
        3 -> ByteBuffer.wrap(readExact(4)).order(ByteOrder.LITTLE_ENDIAN).int
        else -> error("Unknown HPLPP length type $lengthType")
    }
    return readExact(length)
}

private fun parsePrinterStatus(body: ByteArray, maxTarget: Int, security: Int): PrinterStatus {
    var i = 1
    var systemFlags: Long? = null
    var printStatus: Int? = null
    var batteryLevel: Int? = null
    var printProgress: Int? = null
    var currentJob: Int? = null
    var batteryStatus: Int? = null
    var queueStatus: Int? = null
    var numHosts: Int? = null

    while (i < body.size) {
        when (val field = body[i++].toInt() and 0xff) {
            1 -> { systemFlags = body.readUIntLE(i); i += 4 }
            2 -> printStatus = body[i++].toInt() and 0xff
            3 -> batteryLevel = body[i++].toInt() and 0xff
            4 -> printProgress = body[i++].toInt() and 0xff
            5 -> { currentJob = body.readUShortLE(i); i += 2 }
            6 -> batteryStatus = body[i++].toInt() and 0xff
            7 -> queueStatus = body[i++].toInt() and 0xff
            9 -> numHosts = body[i++].toInt() and 0xff
            else -> error("Unknown status field $field in ${body.toHex()}")
        }
    }

    return PrinterStatus(maxTarget, security, systemFlags, printStatus, batteryLevel, printProgress, currentJob, batteryStatus, queueStatus, numHosts)
}

private fun shortLe(value: Int): ByteArray = ByteBuffer
    .allocate(2)
    .order(ByteOrder.LITTLE_ENDIAN)
    .putShort(value.toShort())
    .array()

private fun intLe(value: Int): ByteArray = ByteBuffer
    .allocate(4)
    .order(ByteOrder.LITTLE_ENDIAN)
    .putInt(value)
    .array()

private fun ByteArray.readUShortLE(offset: Int): Int =
    (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.readUIntLE(offset: Int): Long =
    (this[offset].toLong() and 0xff) or
        ((this[offset + 1].toLong() and 0xff) shl 8) or
        ((this[offset + 2].toLong() and 0xff) shl 16) or
        ((this[offset + 3].toLong() and 0xff) shl 24)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
