package com.umuterayaltay.sosyal.nativeapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ConversationScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.FollowListScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.FollowRequestsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.InsightsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.LoginScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.MainScaffold
import com.umuterayaltay.sosyal.nativeapp.ui.screens.NewMessageScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ProfileScreen
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowListKind

private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN = "main"

/**
 * Token yoksa "login", token varsa "main" ile başlar (proaktif doğrulama YOK —
 * bkz. spesifikasyon: ilk API çağrısı 401 dönerse FeedViewModel token'ı
 * temizleyip SessionExpired olayı yayınlar, burada "login"e geri döneriz).
 *
 * Faz 4 (native Android profil ekrani): "profile/{username}", "followers/
 * {username}", "following/{username}", "insights", "followRequests" - hepsi
 * ROUTE_MAIN ile AYNI seviyede, üstüne PUSH edilir (bottom nav'in bu route'larda
 * gizlenmesi standart/beklenen davranis - bunlar MainScaffold'un Scaffold'u
 * DISINDA, kendi Scaffold+TopAppBar+geri oklariyla render edilir).
 *
 * Faz 3'ün SON parçası (native Android mesajlaşma): "conversation/
 * {conversationId}" (bir konuşmayı açar) ve "newMessage" (yeni konuşma başlat)
 * - AYNI desenle ROUTE_MAIN üstüne PUSH edilir. "newMessage" ekranı bir
 * kullanıcı seçilip konuşma get-or-create edilince "conversation/{id}"ye
 * GEÇER (navigate + popUpTo("newMessage") ile kendini yığından çıkarır - geri
 * tuşu "Yeni Mesaj"a değil doğrudan Mesajlar listesine dönsün diye).
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
                navController = navController,
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "profile/{username}",
            arguments = listOf(navArgument("username") { type = NavType.StringType }),
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username")
            ProfileScreen(
                username = username,
                onNavigateToProfile = { u -> navController.navigate("profile/$u") },
                onNavigateToFollowers = { u -> navController.navigate("followers/$u") },
                onNavigateToFollowing = { u -> navController.navigate("following/$u") },
                onNavigateToInsights = { navController.navigate("insights") },
                onNavigateToFollowRequests = { navController.navigate("followRequests") },
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "followers/{username}",
            arguments = listOf(navArgument("username") { type = NavType.StringType }),
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: return@composable
            FollowListScreen(
                username = username,
                kind = FollowListKind.Followers,
                onUserClick = { u -> navController.navigate("profile/$u") },
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "following/{username}",
            arguments = listOf(navArgument("username") { type = NavType.StringType }),
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: return@composable
            FollowListScreen(
                username = username,
                kind = FollowListKind.Following,
                onUserClick = { u -> navController.navigate("profile/$u") },
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("insights") {
            InsightsScreen(
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("followRequests") {
            FollowRequestsScreen(
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "conversation/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            ConversationScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("newMessage") {
            NewMessageScreen(
                onConversationReady = { conversationId ->
                    // "Yeni Mesaj" ekranı geri yığında KALMASIN - geri tuşu
                    // doğrudan Mesajlar listesine dönsün diye kendini çıkarır.
                    navController.navigate("conversation/$conversationId") {
                        popUpTo("newMessage") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}
