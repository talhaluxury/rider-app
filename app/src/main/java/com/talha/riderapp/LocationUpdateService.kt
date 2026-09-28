package com.talha.riderapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Runs while a delivery is active. Pushes the rider's location to
 * restaurants/{restaurantId}/orders/{orderId}.liveLocation every ~8 seconds — the exact
 * same field the main RestaurantPOS app (updateLiveLocation) and the ordering website
 * (leaflet tracking map) both already read, so the customer sees the rider move on their
 * order-tracking page in real time, with no changes needed on either of those.
 */
class LocationUpdateService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var restaurantId: String = ""
    private var orderId: String = ""
    private var orderNumber: String = ""

    companion object {
        const val CHANNEL_ID = "rider_location_status"
        const val NOTIF_ID = 7001
        const val EXTRA_RESTAURANT_ID = "restaurantId"
        const val EXTRA_ORDER_ID = "orderId"
        const val EXTRA_ORDER_NUMBER = "orderNumber"

        fun start(context: Context, restaurantId: String, orderId: String, orderNumber: String) {
            val intent = Intent(context, LocationUpdateService::class.java).apply {
                putExtra(EXTRA_RESTAURANT_ID, restaurantId)
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_ORDER_NUMBER, orderNumber)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationUpdateService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        restaurantId = intent?.getStringExtra(EXTRA_RESTAURANT_ID) ?: restaurantId
        orderId = intent?.getStringExtra(EXTRA_ORDER_ID) ?: orderId
        orderNumber = intent?.getStringExtra(EXTRA_ORDER_NUMBER) ?: orderNumber

        startForeground(NOTIF_ID, buildNotification())
        beginLocationUpdates()
        return START_STICKY
    }

    private fun beginLocationUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it) }

        val hasPermission = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission || restaurantId.isBlank() || orderId.isBlank()) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 8000L)
            .setMinUpdateIntervalMillis(5000L)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                pushLocation(loc.latitude, loc.longitude)
            }
        }
        fusedClient.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    private fun pushLocation(lat: Double, lng: Double) {
        FirebaseFirestore.getInstance()
            .collection("restaurants").document(restaurantId)
            .collection("orders").document(orderId)
            .update(
                "liveLocation", mapOf(
                    "lat" to lat, "lng" to lng, "updatedAt" to System.currentTimeMillis()
                )
            )
    }

    override fun onDestroy() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "Delivery Location Sharing", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while your location is being shared for an active delivery" }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Sharing location")
            .setContentText(if (orderNumber.isNotBlank()) "Delivering order $orderNumber" else "Active delivery in progress")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
