package app.wasla.messenger

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF070D18)
private val DeepBlue = Color(0xFF0B1728)
private val Ice = Color(0xFFEAF8FF)
private val Aqua = Color(0xFF8DEBFA)
private val Soft = Color(0xFF9BAFC3)
private val Glass = Color(0x24D9F4FF)
private val GlassLine = Color(0x35E4F8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WaslaRoot() }
    }
}


@Composable
private fun WaslaRoot() {
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser?.takeIf { it.isEmailVerified }) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            currentUser = user?.takeIf { it.isEmailVerified }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    if (currentUser == null) {
        AuthScreen(onAuthenticated = { currentUser = it })
    } else {
        WaslaApp(currentUser = currentUser!!, onSignOut = { auth.signOut() })
    }
}

@Composable
private fun AuthScreen(onAuthenticated: (FirebaseUser) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun showError(text: String) {
        isError = true
        message = text
        loading = false
    }
    fun validInput(): Boolean {
        if (isRegister && name.trim().length < 2) {
            showError("اكتب اسمك بشكل صحيح.")
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            showError("اكتب بريد إلكتروني صحيح.")
            return false
        }
        if (password.length < 8) {
            showError("كلمة المرور لازم تكون 8 أحرف على الأقل.")
            return false
        }
        if (isRegister && password != confirmPassword) {
            showError("كلمتا المرور غير متطابقتين.")
            return false
        }
        return true
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize().background(AppBackground)) {
            AmbientGlow(Modifier.align(Alignment.TopEnd).offset(x = 85.dp, y = 70.dp), Color(0xFF1B9DB8))
            AmbientGlow(Modifier.align(Alignment.BottomStart).offset(x = (-100).dp, y = (-90).dp), Color(0xFF385A9A))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("WASLA", color = Ice, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("تواصل بلا حدود", color = Aqua, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
                Spacer(Modifier.height(28.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Color(0xCC102238)).border(1.dp, GlassLine, RoundedCornerShape(28.dp)).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (isRegister) "إنشاء حساب جديد" else "أهلاً بعودتك", color = Ice, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isRegister) "ابدأ رحلتك على WASLA" else "سجّل الدخول لمتابعة محادثاتك",
                        color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp, bottom = 20.dp)
                    )
                    if (isRegister) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it; message = null },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("الاسم") }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = email, onValueChange = { email = it; message = null },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("البريد الإلكتروني") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; message = null },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("كلمة المرور") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (isRegister) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirmPassword, onValueChange = { confirmPassword = it; message = null },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("تأكيد كلمة المرور") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                    if (message != null) {
                        Text(
                            message!!, color = if (isError) Color(0xFFFF9A9A) else Aqua,
                            fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            if (!validInput()) return@Button
                            loading = true
                            message = null
                            if (isRegister) {
                                auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                    if (!task.isSuccessful) {
                                        showError("تعذر إنشاء الحساب. تأكد من البريد وكلمة المرور أو أن البريد غير مستخدم.")
                                    } else {
                                        val user = auth.currentUser
                                        val profile = UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
                                        if (user == null) {
                                            showError("تم إنشاء الحساب لكن تعذر تحميل بياناته.")
                                        } else {
                                            user.updateProfile(profile).addOnCompleteListener {
                                                saveWaslaProfile(user, name.trim()) { profileSaved ->
                                                    user.sendEmailVerification().addOnCompleteListener { verification ->
                                                        auth.signOut()
                                                        loading = false
                                                        isError = !verification.isSuccessful || !profileSaved
                                                        message = when {
                                                            !profileSaved -> "تم إنشاء الحساب، لكن قاعدة بيانات المحادثات غير جاهزة. يلزم تفعيل Cloud Firestore ونشر firestore.rules."
                                                            verification.isSuccessful -> "تم إنشاء الحساب. افتح بريدك واضغط رابط التفعيل، ثم سجّل الدخول."
                                                            else -> "تم إنشاء الحساب، لكن تعذر إرسال رسالة التفعيل. جرّب تسجيل الدخول لإعادة الإرسال."
                                                        }
                                                        isRegister = false
                                                        password = ""
                                                        confirmPassword = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                                    if (!task.isSuccessful) {
                                        showError("بيانات الدخول غير صحيحة أو تعذر الاتصال. حاول مرة أخرى.")
                                    } else {
                                        val user = auth.currentUser
                                        if (user?.isEmailVerified == true) {
                                            loading = false
                                            onAuthenticated(user)
                                        } else if (user != null) {
                                            user.sendEmailVerification().addOnCompleteListener {
                                                auth.signOut()
                                                loading = false
                                                isError = false
                                                message = "لازم تأكد بريدك الإلكتروني أولاً. أرسلنا لك رابط التفعيل."
                                            }
                                        } else {
                                            showError("تعذر إكمال تسجيل الدخول.")
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text(if (isRegister) "إنشاء الحساب" else "تسجيل الدخول", fontWeight = FontWeight.Bold)
                    }
                    if (!isRegister) {
                        TextButton(onClick = {
                            if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                                showError("اكتب بريدك الإلكتروني أولاً لإرسال رابط استعادة كلمة المرور.")
                            } else {
                                loading = true
                                auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { task ->
                                    loading = false
                                    isError = !task.isSuccessful
                                    message = if (task.isSuccessful) {
                                        "لو البريد مسجل، هيوصلك رابط استعادة كلمة المرور."
                                    } else {
                                        "تعذر إرسال رابط الاستعادة. راجع البريد وحاول مرة أخرى."
                                    }
                                }
                            }
                        }, enabled = !loading) { Text("نسيت كلمة المرور؟", color = Aqua) }
                    }
                    TextButton(onClick = {
                        isRegister = !isRegister
                        message = null
                        isError = false
                    }, enabled = !loading) {
                        Text(if (isRegister) "عندك حساب؟ سجّل الدخول" else "مستخدم جديد؟ أنشئ حساب", color = Ice)
                    }
                }
                Text(
                    "حسابك محمي، ولن يتم تسجيل الدخول قبل تأكيد البريد الإلكتروني.",
                    color = Soft, fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val AppTabs = listOf(
    Tab("الدردشات", Icons.Default.ChatBubble),
    Tab("جهات الاتصال", Icons.Default.PeopleAlt),
    Tab("القريبون", Icons.Default.WifiTethering),
    Tab("المجموعات", Icons.Default.Groups),
    Tab("الإعدادات", Icons.Default.Settings)
)

private val AppBackground = Brush.verticalGradient(
    listOf(Color(0xFF142A43), Ink, Color(0xFF080E19))
)

@Composable
private fun WaslaApp(currentUser: FirebaseUser, onSignOut: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Aqua, background = Ink, surface = DeepBlue)) {
            Box(Modifier.fillMaxSize().background(AppBackground)) {
                AmbientGlow(Modifier.align(Alignment.TopEnd).offset(x = 85.dp, y = 80.dp), Color(0xFF1B9DB8))
                AmbientGlow(Modifier.align(Alignment.BottomStart).offset(x = (-100).dp, y = (-90).dp), Color(0xFF385A9A))
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    WaslaHeader()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (selected) {
                            0 -> MessagingHome(currentUser)
                            1 -> FeatureEmpty(Icons.Default.PeopleAlt, "جهات الاتصال", "عند تنفيذ ربط جهات الاتصال ستظهر هنا الأسماء التي لديها حساب WASLA، مع تحكم كامل في إذن الوصول.")
                            2 -> FeatureEmpty(Icons.Default.WifiTethering, "اتصال قريب", "سيظهر هنا اكتشاف الأجهزة القريبة والاتصال المباشر. لن نعرض أجهزة وهمية أو ندّعي وجود اتصال غير فعّال.")
                            3 -> FeatureEmpty(Icons.Default.Groups, "المجموعات والقنوات", "ستتمكن هنا من إنشاء مجموعات وقنوات وإدارة أعضائها بعد توصيل نظام الحسابات والرسائل.")
                            else -> SettingsHome(onSignOut)
                        }
                    }
                    GlassNavigation(AppTabs, selected) { selected = it }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun AmbientGlow(modifier: Modifier, color: Color) {
    val glow = remember(color) { Brush.radialGradient(listOf(color.copy(alpha = .20f), color.copy(alpha = .06f), Color.Transparent)) }
    Box(modifier.size(210.dp).clip(CircleShape).background(glow))
}

@Composable
private fun WaslaHeader() {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("WASLA", color = Ice, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
            Text("تواصل بلا حدود", color = Aqua.copy(alpha = .9f), fontSize = 12.sp, letterSpacing = .5.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassAction(Icons.Default.QrCodeScanner, "رمز QR")
            GlassAction(Icons.Default.Edit, "رسالة جديدة")
        }
    }
}

@Composable
private fun GlassAction(icon: ImageVector, label: String) {
    Box(
        Modifier.size(44.dp).shadow(12.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0x44FFFFFF), Color(0x1117A9C7))))
            .border(1.dp, GlassLine, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Ice, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ChatsHome() {
    var search by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("مساحة تواصلك", color = Ice, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("كل محادثاتك واتصالاتك في مكان واحد", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0x25D8F3FF)).border(1.dp, GlassLine, RoundedCornerShape(18.dp)).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Soft, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = search, onValueChange = { search = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ice, fontSize = 14.sp),
                cursorBrush = Brush.verticalGradient(listOf(Aqua, Aqua)),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (search.isEmpty()) Text("ابحث عن محادثة أو شخص...", color = Soft, fontSize = 13.sp)
                        inner()
                    }
                }
            )
            if (search.isNotEmpty()) {
                Icon(Icons.Default.Close, "مسح البحث", tint = Soft, modifier = Modifier.size(18.dp).clickable { search = "" })
            }
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("رسائلك", color = Ice, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("0 محادثات", color = Soft, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                Box(
                    Modifier.size(94.dp).clip(RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(Color(0x445BE0F4), Color(0x222C6592))))
                        .border(1.dp, GlassLine, RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Forum, null, tint = Aqua, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(if (search.isBlank()) "لسه مفيش محادثات" else "مش لاقيين نتائج", color = Ice, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (search.isBlank()) "ابدأ بإضافة جهات اتصالك أو الاتصال بشخص قريب منك. ستظهر محادثاتك الحقيقية هنا."
                    else "لا توجد محادثات مطابقة لبحثك حالياً.",
                    color = Soft, fontSize = 13.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GlassNavigation(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).shadow(22.dp, RoundedCornerShape(27.dp))
            .clip(RoundedCornerShape(27.dp))
            .background(Brush.verticalGradient(listOf(Color(0xD82C4962), Color(0xE6101C2C))))
            .border(1.dp, Color(0x55E7FAFF), RoundedCornerShape(27.dp))
            .padding(horizontal = 5.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = selected == index
            val tint by animateColorAsState(if (active) Aqua else Color(0xFF9BAFC3), tween(220), label = "tabTint")
            val labelAlpha by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, tween(160), label = "tabLabel")
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).clickable { onSelect(index) }
                    .padding(vertical = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.height(37.dp).fillMaxWidth(.78f).clip(RoundedCornerShape(15.dp))
                        .background(if (active) Brush.verticalGradient(listOf(Color(0x5548CDE4), Color(0x332B91B8))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                        .border(if (active) 1.dp else 0.dp, if (active) Color(0x668BEFFF) else Color.Transparent, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(tab.icon, tab.title, tint = tint, modifier = Modifier.size(21.dp)) }
                Text(tab.title, color = Aqua.copy(alpha = labelAlpha), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
private fun FeatureEmpty(icon: ImageVector, title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(100.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0x445BE0F4), Color(0x222C6592))))
                .border(1.dp, GlassLine, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Aqua, modifier = Modifier.size(44.dp)) }
        Spacer(Modifier.height(22.dp))
        Text(title, color = Ice, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(description, color = Soft, fontSize = 14.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingsHome(onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("الإعدادات", color = Ice, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("تحكم في تجربتك على WASLA", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp, bottom = 22.dp))
        val rows = listOf(
            Icons.Default.Person to ("الملف الشخصي" to "إعداد بياناتك وصورتك"),
            Icons.Default.Notifications to ("الإشعارات" to "إعداد تنبيهات الرسائل"),
            Icons.Default.Lock to ("الخصوصية والأمان" to "إعدادات الحماية والتشفير"),
            Icons.Default.Language to ("اللغة والمظهر" to "اللغة والوضع الداكن"),
            Icons.Default.Storage to ("التخزين والبيانات" to "إدارة الملفات والمساحة"),
            Icons.Default.Info to ("حول WASLA" to "معلومات وإصدار التطبيق")
        )
        rows.forEach { (icon, pair) ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp))
                    .background(Glass).border(1.dp, GlassLine, RoundedCornerShape(18.dp)).padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Aqua, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(pair.first, color = Ice, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(pair.second, color = Soft, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronLeft, null, tint = Soft)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33FF7777))
                .border(1.dp, Color(0x55FF9999), RoundedCornerShape(18.dp)).clickable { onSignOut() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Logout, null, tint = Color(0xFFFFA0A0))
            Spacer(Modifier.width(12.dp))
            Text("تسجيل الخروج", color = Color(0xFFFFB0B0), fontWeight = FontWeight.SemiBold)
        }
    }
}
