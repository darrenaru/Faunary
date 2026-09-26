package com.faunary.app.ui.map

/**
 * A minimal Mapbox style built on streets-v8 tiles with the warm, low-saturation palette
 * (cream land, muted parks, soft water, low-contrast roads, few labels).
 *
 * In 3D mode it adds terrain + hillshade, extruded buildings and a warm sky/fog.
 *
 * Always uses the globe projection: zoomed far out the map becomes a 3D globe (with a soft
 * atmosphere and country labels), and it flattens back to a regular map around zoom 5–6.
 */
object MapStyle {
    private data class Palette(
        val land: String, val park: String, val water: String, val building: String,
        val road: String, val roadMajor: String, val roadCasing: String,
        val label: String, val labelHalo: String, val placeLabel: String,
        val extrusion: String, val hillShadow: String, val hillHighlight: String,
        val fogHigh: String, val space: String,
        val border: String, val countryLabel: String, val stars: Double,
    )

    private val light = Palette(
        land = "#F5EDE0", park = "#D8DEB2", water = "#C8D9E8", building = "#EDE2D2",
        road = "#FBF8F1", roadMajor = "#F3E3CF", roadCasing = "#E5D7C6",
        label = "#7A6658", labelHalo = "#F5EDE0", placeLabel = "#4A3023",
        extrusion = "#E2D2BC", hillShadow = "#7A4E28", hillHighlight = "#FBF8F1",
        fogHigh = "#F7D89A", space = "#D8E2F0",
        border = "#C9B49C", countryLabel = "#6B5444", stars = 0.0,
    )

    private val dark = Palette(
        land = "#29231F", park = "#35382A", water = "#2B3640", building = "#332A24",
        road = "#3A302A", roadMajor = "#4A3C31", roadCasing = "#221C18",
        label = "#AFA094", labelHalo = "#29231F", placeLabel = "#F5EDE0",
        extrusion = "#54443A", hillShadow = "#120E0B", hillHighlight = "#655246",
        fogHigh = "#40342B", space = "#1E1814",
        border = "#5A4A3E", countryLabel = "#CDBFB2", stars = 0.25,
    )

    fun json(darkTheme: Boolean, threeD: Boolean = false): String {
        val p = if (darkTheme) dark else light

        val demSource = if (threeD) {
            // Separate DEM sources: sharing one between terrain and hillshade halves hillshade resolution.
            """, "mapbox-dem": { "type": "raster-dem", "url": "mapbox://mapbox.mapbox-terrain-dem-v1", "tileSize": 512, "maxzoom": 14 },
            "hillshade-dem": { "type": "raster-dem", "url": "mapbox://mapbox.mapbox-terrain-dem-v1", "tileSize": 512, "maxzoom": 14 }"""
        } else ""

        // Lights stay neutral white (ambient 0.8 + sun 0.2): tinted light would recolour every
        // flat layer and push the cream palette towards yellow.
        // Fog is also the globe's atmosphere and the colour of space around it, so it's on in 2D too.
        val globe = """
          "projection": { "name": "globe" },
          "fog": { "range": [1, 12], "color": "${p.land}", "high-color": "${p.fogHigh}", "horizon-blend": 0.12, "space-color": "${p.space}", "star-intensity": ${p.stars} },"""

        val atmosphere = if (threeD) {
            """
          "terrain": { "source": "mapbox-dem", "exaggeration": 1.4 },
          "lights": [
            { "id": "ambient", "type": "ambient", "properties": { "color": "#FFFFFF", "intensity": 0.8 } },
            { "id": "sun", "type": "directional", "properties": { "color": "#FFFFFF", "intensity": 0.2,
              "direction": [210, 40], "cast-shadows": true, "shadow-intensity": ${if (darkTheme) 0.5 else 0.35} } }
          ],"""
        } else ""

        val hillshade = if (threeD) {
            """
            { "id": "hillshade", "type": "hillshade", "source": "hillshade-dem",
              "paint": { "hillshade-shadow-color": "${p.hillShadow}", "hillshade-highlight-color": "${p.hillHighlight}",
                         "hillshade-accent-color": "${p.hillShadow}", "hillshade-exaggeration": ${if (darkTheme) 0.35 else 0.25} } },"""
        } else ""

        // Flat footprints in 2D; extruded, softly lit blocks in 3D (drawn above roads, below labels).
        val flatBuildings = if (threeD) "" else """
            { "id": "building", "type": "fill", "source": "streets", "source-layer": "building", "minzoom": 14,
              "paint": { "fill-color": "${p.building}", "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0, 16, 0.8] } },"""
        val extrudedBuildings = if (!threeD) "" else """
            { "id": "building-3d", "type": "fill-extrusion", "source": "streets", "source-layer": "building", "minzoom": 14,
              "filter": ["==", ["get", "extrude"], "true"],
              "paint": {
                "fill-extrusion-color": "${p.extrusion}",
                "fill-extrusion-height": ["interpolate", ["linear"], ["zoom"], 14, 0, 15.5, ["max", ["get", "height"], 4]],
                "fill-extrusion-base": ["interpolate", ["linear"], ["zoom"], 14, 0, 15.5, ["get", "min_height"]],
                "fill-extrusion-opacity": 0.92,
                "fill-extrusion-cast-shadows": true,
                "fill-extrusion-vertical-gradient": true
              } },"""

        return """
        {
          "version": 8,
          "name": "Faunary Warm",
          "glyphs": "mapbox://fonts/mapbox/{fontstack}/{range}.pbf",$globe$atmosphere
          "sources": {
            "streets": { "type": "vector", "url": "mapbox://mapbox.mapbox-streets-v8" }$demSource
          },
          "layers": [
            { "id": "land", "type": "background", "paint": { "background-color": "${p.land}" } },
            { "id": "landuse-park", "type": "fill", "source": "streets", "source-layer": "landuse",
              "filter": ["match", ["get", "class"], ["park", "grass", "wood", "scrub", "pitch", "cemetery", "agriculture", "golf_course"], true, false],
              "paint": { "fill-color": "${p.park}", "fill-opacity": 0.9 } },
            { "id": "national-park", "type": "fill", "source": "streets", "source-layer": "landuse_overlay",
              "paint": { "fill-color": "${p.park}", "fill-opacity": 0.6 } },$hillshade
            { "id": "water", "type": "fill", "source": "streets", "source-layer": "water",
              "paint": { "fill-color": "${p.water}" } },
            { "id": "waterway", "type": "line", "source": "streets", "source-layer": "waterway",
              "paint": { "line-color": "${p.water}", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 1, 16, 4] } },
            { "id": "admin-country", "type": "line", "source": "streets", "source-layer": "admin",
              "filter": ["all", ["==", ["get", "admin_level"], 0], ["==", ["get", "maritime"], "false"], ["match", ["get", "worldview"], ["all", "US"], true, false]],
              "layout": { "line-join": "round" },
              "paint": { "line-color": "${p.border}", "line-width": ["interpolate", ["linear"], ["zoom"], 1, 0.6, 8, 1.6],
                         "line-opacity": ["interpolate", ["linear"], ["zoom"], 8, 1, 11, 0] } },
$flatBuildings
            { "id": "road-minor", "type": "line", "source": "streets", "source-layer": "road", "minzoom": 12,
              "filter": ["match", ["get", "class"], ["street", "street_limited", "service", "secondary_link", "tertiary_link", "primary_link"], true, false],
              "layout": { "line-cap": "round", "line-join": "round" },
              "paint": { "line-color": "${p.road}", "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 12, 0.5, 18, 12] } },
            { "id": "road-path", "type": "line", "source": "streets", "source-layer": "road", "minzoom": 15,
              "filter": ["match", ["get", "class"], ["path", "pedestrian"], true, false],
              "paint": { "line-color": "${p.roadCasing}", "line-width": 1.2, "line-dasharray": [2, 1.5] } },
            { "id": "road-major-casing", "type": "line", "source": "streets", "source-layer": "road",
              "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary", "tertiary"], true, false],
              "layout": { "line-cap": "round", "line-join": "round" },
              "paint": { "line-color": "${p.roadCasing}", "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 8, 1, 18, 22] } },
            { "id": "road-major", "type": "line", "source": "streets", "source-layer": "road",
              "filter": ["match", ["get", "class"], ["motorway", "trunk", "primary", "secondary", "tertiary"], true, false],
              "layout": { "line-cap": "round", "line-join": "round" },
              "paint": { "line-color": "${p.roadMajor}", "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 8, 0.5, 18, 18] } },
$extrudedBuildings
            { "id": "road-label", "type": "symbol", "source": "streets", "source-layer": "road", "minzoom": 14,
              "filter": ["has", "name"],
              "layout": { "symbol-placement": "line", "text-field": ["get", "name"], "text-size": 11,
                          "text-font": ["Inter Medium", "Arial Unicode MS Regular"] },
              "paint": { "text-color": "${p.label}", "text-halo-color": "${p.labelHalo}", "text-halo-width": 1.4 } },
            { "id": "park-label", "type": "symbol", "source": "streets", "source-layer": "poi_label", "minzoom": 14,
              "filter": ["match", ["get", "class"], ["park_like"], true, false],
              "layout": { "text-field": ["get", "name"], "text-size": 11, "text-max-width": 8,
                          "text-font": ["Inter Italic", "Arial Unicode MS Regular"] },
              "paint": { "text-color": "#82985A", "text-halo-color": "${p.labelHalo}", "text-halo-width": 1.2 } },
            { "id": "place-label", "type": "symbol", "source": "streets", "source-layer": "place_label",
              "filter": ["match", ["get", "class"], ["settlement", "settlement_subdivision"], true, false],
              "layout": { "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]], "text-transform": "uppercase", "text-letter-spacing": 0.08,
                          "text-size": ["interpolate", ["linear"], ["zoom"], 10, 10, 16, 13], "text-max-width": 8,
                          "text-font": ["Inter Bold", "Arial Unicode MS Bold"] },
              "paint": { "text-color": "${p.placeLabel}", "text-opacity": 0.7, "text-halo-color": "${p.labelHalo}", "text-halo-width": 1.5 } },
            { "id": "state-label", "type": "symbol", "source": "streets", "source-layer": "place_label", "minzoom": 4, "maxzoom": 8,
              "filter": ["==", ["get", "class"], "state"],
              "layout": { "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]], "text-size": 11, "text-max-width": 8,
                          "text-font": ["Inter Medium", "Arial Unicode MS Regular"] },
              "paint": { "text-color": "${p.countryLabel}", "text-opacity": 0.8, "text-halo-color": "${p.labelHalo}", "text-halo-width": 1.2 } },
            { "id": "country-label", "type": "symbol", "source": "streets", "source-layer": "place_label", "maxzoom": 7,
              "filter": ["==", ["get", "class"], "country"],
              "layout": { "text-field": ["coalesce", ["get", "name_en"], ["get", "name"]], "text-transform": "uppercase", "text-letter-spacing": 0.1,
                          "text-size": ["interpolate", ["linear"], ["zoom"], 1, 10, 6, 14], "text-max-width": 7,
                          "text-font": ["Inter Bold", "Arial Unicode MS Bold"] },
              "paint": { "text-color": "${p.countryLabel}", "text-halo-color": "${p.labelHalo}", "text-halo-width": 1.5 } }
          ]
        }
        """.trimIndent()
    }
}
