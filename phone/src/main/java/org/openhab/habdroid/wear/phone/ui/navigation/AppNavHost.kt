package org.openhab.habdroid.wear.phone.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.openhab.habdroid.wear.phone.ui.complications.ComplicationEditorScreen
import org.openhab.habdroid.wear.phone.ui.debug.DebugLogScreen
import org.openhab.habdroid.wear.phone.ui.home.HomeScreen
import org.openhab.habdroid.wear.phone.ui.setup.SetupScreen
import org.openhab.habdroid.wear.phone.ui.setup.SetupViewModel
import org.openhab.habdroid.wear.phone.ui.tiledesign.TileDesignScreen
import org.openhab.habdroid.wear.phone.ui.tiledesign.TileDesignViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
        modifier = modifier,
        enterTransition = { slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300)) },
        exitTransition = { slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(300)) }
    ) {
        composable(NavRoutes.HOME) {
            HomeScreen(
                onNavigateToConnection = { navController.navigate(NavRoutes.CONNECTION) },
                onNavigateToTileDesign = { navController.navigate(NavRoutes.TILE_DESIGN) },
                onNavigateToComplications = { navController.navigate(NavRoutes.COMPLICATIONS) },
                onNavigateToDebugLog = { navController.navigate(NavRoutes.DEBUG_LOG) }
            )
        }

        composable(NavRoutes.CONNECTION) {
            val viewModel: SetupViewModel = hiltViewModel()
            SetupScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.TILE_DESIGN) {
            val viewModel: TileDesignViewModel = hiltViewModel()
            TileDesignScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }

        composable(NavRoutes.COMPLICATIONS) {
            ComplicationEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.DEBUG_LOG) {
            DebugLogScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
