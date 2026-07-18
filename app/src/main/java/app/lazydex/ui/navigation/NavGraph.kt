package app.lazydex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.lazydex.ui.addedit.UnifiedAddEditScreen
import app.lazydex.ui.settings.AboutScreen
import app.lazydex.ui.settings.AppearanceScreen
import app.lazydex.ui.settings.DataAndStorageScreen
import app.lazydex.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object MainShellRoute

@Serializable
data class AddEditRoute(val itemId: String? = null)

@Serializable
object SettingsRoute

@Serializable
object AppearanceRoute

@Serializable
object DataAndStorageRoute

@Serializable
object AboutRoute

@Composable
fun LazyDexNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MainShellRoute,
        modifier = modifier
    ) {
        composable<MainShellRoute> {
            MainShellScreen(
                onNavigateToAddItem = {
                    navController.navigate(AddEditRoute(itemId = null))
                },
                onNavigateToEditItem = { itemId ->
                    navController.navigate(AddEditRoute(itemId = itemId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }
        composable<AddEditRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AddEditRoute>()
            UnifiedAddEditScreen(
                itemId = route.itemId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToAppearance = {
                    navController.navigate(AppearanceRoute)
                },
                onNavigateToDataAndStorage = {
                    navController.navigate(DataAndStorageRoute)
                },
                onNavigateToAbout = {
                    navController.navigate(AboutRoute)
                }
            )
        }
        composable<AppearanceRoute> {
            AppearanceScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<DataAndStorageRoute> {
            DataAndStorageScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable<AboutRoute> {
            AboutScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
