package com.talha.riderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeliveryOrder(
    val id: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val status: String = "",
    val riderStatus: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val total: Double = 0.0,
    val itemsSummary: String = "",
    val deliveryLat: Double? = null,
    val deliveryLng: Double? = null,
    val createdAt: Long = 0L,
    val deliveredAt: Long = 0L
)

private val ACTIVE_RIDER_STATUSES = setOf("ACCEPTED", "ARRIVED", "PICKED_UP", "ON_THE_WAY")

private fun isToday(ms: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.US)
    return f.format(Date(ms)) == f.format(Date())
}

private fun formatKm(meters: Double): String =
    if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000) else "${meters.toInt()} m"

class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var restaurantId by remember { mutableStateOf<String?>(null) }
            var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
            var loading by remember { mutableStateOf(false) }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }

            LaunchedEffect(loggedIn) {
                if (loggedIn && restaurantId == null) {
                    val uid = auth.currentUser?.uid ?: return@LaunchedEffect
                    runCatching {
                        val doc = firestore.collection("userRestaurants").document(uid).get().await2()
                        restaurantId = doc?.getString("restaurantId")
                    }.onFailure { errorMsg = "Could not load restaurant: ${it.message}" }
                }
            }

            RiderTheme {
                GlassBackground {
                    when {
                        !loggedIn -> LoginScreen(
                            loading = loading,
                            error = errorMsg,
                            onLogin = { email, pass ->
                                loading = true; errorMsg = null
                                auth.signInWithEmailAndPassword(email, pass)
                                    .addOnSuccessListener { loggedIn = true; loading = false }
                                    .addOnFailureListener { e -> errorMsg = e.message ?: "Login failed"; loading = false }
                            }
                        )
                        restaurantId == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            if (errorMsg != null) Text(errorMsg!!, color = RiderColors.Red) else CircularProgressIndicator(color = RiderColors.Blue)
                        }
                        else -> RiderShell(
                            restaurantId = restaurantId!!,
                            firestore = firestore,
                            onLogout = {
                                LocationUpdateService.stop(this@MainActivity)
                                auth.signOut()
                                loggedIn = false
                                restaurantId = null
                            }
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Login ─────────────────────────────

@Composable
fun LoginScreen(loading: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(Spacing.XL), verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(72.dp).background(RiderColors.Blue.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.DeliveryDining, null, tint = RiderColors.Cyan, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(Spacing.LG))
        Text("Rider Delivery", color = RiderColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Sign in with the same email and password as the Restaurant POS app.",
            color = RiderColors.TextSecondary, fontSize = 13.sp
        )
        Spacer(Modifier.height(Spacing.XL))
        GlassCard {
            GlassInput(email, { email = it }, "Email", KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(Spacing.MD))
            GlassInput(password, { password = it }, "Password", KeyboardOptions(keyboardType = KeyboardType.Password), PasswordVisualTransformation())
            if (error != null) {
                Spacer(Modifier.height(Spacing.SM))
                Text(error, color = RiderColors.Red, fontSize = 12.sp)
            }
            Spacer(Modifier.height(Spacing.LG))
            GlassButton(
                if (loading) "Signing in..." else "Login", { onLogin(email.trim(), password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && email.isNotBlank() && password.isNotBlank()
            )
        }
    }
}

// ───────────────────────────── Shell (tabs) ─────────────────────────────

@Composable
fun RiderShell(restaurantId: String, firestore: FirebaseFirestore, onLogout: () -> Unit) {
    val context = LocalContext.current
    val myId = remember { RiderPrefs.riderId(context) }

    var orders by remember { mutableStateOf<List<DeliveryOrder>>(emptyList()) }
    var online by remember { mutableStateOf(RiderPrefs.online(context)) }
    var tab by remember { mutableStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var restaurantName by remember { mutableStateOf("Restaurant") }
    var currency by remember { mutableStateOf("PKR") }
    var riderName by remember { mutableStateOf(RiderPrefs.riderName(context)) }
    var rate by remember { mutableStateOf(RiderPrefs.ratePerDelivery(context)) }

    LaunchedEffect(restaurantId) {
        runCatching {
            val d = firestore.collection("restaurants").document(restaurantId).get().await2()
            d?.getString("name")?.takeIf { it.isNotBlank() }?.let { restaurantName = it }
            d?.getString("currency")?.takeIf { it.isNotBlank() }?.let { currency = it }
        }
    }

    DisposableEffect(restaurantId) {
        val reg = firestore.collection("restaurants").document(restaurantId)
            .collection("orders")
            .whereEqualTo("orderType", "DELIVERY")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                orders = snap.documents.map { d ->
                    val loc = d.get("deliveryLocation") as? Map<*, *>
                    val items = (d.get("items") as? List<*>)?.mapNotNull { it as? Map<*, *> }
                        ?.joinToString(", ") { "${(it["quantity"] as? Number)?.toInt() ?: 1}x ${it["name"] ?: ""}" } ?: ""
                    DeliveryOrder(
                        id = d.id,
                        orderNumber = d.getString("orderNumber") ?: "",
                        customerName = d.getString("customerName") ?: "",
                        customerPhone = d.getString("customerPhone") ?: "",
                        customerAddress = d.getString("customerAddress") ?: "",
                        status = d.getString("status") ?: "",
                        riderStatus = d.getString("riderStatus") ?: "",
                        riderId = d.getString("riderId") ?: "",
                        riderName = d.getString("riderName") ?: "",
                        total = d.getDouble("total") ?: 0.0,
                        itemsSummary = items,
                        deliveryLat = (loc?.get("lat") as? Number)?.toDouble(),
                        deliveryLng = (loc?.get("lng") as? Number)?.toDouble(),
                        createdAt = (d.get("createdAt") as? Number)?.toLong() ?: 0L,
                        deliveredAt = (d.get("deliveredAt") as? Number)?.toLong() ?: 0L
                    )
                }
            }
        onDispose { reg.remove() }
    }

    val myActive = orders.firstOrNull {
        it.riderId == myId && it.riderStatus in ACTIVE_RIDER_STATUSES && it.status != "COMPLETED" && it.status != "CANCELLED"
    }
    val available = orders
        .filter { (it.status == "NEW" || it.status == "PREPARING" || it.status == "READY") && it.riderStatus.isBlank() }
        .sortedBy { it.createdAt }
    val doneToday = orders.filter {
        it.riderId == myId && it.riderStatus == "DELIVERED" && isToday(if (it.deliveredAt > 0) it.deliveredAt else it.createdAt)
    }

    // If the app was reopened mid-delivery, make sure location sharing is running again.
    LaunchedEffect(myActive?.id) {
        if (myActive != null) LocationUpdateService.start(context, restaurantId, myActive.id, myActive.orderNumber)
    }

    fun advance(order: DeliveryOrder, newStatus: String) {
        val data = mutableMapOf<String, Any>(
            "riderStatus" to newStatus,
            "riderId" to myId,
            "riderName" to riderName.ifBlank { "Rider" },
            "riderUpdatedAt" to System.currentTimeMillis()
        )
        if (newStatus == "DELIVERED") {
            data["status"] = "COMPLETED"
            data["deliveredAt"] = System.currentTimeMillis()
        }
        firestore.collection("restaurants").document(restaurantId)
            .collection("orders").document(order.id).update(data)
        if (newStatus == "ACCEPTED") LocationUpdateService.start(context, restaurantId, order.id, order.orderNumber)
        if (newStatus == "DELIVERED") LocationUpdateService.stop(context)
    }

    val selected = selectedId?.let { id -> orders.find { it.id == id } }
    BackHandler(enabled = selected != null) { selectedId = null }

    if (selected != null) {
        DeliveryDetailScreen(
            order = selected,
            restaurantId = restaurantId,
            restaurantName = restaurantName,
            currency = currency,
            firestore = firestore,
            myId = myId,
            online = online,
            hasOtherActive = myActive != null && myActive.id != selected.id,
            onBack = { selectedId = null },
            onAdvance = { status -> advance(selected, status) }
        )
        return
    }

    val navItems = listOf(
        NavItem("Home", Icons.Default.Home),
        NavItem("Orders", Icons.Default.ListAlt),
        NavItem("Map", Icons.Default.Map),
        NavItem("Earnings", Icons.Default.AccountBalanceWallet),
        NavItem("Profile", Icons.Default.Person)
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = tab, label = "tabs") { t ->
                when (t) {
                    0 -> HomeTab(
                        riderName = riderName, restaurantName = restaurantName, online = online,
                        onToggleOnline = { online = !online; RiderPrefs.setOnline(context, online) },
                        myActive = myActive, availableCount = available.size,
                        doneCount = doneToday.size, earnings = doneToday.size * rate, currency = currency,
                        distanceM = RiderPrefs.distanceTodayM(context),
                        onOpenActive = { myActive?.let { selectedId = it.id } },
                        onSeeOrders = { tab = 1 }
                    )
                    1 -> OrdersTab(available, myActive, online, currency) { selectedId = it.id }
                    2 -> MapTab(myActive, restaurantId, firestore) { myActive?.let { selectedId = it.id } }
                    3 -> EarningsTab(doneToday, rate, currency)
                    else -> ProfileTab(
                        riderName = riderName, rate = rate, currency = currency,
                        onSave = { name, r ->
                            riderName = name; rate = r
                            RiderPrefs.setRiderName(context, name); RiderPrefs.setRate(context, r)
                        },
                        onLogout = onLogout
                    )
                }
            }
        }
        GlassBottomNavigation(navItems, tab) { tab = it }
    }
}

// ───────────────────────────── Tabs ─────────────────────────────

@Composable
fun HomeTab(
    riderName: String, restaurantName: String, online: Boolean, onToggleOnline: () -> Unit,
    myActive: DeliveryOrder?, availableCount: Int, doneCount: Int, earnings: Double, currency: String,
    distanceM: Double, onOpenActive: () -> Unit, onSeeOrders: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.LG)) {
        GlassTopBar(
            title = if (riderName.isBlank()) "Hello, Rider" else "Hello, $riderName",
            subtitle = restaurantName
        )
        Column(Modifier.padding(horizontal = Spacing.LG), verticalArrangement = Arrangement.spacedBy(Spacing.MD)) {
            GlassCard(Modifier.fillMaxWidth(), accent = online) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(if (online) RiderColors.Green else RiderColors.TextTertiary, CircleShape))
                    Spacer(Modifier.width(Spacing.SM))
                    Text(
                        if (online) "ONLINE" else "OFFLINE", color = RiderColors.TextPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                }
                Text(
                    if (online) "You can accept new deliveries." else "Go online to start accepting deliveries.",
                    color = RiderColors.TextSecondary, fontSize = 13.sp
                )
                Spacer(Modifier.height(Spacing.MD))
                GlassButton(
                    if (online) "GO OFFLINE" else "GO ONLINE", onToggleOnline,
                    modifier = Modifier.fillMaxWidth(), icon = Icons.Default.PowerSettingsNew,
                    tint = if (online) RiderColors.Deep else RiderColors.Blue, height = 64.dp
                )
            }

            if (myActive != null) {
                GlassCard(Modifier.fillMaxWidth(), accent = true, onClick = onOpenActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Active Delivery", color = RiderColors.Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        GlassStatusBadge(riderStatusLabel(myActive.riderStatus), RiderColors.Cyan)
                    }
                    Spacer(Modifier.height(Spacing.SM))
                    Text(myActive.orderNumber, color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(myActive.customerName, color = RiderColors.TextPrimary)
                    Text(myActive.customerAddress, color = RiderColors.TextSecondary, fontSize = 13.sp, maxLines = 2)
                }
            } else {
                GlassCard(Modifier.fillMaxWidth(), onClick = onSeeOrders) {
                    Text("Active Delivery", color = RiderColors.TextSecondary, fontSize = 13.sp)
                    Text("None right now", color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (availableCount > 0) "$availableCount new deliveries waiting - tap to view" else "New deliveries will appear here.",
                        color = RiderColors.TextSecondary, fontSize = 13.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.MD)) {
                GlassStatCard("Today's Deliveries", "${doneCount + if (myActive != null) 1 else 0}", Icons.Default.DeliveryDining, Modifier.weight(1f))
                GlassStatCard("Completed Orders", "$doneCount", Icons.Default.CheckCircle, Modifier.weight(1f), RiderColors.Green)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.MD)) {
                GlassStatCard("Today's Earnings", "$currency ${earnings.toInt()}", Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                GlassStatCard("Distance", formatKm(distanceM), Icons.Default.Navigation, Modifier.weight(1f), RiderColors.Cyan)
            }
        }
    }
}

@Composable
fun OrdersTab(available: List<DeliveryOrder>, myActive: DeliveryOrder?, online: Boolean, currency: String, onOpen: (DeliveryOrder) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Orders", "${available.size} available")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.LG),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD)
        ) {
            if (!online) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("You are offline. Go online from Home to accept deliveries.", color = RiderColors.Amber, fontSize = 13.sp)
                }
            }
            myActive?.let { GlassOrderCard(it, currency, highlight = true) { onOpen(it) } }
            if (available.isEmpty() && myActive == null) {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), Alignment.Center) {
                    Text("No delivery orders right now", color = RiderColors.TextTertiary)
                }
            }
            available.forEach { GlassOrderCard(it, currency) { onOpen(it) } }
            Spacer(Modifier.height(Spacing.LG))
        }
    }
}

@Composable
fun GlassOrderCard(order: DeliveryOrder, currency: String, highlight: Boolean = false, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = highlight, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.orderNumber, color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (order.riderStatus.isNotBlank()) GlassStatusBadge(riderStatusLabel(order.riderStatus), RiderColors.Cyan)
            else GlassStatusBadge("New Delivery", RiderColors.Blue)
        }
        Spacer(Modifier.height(Spacing.SM))
        Text(order.customerName, color = RiderColors.TextPrimary, fontWeight = FontWeight.Medium)
        Text(order.customerAddress, color = RiderColors.TextSecondary, fontSize = 13.sp, maxLines = 2)
        Spacer(Modifier.height(Spacing.SM))
        Row {
            Text(order.itemsSummary, color = RiderColors.TextTertiary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("$currency ${order.total.toInt()}", color = RiderColors.Cyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MapTab(myActive: DeliveryOrder?, restaurantId: String, firestore: FirebaseFirestore, onOpenDetails: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Map", if (myActive != null) "Delivery ${myActive.orderNumber}" else null)
        if (myActive == null) {
            Box(Modifier.fillMaxSize().padding(Spacing.LG), Alignment.TopCenter) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("No active delivery", color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Accept a delivery from the Orders tab to see the live route to your customer here.", color = RiderColors.TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            Column(Modifier.padding(horizontal = Spacing.LG)) {
                RouteMap(myActive, restaurantId, firestore, isSharing = true, mapHeight = 460.dp)
                Spacer(Modifier.height(Spacing.MD))
                GlassButton("Delivery Details", onOpenDetails, modifier = Modifier.fillMaxWidth(), primary = false)
            }
        }
    }
}

@Composable
fun EarningsTab(done: List<DeliveryOrder>, rate: Double, currency: String) {
    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Earnings", "Today")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.LG),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD)
        ) {
            GlassCard(Modifier.fillMaxWidth(), accent = true) {
                Text("Total earned today", color = RiderColors.TextSecondary, fontSize = 13.sp)
                Text("$currency ${(done.size * rate).toInt()}", color = RiderColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                Text("${done.size} deliveries", color = RiderColors.TextSecondary, fontSize = 13.sp)
                if (rate <= 0.0) {
                    Spacer(Modifier.height(Spacing.SM))
                    Text("Set your rate per delivery in Profile to see earnings.", color = RiderColors.Amber, fontSize = 12.sp)
                }
            }
            if (done.isEmpty()) {
                Text("Completed deliveries will show up here.", color = RiderColors.TextTertiary)
            }
            done.sortedByDescending { it.deliveredAt }.forEach { o ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.orderNumber, color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold)
                            Text(o.customerName, color = RiderColors.TextSecondary, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$currency ${rate.toInt()}", color = RiderColors.Cyan, fontWeight = FontWeight.Bold)
                            Text("Order $currency ${o.total.toInt()}", color = RiderColors.TextTertiary, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.LG))
        }
    }
}

@Composable
fun ProfileTab(riderName: String, rate: Double, currency: String, onSave: (String, Double) -> Unit, onLogout: () -> Unit) {
    var name by remember { mutableStateOf(riderName) }
    var rateText by remember { mutableStateOf(if (rate > 0) rate.toInt().toString() else "") }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GlassTopBar("Profile")
        Column(Modifier.padding(horizontal = Spacing.LG), verticalArrangement = Arrangement.spacedBy(Spacing.MD)) {
            GlassCard(Modifier.fillMaxWidth()) {
                GlassInput(name, { name = it; saved = false }, "Your name")
                Spacer(Modifier.height(Spacing.MD))
                GlassInput(rateText, { rateText = it.filter { c -> c.isDigit() }; saved = false }, "Earning per delivery ($currency)", KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(Spacing.LG))
                GlassButton(if (saved) "Saved" else "Save", {
                    onSave(name.trim(), rateText.toDoubleOrNull() ?: 0.0); saved = true
                }, modifier = Modifier.fillMaxWidth())
            }
            GlassButton("Logout", onLogout, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.Logout, primary = false)
        }
    }
}

// ───────────────────────────── Delivery detail ─────────────────────────────

fun riderStatusLabel(s: String) = when (s) {
    "ACCEPTED" -> "Accepted"
    "ARRIVED" -> "At Restaurant"
    "PICKED_UP" -> "Picked Up"
    "ON_THE_WAY" -> "On the Way"
    "DELIVERED" -> "Delivered"
    else -> "New Delivery"
}

@Composable
fun RouteMap(order: DeliveryOrder, restaurantId: String, firestore: FirebaseFirestore, isSharing: Boolean, mapHeight: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val customerPoint = remember(order.deliveryLat, order.deliveryLng) {
        val la = order.deliveryLat
        val ln = order.deliveryLng
        if (la != null && ln != null) GeoPoint(la, ln) else null
    }
    var myPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var livePoint by remember { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(order.id) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { loc -> if (loc != null) myPoint = GeoPoint(loc.latitude, loc.longitude) }
            }
        }
    }
    DisposableEffect(order.id) {
        val reg = firestore.collection("restaurants").document(restaurantId)
            .collection("orders").document(order.id)
            .addSnapshotListener { snap, _ ->
                val ll = snap?.get("liveLocation") as? Map<*, *>
                val lat = (ll?.get("lat") as? Number)?.toDouble()
                val lng = (ll?.get("lng") as? Number)?.toDouble()
                if (lat != null && lng != null) livePoint = GeoPoint(lat, lng)
            }
        onDispose { reg.remove() }
    }

    val riderPoint = if (isSharing) (livePoint ?: myPoint) else myPoint

    var routeInfo by remember { mutableStateOf<RouteInfo?>(null) }
    var routeFrom by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(riderPoint, customerPoint) {
        val r = riderPoint
        val c = customerPoint
        if (r != null && c != null) {
            val last = routeFrom
            if (last == null || last.distanceToAsDouble(r) > 150.0) {
                routeFrom = r
                fetchRoute(r, c)?.let { routeInfo = it }
            }
        }
    }

    if (customerPoint == null) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "This customer did not pin a location - use the written address below.",
                color = RiderColors.Amber, fontSize = 13.sp
            )
        }
        return
    }

    val r = riderPoint
    val meters = if (r != null) (routeInfo?.distanceM ?: r.distanceToAsDouble(customerPoint)) else null
    val minutes = routeInfo?.durationS?.let { (it / 60).toInt().coerceAtLeast(1) }

    Column {
        LiveMap(
            modifier = Modifier.fillMaxWidth().height(mapHeight).clipToShape(),
            customer = customerPoint, rider = riderPoint, route = routeInfo?.points ?: emptyList()
        )
        if (meters != null) {
            Spacer(Modifier.height(Spacing.SM))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.MD)) {
                GlassStatCard("Distance to customer", formatKm(meters), Icons.Default.Navigation, Modifier.weight(1f), RiderColors.Cyan)
                GlassStatCard("ETA", if (minutes != null) "$minutes min" else "-", Icons.Default.Place, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DeliveryDetailScreen(
    order: DeliveryOrder,
    restaurantId: String,
    restaurantName: String,
    currency: String,
    firestore: FirebaseFirestore,
    myId: String,
    online: Boolean,
    hasOtherActive: Boolean,
    onBack: () -> Unit,
    onAdvance: (String) -> Unit
) {
    val context = LocalContext.current
    val mine = order.riderId == myId
    val takenByOther = order.riderStatus.isNotBlank() && !mine
    val sharing = mine && order.riderStatus in ACTIVE_RIDER_STATUSES

    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Order ${order.orderNumber}", riderStatusLabel(order.riderStatus), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.LG),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD)
        ) {
            RouteMap(order, restaurantId, firestore, isSharing = sharing, mapHeight = 260.dp)

            if (mine && order.riderStatus.isNotBlank()) StatusTimeline(order.riderStatus)

            GlassCard(Modifier.fillMaxWidth()) {
                Text("FROM", color = RiderColors.TextTertiary, fontSize = 11.sp)
                Text(restaurantName, color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.MD))
                Text("TO", color = RiderColors.TextTertiary, fontSize = 11.sp)
                Text(order.customerName, color = RiderColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(order.customerPhone, color = RiderColors.TextSecondary)
                Text(order.customerAddress, color = RiderColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(Spacing.MD))
                Text("ITEMS", color = RiderColors.TextTertiary, fontSize = 11.sp)
                Text(order.itemsSummary, color = RiderColors.TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(Spacing.SM))
                Text("Total: $currency ${order.total.toInt()}", color = RiderColors.Cyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.MD)) {
                GlassButton("Call", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}"))) }
                }, modifier = Modifier.weight(1f), icon = Icons.Default.Call, primary = false)
                GlassButton("Navigate", {
                    val uri = if (order.deliveryLat != null && order.deliveryLng != null) {
                        Uri.parse("geo:${order.deliveryLat},${order.deliveryLng}?q=${order.deliveryLat},${order.deliveryLng}(${Uri.encode(order.customerName)})")
                    } else {
                        Uri.parse("geo:0,0?q=${Uri.encode(order.customerAddress)}")
                    }
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }, modifier = Modifier.weight(1f), icon = Icons.Default.Navigation, primary = false)
            }

            when {
                takenByOther -> Text("Taken by ${order.riderName.ifBlank { "another rider" }}", color = RiderColors.Amber)
                order.riderStatus.isBlank() -> {
                    GlassButton(
                        "Accept Delivery", { onAdvance("ACCEPTED") },
                        modifier = Modifier.fillMaxWidth(), icon = Icons.Default.DeliveryDining,
                        enabled = online && !hasOtherActive, height = 64.dp
                    )
                    if (!online) Text("Go online from Home first.", color = RiderColors.Amber, fontSize = 12.sp)
                    else if (hasOtherActive) Text("Finish your current delivery first.", color = RiderColors.Amber, fontSize = 12.sp)
                }
                order.riderStatus == "ACCEPTED" ->
                    GlassButton("Arrived at Restaurant", { onAdvance("ARRIVED") }, Modifier.fillMaxWidth(), icon = Icons.Default.Storefront, height = 64.dp)
                order.riderStatus == "ARRIVED" ->
                    GlassButton("Picked Up Order", { onAdvance("PICKED_UP") }, Modifier.fillMaxWidth(), icon = Icons.Default.CheckCircle, height = 64.dp)
                order.riderStatus == "PICKED_UP" ->
                    GlassButton("Start Delivery", { onAdvance("ON_THE_WAY") }, Modifier.fillMaxWidth(), icon = Icons.Default.Navigation, height = 64.dp)
                order.riderStatus == "ON_THE_WAY" ->
                    GlassButton("Complete Delivery", { onAdvance("DELIVERED") }, Modifier.fillMaxWidth(), icon = Icons.Default.CheckCircle, tint = RiderColors.Green, height = 64.dp)
                order.riderStatus == "DELIVERED" ->
                    Text("Delivered \u2713", color = RiderColors.Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            if (sharing) Text("Your live location is being shared with the customer.", color = RiderColors.Cyan, fontSize = 12.sp)
            Spacer(Modifier.height(Spacing.XL))
        }
    }
}

@Composable
fun StatusTimeline(current: String) {
    val steps = listOf("ACCEPTED" to "Accepted", "ARRIVED" to "At Rest.", "PICKED_UP" to "Picked Up", "ON_THE_WAY" to "On the Way", "DELIVERED" to "Delivered")
    val idx = steps.indexOfFirst { it.first == current }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            steps.forEachIndexed { i, (_, label) ->
                val done = i <= idx
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(if (i == idx) 16.dp else 12.dp)
                            .background(if (done) RiderColors.Cyan else RiderColors.GlassBorderActive, CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label, color = if (done) RiderColors.TextPrimary else RiderColors.TextTertiary, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

// Small helper: awaits a Firestore Task using a plain coroutine, so we don't need to pull
// in Google's Play-services Kotlin extensions just for this one call.
suspend fun <T> com.google.android.gms.tasks.Task<T>.await2(): T? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resumeWith(Result.success(result)) }
        addOnFailureListener { e -> cont.resumeWith(Result.failure(e)) }
    }
