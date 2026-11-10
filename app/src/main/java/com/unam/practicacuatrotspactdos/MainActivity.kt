package com.unam.practicacuatrotspactdos

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.unam.practicacuatrotspactdos.ui.theme.PracticacuatrotspactdosTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val myUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Estado para guardar los mensajes del log de comunicación
    private val logMessages = mutableStateListOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            Toast.makeText(this, "Permisos de Bluetooth concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Se requieren permisos para conectar", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestBluetoothPermissions()

        setContent {
            PracticacuatrotspactdosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ControlScreen(
                        logList = logMessages,
                        onConnect = { macAddress -> connectToDevice(macAddress) },
                        onSendCommand = { command -> sendData(command) }
                    )
                }
            }
        }
    }

    private fun checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val missingPermissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (missingPermissions.isNotEmpty()) {
                requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }

    @Suppress("MissingPermission")
    private fun connectToDevice(macAddress: String) {
        if (macAddress.isBlank()) {
            Toast.makeText(this, "Ingresa una dirección MAC válida", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Permiso de Bluetooth no concedido", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress.trim())
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUuid)
                bluetoothAdapter.cancelDiscovery()
                bluetoothSocket?.connect()

                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "¡Conectado al ESP32!", Toast.LENGTH_SHORT).show()
                    logMessages.add("Estado: Conectado")
                }

                // Iniciar la escucha de datos entrantes (Actividad 2 - Recepción)
                startReceivingData()

            } catch (e: Exception) {
                try { bluetoothSocket?.close() } catch (_: IOException) {}
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error de conexión", Toast.LENGTH_LONG).show()
                    logMessages.add("Error al conectar: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun startReceivingData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (bluetoothSocket?.isConnected == true) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val incomingMessage = String(buffer, 0, bytes).trim()
                        withContext(Dispatchers.Main) {
                            logMessages.add("RX: $incomingMessage") // Muestra la temperatura u otros datos recibidos
                        }
                    }
                } catch (_: IOException) {
                    break
                }
            }
        }
    }

    private fun sendData(input: String) {
        if (bluetoothSocket?.isConnected == true) {
            try {
                outputStream?.write(input.toByteArray())
                logMessages.add("TX: $input")
            } catch (_: IOException) {
                Toast.makeText(this, "Error al enviar datos", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No hay conexión Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ControlScreen(
    logList: List<String>,
    onConnect: (String) -> Unit,
    onSendCommand: (String) -> Unit
) {
    var macAddress by remember { mutableStateOf("") } // Escribe aquí la MAC de tu ESP32

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Control ESP32 - Actividad 2", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("Dirección MAC del ESP32") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onConnect(macAddress) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Conectar")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSendCommand("1") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("LEDs ON")
            }

            Button(
                onClick = { onSendCommand("t") }, // Pide la temperatura al ESP32
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("LEER TEMP")
            }

            Button(
                onClick = { onSendCommand("0") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
            ) {
                Text("APAGAR")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Log de Comunicación:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // Ventaja de visualización en pantalla (Log)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            LazyColumn(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logList) { message ->
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}