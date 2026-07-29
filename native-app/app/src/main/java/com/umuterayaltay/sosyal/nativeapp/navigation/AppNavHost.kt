package com.umuterayaltay.sosyal.nativeapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.ui.screens.LoginScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.MainScaffold

private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN = "main"

/**
 * Token yoksa "login", token varsa "main" ile başlar (proaktif doğrulama YOK —
 * bkz. spesifikasyon: ilk API çağrısı 401 dönerse FeedViewModel token'ı
 * temizleyip SessionExpired olayı yayınlar, burada "login"e geri döneriz).
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val startDestination = if (ServiceLocator.authRepository.isLoggedIn()) ROUTE_MAIN else ROUTE_LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(ROUTE_MAIN) {
            MainScaffold(
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}
