package com.faunary.app.ui.map

/**
 * 2D: a minimal Mapbox style built on streets-v8 tiles with the warm, low-saturation palette
 * (cream land, muted parks, soft water, low-contrast roads, few labels). Light on data and battery.
 *
 * 3D: Mapbox Standard imported as the basemap for its 3D buildings (with façades), landmarks and
 * trees, recoloured with the same palette, plus terrain. Day light in the light theme, dusk light in
 * the dark theme (night light turns everything near-black outside lit city centres).
 *
 * Always uses the globe projection: zoomed far out the map becomes a 3D globe (with a soft
 * atmosphere and country labels), and it flattens back to a regular map around zoom 5–6.
 */
object MapStyle {
    private data class Palette(
        val land: String, val park: String, val water: String, val building: String,
        val road: String, val roadMajor: String, val roadCasing: String,
        val label: String, val labelHalo: String, val placeLabel: String,
        val extrusion: String,
        val fogHigh: String, val space: String,
        val border: String, val countryLabel: String, val stars: Double,
    )

    private val light = Palette(
        land = "#F5EDE0", park = "#D8DEB2", water = "#C8D9E8", building = "#EDE2D2",
        road = "#FBF8F1", roadMajor = "#F3E3CF", roadCasing = "#E5D7C6",
        label = "#7A6658", labelHalo = "#F5EDE0", placeLabel = "#4A3023",
        extrusion = "#E2D2BC",
        fogHigh = "#F7D89A", space = "#D8E2F0",
        border = "#C9B49C", countryLabel = "#6B5444", stars = 0.0,
    )

    private val dark = Palette(
        land = "#29231F", park = "#35382A", water = "#2B3640", building = "#332A24",
        road = "#3A302A", roadMajor = "#4A3C31", roadCasing = "#221C18",
        label = "#AFA094", labelHalo = "#29231F", placeLabel = "#F5EDE0",
        extrusion = "#54443A",
        fogHigh = "#40342B", space = "#1E1814",
        border = "#5A4A3E", countryLabel = "#CDBFB2", stars = 0.25,
    )

    fun json(darkTheme: Boolean, threeD: Boolean = false): String {
        val p = if (darkTheme) dark else light
        return if (threeD) standard3D(p, darkTheme) else flat(p)
    }

    /** Fog is also the globe's atmosphere and the colour of space around it, so both styles use it. */
    private fun globe(p: Palette) = """
          "projection": { "name": "globe" },
          "fog": { "range": [1, 12], "color": "${p.land}", "high-color": "${p.fogHigh}", "horizon-blend": 0.12, "space-color": "${p.space}", "star-intensity": ${p.stars} },"""

    private fun standard3D(p: Palette, darkTheme: Boolean): String = """
        {
          "version": 8,
          "name": "Faunary 3D",${globe(p)}
          "imports": [
            {
              "id": "basemap",
              "url": "mapbox://styles/mapbox/standard",
              "config": {
                "lightPreset": "${if (darkTheme) "dusk" else "day"}",
                "font": "Inter",
                "show3dObjects": true,
                "show3dBuildings": true,
                "show3dFacades": true,
                "show3dLandmarks": true,
                "show3dTrees": true,
                "showLandmarkIcons": true,
                "showPlaceLabels": true,
                "showRoadLabels": true,
                "showPointOfInterestLabels": false,
                "showTransitLabels": false,
                "colorLand": "${p.land}",
                "colorWater": "${p.water}",
                "colorGreenspace": "${p.park}",
                "colorCommercial": "${p.building}",
                "colorEducation": "${p.building}",
                "colorMedical": "${p.building}",
                "colorIndustrial": "${p.building}",
                "colorRoads": "${p.road}",
                "colorTrunks": "${p.roadMajor}",
                "colorMotorways": "${p.roadMajor}",
                "colorBuildings": "${p.extrusion}",
                "colorPlaceLabels": "${p.placeLabel}",
                "colorRoadLabels": "${p.label}",
                "colorAdminBoundaries": "${p.border}"
              }
            }
          ],
          "sources": {
            "mapbox-dem": { "type": "raster-dem", "url": "mapbox://mapbox.mapbox-terrain-dem-v1", "tileSize": 512, "maxzoom": 14 }
          },
          "terrain": { "source": "mapbox-dem", "exaggeration": 1.4 },
          "layers": []
        }
        """.trimIndent()

    private fun flat(p: Palette): String {
        val flatBuildings = """
            { "id": "building", "type": "fill", "source": "streets", "source-layer": "building", "minzoom": 14,
              "paint": { "fill-color": "${p.building}", "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0, 16, 0.8] } },"""

        return """
        {
          "version": 8,
          "name": "Faunary Warm",
          "glyphs": "mapbox://fonts/mapbox/{fontstack}/{range}.pbf",${globe(p)}
          "sources": {
            "streets": { "type": "vector", "url": "mapbox://mapbox.mapbox-streets-v8" }
          },
          "layers": [
            { "id": "land", "type": "background", "paint": { "background-color": "${p.land}" } },
            { "id": "landuse-park", "type": "fill", "source": "streets", "source-layer": "landuse",
              "filter": ["match", ["get", "class"], ["park", "grass", "wood", "scrub", "pitch", "cemetery", "agriculture", "golf_course"], true, false],
              "paint": { "fill-color": "${p.park}", "fill-opacity": 0.9 } },
            { "id": "national-park", "type": "fill", "source": "streets", "source-layer": "landuse_overlay",
              "paint": { "fill-color": "${p.park}", "fill-opacity": 0.6 } },
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
