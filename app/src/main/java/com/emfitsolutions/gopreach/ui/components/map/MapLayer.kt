package com.emfitsolutions.gopreach.ui.components.map

/** "Add a feature for the user to select what he wants to see specifically"
 * — a single-choice filter both [TomTomBoundaryMap] and the Leaflet
 * fallback's own boundary map offer as their own picker, narrowing which
 * landmark pins/labels draw. The boundary tint, street lines, and building
 * footprints are never gated by this — they're base map context, not a
 * "kind of place" someone is choosing to see or hide. */
enum class MapLayer(val label: String) {
    ALL("All"),
    LANDMARKS("Landmarks"),
    CHURCHES("Churches"),
    KINGDOM_HALL("Kingdom Hall"),
    STREET_NAMES("Street Names"),
}
