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

/** What kind of place a [Landmark] is — drives which icon/color the map
 * draws for it (a school pin looks nothing like a police station pin) so
 * the map reads as real, categorized places rather than identical dots. */
enum class LandmarkCategory(val emoji: String, val colorArgb: Int) {
    SCHOOL("🏫", 0xFF7B1FA2.toInt()),
    WORSHIP("⛪", 0xFF6D4C41.toInt()),
    HEALTH("🏥", 0xFFD32F2F.toInt()),
    PHARMACY("💊", 0xFF00897B.toInt()),
    MARKET("🛒", 0xFFF57C00.toInt()),
    SHOP("🏬", 0xFFEF6C00.toInt()),
    GOVERNMENT("🏛", 0xFF455A64.toInt()),
    POLICE("🚓", 0xFF1565C0.toInt()),
    FIRE("🚒", 0xFFC62828.toInt()),
    TOURISM("📷", 0xFF2E7D32.toInt()),
    OTHER("📍", 0xFF616161.toInt()),
}

/** One named point of interest within a boundary's bounding box — a real
 * landmark (school, church, market, clinic, shop, ...) rather than a
 * synthetic label, so "the map" reads as an actual place and not just a
 * satellite photo or a bare polygon. [category] is derived from whichever
 * OSM tag actually matched in [OverpassLandmarkRepository.classify]. */
data class Landmark(val name: String, val lat: Double, val lng: Double, val category: LandmarkCategory)

/** One real road, as the ordered (lat, lng) points tracing its actual path —
 * drawn as its own line, not flattened into a point cloud like
 * [OverpassStreetRepository.nearbyStreetPoints] does for its own (different)
 * convex-hull-shape use case. [name] is the road's own OSM "name" tag
 * ("Maharlika Highway", "Burgos St.", ...) so the map can label it instead
 * of just drawing an anonymous line — null for unnamed ways (alleys,
 * driveways, ...), which still draw, just unlabeled. */
data class StreetSegment(val points: List<Pair<Double, Double>>, val name: String? = null)

data class MapDetails(val landmarks: List<Landmark>, val streets: List<StreetSegment>)

/**
 * Live, best-effort landmark + street lookup for a boundary's bounding box —
 * same public Overpass (OpenStreetMap) API and same "never block, never
 * error, just come back empty" spirit as [OverpassStreetRepository], the
 * existing precedent for calling this exact service from this app. Exists
 * because the native TomTom map's own vector "Standard"/"Driving" styles
 * have no road, building, or label data at all for the rural provinces this
 * app actually serves — confirmed on-device, and confirmed it isn't just a
 * style choice: both of TomTom's own vector styles (same Map Display API,
 * same key) render the identical blank canvas for the same real boundary.
 * Raw satellite imagery alone shows terrain/rooftops but names and draws
 * nothing — neither gives a Service Overseer/Secretary an actual
 * recognizable street or place to go by, so both are drawn here instead,
 * independent of whatever TomTom's own map data does or doesn't have.
 */
@Singleton
class OverpassLandmarkRepository @Inject constructor() {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, MapDetails>()
    private val maxCacheEntries = 40

    /** Up to 40 named landmarks (schools, churches, markets, clinics, shops,
     * government offices, notable attractions) and up to 150 real roads
     * inside ([south],[west])-([north],[east]), or both empty on any
     * failure (offline, timeout, rate limit) or if the area genuinely has
     * none mapped — every caller already treats "nothing found" the same
     * as "didn't fetch," so this never needs to distinguish the two. */
    suspend fun detailsIn(south: Double, west: Double, north: Double, east: Double): MapDetails {
        val key = "${(south * 2000).toInt()},${(west * 2000).toInt()},${(north * 2000).toInt()},${(east * 2000).toInt()}"
        synchronized(cache) { cache[key] }?.let { return it }
        return mutex.withLock {
            synchronized(cache) { cache[key] }?.let { return@withLock it }
            val result = withTimeoutOrNull(10000) { fetch(south, west, north, east) } ?: MapDetails(emptyList(), emptyList())
            synchronized(cache) {
                cache[key] = result
                if (cache.size > maxCacheEntries) cache.remove(cache.keys.first())
            }
            result
        }
    }

    private suspend fun fetch(south: Double, west: Double, north: Double, east: Double): MapDetails =
        withContext(Dispatchers.IO) {
            runCatching {
                val bbox = "$south,$west,$north,$east"
                // Landmarks (named nodes) and streets (highway ways) in one
                // round trip — cheaper than two separate requests against the
                // same bounding box, and this public service is already
                // best shared sparingly.
                val query = "[out:json][timeout:10];(" +
                    "node[\"name\"][\"amenity\"~\"^(school|place_of_worship|hospital|clinic|pharmacy|marketplace|townhall|police|fire_station)$\"]($bbox);" +
                    "node[\"name\"][\"shop\"]($bbox);" +
                    "node[\"name\"][\"tourism\"~\"^(attraction|museum|viewpoint)$\"]($bbox);" +
                    "node[\"name\"][\"office\"=\"government\"]($bbox);" +
                    ");out body 40;" +
                    "way[\"highway\"]($bbox);out geom 150;"
                // overpass-api.de itself started rejecting every request with
                // a bare "406 Not Acceptable" (confirmed both from this app
                // on-device and independently via curl — not a client/query
                // problem, the server rejects even a trivial [out:json];node(1);out;
                // probe). This mirror was confirmed returning real results.
                val connection = (URL(OVERPASS_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                connection.outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
                val code = connection.responseCode
                Log.i(TAG, "Overpass query HTTP $code for bbox $bbox")
                if (code != 200) {
                    val errorBody = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                    Log.w(TAG, "Overpass query returned HTTP $code: ${errorBody?.take(300)}")
                    return@runCatching MapDetails(emptyList(), emptyList())
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val elements = JsonParser.parseString(body).asJsonObject.getAsJsonArray("elements") ?: return@runCatching MapDetails(emptyList(), emptyList())
                val landmarks = elements.mapNotNull { el ->
                    val obj = el.asJsonObject
                    if (obj.get("type")?.asString != "node") return@mapNotNull null
                    val lat = obj.get("lat")?.asDouble ?: return@mapNotNull null
                    val lon = obj.get("lon")?.asDouble ?: return@mapNotNull null
                    val tags = obj.getAsJsonObject("tags") ?: return@mapNotNull null
                    val name = tags.get("name")?.asString ?: return@mapNotNull null
                    Landmark(name, lat, lon, classify(tags))
                }
                val streets = elements.mapNotNull { el ->
                    val obj = el.asJsonObject
                    if (obj.get("type")?.asString != "way") return@mapNotNull null
                    val geometry = obj.getAsJsonArray("geometry") ?: return@mapNotNull null
                    val points = geometry.mapNotNull { g ->
                        val point = g.asJsonObject
                        val lat = point.get("lat")?.asDouble ?: return@mapNotNull null
                        val lon = point.get("lon")?.asDouble ?: return@mapNotNull null
                        lat to lon
                    }
                    val name = obj.getAsJsonObject("tags")?.get("name")?.asString
                    if (points.size >= 2) StreetSegment(points, name) else null
                }
                Log.i(TAG, "Overpass query found ${landmarks.size} landmark(s), ${streets.size} street(s) of ${elements.size()} element(s)")
                MapDetails(landmarks, streets)
            }.onFailure { Log.w(TAG, "Overpass fetch threw", it) }.getOrDefault(MapDetails(emptyList(), emptyList()))
        }

    /** Which [LandmarkCategory] a node's own OSM tags actually describe —
     * checked in the same priority the query itself groups by (a specific
     * `amenity` value first, since it's the most precise tag a POI can
     * carry; a bare `shop`/`tourism` presence last, since those tags only
     * say "this is a shop/attraction", never which kind). */
    private fun classify(tags: com.google.gson.JsonObject): LandmarkCategory {
        val amenity = tags.get("amenity")?.asString
        val office = tags.get("office")?.asString
        return when {
            amenity == "school" -> LandmarkCategory.SCHOOL
            amenity == "place_of_worship" -> LandmarkCategory.WORSHIP
            amenity == "hospital" || amenity == "clinic" -> LandmarkCategory.HEALTH
            amenity == "pharmacy" -> LandmarkCategory.PHARMACY
            amenity == "marketplace" -> LandmarkCategory.MARKET
            amenity == "townhall" -> LandmarkCategory.GOVERNMENT
            amenity == "police" -> LandmarkCategory.POLICE
            amenity == "fire_station" -> LandmarkCategory.FIRE
            office == "government" -> LandmarkCategory.GOVERNMENT
            tags.has("tourism") -> LandmarkCategory.TOURISM
            tags.has("shop") -> LandmarkCategory.SHOP
            else -> LandmarkCategory.OTHER
        }
    }

    private companion object {
        const val TAG = "OverpassLandmarkRepo"
        const val OVERPASS_URL = "https://overpass.openstreetmap.fr/api/interpreter"
    }
}
