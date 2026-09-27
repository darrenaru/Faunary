package com.faunary.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.ThemeMode
import com.faunary.app.location.GpsAccuracy
import com.faunary.app.location.IosLocationSource
import com.faunary.app.notify.IosInteractionNotifier
import com.faunary.app.notify.NotificationOpen
import com.faunary.app.ui.camera.rememberIosCamera
import com.faunary.app.ui.components.FloatingBottomBar
import com.faunary.app.ui.components.MainTab
import com.faunary.app.ui.detail.CommunityDetailScreen
import com.faunary.app.ui.detail.DetailScreen
import com.faunary.app.ui.gallery.GalleryScreen
import com.faunary.app.ui.journal.JournalScreen
import com.faunary.app.ui.map.MapScreen
import com.faunary.app.ui.navigation.NO_ID
import com.faunary.app.ui.navigation.decodeLatLng
import com.faunary.app.ui.navigation.encodeFix
import com.faunary.app.ui.navigation.encodeLatLng
import com.faunary.app.ui.notifications.NotificationsScreen
import com.faunary.app.ui.notifications.NotificationsViewModel
import com.faunary.app.ui.platform.IosMessages
import com.faunary.app.ui.profile.ProfileScreen
import com.faunary.app.ui.review.ReviewArgs
import com.faunary.app.ui.review.ReviewScreen
import com.faunary.app.ui.theme.FaunaryTheme
import com.faunary.app.util.currentTimeMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// iOS routes, mirroring the Android app's (ui/navigation/Routes.kt).
/** [routeTo] ("lat,lng") + [routeLabel] open the map with an in-app route already drawn. */
@Serializable private data class MapTab(val focusId: Long = NO_ID, val routeTo: String? = null, val routeLabel: String? = null)
@Serializable private data object GalleryTab
@Serializable private data object JournalTab
@Serializable private data object ProfileTab
@Serializable private data class Detail(val id: Long)
/** Mirrors ReviewArgs; [captureFix] is [encodeFix]'d. */
@Serializable private data class Review(val photoPath: String, val capturedAt: Long, val captureFix: String? = null)
@Serializable private data class CommunityDetail(val id: String)
@Serializable private data object Notifications

private fun NavDestination?.tab(): MainTab? = when {
    this == null -> null
    hasRoute<MapTab>() -> MainTab.Map
    hasRoute<GalleryTab>() -> MainTab.Gallery
    hasRoute<JournalTab>() -> MainTab.Journal
    hasRoute<ProfileTab>() -> MainTab.Profile
    else -> null
}

private fun NavHostController.openTab(tab: MainTab) {
    val route: Any = when (tab) {
        MainTab.Map -> MapTab()
        MainTab.Gallery -> GalleryTab
        MainTab.Journal -> JournalTab
        MainTab.Profile -> ProfileTab
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Opens the map as the only screen on the stack, as Android's nav host does. */
private fun NavHostController.openMap(route: MapTab) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Root of the iOS app: the same screens, theme and bottom bar as Android's FaunaryNavHost. */
@Composable
fun FaunaryIosApp() {
    val settingsRepo = koinInject<SettingsRepository>()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    val location = koinInject<IosLocationSource>()
    // The map, distances and finds need the position; ask once, on first launch.
    LaunchedEffect(Unit) { location.requestPermission() }
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    FaunaryTheme(darkTheme = dark) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentTab = backStack?.destination.tab()
        // Turn-by-turn navigation and search on the map take the whole screen, so the tab bar steps aside.
        var mapFullScreen by remember { mutableStateOf(false) }
        val openDetail: (Long) -> Unit = { navController.navigate(Detail(it)) }
        val openCommunity: (String) -> Unit = { navController.navigate(CommunityDetail(it)) }
        val routeOnMap: (Double, Double, String) -> Unit = { lat, lng, label ->
            navController.openMap(MapTab(routeTo = encodeLatLng(lat, lng), routeLabel = label))
        }

        // Camera: GPS runs while it is up, so the shot gets a fresh fix (as on Android's camera screen).
        val scope = rememberCoroutineScope()
        var warmUp by remember { mutableStateOf<Job?>(null) }
        val launchCamera = rememberIosCamera(
            onCaptured = { path ->
                warmUp?.cancel()
                val now = currentTimeMillis()
                val fix = location.lastFix.value?.takeIf { now - it.timeMs <= GpsAccuracy.CAPTURE_MAX_AGE_MS }
                navController.navigate(Review(path, capturedAt = now, captureFix = fix?.let(::encodeFix)))
            },
            onCancelled = { warmUp?.cancel() },
        )
        val openCamera: () -> Unit = {
            warmUp?.cancel()
            warmUp = scope.launch { location.updates().collect {} }
            launchCamera()
        }

        // A tapped notification opens its find (or the inbox), as MainActivity does on Android.
        val notifier = koinInject<IosInteractionNotifier>()
        val notifications: NotificationsViewModel = koinViewModel()
        val unreadNotifications by notifications.unreadCount.collectAsStateWithLifecycle()
        val pendingOpen by notifier.pendingOpen.collectAsStateWithLifecycle()
        LaunchedEffect(pendingOpen) {
            when (val open = pendingOpen) {
                is NotificationOpen.Sighting -> notifications.open(open.sightingId, open.notificationId, openDetail, openCommunity)
                NotificationOpen.Inbox -> navController.navigate(Notifications) { launchSingleTop = true }
                null -> return@LaunchedEffect
            }
            notifier.consumeOpen()
        }

        Box(Modifier.fillMaxSize().background(FaunaryTheme.colors.background)) {
            NavHost(
                navController = navController,
                startDestination = MapTab(),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
                composable<MapTab> { entry ->
                    val args = entry.toRoute<MapTab>()
                    MapScreen(
                        focusId = args.focusId.takeIf { it != NO_ID },
                        routeTo = decodeLatLng(args.routeTo),
                        routeLabel = args.routeLabel,
                        onOpenDetail = openDetail,
                        onOpenCommunity = openCommunity,
                        onOpenCamera = openCamera,
                        onFullScreenChange = { mapFullScreen = it },
                        unreadNotifications = unreadNotifications,
                        onOpenNotifications = { navController.navigate(Notifications) { launchSingleTop = true } },
                    )
                }
                composable<GalleryTab> { GalleryScreen(onOpenDetail = openDetail, onOpenCamera = openCamera) }
                composable<JournalTab> { JournalScreen(onOpenDetail = openDetail, onOpenCamera = openCamera) }
                composable<ProfileTab> { ProfileScreen() }
                composable<Notifications> {
                    NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenOwn = openDetail,
                        onOpenRemote = openCommunity,
                        viewModel = notifications,
                    )
                }
                composable<Review>(
                    enterTransition = { slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250)) },
                ) { entry ->
                    val route = entry.toRoute<Review>()
                    ReviewScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { id -> navController.openMap(MapTab(focusId = id)) },
                        viewModel = koinViewModel { parametersOf(ReviewArgs(route.photoPath, route.capturedAt, route.captureFix)) },
                    )
                }
                composable<Detail>(
                    enterTransition = { slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250)) },
                    popExitTransition = { slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(200)) },
                ) { entry ->
                    val id = entry.toRoute<Detail>().id
                    DetailScreen(
                        onBack = { navController.popBackStack() },
                        onShowOnMap = { navController.openMap(MapTab(focusId = it)) },
                        onRoute = routeOnMap,
                        viewModel = koinViewModel { parametersOf(id) },
                    )
                }
                composable<CommunityDetail>(
                    enterTransition = { slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250)) },
                    popExitTransition = { slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(200)) },
                ) { entry ->
                    val id = entry.toRoute<CommunityDetail>().id
                    CommunityDetailScreen(
                        onBack = { navController.popBackStack() },
                        onRoute = routeOnMap,
                        viewModel = koinViewModel { parametersOf(id) },
                    )
                }
            }

            AnimatedVisibility(
                visible = currentTab != null && !(currentTab == MainTab.Map && mapFullScreen),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
            ) {
                FloatingBottomBar(current = currentTab, onTab = { navController.openTab(it) }, onCamera = openCamera)
            }

            MessageHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** iOS stand-in for Android's Toast: the latest message, briefly, above the bottom bar. */
@Composable
private fun MessageHost(modifier: Modifier) {
    val message by IosMessages.current.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_MS)
            IosMessages.clear()
        }
    }
    AnimatedVisibility(message != null, modifier, enter = fadeIn(tween(150)), exit = fadeOut(tween(200))) {
        val c = FaunaryTheme.colors
        Text(
            message?.first.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = c.background,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 120.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.foreground.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

private const val MESSAGE_MS = 2_000L
