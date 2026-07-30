package com.umuterayaltay.sosyal.nativeapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.ui.screens.CloseFriendsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ConversationScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.CreatePostScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.EditProfileScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.FollowListScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.FollowRequestsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.InsightsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.LoginScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.MainScaffold
import com.umuterayaltay.sosyal.nativeapp.ui.screens.NewMessageScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.NotificationPreferencesScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.PostDetailScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ProfileScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.RegisterScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.SettingsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.TwoFactorScreen
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
 *
 * Faz 4 (native Android beğeni+yorum): "postDetail/{postId}" - Feed/Discover/
 * Reels/Profil'deki PostCard'ın yorum ikonuna veya ReelOverlay'in yorum
 * ikonuna tıklanınca ROUTE_MAIN üstüne PUSH edilir (AYNI desen).
 *
 * Faz 4 (native Android post OLUŞTURMA): "createPost" - FeedScreen'in
 * TopAppBar'ındaki "+" ikonundan ROUTE_MAIN üstüne PUSH edilir. Paylaşım
 * BAŞARILI olunca sadece geri navigasyon yapılır (navigateUp) - Feed'in
 * ANINDA yeni postu göstermesi bu turun kapsamı DIŞI, kullanıcı var olan
 * pull-to-refresh ile görebilir.
 *
 * Faz 4 (native Android profil ayarları): "settings", "editProfile",
 * "notificationPreferences", "closeFriends" - "insights" gibi basit route'lar
 * DESENİYLE ROUTE_MAIN üstüne PUSH edilir, ProfileScreen'in TopAppBar'ındaki
 * (SADECE isSelf iken görünen) "Ayarlar" ikonundan erişilir. "settings"in
 * deaktivasyon BAŞARILI olduğunda çağırdığı onDeactivated, onSessionExpired
 * ile AYNI hedefe (ROUTE_LOGIN, ROUTE_MAIN'i yığından temizleyerek) gider -
 * kavramsal olarak farklı (kullanıcı BİLEREK çıktı) ama navigasyon davranışı
 * özdeş olduğu için AYNI lambda gövdesi kullanılır.
 *
 * Faz 4 (native Android 2FA yönetimi - enroll/disable): "twoFactor" - AYNI
 * PUSH deseniyle "settings"in "Güvenlik (2FA)" satırından erişilir. Login
 * akışının 2FA-KOD İSTEME kısmından (LoginScreen'in NeedsCode alt-ekranı) AYRI
 * bir özellik - bu route SADECE Ayarlar'dan enroll/disable yönetir.
 *
 * Faz 4 (native Android auth genişletme: kayıt ol + Google ile giriş + 2FA):
 * "register" - ROUTE_LOGIN'in YANINDA, AYNI seviyede (ROUTE_MAIN üstüne PUSH
 * DEĞİL, henüz oturum yok). LoginScreen'deki "Kayıt ol" linkinden erişilir,
 * başarılı kayıt onLoginSuccess ile AYNI davranışı (ROUTE_MAIN'e navigate +
 * ROUTE_LOGIN'i popUpTo(inclusive) ile temizleme) çağırır. 2FA (mfa_required)
 * ve Google Sign-In (Credential Manager) akışları AYRI bir route GEREKTİRMEZ -
 * LoginScreen kendi içinde (AuthViewModel.LoginUiState.NeedsCode) bir alt-ekran
 * olarak render eder.
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
                onNavigateToRegister = { navController.navigate("register") },
            )
        }
        composable("register") {
            RegisterScreen(
                onNavigateBack = { navController.navigateUp() },
                onRegisterSuccess = {
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
                onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                onNavigateToSettings = { navController.navigate("settings") },
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
        composable(
            route = "postDetail/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            PostDetailScreen(
                postId = postId,
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("createPost") {
            CreatePostScreen(
                onNavigateBack = { navController.navigateUp() },
                onPostCreated = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToEditProfile = { navController.navigate("editProfile") },
                onNavigateToNotificationPreferences = { navController.navigate("notificationPreferences") },
                onNavigateToCloseFriends = { navController.navigate("closeFriends") },
                onNavigateToTwoFactor = { navController.navigate("twoFactor") },
                onDeactivated = {
                    // onSessionExpired ile AYNI navigasyon hedefi - kullanıcı burada
                    // BİLEREK çıktı, oturumu dışarıdan geçersizleşmedi (bkz. yukarıdaki
                    // dosya docstring'i).
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("editProfile") {
            EditProfileScreen(
                onNavigateBack = { navController.navigateUp() },
                onSaved = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("notificationPreferences") {
            NotificationPreferencesScreen(
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("closeFriends") {
            CloseFriendsScreen(
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("twoFactor") {
            TwoFactorScreen(
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
