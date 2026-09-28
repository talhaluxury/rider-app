# Rider App

A small, separate Android app for delivery riders. It talks to the **exact same Firebase
project** as the RestaurantPOS app and the ordering website — no extra setup, no separate
database, no google-services.json needed (Firebase is initialized by hand in
`RiderApplication.kt` using the same config as the website).

## How it connects everything together

- **Website** → customer places a delivery order → captures their GPS pin
  (`deliveryLocation`) → saves the order to Firestore.
- **Rider App** (this app) → listens for `orderType == DELIVERY` orders → shows the
  customer's name, phone, address and a "Navigate" button (opens Google Maps using their
  pinned location) → rider taps **Start Delivery** → app streams the rider's live GPS to
  the order's `liveLocation` field every ~8 seconds.
- **Website's tracking page** → already reads `liveLocation` and shows the rider moving
  on the map in real time (this was already built — no website changes needed).
- **Main RestaurantPOS app** → sees the same order/status updates too, since everything
  lives in one Firestore database.

## Login

Riders log in with the **same email and password used for the main RestaurantPOS app**
(the restaurant owner/staff account). This is the simplest setup that works with the
existing Firestore security rules with no changes.

**Trade-off to know:** this means anyone with that login can see everything the main app
can (not just deliveries). For a small restaurant with 1–2 trusted riders this is usually
fine. If you later want each rider to have their own separate, restricted login (only
seeing delivery orders, nothing else), that needs a small Firestore rules change — ask
and it can be added.

## Building

1. Open the `RiderApp` folder in Android Studio (separate project from RestaurantPOS —
   don't open them both in the same window).
2. Let Gradle sync (Android Studio will offer to set up the Gradle wrapper automatically
   if it's missing — accept it).
3. Build → Build APK, or just hit Run with a phone connected.

## Permissions

On first launch the app asks for:
- **Location** ("Allow all the time" is best — needed to keep sharing location while the
  phone screen is off during a delivery. "While using the app" also works but the phone
  must stay unlocked/open during delivery.)
- **Notifications** (Android 13+) — needed to show the "Sharing location…" ongoing
  notification while a delivery is active.

For reliability, also turn off battery optimization for this app (Settings → Apps →
Rider App → Battery → Unrestricted), same as recommended for the main RestaurantPOS app.

## One-time Firestore note

The very first time the order list loads, if the phone shows a Firestore permission or
index error in the logs, it usually just means the security rules need the update
already applied to this project (see `RestaurantPOS/firestore.rules`) — nothing else to
change here.
