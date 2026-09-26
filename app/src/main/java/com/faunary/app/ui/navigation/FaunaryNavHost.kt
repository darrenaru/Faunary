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
import androidx.compose.runtime.getValue
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
import com.faunary.app.ui.detail.DetailScreen
import com.faunary.app.ui.gallery.GalleryScreen
import com.faunary.app.ui.journal.JournalScreen
import com.faunary.app.ui.map.MapScreen
import com.faunary.app.ui.picker.LocationPickerScreen
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
fun FaunaryNavHost(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = backStack?.destination.tab()
    val openDetail: (Long) -> Unit = { navController.navigate(DetailRoute(it)) }
    val openCamera: () -> Unit = { navController.navigate(CameraRoute) }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = MapRoute(),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            composable<MapRoute> { entry ->
                val focus = entry.toRoute<MapRoute>().focusId.takeIf { it != NO_ID }
                MapScreen(focusId = focus, onOpenDetail = openDetail, onOpenCamera = openCamera)
            }
            composable<GalleryRoute> { GalleryScreen(onOpenDetail = openDetail, onOpenCamera = openCamera) }
            composable<JournalRoute> { JournalScreen(onOpenDetail = openDetail, onOpenCamera = openCamera) }
            composable<ProfileRoute> { ProfileScreen() }

            composable<CameraRoute>(
                enterTransition = { slideInVertically(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                popExitTransition = { slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(200)) },
            ) {
                CameraScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoReady = { path, lat, lng ->
                        val exif = if (lat != null && lng != null) encodeLatLng(lat, lng) else null
                        navController.navigate(ReviewRoute(path, exif)) {
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
                    onPickLocation = { start -> navController.navigate(LocationPickerRoute(start)) },
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
                    onEditLocation = { start -> navController.navigate(LocationPickerRoute(start)) },
                )
            }
            composable<LocationPickerRoute> { entry ->
                LocationPickerScreen(
                    start = decodeLatLng(entry.toRoute<LocationPickerRoute>().start),
                    onBack = { navController.popBackStack() },
                    onPicked = { lat, lng ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_LOCATION_KEY, encodeLatLng(lat, lng))
                        navController.popBackStack()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = currentTab != null,
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
