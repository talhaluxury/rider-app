package com.talha.riderapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Initializes Firebase by hand with the exact same project config used by the website
 * (see website/index.html's firebaseConfig) and the main RestaurantPOS app. Doing it this
 * way means this rider app needs NO google-services.json and does NOT need to be
 * separately registered as an "Android app" in the Firebase Console — it just talks to
 * the same Firestore project as everything else.
 *
 * If you ever change Firebase projects, update these six values to match the
 * firebaseConfig block in website/index.html.
 */
class RiderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAfB61zHgxWxnzbWOvFPVhvn0Yz9ejZ9-A")
                .setApplicationId("1:1072408505617:web:939ae2d2be43d456e730b0")
                .setProjectId("bill-apps-3d675")
                .setDatabaseUrl("https://bill-apps-3d675-default-rtdb.firebaseio.com")
                .setStorageBucket("bill-apps-3d675.firebasestorage.app")
                .setGcmSenderId("1072408505617")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
