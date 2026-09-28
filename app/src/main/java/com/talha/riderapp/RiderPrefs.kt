package com.talha.riderapp

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Small on-device settings: rider identity, online flag, per-delivery rate and today's distance. */
object RiderPrefs {
    private fun p(c: Context) = c.getSharedPreferences("rider_prefs", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun riderId(c: Context): String {
        val cur = p(c).getString("riderId", null)
        if (cur != null) return cur
        val id = UUID.randomUUID().toString()
        p(c).edit().putString("riderId", id).apply()
        return id
    }

    fun riderName(c: Context) = p(c).getString("riderName", "") ?: ""
    fun setRiderName(c: Context, v: String) = p(c).edit().putString("riderName", v).apply()

    fun online(c: Context) = p(c).getBoolean("online", false)
    fun setOnline(c: Context, v: Boolean) = p(c).edit().putBoolean("online", v).apply()

    fun ratePerDelivery(c: Context) = p(c).getFloat("rate", 0f).toDouble()
    fun setRate(c: Context, v: Double) = p(c).edit().putFloat("rate", v.toFloat()).apply()

    fun addDistance(c: Context, meters: Double) {
        val key = "dist_${today()}"
        p(c).edit().putFloat(key, p(c).getFloat(key, 0f) + meters.toFloat()).apply()
    }
    fun distanceTodayM(c: Context) = p(c).getFloat("dist_${today()}", 0f).toDouble()
}
