package app.wasla.messenger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.UUID

private const val WaslaNearbyService = "app.wasla.messenger.nearby.v1"
private val WaslaNearbyStrategy = Strategy.P2P_CLUSTER
private data class NearbyLine(val id: String, val text: String, val mine: Boolean)

@Composable
fun NearbyMessagingScreen() {
    val context = LocalContext.current
    val client = remember { Nearby.getConnectionsClient(context) }
    val deviceName = remember { "WASLA-${UUID.randomUUID().toString().take(4).uppercase()}" }
    val peers = remember { mutableStateMapOf<String, String>() }
    val messages = remember { mutableStateListOf<NearbyLine>() }
    var status by remember { mutableStateOf("فعّل الأذونات للبحث عن أجهزة WASLA القريبة.") }
    var running by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var connectedEndpoint by remember { mutableStateOf<String?>(null) }
    var pendingEndpoint by remember { mutableStateOf<String?>(null) }
    var pendingName by remember { mutableStateOf("") }

    val payloadCallback = remember {
        object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                val text = payload.asBytes()?.toString(Charsets.UTF_8) ?: return
                messages.add(NearbyLine(UUID.randomUUID().toString(), text, false))
            }
            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
        }
    }
    val lifecycleCallback = remember {
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                pendingEndpoint = endpointId
                pendingName = info.endpointName
            }
            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedEndpoint = endpointId
                    pendingEndpoint = null
                    status = "متصل مباشرة مع الجهاز القريب. تقدر تراسله بدون إنترنت."
                } else status = "الاتصال لم يكتمل. جرّب مرة أخرى."
            }
            override fun onDisconnected(endpointId: String) {
                if (connectedEndpoint == endpointId) connectedEndpoint = null
                status = "انقطع الاتصال بالجهاز القريب."
            }
        }
    }
    val discoveryCallback = remember {
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                peers[endpointId] = info.endpointName
            }
            override fun onEndpointLost(endpointId: String) { peers.remove(endpointId) }
        }
    }

    fun startNearby() {
        if (running) return
        running = true
        status = "جاري تشغيل البحث والظهور للأجهزة القريبة..."
        client.startAdvertising(deviceName, WaslaNearbyService, lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(WaslaNearbyStrategy).build())
            .addOnFailureListener { status = "تعذر تشغيل استقبال الاتصالات القريبة: ${it.localizedMessage ?: "خطأ غير معروف"}" }
        client.startDiscovery(WaslaNearbyService, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(WaslaNearbyStrategy).build())
            .addOnFailureListener { status = "تعذر البحث عن الأجهزة: ${it.localizedMessage ?: "خطأ غير معروف"}" }
    }

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (requiredPermissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) startNearby()
        else status = "لازم تسمح بأذونات الأجهزة القريبة عشان الاتصال المباشر يشتغل."
    }

    DisposableEffect(client) {
        onDispose {
            client.stopAdvertising()
            client.stopDiscovery()
            connectedEndpoint?.let { client.disconnectFromEndpoint(it) }
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("اتصال بدون إنترنت", color = Color(0xFFEAF8FF), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("اتصال مباشر بين الأجهزة القريبة", color = Color(0xFF9BAFC3), fontSize = 12.sp)
                }
                Icon(Icons.Default.BluetoothSearching, null, tint = Color(0xFF8DEBFA), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(status, color = Color(0xFF9BAFC3), fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            if (!running) {
                Button(onClick = {
                    val missing = requiredPermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isEmpty()) startNearby() else permissionLauncher.launch(missing.toTypedArray())
                }, modifier = Modifier.fillMaxWidth()) { Text("ابدأ البحث عن أجهزة قريبة") }
            } else {
                OutlinedButton(onClick = {
                    client.stopAdvertising(); client.stopDiscovery(); peers.clear()
                    running = false; status = "تم إيقاف البحث."
                }, modifier = Modifier.fillMaxWidth()) { Text("إيقاف البحث") }
            }
            Spacer(Modifier.height(16.dp))
            Text("الأجهزة المكتشفة", color = Color(0xFFEAF8FF), fontWeight = FontWeight.Bold)
            if (peers.isEmpty()) {
                Text("افتح WASLA على جهاز آخر واضغط بدء البحث بالقرب منك.", color = Color(0xFF9BAFC3), fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(peers.toList(), key = { it.first }) { (endpointId, name) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x241EADC8)).clickable {
                            client.requestConnection(deviceName, endpointId, lifecycleCallback)
                                .addOnFailureListener { status = "تعذر طلب الاتصال: ${it.localizedMessage}" }
                            status = "تم إرسال طلب الاتصال إلى $name."
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = Color(0xFFEAF8FF), modifier = Modifier.weight(1f))
                            Text("اتصال", color = Color(0xFF8DEBFA), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(if (connectedEndpoint == null) "المحادثة المباشرة" else "متصل الآن", color = Color(0xFFEAF8FF), fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(messages, key = { it.id }) { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.mine) Arrangement.Start else Arrangement.End) {
                        Text(line.text, color = Color(0xFFEAF8FF), modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (line.mine) Color(0xFF14516A) else Color(0xFF26394E)).padding(11.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                OutlinedTextField(value = draft, onValueChange = { if (it.length <= 2000) draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("رسالة قريبة...") }, enabled = connectedEndpoint != null, maxLines = 3)
                Spacer(Modifier.width(7.dp))
                FilledIconButton(onClick = {
                    val endpoint = connectedEndpoint
                    val value = draft.trim()
                    if (endpoint != null && value.isNotEmpty()) {
                        client.sendPayload(endpoint, Payload.fromBytes(value.toByteArray(Charsets.UTF_8)))
                            .addOnSuccessListener { messages.add(NearbyLine(UUID.randomUUID().toString(), value, true)); draft = "" }
                            .addOnFailureListener { status = "فشل الإرسال. حاول إعادة الاتصال." }
                    }
                }, enabled = connectedEndpoint != null && draft.isNotBlank()) { Icon(Icons.Default.Send, contentDescription = "إرسال") }
            }
        }
    }
    if (pendingEndpoint != null) {
        AlertDialog(onDismissRequest = { },
            title = { Text("طلب اتصال قريب") },
            text = { Text("الجهاز $pendingName يطلب الاتصال بك. هل توافق؟") },
            confirmButton = {
                TextButton(onClick = {
                    val endpoint = pendingEndpoint ?: return@TextButton
                    client.acceptConnection(endpoint, payloadCallback).addOnFailureListener { status = "تعذر قبول الاتصال." }
                    pendingEndpoint = null
                }) { Text("قبول") }
            },
            dismissButton = {
                TextButton(onClick = { pendingEndpoint?.let { client.rejectConnection(it) }; pendingEndpoint = null }) { Text("رفض") }
            }
        )
    }
}
