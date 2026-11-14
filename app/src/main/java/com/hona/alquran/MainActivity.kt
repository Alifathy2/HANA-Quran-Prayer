package com.hona.alquran

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.hona.alquran.service.AdhanScheduler
import com.hona.alquran.service.AdhanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class SurahInfo(val number: Int, val name: String, val ayahsCount: Int)
data class AyahWithTafsir(val numberInSurah: Int, val text: String, val tafsir: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdhanScheduler.scheduleAllPrayers(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD4AF37),
                    background = Color(0xFF0D1B1E),
                    surface = Color(0xFF15292E),
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HonaAlQuranApp()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun HonaAlQuranApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTvOrWideScreen = configuration.screenWidthDp >= 600

    var selectedTab by remember { mutableIntStateOf(0) }
    var prayers by remember { mutableStateOf(emptyList<PrayerItem>()) }
    var locationStatus by remember { mutableStateOf("جاري تحديث الموقع...") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
            fusedLocation.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    PrayerCalculator.saveLocation(context, loc.latitude, loc.longitude)
                    prayers = PrayerCalculator.getTodayPrayers(loc.latitude, loc.longitude)
                    locationStatus = "تم التحديث حسب موقعك الحالي (GPS)"
                    AdhanScheduler.scheduleAllPrayers(context)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val (lat, lng) = PrayerCalculator.getSavedLocation(context)
        prayers = PrayerCalculator.getTodayPrayers(lat, lng)
        locationStatus = "مواقيت الصلاة المحفوظة"

        val neededPerms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPerms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(neededPerms.toTypedArray())
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "هنا القرآن",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvFriendlyButton(
                    text = "تثبيت الخلفية",
                    icon = Icons.Default.BatteryChargingFull
                ) {
                    val pm = context.getSystemService(PowerManager::class.java)
                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
                TvFriendlyButton(
                    text = "تجربة الأذان",
                    icon = Icons.Default.VolumeUp
                ) {
                    val intent = Intent(context, AdhanService::class.java).apply {
                        putExtra("PRAYER_NAME", "التجربة")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
        }

        if (isTvOrWideScreen) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(0.38f).fillMaxHeight()) {
                    PrayerTimesScreen(prayers, locationStatus)
                }
                Box(modifier = Modifier.weight(0.62f).fillMaxHeight()) {
                    QuranAndTafsirScreen()
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    PrayerTimesScreen(prayers, locationStatus)
                } else {
                    QuranAndTafsirScreen()
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                    label = { Text("مواقيت الصلاة") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                    label = { Text("القرآن والتفسير") }
                )
            }
        }
    }
}

@Composable
fun TvFriendlyButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun PrayerTimesScreen(prayers: List<PrayerItem>, status: String) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("مواقيت الصلاة اليوم", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(status, fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(bottom = 16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(prayers) { prayer ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E383E), RoundedCornerShape(12.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(prayer.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(prayer.formattedTime, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QuranAndTafsirScreen() {
    val scope = rememberCoroutineScope()
    var surahs by remember { mutableStateOf(emptyList<SurahInfo>()) }
    var selectedSurah by remember { mutableStateOf<SurahInfo?>(null) }
    var ayahs by remember { mutableStateOf(emptyList<AyahWithTafsir>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showTafsir by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val json = URL("https://api.alquran.cloud/v1/surah").readText()
                val data = JSONObject(json).getJSONArray("data")
                val list = mutableListOf<SurahInfo>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    list.add(SurahInfo(item.getInt("number"), item.getString("name"), item.getInt("numberOfAyahs")))
                }
                surahs = list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (selectedSurah == null) {
                Text("القرآن الكريم والتفسير الميسر", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(surahs) { surah ->
                            var focused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E383E), RoundedCornerShape(10.dp))
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                                    .border(if (focused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedSurah = surah
                                        isLoading = true
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val res = URL("https://api.alquran.cloud/v1/surah/${surah.number}/editions/quran-uthmani,ar.muyassar").readText()
                                                val editions = JSONObject(res).getJSONArray("data")
                                                val quranAyahs = editions.getJSONObject(0).getJSONArray("ayahs")
                                                val tafsirAyahs = editions.getJSONObject(1).getJSONArray("ayahs")
                                                val combined = mutableListOf<AyahWithTafsir>()
                                                for (i in 0 until quranAyahs.length()) {
                                                    combined.add(
                                                        AyahWithTafsir(
                                                            numberInSurah = quranAyahs.getJSONObject(i).getInt("numberInSurah"),
                                                            text = quranAyahs.getJSONObject(i).getString("text"),
                                                            tafsir = tafsirAyahs.getJSONObject(i).getString("text")
                                                        )
                                                    )
                                                }
                                                ayahs = combined
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${surah.number}. ${surah.name}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text("${surah.ayahsCount} آية", fontSize = 14.sp, color = Color.LightGray)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvFriendlyButton("رجوع للسور", Icons.Default.ArrowForward) { selectedSurah = null }
                    Text(selectedSurah!!.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TvFriendlyButton(if (showTafsir) "إخفاء التفسير" else "إظهار التفسير", Icons.Default.Description) {
                        showTafsir = !showTafsir
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(ayahs) { ayah ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E383E), RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "${ayah.text} ﴿${ayah.numberInSurah}﴾",
                                    fontSize = 22.sp,
                                    lineHeight = 36.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (showTafsir) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
                                    Text(
                                        text = "التفسير الميسر: ${ayah.tafsir}",
                                        fontSize = 15.sp,
                                        color = Color(0xFFE0E0E0),
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
