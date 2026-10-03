package com.emfitsolutions.gopreach.ui.components.map

import com.google.gson.JsonParser

/**
 * Parses a raw GeoJSON `Polygon`/`MultiPolygon` geometry string — exactly
 * what [com.emfitsolutions.gopreach.data.repository.TerritoryBoundaryRepository]
 * hands to Leaflet's `L.geoJSON()` today — into plain (lat, lng) rings, so
 * both the Leaflet fallback and [com.tomtom.sdk.map.display.polygon.PolygonOptions]
 * can be built from the same bundled data without either renderer's types
 * leaking into this shared parsing code. Only outer rings are kept (holes —
 * a ring after the first in a `Polygon`'s `coordinates` — are dropped), the
 * same simplification the existing boundary dialogs already make by filling
 * the whole polygon at low opacity rather than punching out exclusions.
 */
object BoundaryGeometry {
    /** One ring per outer boundary, each a list of (lat, lng) pairs, GeoJSON's
     * own [lng, lat] order flipped to the (lat, lng) order every Android/Maps
     * API expects. */
    fun outerRings(geometryJson: String): List<List<Pair<Double, Double>>> = runCatching {
        val element = JsonParser.parseString(geometryJson)
        if (!element.isJsonObject) return@runCatching emptyList()
        val obj = element.asJsonObject
        val coordinates = obj.getAsJsonArray("coordinates") ?: return@runCatching emptyList()
        when (obj.get("type")?.asString) {
            "Polygon" -> listOfNotNull(outerRingOf(coordinates))
            "MultiPolygon" -> coordinates.mapNotNull { polygon -> outerRingOf(polygon.asJsonArray) }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun outerRingOf(polygonCoordinates: com.google.gson.JsonArray): List<Pair<Double, Double>>? {
        val outer = polygonCoordinates.firstOrNull()?.asJsonArray ?: return null
        return outer.map { point ->
            val pair = point.asJsonArray
            val lng = pair[0].asDouble
            val lat = pair[1].asDouble
            lat to lng
        }
    }
}
