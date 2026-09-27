package com.faunary.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faunary.app.ui.notifications.NotificationsScreen
import com.faunary.app.ui.notifications.NotificationsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.faunary.app.ui.camera.CameraScreen
import com.faunary.app.ui.components.FloatingBottomBar
import com.faunary.app.ui.components.MainTab
import com.faunary.app.ui.detail.CommunityDetailScreen
import com.faunary.app.ui.detail.DetailScreen
import com.faunary.app.ui.gallery.GalleryScreen
import com.faunary.app.ui.journal.JournalScreen
import com.faunary.app.ui.map.MapScreen
import com.faunary.app.ui.profile.ProfileScreen
import com.faunary.app.ui.review.ReviewScreen

private fun NavDestination?.tab(): MainTab? = when {
    this == null -> null
    hasRoute<MapRoute>() -> MainTab.Map
    hasRoute<GalleryRoute>() -> MainTab.Gallery
    hasRoute<JournalRoute>() -> MainTab.Journal
    hasRoute<ProfileRoute>() -> MainTab.Profile
    else -> null
}

private fun NavHostController.openTab(tab: MainTab) {
    val route: Any = when (tab) {
        MainTab.Map -> MapRoute()
        MainTab.Gallery -> GalleryRoute
        MainTab.Journal -> JournalRoute
        MainTab.Profile -> ProfileRoute
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun FaunaryNavHost(
    navController: NavHostController = rememberNavController(),
    /** Tapped system notification to open; cleared once handled. */
    notificationOpen: MutableStateFlow<NotificationOpen?> = MutableStateFlow(null),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = backStack?.destination.tab()
    // Turn-by-turn navigation on the map takes the whole screen, so the tab bar steps aside.
    var mapNavigating by remember { mutableStateOf(false) }
    val openDetail: (Long) -> Unit = { navController.navigate(DetailRoute(it)) }
    // Single top: a quick double tap must not stack two cameras.
    val openCamera: () -> Unit = { navController.navigate(CameraRoute) { launchSingleTop = true } }
    val routeOnMap: (Double, Double, String) -> Unit = { lat, lng, label ->
        navController.navigate(MapRoute(routeTo = encodeLatLng(lat, lng), routeLabel = label)) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val notifications: NotificationsViewModel = hiltViewModel()
    val unreadNotifications by notifications.unreadCount.collectAsStateWithLifecycle()
    val openCommunity: (String) -> Unit = { navController.navigate(CommunityDetailRoute(it)) }
    val pendingOpen by notificationOpen.collectAsState()
    LaunchedEffect(pendingOpen) {
        when (val open = pendingOpen) {
            is NotificationOpen.Sighting -> notifications.open(open.sightingId, open.notificationId, openDetail, openCommunity)
            NotificationOpen.Inbox -> navController.navigate(NotificationsRoute) { launchSingleTop = true }
            null -> return@LaunchedEffect
        }
        notificationOpen.value = null
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = MapRoute(),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable<MapRoute> { entry ->
                val args = entry.toRoute<MapRoute>()
                MapScreen(
                    focusId = args.focusId.takeIf { it != NO_ID },
                    routeTo = decodeLatLng(args.routeTo),
                    routeLabel = args.routeLabel,
                    onOpenDetail = openDetail,
                    onOpenCommunity = openCommunity,
                    onOpenCamera = openCamera,
                    onNavigatingChange = { mapNavigating = it },
                    unreadNotifications = unreadNotifications,
                    onOpenNotifications = { navController.navigate(NotificationsRoute) { launchSingleTop = true } },
                )
            }
            composable<GalleryRoute> { GalleryScreen(onOpenDetail = openDetail, onOpenCamera = openCamera) }
            composable<JournalRoute> { JournalScreen(onOpenDetail = openDetail, onOpenCamera = openCamera) }
            composable<ProfileRoute> { ProfileScreen() }
            composable<NotificationsRoute> {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenOwn = openDetail,
                    onOpenRemote = openCommunity,
                )
            }

            composable<CameraRoute>(
                enterTransition = { slideInVertically(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(200)) },
            ) {
                CameraScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoReady = { path, fix ->
                        navController.navigate(ReviewRoute(path, capturedAt = System.currentTimeMillis(), captureFix = fix?.let(::encodeFix))) {
                            popUpTo<CameraRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<ReviewRoute>(
                enterTransition = { slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250)) },
            ) {
                ReviewScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(MapRoute(focusId = id)) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<DetailRoute>(
                enterTransition = { slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(200)) },
            ) {
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    onShowOnMap = { id ->
                        navController.navigate(MapRoute(focusId = id)) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onRoute = routeOnMap,
                )
            }
            composable<CommunityDetailRoute>(
                enterTransition = { slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(200)) },
            ) {
                CommunityDetailScreen(onBack = { navController.popBackStack() }, onRoute = routeOnMap)
            }
        }

        AnimatedVisibility(
            visible = currentTab != null && !(currentTab == MainTab.Map && mapNavigating),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        ) {
            FloatingBottomBar(
                current = currentTab,
                onTab = { navController.openTab(it) },
                onCamera = openCamera,
            )
        }
    }
}
