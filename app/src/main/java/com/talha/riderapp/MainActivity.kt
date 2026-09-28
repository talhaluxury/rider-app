@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.talha.riderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class DeliveryOrder(
    val id: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val status: String = "",
    val total: Double = 0.0,
    val itemsSummary: String = "",
    val deliveryLat: Double? = null,
    val deliveryLng: Double? = null
)

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
            ) { /* handled by button state re-check */ }

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

            MaterialTheme(colorScheme = riderColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
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
                            if (errorMsg != null) Text(errorMsg!!, color = Color.Red) else CircularProgressIndicator()
                        }
                        else -> OrdersScreen(
                            restaurantId = restaurantId!!,
                            firestore = firestore,
                            onLogout = {
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

@Composable
fun riderColorScheme() = lightColorScheme(
    primary = Color(0xFFFF6B00),
    secondary = Color(0xFF1A1A2E)
)

@Composable
fun LoginScreen(loading: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.DeliveryDining, contentDescription = null, tint = Color(0xFFFF6B00), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Rider Login", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Use the same email/password you use for the restaurant's main app.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color.Red, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onLogin(email.trim(), password) },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text("Login")
        }
    }
}

@Composable
fun OrdersScreen(restaurantId: String, firestore: FirebaseFirestore, onLogout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var orders by remember { mutableStateOf<List<DeliveryOrder>>(emptyList()) }
    var activeOrderId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<DeliveryOrder?>(null) }

    DisposableEffect(restaurantId) {
        val reg: ListenerRegistration = firestore.collection("restaurants").document(restaurantId)
            .collection("orders")
            .whereEqualTo("orderType", "DELIVERY")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val activeStatuses = setOf("NEW", "PREPARING", "READY")
                orders = snap.documents.filter { (it.getString("status") ?: "") in activeStatuses }.map { d ->
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
                        total = (d.getDouble("total")) ?: 0.0,
                        itemsSummary = items,
                        deliveryLat = (loc?.get("lat") as? Number)?.toDouble(),
                        deliveryLng = (loc?.get("lng") as? Number)?.toDouble()
                    )
                }.sortedBy { it.orderNumber }
            }
        onDispose { reg.remove() }
    }

    if (selected != null) {
        OrderDetailScreen(
            order = selected!!,
            restaurantId = restaurantId,
            firestore = firestore,
            isActiveDelivery = activeOrderId == selected!!.id,
            hasAnotherActiveDelivery = activeOrderId != null && activeOrderId != selected!!.id,
            onBack = { selected = null },
            onStartDelivery = {
                activeOrderId = selected!!.id
                LocationUpdateService.start(context, restaurantId, selected!!.id, selected!!.orderNumber)
            },
            onMarkDelivered = {
                firestore.collection("restaurants").document(restaurantId)
                    .collection("orders").document(selected!!.id)
                    .update("status", "COMPLETED")
                LocationUpdateService.stop(context)
                activeOrderId = null
                selected = null
            }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Deliveries (${orders.size})") },
            actions = {
                IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "Logout") }
            }
        )
        if (orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No delivery orders right now", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(orders) { order ->
                    ElevatedCard(
                        onClick = { selected = order },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(order.orderNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                AssistChip(onClick = {}, label = { Text(order.status) })
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(order.customerName, fontWeight = FontWeight.Medium)
                            Text(order.customerAddress, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            if (activeOrderId == order.id) {
                                Spacer(Modifier.height(6.dp))
                                Text("📍 Sharing live location", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(
    order: DeliveryOrder,
    restaurantId: String,
    firestore: FirebaseFirestore,
    isActiveDelivery: Boolean,
    hasAnotherActiveDelivery: Boolean,
    onBack: () -> Unit,
    onStartDelivery: () -> Unit,
    onMarkDelivered: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Order ${order.orderNumber}") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            }
        )
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(order.customerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(order.customerPhone, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(order.customerAddress, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Text("Items", fontWeight = FontWeight.Bold)
            Text(order.itemsSummary, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Total: ${order.total}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Call, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Call") }

                OutlinedButton(
                    onClick = {
                        val uri = if (order.deliveryLat != null && order.deliveryLng != null) {
                            Uri.parse("geo:${order.deliveryLat},${order.deliveryLng}?q=${order.deliveryLat},${order.deliveryLng}(${Uri.encode(order.customerName)})")
                        } else {
                            Uri.parse("geo:0,0?q=${Uri.encode(order.customerAddress)}")
                        }
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Map, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Navigate") }
            }

            Spacer(Modifier.height(24.dp))
            if (!isActiveDelivery) {
                Button(
                    onClick = onStartDelivery,
                    enabled = !hasAnotherActiveDelivery,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Icon(Icons.Default.PlayArrow, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Start Delivery") }
                if (hasAnotherActiveDelivery) {
                    Spacer(Modifier.height(6.dp))
                    Text("Finish your current active delivery first.", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text("📍 Live location is being shared with the customer", color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onMarkDelivered,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Icon(Icons.Default.CheckCircle, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Mark Delivered") }
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
