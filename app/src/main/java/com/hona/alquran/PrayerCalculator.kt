package com.hona.alquran

import android.content.Context
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PrayerItem(
    val name: String,
    val timeInMillis: Long,
    val formattedTime: String
)

object PrayerCalculator {
    fun saveLocation(context: Context, lat: Double, lng: Double) {
        context.getSharedPreferences("hona_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lng", lng.toFloat())
            .apply()
    }

    fun getSavedLocation(context: Context): Pair<Double, Double> {
        val prefs = context.getSharedPreferences("hona_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("lat", 30.0444f).toDouble()
        val lng = prefs.getFloat("lng", 31.2357f).toDouble()
        return Pair(lat, lng)
    }

    fun getTodayPrayers(lat: Double, lng: Double, date: Date = Date()): List<PrayerItem> {
        val coordinates = Coordinates(lat, lng)
        val dateComponents = DateComponents.from(date)
        val params = CalculationMethod.EGYPTIAN.parameters.apply {
            madhab = Madhab.SHAFI
        }
        val prayerTimes = PrayerTimes(coordinates, dateComponents, params)
        val sdf = SimpleDateFormat("hh:mm a", Locale("ar")).apply {
            timeZone = TimeZone.getDefault()
        }

        return listOf(
            PrayerItem("الفجر", prayerTimes.fajr.time, sdf.format(prayerTimes.fajr)),
            PrayerItem("الظهر", prayerTimes.dhuhr.time, sdf.format(prayerTimes.dhuhr)),
            PrayerItem("العصر", prayerTimes.asr.time, sdf.format(prayerTimes.asr)),
            PrayerItem("المغرب", prayerTimes.maghrib.time, sdf.format(prayerTimes.maghrib)),
            PrayerItem("العشاء", prayerTimes.isha.time, sdf.format(prayerTimes.isha))
        )
    }
}
