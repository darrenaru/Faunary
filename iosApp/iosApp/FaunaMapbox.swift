import MapboxMaps
import Shared
import UIKit

/// Creates Mapbox map views for the shared Kotlin map (shared/src/iosMain/.../ui/map/NativeMap.kt).
/// The Mapbox Maps SDK for iOS is Swift-only, so this thin layer is the only map code in Swift:
/// Kotlin draws the markers and runs the camera logic, this places them on a MapView.
final class FaunaMapFactory: NSObject, NativeMapFactory {
    func createMapView() -> NativeMapView { FaunaMapboxView() }
}

final class FaunaMapboxView: NSObject, NativeMapView {
    private let mapView = MapView(frame: .zero)
    // Created in draw order (bottom to top), as on Android: other explorers' routes, the route, then markers.
    private lazy var sharedRoutes = mapView.annotations.makePolylineAnnotationManager(id: "faunary-shared-routes")
    private lazy var route = mapView.annotations.makePolylineAnnotationManager(id: "faunary-route")
    private lazy var markers: PointAnnotationManager = {
        let manager = mapView.annotations.makePointAnnotationManager(id: "faunary-markers")
        // Kotlin groups overlapping photos into stacks itself; every marker stays visible.
        manager.iconAllowOverlap = true
        manager.iconIgnorePlacement = true
        return manager
    }()
    private var cancelables = Set<AnyCancelable>()

    var listener: NativeMapListener?
    var view: UIView { mapView }

    override init() {
        super.init()
        _ = sharedRoutes
        _ = route
        _ = markers
        mapView.ornaments.options.scaleBar.visibility = .hidden
        mapView.ornaments.options.compass.visibility = .hidden
        mapView.gestures.delegate = self

        mapView.gestures.onMapTap.observe { [weak self] _ in
            self?.listener?.onMapTap()
        }.store(in: &cancelables)
        mapView.gestures.onMapLongPress.observe { [weak self] context in
            self?.listener?.onMapLongPress(latitude: context.coordinate.latitude, longitude: context.coordinate.longitude)
        }.store(in: &cancelables)
        mapView.mapboxMap.onCameraChanged.observe { [weak self] event in
            let camera = event.cameraState
            self?.listener?.onCameraChanged(latitude: camera.center.latitude, longitude: camera.center.longitude, zoom: Double(camera.zoom))
        }.store(in: &cancelables)
        mapView.mapboxMap.onMapIdle.observe { [weak self] _ in
            guard let self, let map = self.mapView.mapboxMap else { return }
            let bounds = map.coordinateBounds(for: CameraOptions(cameraState: map.cameraState))
            self.listener?.onCameraIdle(
                south: bounds.southwest.latitude, west: bounds.southwest.longitude,
                north: bounds.northeast.latitude, east: bounds.northeast.longitude
            )
        }.store(in: &cancelables)
    }

    func setStyleJson(json: String) {
        mapView.mapboxMap.styleJSON = json
    }

    func setCamera(
        latitude: Double, longitude: Double, zoom: Double, bearing: Double, pitch: Double,
        topPadding: Double, bottomPadding: Double, durationMs: Double
    ) {
        let options = CameraOptions(
            center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            padding: UIEdgeInsets(top: topPadding, left: 0, bottom: bottomPadding, right: 0),
            zoom: CGFloat(zoom),
            bearing: bearing,
            pitch: CGFloat(pitch)
        )
        if durationMs <= 0 {
            mapView.mapboxMap.setCamera(to: options)
        } else {
            mapView.camera.ease(to: options, duration: durationMs / 1000)
        }
    }

    func fitCoordinates(coordinates: [KotlinDouble], top: Double, left: Double, bottom: Double, right: Double, pitch: Double, maxZoom: Double) {
        let points = Self.coordinates(coordinates)
        guard !points.isEmpty else { return }
        let base = CameraOptions(padding: UIEdgeInsets(top: top, left: left, bottom: bottom, right: right), pitch: CGFloat(pitch))
        guard let camera = try? mapView.mapboxMap.camera(for: points, camera: base, coordinatesPadding: nil, maxZoom: maxZoom, offset: nil) else { return }
        mapView.camera.ease(to: camera, duration: 0.8)
    }

    func cameraState() -> [KotlinDouble] {
        let camera = mapView.mapboxMap.cameraState
        return [camera.center.latitude, camera.center.longitude, Double(camera.zoom), camera.bearing, Double(camera.pitch)]
            .map { KotlinDouble(value: $0) }
    }

    func setMarkers(markers list: [NativeMarker]) {
        markers.annotations = list.map { marker in
            var annotation = PointAnnotation(id: marker.key, coordinate: CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude))
            annotation.image = .init(image: marker.image, name: marker.imageId)
            annotation.iconAnchor = marker.anchorBottom ? .bottom : .center
            annotation.symbolSortKey = marker.sortKey
            let key = marker.key
            annotation.tapHandler = { [weak self] _ in
                self?.listener?.onMarkerTap(key: key)
                return true
            }
            return annotation
        }
    }

    func setRoute(coordinates: [KotlinDouble], color: String) {
        let points = Self.coordinates(coordinates)
        route.annotations = points.count < 2 ? [] : [Self.line(points, color: color, width: 6)]
    }

    func setSharedRoutes(routes: [[KotlinDouble]], color: String) {
        sharedRoutes.annotations = routes.map(Self.coordinates).filter { $0.count >= 2 }.map { Self.line($0, color: color, width: 4) }
    }

    func setUserLocationVisible(visible: Bool) {
        mapView.location.options.puckType = visible ? .puck2D(.makeDefault(showBearing: true)) : nil
        mapView.location.options.puckBearing = .heading
        mapView.location.options.puckBearingEnabled = visible
    }

    func setOrnamentBottomMargin(points: Double) {
        let margins = CGPoint(x: 8, y: points + 8)
        mapView.ornaments.options.logo.margins = margins
        mapView.ornaments.options.attributionButton.margins = margins
    }

    /// Flattened lat,lng pairs from Kotlin to coordinates.
    private static func coordinates(_ values: [KotlinDouble]) -> [CLLocationCoordinate2D] {
        let numbers = values.map { $0.doubleValue }
        return stride(from: 0, to: numbers.count - 1, by: 2).map {
            CLLocationCoordinate2D(latitude: numbers[$0], longitude: numbers[$0 + 1])
        }
    }

    private static func line(_ points: [CLLocationCoordinate2D], color: String, width: Double) -> PolylineAnnotation {
        var line = PolylineAnnotation(lineCoordinates: points)
        line.lineColor = StyleColor(UIColor(hex: color))
        line.lineWidth = width
        line.lineJoin = .round
        return line
    }
}

extension FaunaMapboxView: GestureManagerDelegate {
    func gestureManager(_ gestureManager: GestureManager, didBegin gestureType: GestureType) {
        if gestureType == .pan { listener?.onUserPan() }
    }

    func gestureManager(_ gestureManager: GestureManager, didEnd gestureType: GestureType, willAnimate: Bool) {}

    func gestureManager(_ gestureManager: GestureManager, didEndAnimatingFor gestureType: GestureType) {}
}

private extension UIColor {
    /// "#RRGGBB" as sent from Kotlin.
    convenience init(hex: String) {
        let value = UInt32(hex.dropFirst(), radix: 16) ?? 0
        self.init(
            red: CGFloat((value >> 16) & 0xFF) / 255,
            green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255,
            alpha: 1
        )
    }
}
