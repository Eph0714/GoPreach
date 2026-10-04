package com.emfitsolutions.gopreach.data.repository

import android.util.Log
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One named point of interest within a boundary's bounding box — a real
 * landmark (school, church, market, clinic, shop, ...) rather than a
 * synthetic label, so "the map" reads as an actual place and not just a
 * satellite photo or a bare polygon. */
data class Landmark(val name: String, val lat: Double, val lng: Double)

/**
 * Live, best-effort landmark lookup for a boundary's bounding box — same
 * public Overpass (OpenStreetMap) API and same "never block, never error,
 * just come back empty" spirit as [OverpassStreetRepository], the existing
 * precedent for calling this exact service from this app. Exists because
 * the native TomTom map's own vector "Standard"/BROWSING style has no road,
 * building, or label data at all for the rural provinces this app actually
 * serves (confirmed on-device: a flat blank canvas), and raw satellite
 * imagery alone shows terrain/rooftops but names nothing — neither gives a
 * Service Overseer/Secretary an actual recognizable place to go by.
 */
@Singleton
class OverpassLandmarkRepository @Inject constructor() {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, List<Landmark>>()
    private val maxCacheEntries = 40

    /** Up to 40 named landmarks (schools, churches, markets, clinics,
     * shops, government offices, notable attractions) inside
     * ([south],[west])-([north],[east]), or empty on any failure (offline,
     * timeout, rate limit) or if the area genuinely has none mapped —
     * every caller already treats "no landmarks" the same as "didn't
     * fetch," so this never needs to distinguish the two. */
    suspend fun landmarksIn(south: Double, west: Double, north: Double, east: Double): List<Landmark> {
        val key = "${(south * 2000).toInt()},${(west * 2000).toInt()},${(north * 2000).toInt()},${(east * 2000).toInt()}"
        synchronized(cache) { cache[key] }?.let { return it }
        return mutex.withLock {
            synchronized(cache) { cache[key] }?.let { return@withLock it }
            val result = withTimeoutOrNull(8000) { fetch(south, west, north, east) } ?: emptyList()
            synchronized(cache) {
                cache[key] = result
                if (cache.size > maxCacheEntries) cache.remove(cache.keys.first())
            }
            result
        }
    }

    private suspend fun fetch(south: Double, west: Double, north: Double, east: Double): List<Landmark> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bbox = "$south,$west,$north,$east"
                val query = "[out:json][timeout:8];(" +
                    "node[\"name\"][\"amenity\"~\"^(school|place_of_worship|hospital|clinic|pharmacy|marketplace|townhall|police|fire_station)$\"]($bbox);" +
                    "node[\"name\"][\"shop\"]($bbox);" +
                    "node[\"name\"][\"tourism\"~\"^(attraction|museum|viewpoint)$\"]($bbox);" +
                    "node[\"name\"][\"office\"=\"government\"]($bbox);" +
                    ");out body 40;"
                val connection = (URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 8000
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                connection.outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
                if (connection.responseCode != 200) {
                    Log.w(TAG, "Overpass landmark query returned HTTP ${connection.responseCode}")
                    return@runCatching emptyList()
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val elements = JsonParser.parseString(body).asJsonObject.getAsJsonArray("elements") ?: return@runCatching emptyList()
                elements.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val lat = obj.get("lat")?.asDouble ?: return@mapNotNull null
                    val lon = obj.get("lon")?.asDouble ?: return@mapNotNull null
                    val name = obj.getAsJsonObject("tags")?.get("name")?.asString ?: return@mapNotNull null
                    Landmark(name, lat, lon)
                }
            }.onFailure { Log.w(TAG, "Overpass landmark fetch threw", it) }.getOrDefault(emptyList())
        }

    private companion object {
        const val TAG = "OverpassLandmarkRepo"
    }
}
