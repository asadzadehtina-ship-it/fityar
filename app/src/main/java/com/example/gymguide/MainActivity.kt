package com.example.gymguide

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.delay

// ---------- ذخیره‌سازی ----------
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gym", Context.MODE_PRIVATE)
    var favorites: Set<String>
        get() = HashSet(sp.getStringSet("fav", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("fav", v).apply() }
    var darkMode: Int // 0 سیستم، 1 روشن، 2 تاریک
        get() = sp.getInt("dark", 0)
        set(v) { sp.edit().putInt("dark", v).apply() }
    var height: String
        get() = sp.getString("h", "") ?: ""
        set(v) { sp.edit().putString("h", v).apply() }
    var weight: String
        get() = sp.getString("w", "") ?: ""
        set(v) { sp.edit().putString("w", v).apply() }
    var target: String
        get() = sp.getString("t", "") ?: ""
        set(v) { sp.edit().putString("t", v).apply() }
    var days: Int
        get() = sp.getInt("days", 4)
        set(v) { sp.edit().putInt("days", v).apply() }
}

class MainActivity : ComponentActivity() {
    private val phase = mutableIntStateOf(0)       // 0 = صفحه شروع، 1 = اپ
    private val adsOk = mutableStateOf(false)
    private var appOpenAd: AppOpenAd? = null
    private var adsStarted = false
    private var splashStart = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        setContent { GymApp(prefs, phase.intValue, adsOk.value) }
        splashStart = SystemClock.elapsedRealtime()
        // اگر تبلیغ تا ۷ ثانیه آماده نشد، وارد اپ شو
        handler.postDelayed({ if (phase.intValue == 0) phase.intValue = 1 }, 7000)
        requestConsentThenAds()
    }

    // رضایت‌نامه کاربران اروپا (UMP) و بعد بارگذاری تبلیغ
    private fun requestConsentThenAds() {
        val info = UserMessagingPlatform.getConsentInformation(this)
        info.requestConsentInfoUpdate(
            this, ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { startAds(info) } },
            { startAds(info) }
        )
    }

    private fun startAds(info: ConsentInformation) {
        if (adsStarted) return
        if (!info.canRequestAds()) { phase.intValue = 1; return }
        adsStarted = true
        adsOk.value = true
        MobileAds.initialize(this) {}
        AppOpenAd.load(
            this, BuildConfig.APP_OPEN_ID, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) { appOpenAd = ad; showWhenReady() }
                override fun onAdFailedToLoad(error: LoadAdError) { phase.intValue = 1 }
            }
        )
    }

    // صفحه شروع حداقل ۱٫۵ ثانیه دیده شود، بعد تبلیغ نمایش داده شود
    private fun showWhenReady() {
        val wait = 1500 - (SystemClock.elapsedRealtime() - splashStart)
        handler.postDelayed({ showAd() }, wait.coerceAtLeast(0))
    }

    private fun showAd() {
        if (phase.intValue != 0) return
        val ad = appOpenAd
        if (ad == null) { phase.intValue = 1; return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { appOpenAd = null; phase.intValue = 1 }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { appOpenAd = null; phase.intValue = 1 }
        }
        ad.show(this) // دکمه بستن (X) را خود تبلیغ نشان می‌دهد
    }
}

// ---------- تم ----------
private val LightColors = lightColorScheme(
    primary = Color(0xFF7C3AED), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF), onPrimaryContainer = Color(0xFF3B1A8C),
    background = Color(0xFFF8F7FB), surface = Color.White,
    onSurface = Color(0xFF1B1A20), onSurfaceVariant = Color(0xFF6B6877),
    surfaceVariant = Color(0xFFECE9F4), error = Color(0xFFB3261E)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB69CFF), onPrimary = Color(0xFF2A0F6B),
    primaryContainer = Color(0xFF2E2150), onPrimaryContainer = Color(0xFFE5DAFF),
    background = Color(0xFF111015), surface = Color(0xFF1B1A21),
    onSurface = Color(0xFFEDEBF2), onSurfaceVariant = Color(0xFFA09DAE),
    surfaceVariant = Color(0xFF282633), error = Color(0xFFF2B8B5)
)

@Composable
fun GymApp(prefs: Prefs, phase: Int, adsOk: Boolean) {
    var mode by remember { mutableIntStateOf(prefs.darkMode) }
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
            if (phase == 0) Splash() else Main(prefs, mode, adsOk) { mode = it; prefs.darkMode = it }
        }
    }
}

// ---------- ساختار اصلی ----------
@Composable
fun Main(prefs: Prefs, darkMode: Int, adsOk: Boolean, setDark: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Exercise?>(null) }
    var favs by remember { mutableStateOf(prefs.favorites) }
    var timer by remember { mutableStateOf(false) }
    val toggleFav = { id: String ->
        favs = if (id in favs) favs - id else favs + id
        prefs.favorites = favs
    }
    BackHandler(open != null) { open = null }

    val tabs = listOf("حرکات" to Icons.Filled.Home, "برنامه من" to Icons.Filled.Person,
        "علاقه‌مندی" to Icons.Filled.Favorite, "تنظیمات" to Icons.Filled.Settings)

    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            Column {
                if (adsOk) AdBanner()
                NavigationBar(containerColor = cs.surface) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i && open == null,
                            onClick = { tab = i; open = null },
                            icon = { Icon(icon, null) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { timer = true },
                containerColor = cs.primary, contentColor = cs.onPrimary
            ) { Text("⏱ تایمر استراحت", fontWeight = FontWeight.Medium) }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val current = open
            if (current != null) {
                DetailScreen(current, current.id in favs, { toggleFav(current.id) }, { open = null })
            } else when (tab) {
                0 -> ExercisesTab(favs) { open = it }
                1 -> PlanTab(prefs) { open = it }
                2 -> FavoritesTab(favs) { open = it }
                else -> SettingsTab(darkMode, setDark)
            }
        }
    }
    if (timer) RestTimerDialog { timer = false }
}

@Composable
fun AdBanner() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        })
    }
}

// ---------- اجزای مشترک ----------
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape)
            .background(if (on) cs.primary else cs.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text, fontSize = 14.sp, color = if (on) cs.onPrimary else cs.onSurface)
    }
}

@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (value, label) -> Chip(label, value == selected) { onSelect(value) } }
    }
}

@Composable
fun ExerciseCard(ex: Exercise, fav: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surface)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(ex.emoji, fontSize = 26.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(ex.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Spacer(Modifier.height(3.dp))
            Text("${ex.group} · ${ex.level}", fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        if (fav) Icon(Icons.Filled.Favorite, null, tint = cs.primary, modifier = Modifier.size(20.dp))
    }
}

// ---------- تب حرکات ----------
@Composable
fun ExercisesTab(favs: Set<String>, onOpen: (Exercise) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var goal by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("همه") }
    var query by remember { mutableStateOf("") }
    val list = exercises.filter {
        (goal.isEmpty() || it.tag == goal || it.tag == "both") &&
            (group == "همه" || it.group == group) &&
            (query.isBlank() || it.name.contains(query.trim()))
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("فیت‌یار", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Text("حرکت درست، نتیجه بهتر", fontSize = 14.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("جستجوی حرکت...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            ChipRow(listOf("" to "همه", "muscle" to "عضله‌سازی", "fat" to "کاهش وزن"), goal) { goal = it }
            Spacer(Modifier.height(8.dp))
            ChipRow(groups.map { it to it }, group) { group = it }
        }
        items(list, key = { it.id }) { ex -> ExerciseCard(ex, ex.id in favs) { onOpen(ex) } }
        if (list.isEmpty()) item {
            Text("حرکتی پیدا نشد.", color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

// ---------- صفحه جزئیات ----------
fun openVideo(ctx: Context, ex: Exercise) {
    val url = ex.video
        ?: ("https://www.youtube.com/results?search_query=" + Uri.encode("${ex.en} proper form tutorial"))
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { }
}

@Composable
fun DetailScreen(ex: Exercise, fav: Boolean, onFav: () -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = cs.onSurface) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFav) {
                Icon(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "علاقه‌مندی", tint = cs.primary
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(ex.emoji, fontSize = 68.sp) }
        Spacer(Modifier.height(18.dp))
        Text(ex.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(ex.group); Tag(ex.level) }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("پیشنهاد تمرین", color = cs.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(ex.sets, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { openVideo(ctx, ex) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("مشاهده ویدیوی آموزشی", fontSize = 15.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("مراحل اجرا", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(10.dp))
        ex.steps.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(cs.primary),
                    contentAlignment = Alignment.Center
                ) { Text("${i + 1}", color = cs.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text(s, fontSize = 15.sp, color = cs.onSurface, lineHeight = 24.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surface).padding(18.dp)) {
            Text("نکات مهم", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Spacer(Modifier.height(8.dp))
            ex.tips.forEach {
                Text("• $it", fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun Tag(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.clip(CircleShape).background(cs.surface).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, fontSize = 13.sp, color = cs.primary, fontWeight = FontWeight.Medium)
    }
}

// ---------- تب برنامه من ----------
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp), modifier = modifier
    )
}

@Composable
fun PlanTab(prefs: Prefs, onOpen: (Exercise) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var h by remember { mutableStateOf(prefs.height) }
    var w by remember { mutableStateOf(prefs.weight) }
    var t by remember { mutableStateOf(prefs.target) }
    var days by remember { mutableIntStateOf(prefs.days) }
    var error by remember { mutableStateOf<String?>(null) }

    fun calc(): Pair<Insight, List<DayPlan>>? {
        val hh = h.toDoubleOrNull() ?: return null
        val ww = w.toDoubleOrNull() ?: return null
        val tt = t.toDoubleOrNull() ?: return null
        if (hh !in 100.0..250.0 || ww !in 30.0..300.0 || tt !in 30.0..300.0) return null
        val insight = analyze(hh, ww, tt)
        return insight to buildPlan(insight.goal, days)
    }
    var result by remember { mutableStateOf(calc()) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("برنامه من", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Text("وزن هدفت چنده؟ بگو تا برنامه مناسبت را بسازم", fontSize = 14.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("قد (سانتی‌متر)", h, { h = it }, Modifier.weight(1f))
                Field("وزن فعلی (کیلو)", w, { w = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Field("وزن هدف (کیلو)", t, { t = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("چند روز در هفته می‌توانی تمرین کنی؟", fontSize = 14.sp, color = cs.onSurface)
            Spacer(Modifier.height(8.dp))
            ChipRow(listOf("3" to "۳ روز", "4" to "۴ روز", "5" to "۵ روز"), days.toString()) { days = it.toInt() }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val r = calc()
                    if (r == null) error = "لطفاً قد، وزن فعلی و وزن هدف را درست وارد کن."
                    else {
                        error = null; result = r
                        prefs.height = h; prefs.weight = w; prefs.target = t; prefs.days = days
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("ساخت برنامه", fontSize = 15.sp) }
            error?.let { Text(it, color = cs.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        result?.let { (ins, plan) ->
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.primaryContainer).padding(18.dp)) {
                    Text("هدف تو: ${ins.goal.label}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    val info = listOf(
                        "BMI فعلی: ${"%.1f".format(ins.bmi)}",
                        "محدوده وزن سالم برای قد تو: ${"%.0f".format(ins.low)} تا ${"%.0f".format(ins.high)} کیلوگرم",
                        if (ins.diff < 0.5) "به وزن هدف خیلی نزدیکی؛ تمرین برای حفظ و تناسب اندام."
                        else "تا هدف: ${"%.1f".format(ins.diff)} کیلوگرم، حدود ${ins.weeksMin} تا ${ins.weeksMax} هفته با سرعت ایمن.",
                        ins.kcal
                    )
                    info.forEach {
                        Text(it, fontSize = 14.sp, color = cs.onPrimaryContainer, lineHeight = 22.sp,
                            modifier = Modifier.padding(vertical = 2.dp))
                    }
                    ins.warn?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠️ $it", fontSize = 13.sp, color = cs.error, lineHeight = 21.sp)
                    }
                }
            }
            items(plan) { day ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surface).padding(16.dp)) {
                    Text(day.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
                    Spacer(Modifier.height(6.dp))
                    day.items.forEach { ex ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(ex) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ex.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, fontSize = 15.sp, color = cs.onSurface)
                                Text(ex.sets, fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "این برنامه عمومی است و جایگزین نظر پزشک یا مربی نیست. قبل از شروع، اگر بیماری یا آسیب‌دیدگی داری با پزشک مشورت کن. بین روزهای تمرین استراحت کافی و خواب منظم داشته باش.",
                    fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 20.sp
                )
            }
        }
    }
}

// ---------- تب علاقه‌مندی‌ها ----------
@Composable
fun FavoritesTab(favs: Set<String>, onOpen: (Exercise) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val list = exercises.filter { it.id in favs }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("علاقه‌مندی‌ها", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onSurface) }
        if (list.isEmpty()) item {
            Text("هنوز حرکتی ذخیره نکرده‌ای. در صفحه هر حرکت روی قلب بزن.",
                color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp), textAlign = TextAlign.Center)
        }
        items(list, key = { it.id }) { ex -> ExerciseCard(ex, true) { onOpen(ex) } }
    }
}

// ---------- تنظیمات ----------
@Composable
fun SettingsTab(darkMode: Int, setDark: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("تنظیمات", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(20.dp))
        Text("حالت نمایش", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Spacer(Modifier.height(10.dp))
        ChipRow(listOf("0" to "سیستم", "1" to "روشن", "2" to "تاریک"), darkMode.toString()) { setDark(it.toInt()) }
        Spacer(Modifier.height(28.dp))
        Text("درباره اپ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "فیت‌یار رایگان است و با نمایش تبلیغات پشتیبانی می‌شود. محتوای آموزشی جنبه عمومی دارد و جایگزین مشاوره پزشک یا مربی نیست.",
            fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 22.sp
        )
    }
}

// ---------- تایمر استراحت ----------
@Composable
fun RestTimerDialog(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var total by remember { mutableIntStateOf(60) }
    var left by remember { mutableIntStateOf(60) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (running) {
            while (left > 0) { delay(1000); left-- }
            running = false
            try {
                ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            } catch (_: Exception) { }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("بستن") } },
        title = { Text("تایمر استراحت") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d".format(left / 60, left % 60), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { if (total == 0) 0f else left / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                ChipRow(listOf(30, 60, 90, 120).map { it.toString() to "$it ثانیه" }, total.toString()) {
                    running = false; total = it.toInt(); left = total
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (left == 0) left = total; running = !running }) {
                        Text(if (running) "توقف" else "شروع")
                    }
                    OutlinedButton(onClick = { running = false; left = total }) { Text("از نو") }
                }
            }
        }
    )
}

// ---------- صفحه شروع ----------
@Composable
fun Splash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF7C3AED)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏋️", fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text("فیت‌یار", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("حرکت درست، نتیجه بهتر", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
    }
}
