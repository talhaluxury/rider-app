package com.talha.riderapp

import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL

data class RouteInfo(val points: List<GeoPoint>, val distanceM: Double, val durationS: Double)

/** Road route from -> to using the free public OSRM server. Returns null if offline/unavailable. */
suspend fun fetchRoute(from: GeoPoint, to: GeoPoint): RouteInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL(
            "https://router.project-osrm.org/route/v1/driving/" +
                "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
                "?overview=full&geometries=geojson"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "RiderApp")
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val route = JSONObject(text).getJSONArray("routes").getJSONObject(0)
        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = (0 until coords.length()).map {
            val c = coords.getJSONArray(it)
            GeoPoint(c.getDouble(1), c.getDouble(0))
        }
        RouteInfo(pts, route.getDouble("distance"), route.getDouble("duration"))
    }.getOrNull()
}

/**
 * OpenStreetMap view showing the customer's pinned delivery location (red pin), the rider
 * (blue dot) and the route between them. Uses osmdroid, so no Google Maps API key is needed.
 */
@Composable
fun LiveMap(
    modifier: Modifier,
    customer: GeoPoint?,
    rider: GeoPoint?,
    route: List<GeoPoint>
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
    // Zoom-to-fit only when the set of visible points changes, so the rider can still pan freely.
    var fitKey by remember { mutableStateOf("") }

    AndroidView(factory = { mapView }, modifier = modifier, update = { map ->
        map.overlays.clear()

        val line = when {
            route.size > 1 -> route
            customer != null && rider != null -> listOf(rider, customer)
            else -> emptyList()
        }
        if (line.size > 1) {
            map.overlays.add(Polyline(map).apply {
                setPoints(line)
                outlinePaint.color = 0xFF1E88E5.toInt()
                outlinePaint.strokeWidth = 12f
            })
        }
        customer?.let {
            map.overlays.add(Marker(map).apply {
                position = it
                title = "Customer"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
        rider?.let {
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1E88E5.toInt())
                setStroke(6, 0xFFFFFFFF.toInt())
                setSize(48, 48)
            }
            map.overlays.add(Marker(map).apply {
                position = it
                title = "You"
                icon = dot
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }

        val key = "${customer != null}-${rider != null}"
        if (key != fitKey) {
            fitKey = key
            map.post {
                if (customer != null && rider != null) {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPoints(listOf(customer, rider)), true, 120)
                } else if (customer != null) {
                    map.controller.setZoom(16.0); map.controller.setCenter(customer)
                } else if (rider != null) {
                    map.controller.setZoom(16.0); map.controller.setCenter(rider)
                }
            }
        }
        map.invalidate()
    })
}
