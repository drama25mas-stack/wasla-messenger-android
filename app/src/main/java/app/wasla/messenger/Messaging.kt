package app.wasla.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest

private val WaslaDb get() = FirebaseFirestore.getInstance()

data class WaslaProfile(val uid: String, val name: String, val email: String)
data class WaslaChat(val id: String, val otherUid: String, val otherName: String, val lastMessage: String, val updatedAt: Long)
data class WaslaMessage(val id: String, val senderId: String, val text: String, val createdAt: Long, val readBy: List<String> = emptyList())

fun saveWaslaProfile(user: FirebaseUser, displayName: String, done: (Boolean) -> Unit) {
    val email = user.email?.trim()?.lowercase().orEmpty()
    if (email.isBlank()) { done(false); return }
    val name = displayName.trim().ifBlank { user.displayName ?: email.substringBefore("@") }
    val profile = mapOf("uid" to user.uid, "displayName" to name, "email" to email, "photoUrl" to (user.photoUrl?.toString() ?: ""), "updatedAt" to FieldValue.serverTimestamp())
    val batch = WaslaDb.batch()
    batch.set(WaslaDb.collection("profiles").document(user.uid), profile, SetOptions.merge())
    batch.set(WaslaDb.collection("user_lookup").document(email), profile, SetOptions.merge())
    batch.commit().addOnCompleteListener { done(it.isSuccessful) }
}

fun normalizeWaslaPhone(phone: String): String {
    var digits = phone.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    if (digits.startsWith("0") && digits.length == 11) digits = "20" + digits.drop(1)
    return digits
}

fun saveWaslaPhone(user: FirebaseUser, phone: String, done: (Boolean, String?) -> Unit) {
    val normalized = normalizeWaslaPhone(phone)
    if (normalized.length !in 10..15) {
        done(false, "اكتب رقم هاتف صحيحًا مع مفتاح الدولة.")
        return
    }
    val email = user.email?.trim()?.lowercase().orEmpty()
    val name = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
    val profileRef = WaslaDb.collection("profiles").document(user.uid)
    val lookupRef = WaslaDb.collection("phone_lookup").document(normalized)
    val profileData = mapOf("phone" to normalized, "updatedAt" to FieldValue.serverTimestamp())
    val lookupData = mapOf(
        "uid" to user.uid,
        "displayName" to name,
        "email" to email,
        "phone" to normalized,
        "updatedAt" to FieldValue.serverTimestamp()
    )
    WaslaDb.runBatch { batch ->
        batch.set(profileRef, profileData, SetOptions.merge())
        batch.set(lookupRef, lookupData, SetOptions.merge())
    }.addOnSuccessListener { done(true, null) }
        .addOnFailureListener { done(false, "تعذر حفظ الرقم. تأكد من نشر قواعد Firestore المحدّثة.") }
}

fun lookupWaslaUserByPhone(phone: String, done: (WaslaProfile?, String?) -> Unit) {
    val normalized = normalizeWaslaPhone(phone)
    if (normalized.length !in 10..15) {
        done(null, "اكتب رقم هاتف صحيحًا.")
        return
    }
    WaslaDb.collection("phone_lookup").document(normalized).get()
        .addOnSuccessListener { snap ->
            val uid = snap.getString("uid").orEmpty()
            if (!snap.exists() || uid.isBlank()) {
                done(null, "لا يوجد حساب WASLA مسجل بهذا الرقم.")
            } else {
                done(
                    WaslaProfile(
                        uid,
                        snap.getString("displayName").orEmpty().ifBlank { "مستخدم WASLA" },
                        snap.getString("email").orEmpty()
                    ),
                    null
                )
            }
        }
        .addOnFailureListener { done(null, "تعذر البحث بالرقم. راجع قواعد Firestore واتصال الإنترنت.") }
}

fun lookupWaslaUser(email: String, done: (WaslaProfile?, String?) -> Unit) {
    val normalized = email.trim().lowercase()
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
        done(null, "اكتب بريد إلكتروني صحيح."); return
    }
    WaslaDb.collection("user_lookup").document(normalized).get()
        .addOnSuccessListener { snap ->
            if (!snap.exists()) done(null, "مش لاقيين حساب بالبريد ده. لازم الشخص ينشئ حساب WASLA الأول.")
            else {
                val uid = snap.getString("uid").orEmpty()
                if (uid.isBlank()) done(null, "بيانات الحساب غير مكتملة.")
                else done(WaslaProfile(uid, snap.getString("displayName").orEmpty().ifBlank { normalized.substringBefore("@") }, snap.getString("email").orEmpty()), null)
            }
        }
        .addOnFailureListener { done(null, "تعذر البحث. تأكد من اتصال الإنترنت وإعداد Firestore.") }
}

private fun stableChatId(uidA: String, uidB: String): String {
    val raw = listOf(uidA, uidB).sorted().joinToString("_")
    return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun openWaslaChat(me: FirebaseUser, other: WaslaProfile, done: (String?, String?) -> Unit) {
    if (me.uid == other.uid) { done(null, "مينفعش تبدأ محادثة مع نفسك."); return }
    val id = stableChatId(me.uid, other.uid)
    val ref = WaslaDb.collection("chats").document(id)
    ref.get().addOnSuccessListener { existing ->
        if (existing.exists()) { done(id, null); return@addOnSuccessListener }
        val names = mapOf(me.uid to (me.displayName ?: me.email?.substringBefore("@").orEmpty()), other.uid to other.name)
        val data = mapOf(
            "members" to listOf(me.uid, other.uid).sorted(),
            "memberNames" to names,
            "lastMessage" to "",
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        ref.set(data).addOnSuccessListener { done(id, null) }
            .addOnFailureListener { done(null, "تعذر إنشاء المحادثة. راجع إعدادات Firestore.") }
    }.addOnFailureListener { done(null, "تعذر فتح المحادثة.") }
}

private fun listenWaslaChats(uid: String, onChange: (List<WaslaChat>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
    return WaslaDb.collection("chats").whereArrayContains("members", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { onError("تعذر تحميل المحادثات. راجع قواعد Firestore."); return@addSnapshotListener }
            val chats = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val members = doc.get("members") as? List<*> ?: return@mapNotNull null
                val otherUid = members.filterIsInstance<String>().firstOrNull { it != uid } ?: return@mapNotNull null
                val names = doc.get("memberNames") as? Map<*, *>
                val name = names?.get(otherUid) as? String ?: "مستخدم WASLA"
                val time = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                WaslaChat(doc.id, otherUid, name, doc.getString("lastMessage").orEmpty(), time)
            }.sortedByDescending { it.updatedAt }
            onChange(chats)
        }
}

private fun listenWaslaMessages(chatId: String, onChange: (List<WaslaMessage>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
    return WaslaDb.collection("chats").document(chatId).collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { onError("تعذر تحميل الرسائل."); return@addSnapshotListener }
            onChange(snapshot?.documents.orEmpty().map { doc ->
                WaslaMessage(doc.id, doc.getString("senderId").orEmpty(), doc.getString("text").orEmpty(), doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L, doc.get("readBy") as? List<String> ?: emptyList())
            })
        }
}

private fun markDirectMessageRead(chatId: String, messageId: String, uid: String) {
    WaslaDb.collection("chats").document(chatId).collection("messages").document(messageId).update("readBy", FieldValue.arrayUnion(uid))
}

private fun sendWaslaMessage(chatId: String, user: FirebaseUser, text: String, done: (Boolean) -> Unit) {
    val value = text.trim()
    if (value.isEmpty() || value.length > 5000) { done(false); return }
    val chat = WaslaDb.collection("chats").document(chatId)
    val batch = WaslaDb.batch()
    val message = chat.collection("messages").document()
    batch.set(message, mapOf("senderId" to user.uid, "text" to value, "readBy" to listOf(user.uid), "createdAt" to FieldValue.serverTimestamp()))
    batch.update(chat, mapOf("lastMessage" to value, "updatedAt" to FieldValue.serverTimestamp()))
    batch.commit().addOnCompleteListener { done(it.isSuccessful) }
}

@Composable
fun MessagingHome(currentUser: FirebaseUser) {
    var chats by remember { mutableStateOf<List<WaslaChat>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<WaslaChat?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    DisposableEffect(currentUser.uid) {
        val registration = listenWaslaChats(currentUser.uid, { chats = it; error = null }, { error = it })
        onDispose { registration.remove() }
    }
    val filteredChats = remember(chats, search) {
        val query = search.trim()
        if (query.isEmpty()) chats else chats.filter {
            it.otherName.contains(query, ignoreCase = true) || it.lastMessage.contains(query, ignoreCase = true)
        }
    }
    if (selected != null) {
        WaslaChatScreen(currentUser, selected!!, onBack = { selected = null })
        return
    }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مساحة تواصلك", color = Color(0xFFEAF8FF), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("رسائلك الحقيقية في مكان واحد", color = Color(0xFF9BAFC3), fontSize = 12.sp)
                }
                FilledTonalIconButton(onClick = { showNew = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "محادثة جديدة")
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("ابحث في المحادثات") },
                leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null) }
            )
            Spacer(Modifier.height(12.dp))
            if (error != null) Text(error!!, color = Color(0xFFFFA0A0), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            if (filteredChats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                        Icon(Icons.Default.Chat, null, tint = Color(0xFF8DEBFA), modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(if (chats.isEmpty()) "لسه مفيش محادثات" else "مفيش نتائج مطابقة", color = Color(0xFFEAF8FF), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(if (chats.isEmpty()) "اضغط على علامة إضافة شخص وابدأ محادثة باستخدام بريده الإلكتروني." else "جرّب اسمًا أو كلمة مختلفة.", color = Color(0xFF9BAFC3), textAlign = TextAlign.Center, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showNew = true }) { Text("ابدأ محادثة") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(filteredChats, key = { it.id }) { chat ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x241EADC8))
                                .clickable { selected = chat }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0x334CCFE5)), contentAlignment = Alignment.Center) {
                                Text(chat.otherName.take(1).uppercase(), color = Color(0xFF8DEBFA), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chat.otherName, color = Color(0xFFEAF8FF), fontWeight = FontWeight.SemiBold)
                                Text(chat.lastMessage.ifBlank { "ابدأ المحادثة الآن" }, color = Color(0xFF9BAFC3), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showNew) NewWaslaChatDialog(currentUser, onDismiss = { showNew = false }, onOpen = { chat ->
        showNew = false
        selected = chat
    })
}

@Composable
private fun NewWaslaChatDialog(currentUser: FirebaseUser, onDismiss: () -> Unit, onOpen: (WaslaChat) -> Unit) {
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("محادثة جديدة") },
        text = {
            Column {
                Text("اكتب البريد الإلكتروني المسجل على WASLA للشخص اللي عايز تكلمه.", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = email, onValueChange = { email = it; error = null }, label = { Text("البريد الإلكتروني") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (loading) return@TextButton
                loading = true
                lookupWaslaUser(email) { profile, lookupError ->
                    if (profile == null) { loading = false; error = lookupError }
                    else openWaslaChat(currentUser, profile) { id, openError ->
                        loading = false
                        if (id == null) error = openError
                        else onOpen(WaslaChat(id, profile.uid, profile.name, "", System.currentTimeMillis()))
                    }
                }
            }, enabled = !loading) { if (loading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("فتح المحادثة") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("إلغاء") } }
    )
}

@Composable
private fun WaslaChatScreen(currentUser: FirebaseUser, chat: WaslaChat, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val readReceiptsEnabled = remember { context.getSharedPreferences("wasla_settings", android.content.Context.MODE_PRIVATE).getBoolean("read_receipts", true) }
    var messages by remember(chat.id) { mutableStateOf<List<WaslaMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    DisposableEffect(chat.id) {
        val registration = listenWaslaMessages(chat.id, { incoming ->
            messages = incoming; error = null
            val unread = if (readReceiptsEnabled) {
                incoming.filter { it.senderId != currentUser.uid && currentUser.uid !in it.readBy }
            } else {
                emptyList()
            }
            unread.forEach { message ->
                markDirectMessageRead(chat.id, message.id, currentUser.uid)
            }
        }, { error = it })
        onDispose { registration.remove() }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color(0xFFEAF8FF)) }
            Column {
                Text(chat.otherName, color = Color(0xFFEAF8FF), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("محادثة عبر الإنترنت", color = Color(0xFF8DEBFA), fontSize = 11.sp)
            }
        }
        if (error != null) Text(error!!, color = Color(0xFFFFA0A0), fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderId == currentUser.uid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                    Text(
                        message.text,
                        color = Color(0xFFEAF8FF),
                        modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (mine) Color(0xFF14516A) else Color(0xFF26394E)).padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 14.sp
                    )
                    if (mine) Text(if (chat.otherUid in message.readBy) "✓✓" else "✓", color = if (chat.otherUid in message.readBy) Color(0xFF62DDF2) else Color(0xFF9BAFC3), fontSize = 11.sp, modifier = Modifier.align(Alignment.Bottom))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 5000) draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("اكتب رسالتك...") }, maxLines = 4)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    if (draft.isNotBlank() && !sending) {
                        sending = true
                        val value = draft
                        sendWaslaMessage(chat.id, currentUser, value) { ok ->
                            sending = false
                            if (ok) draft = "" else error = "فشل إرسال الرسالة. حاول مرة أخرى."
                        }
                    }
                },
                enabled = draft.isNotBlank() && !sending
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Send, contentDescription = "إرسال")
            }
        }
    }
}


private fun readDeviceContacts(context: android.content.Context): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val cursor = context.contentResolver.query(
        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null,
        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
    )
    cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (it.moveToNext()) {
            val name = if (nameIndex >= 0) it.getString(nameIndex).orEmpty() else ""
            val number = if (numberIndex >= 0) it.getString(numberIndex).orEmpty() else ""
            if (name.isNotBlank() && number.isNotBlank()) result.add(name to number)
        }
    }
    return result.distinctBy { normalizeWaslaPhone(it.second) }
}

@Composable
fun ContactsHome(currentUser: FirebaseUser) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var chat by remember { mutableStateOf<WaslaChat?>(null) }
    var deviceContacts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var permissionGranted by remember {
        mutableStateOf(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) deviceContacts = readDeviceContacts(context)
    }
    if (chat != null) {
        WaslaChatScreen(currentUser, chat!!, onBack = { chat = null })
        return
    }
    androidx.compose.runtime.LaunchedEffect(permissionGranted) {
        if (permissionGranted && deviceContacts.isEmpty()) {
            deviceContacts = readDeviceContacts(context)
        }
    }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Text("جهات الاتصال", color = Color(0xFFEAF8FF), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("ابحث بالبريد أو رقم الهاتف، أو اعرض جهات اتصال جهازك", color = Color(0xFF9BAFC3), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it; error = null },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("البريد الإلكتروني أو رقم الهاتف") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (search.contains("@")) androidx.compose.ui.text.input.KeyboardType.Email else androidx.compose.ui.text.input.KeyboardType.Phone
                )
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    loading = true
                    error = null
                    val query = search.trim()
                    val lookup: ((WaslaProfile?, String?) -> Unit) = { profile, lookupError ->
                        if (profile == null) { loading = false; error = lookupError }
                        else openWaslaChat(currentUser, profile) { id, openError ->
                            loading = false
                            if (id == null) error = openError
                            else chat = WaslaChat(id, profile.uid, profile.name, "", System.currentTimeMillis())
                        }
                    }
                    if (query.contains("@")) lookupWaslaUser(query, lookup)
                    else lookupWaslaUserByPhone(query, lookup)
                },
                enabled = !loading && search.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("بحث وفتح محادثة")
            }
            if (error != null) Text(error!!, color = Color(0xFFFFA0A0), fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("جهات اتصال الهاتف", color = Color(0xFFEAF8FF), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (permissionGranted) deviceContacts = readDeviceContacts(context)
                    else permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }) { Text(if (permissionGranted) "تحديث" else "السماح بالوصول") }
            }
            Text("الأسماء والأرقام تظل على جهازك. لا نرفع دفتر العناوين؛ عند الضغط على تحقق نبحث عن الرقم المحدد فقط.", color = Color(0xFF9BAFC3), fontSize = 11.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(8.dp))
            if (!permissionGranted) {
                Text("اسمح بالوصول لعرض جهات اتصالك والبحث عن أصدقائك على WASLA.", color = Color(0xFF9BAFC3), fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
            } else if (deviceContacts.isEmpty()) {
                Text("لا توجد جهات اتصال بأرقام هاتف محفوظة.", color = Color(0xFF9BAFC3), fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(deviceContacts, key = { normalizeWaslaPhone(it.second) }) { contact ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x241EADC8)).padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(contact.first, color = Color(0xFFEAF8FF), fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(contact.second, color = Color(0xFF9BAFC3), fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                if (!loading) {
                                    loading = true
                                    error = null
                                    lookupWaslaUserByPhone(contact.second) { profile, lookupError ->
                                        if (profile == null) { loading = false; error = lookupError }
                                        else openWaslaChat(currentUser, profile) { id, openError ->
                                            loading = false
                                            if (id == null) error = openError
                                            else chat = WaslaChat(id, profile.uid, profile.name, "", System.currentTimeMillis())
                                        }
                                    }
                                }
                            }, enabled = !loading) { Text("تحقق") }
                        }
                    }
                }
            }
        }
    }
}
