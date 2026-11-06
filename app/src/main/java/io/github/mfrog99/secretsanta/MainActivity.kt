package io.github.mfrog99.secretsanta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.mfrog99.secretsanta.data.AppContainer
import io.github.mfrog99.secretsanta.ui.AssignmentScreen
import io.github.mfrog99.secretsanta.ui.AuthScreen
import io.github.mfrog99.secretsanta.ui.HomeScreen
import io.github.mfrog99.secretsanta.ui.ROOM_ID_ARG
import io.github.mfrog99.secretsanta.ui.RoomScreen
import io.github.mfrog99.secretsanta.ui.SecretSantaTheme
import io.github.mfrog99.secretsanta.ui.WishlistScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecretSantaTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val status by AppContainer.authRepository.sessionStatus.collectAsStateWithLifecycle()
    when (status) {
        is SessionStatus.Initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is SessionStatus.NotAuthenticated -> AuthScreen()
        // RefreshFailure keeps the saved session: most often it just means no network right now.
        else -> AppNavigation()
    }
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    val roomArg = listOf(navArgument(ROOM_ID_ARG) { type = NavType.StringType })

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpenRoom = { nav.navigate("room/$it") { launchSingleTop = true } })
        }
        composable("room/{$ROOM_ID_ARG}", roomArg) { entry ->
            val roomId = entry.arguments?.getString(ROOM_ID_ARG).orEmpty()
            RoomScreen(
                onBack = { nav.popBackStack() },
                onWishlist = { nav.navigate("wishlist/$roomId") },
                onAssignment = { nav.navigate("assignment/$roomId") },
            )
        }
        composable("wishlist/{$ROOM_ID_ARG}", roomArg) {
            WishlistScreen(onBack = { nav.popBackStack() })
        }
        composable("assignment/{$ROOM_ID_ARG}", roomArg) {
            AssignmentScreen(onBack = { nav.popBackStack() })
        }
    }
}
