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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.UUID

private data class WaslaGroup(val id: String, val name: String, val lastMessage: String, val updatedAt: Long)
private data class GroupLine(val id: String, val senderId: String, val text: String)

private fun createWaslaGroup(user: FirebaseUser, name: String, members: List<WaslaProfile>, done: (WaslaGroup?, String?) -> Unit) {
    val all = (members + WaslaProfile(user.uid, user.displayName ?: user.email?.substringBefore("@").orEmpty(), user.email.orEmpty())).distinctBy { it.uid }
    if (all.size < 2) { done(null, "أضف شخصًا واحدًا على الأقل للمجموعة."); return }
    if (all.size > 50) { done(null, "الحد الأقصى للمجموعة 50 عضوًا."); return }
    val memberNames = all.associate { it.uid to it.name }
    val ref = FirebaseFirestore.getInstance().collection("groups").document()
    ref.set(mapOf(
        "name" to name.trim(),
        "members" to all.map { it.uid }.sorted(),
        "memberNames" to memberNames,
        "createdBy" to user.uid,
        "lastMessage" to "",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )).addOnSuccessListener { done(WaslaGroup(ref.id, name.trim(), "", System.currentTimeMillis()), null) }
        .addOnFailureListener { done(null, "تعذر إنشاء المجموعة. تأكد من نشر قواعد Firestore.") }
}

private fun listenWaslaGroups(uid: String, onChange: (List<WaslaGroup>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
    return FirebaseFirestore.getInstance().collection("groups").whereArrayContains("members", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { onError("تعذر تحميل المجموعات. راجع قواعد Firestore."); return@addSnapshotListener }
            onChange(snapshot?.documents.orEmpty().map { doc ->
                WaslaGroup(doc.id, doc.getString("name").orEmpty(), doc.getString("lastMessage").orEmpty(), doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L)
            }.sortedByDescending { it.updatedAt })
        }
}

private fun listenGroupMessages(groupId: String, onChange: (List<GroupLine>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
    return FirebaseFirestore.getInstance().collection("groups").document(groupId).collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) { onError("تعذر تحميل رسائل المجموعة."); return@addSnapshotListener }
            onChange(snapshot?.documents.orEmpty().map { GroupLine(it.id, it.getString("senderId").orEmpty(), it.getString("text").orEmpty()) })
        }
}

private fun sendGroupLine(groupId: String, user: FirebaseUser, text: String, done: (Boolean) -> Unit) {
    val value = text.trim()
    if (value.isEmpty() || value.length > 5000) { done(false); return }
    val ref = FirebaseFirestore.getInstance().collection("groups").document(groupId)
    val batch = FirebaseFirestore.getInstance().batch()
    batch.set(ref.collection("messages").document(), mapOf("senderId" to user.uid, "text" to value, "createdAt" to FieldValue.serverTimestamp()))
    batch.update(ref, mapOf("lastMessage" to value, "updatedAt" to FieldValue.serverTimestamp()))
    batch.commit().addOnCompleteListener { done(it.isSuccessful) }
}

@Composable
fun GroupsHome(currentUser: FirebaseUser) {
    var groups by remember { mutableStateOf<List<WaslaGroup>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<WaslaGroup?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    DisposableEffect(currentUser.uid) {
        val registration = listenWaslaGroups(currentUser.uid, { groups = it; error = null }, { error = it })
        onDispose { registration.remove() }
    }
    if (selected != null) {
        GroupThread(currentUser, selected!!, onBack = { selected = null })
        return
    }
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("المجموعات", color = Color(0xFFEAF8FF), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("محادثاتك الجماعية", color = Color(0xFF9BAFC3), fontSize = 12.sp)
                }
                FilledTonalButton(onClick = { createDialog = true }) { Icon(Icons.Default.Groups, null); Spacer(Modifier.width(6.dp)); Text("مجموعة جديدة") }
            }
            if (error != null) Text(error!!, color = Color(0xFFFFA0A0), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(16.dp))
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Groups, null, tint = Color(0xFF8DEBFA), modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("لسه مفيش مجموعات", color = Color(0xFFEAF8FF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("أنشئ مجموعة وابدأ الكلام مع أكتر من شخص.", color = Color(0xFF9BAFC3), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x241EADC8)).clickable { selected = group }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x334CCFE5)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Groups, null, tint = Color(0xFF8DEBFA))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.name, color = Color(0xFFEAF8FF), fontWeight = FontWeight.SemiBold)
                                Text(group.lastMessage.ifBlank { "ابدأ المحادثة الجماعية" }, color = Color(0xFF9BAFC3), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
    if (createDialog) CreateGroupDialog(currentUser, onDismiss = { createDialog = false }, onCreated = { group ->
        createDialog = false
        selected = group
    })
}

@Composable
private fun CreateGroupDialog(currentUser: FirebaseUser, onDismiss: () -> Unit, onCreated: (WaslaGroup) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emails by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("إنشاء مجموعة") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("اسم المجموعة") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = emails, onValueChange = { emails = it; error = null }, label = { Text("إيميلات الأعضاء") }, placeholder = { Text("افصل بين الإيميلات بفاصلة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), minLines = 2)
                Text("أدخل بريد كل عضو مسجل على WASLA، وافصل بينهم بفاصلة.", color = Color(0xFF9BAFC3), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !loading, onClick = {
                val groupName = name.trim()
                val list = emails.split(",", ";", "\n").map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
                if (groupName.length < 2) { error = "اكتب اسمًا للمجموعة."; return@TextButton }
                if (list.isEmpty()) { error = "أدخل بريد عضو واحد على الأقل."; return@TextButton }
                loading = true
                val found = mutableListOf<WaslaProfile>()
                fun next(index: Int) {
                    if (index >= list.size) {
                        createWaslaGroup(currentUser, groupName, found) { group, createError ->
                            loading = false
                            if (group == null) error = createError else onCreated(group)
                        }
                        return
                    }
                    lookupWaslaUser(list[index]) { profile, lookupError ->
                        if (profile == null) { loading = false; error = lookupError }
                        else if (profile.uid == currentUser.uid) next(index + 1)
                        else { found.add(profile); next(index + 1) }
                    }
                }
                next(0)
            }) { if (loading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("إلغاء") } }
    )
}

@Composable
private fun GroupThread(currentUser: FirebaseUser, group: WaslaGroup, onBack: () -> Unit) {
    var lines by remember(group.id) { mutableStateOf<List<GroupLine>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    DisposableEffect(group.id) {
        val registration = listenGroupMessages(group.id, { lines = it; error = null }, { error = it })
        onDispose { registration.remove() }
    }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color(0xFFEAF8FF)) }
            Column {
                Text(group.name, color = Color(0xFFEAF8FF), fontWeight = FontWeight.Bold)
                Text("محادثة جماعية", color = Color(0xFF8DEBFA), fontSize = 11.sp)
            }
        }
        if (error != null) Text(error!!, color = Color(0xFFFFA0A0), fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(lines, key = { it.id }) { line ->
                val mine = line.senderId == currentUser.uid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                    Text(line.text, color = Color(0xFFEAF8FF), modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(17.dp)).background(if (mine) Color(0xFF14516A) else Color(0xFF26394E)).padding(12.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 5000) draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("اكتب للمجموعة...") }, maxLines = 4)
            Spacer(Modifier.width(7.dp))
            FilledIconButton(enabled = draft.isNotBlank() && !sending, onClick = {
                sending = true
                sendGroupLine(group.id, currentUser, draft) { ok ->
                    sending = false
                    if (ok) draft = "" else error = "فشل إرسال الرسالة."
                }
            }) { if (sending) CircularProgressIndicator(Modifier.size(18.dp)) else Icon(Icons.Default.Send, null) }
        }
    }
}
