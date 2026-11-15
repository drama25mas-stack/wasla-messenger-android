package app.wasla.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF08111F)
private val Panel = Color(0xFF142238)
private val Cyan = Color(0xFF69D8E8)
private val Muted = Color(0xFF9AABC1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WaslaApp() }
    }
}

private data class ChatItem(val name: String, val message: String, val time: String, val unread: Int, val color: Color, val online: Boolean)

@Composable
private fun WaslaApp() {
    var selected by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    val chats = listOf(
        ChatItem("أحمد محمد", "تمام، هبعتلك أول ما أوصل", "١٢:٤٥", 2, Color(0xFF547DBB), true),
        ChatItem("سارة", "الصورة وصلت ❤️", "١١:٣٠", 0, Color(0xFF9A6AB8), true),
        ChatItem("فريق العمل", "محمود: تم استلام الملفات", "أمس", 5, Color(0xFF398C83), false),
        ChatItem("عمر خالد", "رسالة صوتية", "أمس", 0, Color(0xFFB47B52), false)
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = Panel, primary = Cyan, onBackground = Color.White, onSurface = Color.White)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10243A), Night, Color(0xFF090F1B))))) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Header()
                    when (selected) {
                        0 -> ChatsScreen(chats, search, { search = it })
                        1 -> ContactsScreen()
                        2 -> NearbyScreen()
                        3 -> GroupsScreen()
                        else -> SettingsScreen()
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationBar(containerColor = Color(0xE6101D30), contentColor = Color.White, tonalElevation = 0.dp) {
                        val tabs = listOf(
                            Triple("الدردشات", Icons.Default.Chat, 0),
                            Triple("جهات الاتصال", Icons.Default.Contacts, 1),
                            Triple("القريبون", Icons.Default.WifiTethering, 2),
                            Triple("المجموعات", Icons.Default.Groups, 3),
                            Triple("الإعدادات", Icons.Default.Settings, 4)
                        )
                        tabs.forEach { (label, icon, index) ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { selected = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0x3348C5D8), unselectedIconColor = Muted, unselectedTextColor = Muted)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("WASLA", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text("تواصل بلا حدود", color = Cyan, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIcon(Icons.Default.QrCodeScanner, "رمز QR")
            GlassIcon(Icons.Default.Edit, "رسالة جديدة")
        }
    }
}

@Composable
private fun GlassIcon(icon: ImageVector, description: String) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color(0x553A5874)), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Color.White)
    }
}

@Composable
private fun ChatsScreen(chats: List<ChatItem>, search: String, onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("رسائلك", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("كل محادثاتك في مكان واحد", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث عن محادثة...", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
            shape = RoundedCornerShape(18.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0x445E7896), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Cyan)
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("الكل") })
            FilterChip(selected = false, onClick = {}, label = { Text("غير مقروءة") })
            FilterChip(selected = false, onClick = {}, label = { Text("المجموعات") })
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(chats.filter { it.name.contains(search, true) || it.message.contains(search, true) }) { chat -> ChatRow(chat) }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatItem) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xAA1A3049), Color(0x7730445D)))).clickable { }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(Modifier.size(52.dp).clip(CircleShape).background(chat.color), contentAlignment = Alignment.Center) {
                Text(chat.name.take(1), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            if (chat.online) Box(Modifier.size(12.dp).align(Alignment.BottomStart).clip(CircleShape).background(Color(0xFF4ADE80)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(chat.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(chat.message, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(chat.time, color = Muted, fontSize = 10.sp)
            if (chat.unread > 0) Box(Modifier.size(20.dp).clip(CircleShape).background(Cyan), contentAlignment = Alignment.Center) {
                Text(chat.unread.toString(), color = Night, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ContactsScreen() {
    EmptyFeature(Icons.Default.Contacts, "جهات الاتصال", "ستظهر هنا جهات اتصال هاتفك التي لديها حساب WASLA، مع إمكانية دعوة الآخرين بعد منح إذن الوصول.")
}
@Composable
private fun NearbyScreen() {
    EmptyFeature(Icons.Default.WifiTethering, "الشبكة القريبة", "اكتشاف الأجهزة والاتصال المباشر بدون إنترنت سيُفعّل في مرحلة الاتصال اللاسلكي.")
}
@Composable
private fun GroupsScreen() {
    EmptyFeature(Icons.Default.Groups, "المجموعات والقنوات", "مساحة محادثاتك الجماعية وقنواتك.")
}
@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("الإعدادات", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        listOf(Icons.Default.Person to "الملف الشخصي", Icons.Default.Notifications to "الإشعارات", Icons.Default.Lock to "الخصوصية والأمان", Icons.Default.Language to "اللغة والمظهر", Icons.Default.Storage to "التخزين والبيانات", Icons.Default.Info to "حول WASLA").forEach { (icon, label) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Cyan)
                Spacer(Modifier.width(14.dp))
                Text(label, color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronLeft, null, tint = Muted)
            }
        }
    }
}
@Composable
private fun EmptyFeature(icon: ImageVector, title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(55.dp))
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Color(0x443C7890)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}
