package org.engawa.microbitblercmq

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.graphics.ColorSpace.Model.RGB
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import org.engawa.microbitblercmq.ui.theme.MicrobitBleRcMqTheme
import java.util.UUID
import kotlin.collections.isNotEmpty

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private val UUID_UART_SERVICE   = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val UUID_UART_RX        = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    data class BleDevice(
        val name: String,
        val address: String,
        val device: BluetoothDevice,
        var gatt: BluetoothGatt? = null,
        var rx: BluetoothGattCharacteristic? = null,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        setContent {
            MicrobitBleRcMqTheme {
                val connect = remember { mutableStateOf<BleDevice?>(null) }
                val command = remember { mutableStateOf("") }
                Column(modifier = Modifier.onKeyEvent { keyProc(it, command) }) {
                    BleConnect(connect)
                    GamePad(connect, command)
                }
            }
        }
    }

    @Composable
    fun BleConnect(connect: MutableState<BleDevice?>) {
        val context = LocalContext.current
        val scanning = remember { mutableStateOf(false) }
        val devices = remember { mutableStateListOf<BleDevice>() }

        val leScanCallback = remember {
            object : ScanCallback() {
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    result.device?.let { dev ->
                        val name = dev.name ?: ""
                        val address = dev.address ?: ""
                        if (devices.all { it.address != address }) {
                            if (name.startsWith("BBC micro:bit")) {
                                devices.add(BleDevice(name, address, dev))
                            }
                        }
                    }
                }
            }
        }

        Row {
            Button(
                content = { Text("Scan Start") },
                enabled = scanning.value == false,
                onClick = {
                    val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
                    if (scanner != null) {
                        if (connect.value != null) {
                            connect.value?.gatt?.disconnect()
                        }
                        devices.clear()
                        scanner.startScan(leScanCallback)
                        scanning.value = true
                    } else {
                        showToast("No scanner.")
                    }
                }
            )
            Button(
                content = { Text("Stop") },
                enabled = scanning.value == true,
                onClick = {
                    val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
                    if (scanner != null) {
                        scanner.stopScan(leScanCallback)
                    } else {
                        showToast("No scanner.")
                    }
                    scanning.value = false
                }
            )
            Button(
                content = { Text("Disconect") },
                enabled = connect.value != null,
                onClick = {
                    devices.clear()
                    connect.value?.gatt?.disconnect()
                }
            )
        }

        val gatCallBack = remember {
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int,newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            gatt?.discoverServices()
                        }
                        BluetoothGatt.STATE_DISCONNECTED -> {
                            gatt?.close()
                            devices.clear()
                            connect.value?.rx = null
                            connect.value?.gatt = null
                            connect.value = null
                        }
                    }
                }
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val uart = gatt?.getService(UUID_UART_SERVICE)
                        connect.value?.rx = uart?.getCharacteristic(UUID_UART_RX)
                    }
                }
            }
        }

        if (devices.isNotEmpty())
        {
            LazyColumn {
                items(devices) {
                    Button(
                        content = { Text(it.name) },
                        enabled = connect.value == null,
                        onClick = {
                            connect.value = it
                            connect.value?.gatt = connect.value?.device?.connectGatt(context, true, gatCallBack)

                            val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
                            if (scanner != null) {
                                scanner.stopScan(leScanCallback)
                            } else {
                                showToast("No scanner.")
                            }
                            scanning.value = false
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun GamePad(connect: MutableState<BleDevice?>,
                command: MutableState<String>) {
        val debugCommand = remember { mutableStateOf( "S" ) }
        val state = rememberLazyListState()
        val keyInfo = remember { mutableStateListOf<String>() }
        val logging = remember { mutableStateOf(false) }

        fun write(cmd: String) {
            if (connect.value != null &&
                connect.value?.gatt != null &&
                connect.value?.rx != null) {
                connect.value?.gatt?.writeCharacteristic(
                    connect.value?.rx!!,
                    "${cmd}\n".toByteArray(),
                    connect.value?.rx?.writeType!!
                )
            }
        }

        LaunchedEffect(command.value) {
            if (command.value.isNotEmpty()) {
                write(command.value)
                if (logging.value) {
                    keyInfo.add(command.value)
                    state.scrollToItem(keyInfo.size, 0)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                content = { Text("Debug") },
                onClick = {
                    write(debugCommand.value)
                }
            )
            TextField(
                label = { Text("Debug command") },
                value = debugCommand.value,
                modifier = Modifier.weight(1.0f),
                onValueChange = { debugCommand.value = it }
            )
            Button(
                content = { Text("EMO") },
                colors = ButtonDefaults.buttonColors().copy(Color.Red),
                onClick = {
                    write("S")
                    speed = 0
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                content = { Text("Clear") },
                onClick = {
                    keyInfo.clear()
                }
            )
            Row(modifier = Modifier.toggleable(logging.value, true, Role.Checkbox) { logging.value = it }) {
                Checkbox(logging.value, null)
                Text("Logging")
            }
        }
        LazyColumn(state = state) {
            items(keyInfo) {
                Text(it)
            }
        }
    }
}

private val hi: Int = 100
private val lo: Int = 10
private var speed: Int = 0
private fun keyCmd(t: KeyEventType, down: String, up: String = "",
                   dproc: (Unit) -> Unit = {}, uproc: (Unit) -> Unit = {}): String {
    return if (t == KeyEventType.KeyDown) {
        dproc(Unit)
        down
    } else {
        uproc(Unit)
        up
    }
}
private fun keyProc(k: KeyEvent, keyCommand: MutableState<String>): Boolean {
    when {
        k.key == Key.DirectionUp -> {
            if (speed >= 0) {
                keyCommand.value = keyCmd(k.type, "M $hi $hi") ; speed = 100
            } else  {
                keyCommand.value = keyCmd(k.type, "S") ; speed = 0
            }
        }
        k.key == Key.DirectionDown -> {
            if (speed > 0) {
                keyCommand.value = keyCmd(k.type, "S") ; speed = 0
            } else {
                keyCommand.value = keyCmd(k.type, "M -$hi -$hi") ; speed = -100
            }
        }
        k.key == Key.DirectionLeft -> {
            if (speed > 0) {
                keyCommand.value = keyCmd(k.type, "M $lo $hi", "M $hi $hi")
            } else if (speed < 0) {
                keyCommand.value = keyCmd(k.type, "M -$lo -$hi", "M -$hi -$hi")
            }
        }
        k.key == Key.DirectionRight -> {
            if (speed > 0) {
                keyCommand.value = keyCmd(k.type, "M $hi $lo", "M $hi $hi")
            } else if (speed < 0) {
                keyCommand.value = keyCmd(k.type, "M -$hi -$lo", "M -$hi -$hi")
            }
        }
        k.key == Key.ButtonA -> { keyCommand.value = keyCmd(k.type, "M $hi $hi") ; speed = 100 }
        k.key == Key.ButtonB -> { keyCommand.value = keyCmd(k.type, "S") ; speed = 0}
        //k.key == Key.ButtonC -> { keyCommand.value = keyCmd(k.type, "STOP", "STOP") }
        k.key == Key.ButtonX -> { keyCommand.value = keyCmd(k.type, "") }
        k.key == Key.ButtonY -> { keyCommand.value = keyCmd(k.type, "M -$hi -$hi") ; speed = -100 }
        //k.key == Key.ButtonZ -> { keyCommand.value = keyCmd(k.type, "STOP", "STOP") }
        k.key == Key.ButtonL1 -> { keyCommand.value = keyCmd(k.type, "M -100 100", "S") ; speed = 0 }
        k.key == Key.ButtonR1 -> { keyCommand.value = keyCmd(k.type, "M 100 -100", "S") ; speed = 0 }
        //k.key == Key.ButtonL2 -> { keyCommand.value = keyCmd(k.type, "STOP", "STOP") }
        //k.key == Key.ButtonR2 -> { keyCommand.value = keyCmd(k.type, "STOP", "STOP") }
        else -> return false
    }
    return true
}

fun ComponentActivity.showToast(message:String)
{
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
