package com.hona.alquran.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hona.alquran.PrayerCalculator
import java.util.Calendar

object AdhanScheduler {
    fun scheduleAllPrayers(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val (lat, lng) = PrayerCalculator.getSavedLocation(context)
        val now = System.currentTimeMillis()

        val todayPrayers = PrayerCalculator.getTodayPrayers(lat, lng)
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowPrayers = PrayerCalculator.getTodayPrayers(lat, lng, tomorrowCal.time)

        val allUpcoming = (todayPrayers + tomorrowPrayers).filter { it.timeInMillis > now }

        allUpcoming.forEachIndexed { index, prayer ->
            val intent = Intent(context, AdhanReceiver::class.java).apply {
                putExtra("PRAYER_NAME", prayer.name)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prayer.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prayer.timeInMillis, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prayer.timeInMillis, pendingIntent)
            }
        }
    }
}
