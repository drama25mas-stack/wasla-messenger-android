package app.wasla.messenger

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import android.graphics.Bitmap

private val SettingsInk = Color(0xFFEAF8FF)
private val SettingsSoft = Color(0xFF9BAFC3)
private val SettingsAqua = Color(0xFF8DEBFA)
private val SettingsCard = Color(0x241EADC8)

@Composable
fun SettingsHome(currentUser: FirebaseUser, onSignOut: () -> Unit, startAtProfile: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wasla_settings", Context.MODE_PRIVATE) }
    var page by remember(startAtProfile) { mutableStateOf(if (startAtProfile) "profile" else "home") }
    var notice by remember { mutableStateOf<String?>(null) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var readReceipts by remember { mutableStateOf(prefs.getBoolean("read_receipts", true)) }
    var showPhone by remember { mutableStateOf(prefs.getBoolean("show_phone", false)) }
    var language by remember { mutableStateOf(prefs.getString("language", "العربية") ?: "العربية") }
    var appearance by remember { mutableStateOf(prefs.getString("appearance", "داكن") ?: "داكن") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != "home") {
                IconButton(onClick = { page = "home" }) { Icon(Icons.Default.ArrowBack, null, tint = SettingsInk) }
            }
            Column {
                Text(when(page) {
                    "profile" -> "الملف الشخصي"
                    "notifications" -> "الإشعارات"
                    "privacy" -> "الخصوصية والأمان"
                    "language" -> "اللغة والمظهر"
                    "storage" -> "التخزين والبيانات"
                    "about" -> "حول WASLA"
                    else -> "الإعدادات"
                }, color = SettingsInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("تحكم في تجربتك على WASLA", color = SettingsSoft, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        when(page) {
            "home" -> {
                SettingsRow(Icons.Default.Person, "الملف الشخصي", "بياناتك وصورتك ورمزك الشخصي") { page = "profile" }
                SettingsRow(Icons.Default.Notifications, "الإشعارات", "تفضيلات التنبيهات") { page = "notifications" }
                SettingsRow(Icons.Default.Security, "الخصوصية والأمان", "إعدادات الخصوصية والحماية") { page = "privacy" }
                SettingsRow(Icons.Default.Palette, "اللغة والمظهر", "لغة التطبيق وشكله") { page = "language" }
                SettingsRow(Icons.Default.Storage, "التخزين والبيانات", "مساحة التطبيق والبيانات") { page = "storage" }
                SettingsRow(Icons.Default.Info, "حول WASLA", "معلومات التطبيق") { page = "about" }
            }
            "profile" -> ProfileSettings(currentUser, onSignOut)
            "notifications" -> {
                SettingsSwitch("تفعيل تفضيل الإشعارات", "يتحكم في تفضيلك المحلي فقط؛ وصول إشعارات الخلفية يحتاج خدمة إرسال مفعّلة.", notifications) {
                    notifications = it; prefs.edit().putBoolean("notifications", it).apply()
                    notice = "تم حفظ تفضيل الإشعارات."
                }
                SettingsRow(Icons.Default.NotificationsActive, "إعدادات إشعارات أندرويد", "افتح إعدادات إشعارات WASLA في الهاتف") {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                }
            }
            "privacy" -> {
                SettingsSwitch("إظهار رقم الهاتف في ملفك", "تحكم في عرضه داخل واجهة التطبيق؛ لا يعني نشره في دليل عام.", showPhone) {
                    showPhone = it; prefs.edit().putBoolean("show_phone", it).apply()
                }
                SettingsSwitch("إيصالات القراءة", "حفظ تفضيل إيصالات القراءة محليًا. لم يتم تفعيل مزامنتها بعد.", readReceipts) {
                    readReceipts = it; prefs.edit().putBoolean("read_receipts", it).apply()
                }
                SettingsRow(Icons.Default.Lock, "حماية الحساب", "تأكيد البريد الإلكتروني وإعادة تعيين كلمة المرور من شاشة الدخول") {
                    notice = "حسابك يستخدم Firebase Authentication. لا يوجد حتى الآن تشفير طرفي للرسائل."
                }
            }
            "language" -> {
                Text("لغة التطبيق", color = SettingsInk, fontWeight = FontWeight.SemiBold)
                listOf("العربية", "English").forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { language = item; prefs.edit().putString("language", item).apply() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item, color = SettingsInk, modifier = Modifier.weight(1f))
                        if(language == item) Icon(Icons.Default.CheckCircle, null, tint = SettingsAqua)
                    }
                }
                Divider(color = Color(0x335F819A))
                Spacer(Modifier.height(10.dp))
                Text("المظهر", color = SettingsInk, fontWeight = FontWeight.SemiBold)
                listOf("داكن", "فاتح", "حسب النظام").forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { appearance = item; prefs.edit().putString("appearance", item).apply(); notice = "تم حفظ اختيار المظهر. تطبيقه على كل الشاشات سيتم استكماله في التحديث التالي." }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item, color = SettingsInk, modifier = Modifier.weight(1f))
                        if(appearance == item) Icon(Icons.Default.CheckCircle, null, tint = SettingsAqua)
                    }
                }
            }
            "storage" -> {
                SettingsRow(Icons.Default.CleaningServices, "مسح ذاكرة التخزين المؤقت", "حذف الملفات المؤقتة الخاصة بالتطبيق") {
                    context.cacheDir.deleteRecursively()
                    notice = "تم مسح ذاكرة التخزين المؤقت. لم يتم حذف رسائلك أو حسابك."
                }
                SettingsRow(Icons.Default.DataUsage, "استخدام البيانات", "إدارة بيانات الرسائل والمزامنة") {
                    notice = "يتم تحميل المحادثات النصية من Firestore. مرفقات الوسائط غير مفعّلة حاليًا."
                }
            }
            "about" -> {
                Text("WASLA — تواصل بلا حدود", color = SettingsInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("الإصدار 0.1.0", color = SettingsSoft, modifier = Modifier.padding(top = 8.dp))
                Text("تطبيق مراسلة يدعم المحادثات عبر الإنترنت والاتصال المباشر بالأجهزة القريبة.", color = SettingsSoft, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
                Text("ملاحظة: التشفير الطرفي، الوسائط، ومزامنة جهات الاتصال السحابية ما زالت قيد التطوير.", color = SettingsAqua, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (notice != null) {
            Spacer(Modifier.height(12.dp))
            Text(notice!!, color = SettingsAqua, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SettingsCard).padding(12.dp))
            TextButton(onClick = { notice = null }) { Text("حسنًا") }
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(17.dp)).background(SettingsCard).border(1.dp, Color(0x335F819A), RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SettingsAqua, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SettingsInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = SettingsSoft, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Icon(Icons.Default.ChevronLeft, null, tint = SettingsSoft)
    }
}

@Composable
private fun SettingsSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).background(SettingsCard).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SettingsInk, fontWeight = FontWeight.SemiBold)
            Text(description, color = SettingsSoft, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProfileSettings(user: FirebaseUser, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wasla_settings", Context.MODE_PRIVATE) }
    var name by remember(user.uid) { mutableStateOf(user.displayName.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var avatarUri by remember { mutableStateOf(prefs.getString("avatar_${user.uid}", null)) }
    var showQr by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            avatarUri = it.toString()
            prefs.edit().putString("avatar_${user.uid}", it.toString()).apply()
            message = "تم حفظ الصورة على هذا الجهاز. مزامنة الصورة بين أجهزتك تحتاج رفعًا سحابيًا."
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(Color(0x334CCFE5)).clickable { picker.launch("image/*") }, contentAlignment = Alignment.Center) {
            if (avatarUri != null) {
                val bitmap = remember(avatarUri) { try { android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(Uri.parse(avatarUri)))?.asImageBitmap() } catch (_: Exception) { null } }
                if(bitmap != null) Image(bitmap, null, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Person, null, tint = SettingsAqua, modifier = Modifier.size(45.dp))
            } else Icon(Icons.Default.AddAPhoto, null, tint = SettingsAqua, modifier = Modifier.size(42.dp))
        }
        TextButton(onClick = { picker.launch("image/*") }) { Text("تغيير الصورة") }
        Text(user.email.orEmpty(), color = SettingsSoft, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الظاهر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Button(onClick = {
            if(name.trim().length < 2) { message = "الاسم لازم يكون حرفين على الأقل."; return@Button }
            saving = true
            val request = UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
            user.updateProfile(request).addOnCompleteListener { task ->
                if(task.isSuccessful) saveWaslaProfile(user, name.trim()) { ok ->
                    saving = false
                    message = if(ok) "تم تحديث اسم الملف الشخصي." else "تم تحديث الاسم، لكن تعذر مزامنته مع قاعدة البيانات."
                } else { saving = false; message = "تعذر تحديث الاسم. حاول مرة أخرى." }
            }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            if(saving) CircularProgressIndicator(Modifier.size(18.dp)) else Text("حفظ التعديلات")
        }
        OutlinedButton(onClick = { showQr = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text("رمز QR الخاص بي") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSignOut, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B343B)), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("تسجيل الخروج") }
        if(message != null) Text(message!!, color = SettingsAqua, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
    }
    if(showQr) {
        val qrBitmap = remember(user.uid) {
            try {
                val matrix = MultiFormatWriter().encode("WASLA:${user.uid}", BarcodeFormat.QR_CODE, 640, 640)
                Bitmap.createBitmap(640, 640, Bitmap.Config.RGB_565).also { bmp ->
                    for(x in 0 until 640) for(y in 0 until 640) bmp.setPixel(x,y,if(matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }.asImageBitmap()
            } catch (_: Exception) { null }
        }
        AlertDialog(onDismissRequest = { showQr = false }, title = { Text("رمز WASLA الشخصي") }, text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if(qrBitmap != null) Image(qrBitmap, null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Text("امسح الرمز لمشاركة معرّف حسابك: ${user.uid.take(10)}…", textAlign = TextAlign.Center, color = SettingsSoft, fontSize = 11.sp)
            }
        }, confirmButton = { TextButton(onClick = { showQr = false }) { Text("إغلاق") } })
    }
}

@Composable
fun WaslaQrDialog(user: FirebaseUser, onDismiss: () -> Unit) {
    val qrBitmap = remember(user.uid) {
        try {
            val matrix = MultiFormatWriter().encode("WASLA:${user.uid}", BarcodeFormat.QR_CODE, 640, 640)
            Bitmap.createBitmap(640, 640, Bitmap.Config.RGB_565).also { bmp ->
                for (x in 0 until 640) for (y in 0 until 640) {
                    bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }.asImageBitmap()
        } catch (_: Exception) { null }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("رمز WASLA الشخصي") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (qrBitmap != null) Image(qrBitmap, null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Text("امسح الرمز لمشاركة معرّف حسابك.", textAlign = TextAlign.Center, color = SettingsSoft, fontSize = 11.sp)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}
