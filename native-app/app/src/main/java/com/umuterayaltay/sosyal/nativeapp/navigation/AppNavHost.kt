package com.umuterayaltay.sosyal.nativeapp.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.CallPhase
import com.umuterayaltay.sosyal.nativeapp.ui.screens.OneOnOneCallScreen
import java.net.URLDecoder
import java.net.URLEncoder
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ActiveSessionsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.BlockedUsersScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.CallScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.CloseFriendsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ConversationScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.CreatePostScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.EditProfileScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.FollowListScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.FollowRequestsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.GroupCreateScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.GroupManageScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.HashtagScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.InsightsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.LoginScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.MainScaffold
import com.umuterayaltay.sosyal.nativeapp.ui.screens.NewMessageScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.NotificationPreferencesScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.NotificationsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.PostDetailScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ProfileScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.RegisterScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.SettingsScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.TrendingScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.TwoFactorScreen
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowListKind
import com.umuterayaltay.sosyal.nativeapp.ui.screens.ForgotPasswordScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.MessageSearchScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.StoryViewerScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.StoryCreateScreen
import com.umuterayaltay.sosyal.nativeapp.ui.screens.HighlightsScreen
// FAZ5_IMPORTS_MARKER — her ajan kendi ekran import'unu bu satırın HEMEN
// ÜSTÜNE ekler (dalga başına ayrı ajan çakışmasın diye).

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
 * Faz 4 (native Android grup sohbeti): "groupCreate" ("newMessage" ile AYNI
 * PUSH+popUpTo(inclusive) deseni, InboxScreen'in "Yeni Grup" ikonundan) ve
 * "groupManage/{conversationId}" (ConversationScreen'in SADECE grup
 * konuşmalarında görünen "Grubu Yönet" ikonundan - conversationId burada
 * composable'ın KENDİ backStackEntry'sinden, ConversationScreen'in kendi
 * ViewModel state'inden DEĞİL, bu yüzden onManageGroupClick parametresiz
 * kalabiliyor). Gruptan ayrılma BAŞARILI olunca Inbox'a döner - conversation
 * VE groupManage ekranları da yığından TEMİZLENİR (artık erişilemez bir
 * konuşma), onDeactivated/onSessionExpired'daki AYNI popUpTo(ROUTE_MAIN,
 * inclusive) deseni.
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
 * Kullanıcı engelleme (Faz 4 sonrası eksik giderme, native Android): mevcut
 * "profile/{username}" ekranının TopAppBar'ındaki (SADECE !isSelf iken görünen)
 * üç-nokta menüsünden POST /block/{username} toggle'ı doğrudan çağrılır - AYRI
 * bir route GEREKMEZ. "blockedUsers" - "closeFriends" ile AYNI basit PUSH
 * deseniyle "settings"in "Engellenen Kullanıcılar" satırından erişilir.
 *
 * Aktif Oturumlar (Faz 4 sonrası eksik giderme SONUNCUSU, native Android):
 * "activeSessions" - "blockedUsers" ile AYNI basit PUSH deseniyle "settings"in
 * "Aktif Oturumlar" satırından erişilir. Web'in "Aktif Oturumlar" (user_sessions,
 * tarayıcı) özelliğinin KAVRAMSAL karşılığı ama FARKLI bir mekanizma - native'in
 * KENDİ bearer token sistemi (api_tokens) üzerinde çalışır, "hesabına giriş
 * yapmış BAŞKA NATIVE CİHAZLAR" listesi gösterir (bkz. ApiModels.kt SessionDto
 * notu). Bu ekrandan çıkış navigasyonu YOK (KENDİ oturumunu bu yoldan
 * kapatamazsın, backend 400 use_logout döner - normal "Çıkış Yap" kullanılmalı).
 *
 * Faz 4 (native Android auth genişletme: kayıt ol + Google ile giriş + 2FA):
 * "register" - ROUTE_LOGIN'in YANINDA, AYNI seviyede (ROUTE_MAIN üstüne PUSH
 * DEĞİL, henüz oturum yok). LoginScreen'deki "Kayıt ol" linkinden erişilir,
 * başarılı kayıt onLoginSuccess ile AYNI davranışı (ROUTE_MAIN'e navigate +
 * ROUTE_LOGIN'i popUpTo(inclusive) ile temizleme) çağırır. 2FA (mfa_required)
 * ve Google Sign-In (Credential Manager) akışları AYRI bir route GEREKTİRMEZ -
 * LoginScreen kendi içinde (AuthViewModel.LoginUiState.NeedsCode) bir alt-ekran
 * olarak render eder.
 *
 * Bildirimler ekranı (native Android liste görüntüleme - backend zaten
 * commit 55833b2'de tamamlandı): "notifications" - "followRequests"/"insights"
 * ile AYNI basit PUSH deseniyle FeedScreen'in TopAppBar'ındaki zil ikonundan
 * erişilir. NotificationsScreen'in 5 navigasyon callback'i burada gerçek
 * route'lara bağlanır: post_id -> "postDetail/{id}", username -> "profile/
 * {username}", conversation_id -> "conversation/{id}", hashtag -> "hashtag/
 * {tag}", type=="follow_request" -> ZATEN VAR olan "followRequests".
 *
 * Hashtag detay + gündem (Faz 4 sonrası eksik giderme SONUNCUSU, native
 * Android): "hashtag/{tag}" - Feed/Discover/Profil/PostDetail'deki PostCard'ın
 * içeriğindeki #etiket'lere, DiscoverScreen'in hashtag arama sonuçlarına,
 * NotificationsScreen'in hashtag_post satırlarına ve TrendingScreen'in etiket
 * satırlarına tıklanınca ROUTE_MAIN üstüne PUSH edilir (postDetail/{postId}
 * ile AYNI basit desen). "trending" - FeedScreen'in TopAppBar'ındaki yeni "#"
 * ikonundan erişilir, bir etikete tıklanınca "hashtag/{tag}"e gider.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val startDestination = if (ServiceLocator.authRepository.isLoggedIn()) ROUTE_MAIN else ROUTE_LOGIN

    // 1:1 sesli/görüntülü arama (native görev — WebRTC + Supabase Realtime
    // broadcast, LiveKit grup aramasından TAMAMEN AYRI): `calls:<meId>`
    // dinleyicisi burada, NavHost'un KÖK seviyesinde, oturum açıkken SÜREKLİ
    // başlatılır (görev notu — "gelen arama uygulamanın HANGİ ekranında
    // olursa olsun yakalanabilmeli", web'in call.js'inin sayfa-geneli
    // davranışıyla AYNI). CallSessionManager.startGlobalListening() idempotent
    // olduğu için AppNavHost'un recomposition'larında tekrar tekrar
    // çağrılması ZARARSIZ.
    LaunchedEffect(Unit) {
        if (ServiceLocator.authRepository.isLoggedIn()) {
            val me = ServiceLocator.authRepository.getCurrentUser()
            me?.id?.let { ServiceLocator.callSessionManager.startGlobalListening(it) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate("register") },
                onNavigateToForgotPassword = { navController.navigate("forgotPassword") },
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
                onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
                // Faz 5 Dalga 4B — "highlights/{userId}" route'u Dalga 2C'de zaten
                // hazırdı, SADECE ProfileScreen'den bağlanmıyordu (bkz. aşağıdaki
                // composable("highlights/{userId}") üzerindeki eski yorum).
                onNavigateToHighlights = { userId -> navController.navigate("highlights/$userId") },
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
        composable("notifications") {
            NotificationsScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToPost = { postId -> navController.navigate("postDetail/$postId") },
                onNavigateToProfile = { username -> navController.navigate("profile/$username") },
                onNavigateToConversation = { conversationId -> navController.navigate("conversation/$conversationId") },
                onNavigateToFollowRequests = { navController.navigate("followRequests") },
                onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
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
                onManageGroupClick = { navController.navigate("groupManage/$conversationId") },
                // Faz 5 Dalga 1B: ConversationScreen'in taşma menüsündeki
                // "Tüm sohbetlerde ara" öğesinden GLOBAL arama ekranına gider.
                onNavigateToMessageSearch = { navController.navigate("messageSearch") },
                // Madde 9 (kullanıcı raporu: paylaşılan post'a tıklanmıyor) -
                // PostDetailScreen'in ZATEN var olan "postDetail/{postId}"
                // route'u REUSE edilir (postDetail/{postId}'nin diğer çağrı
                // yerleriyle AYNI desen, bkz. yukarıdaki "profile/{username}"
                // composable'ındaki onNavigateToPostDetail).
                onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                // Grup sesli/görüntülü arama (native görev — LiveKit) —
                // "conversation/{conversationId}" route'unun HEMEN yanında,
                // AYNI conversationId argüman deseniyle tanımlı "call/
                // {conversationId}" route'una gider (bkz. aşağıdaki
                // composable("call/{conversationId}")). SADECE ConversationScreen'in
                // TopAppBar'ındaki (isGroup==true iken görünen) arama ikonundan
                // erişilir.
                onCallClick = { navController.navigate("call/$conversationId") },
                // 1:1 sesli/görüntülü arama (native görev — WebRTC + Supabase
                // Realtime broadcast) — "call/{conversationId}"nin (grup, LiveKit)
                // HEMEN yanında, AYRI "oneOnOneCall/..." rotasına gider. Tıklanan
                // ikon her zaman GÖRÜNTÜLÜ arama başlatır (isVideo=true path
                // segmenti sabit "true") — arama SIRASINDA sesliye geçiş
                // (kamerayı kapatma) OneOnOneCallScreen'in kontrol çubuğundaki
                // kamera aç/kapat butonuyla yapılır, ama SESLİ başlayıp SONRADAN
                // görüntülüye geçiş (web'in upgrade-offer'ı) bu turun kapsamı
                // DIŞI (görev notu) — bu yüzden tek ikon EN ESNEK varsayılan
                // olarak video ile başlıyor (MVP kararı, rapor edildi).
                onOneOnOneCallClick = { otherUserId, otherName, otherAvatarUrl ->
                    val encodedName = URLEncoder.encode(otherName, "UTF-8")
                    val encodedAvatar = URLEncoder.encode(otherAvatarUrl ?: "_", "UTF-8")
                    navController.navigate("oneOnOneCall/$conversationId/$otherUserId/true/$encodedName/$encodedAvatar")
                },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "call/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            CallScreen(
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
        composable("groupCreate") {
            GroupCreateScreen(
                onGroupCreated = { conversationId ->
                    // "newMessage"in AYNI deseni - "Yeni Grup" ekranı geri
                    // yığında KALMASIN, geri tuşu doğrudan Mesajlar'a dönsün.
                    navController.navigate("conversation/$conversationId") {
                        popUpTo("groupCreate") { inclusive = true }
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
            route = "groupManage/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            GroupManageScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.navigateUp() },
                onLeftGroup = {
                    // Gruptan ayrılınca hem "groupManage" hem altındaki
                    // "conversation/{id}" artık erişilemez - ikisi de ROUTE_MAIN
                    // popUpTo(inclusive) ile temizlenir, onDeactivated/
                    // onSessionExpired ile AYNI navigasyon davranışı.
                    navController.navigate(ROUTE_MAIN) {
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
        composable(
            route = "postDetail/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable
            PostDetailScreen(
                postId = postId,
                onNavigateBack = { navController.navigateUp() },
                onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
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
                onNavigateToBlockedUsers = { navController.navigate("blockedUsers") },
                onNavigateToActiveSessions = { navController.navigate("activeSessions") },
                onDeactivated = {
                    // onSessionExpired ile AYNI navigasyon hedefi - kullanıcı burada
                    // BİLEREK çıktı, oturumu dışarıdan geçersizleşmedi (bkz. yukarıdaki
                    // dosya docstring'i).
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
                onLoggedOut = {
                    // onDeactivated/onSessionExpired ile AYNI navigasyon hedefi -
                    // normal "Çıkış Yap" (daha önce uygulamada HİÇ BİR YERE
                    // bağlanmamıştı - AuthViewModel.logout()/AuthRepository.logout()
                    // zaten vardı ama hiçbir UI elemanı çağırmıyordu, bu satır o
                    // eksikliği giderir).
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
        composable("blockedUsers") {
            BlockedUsersScreen(
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("activeSessions") {
            ActiveSessionsScreen(
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "hashtag/{tag}",
            arguments = listOf(navArgument("tag") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tag = backStackEntry.arguments?.getString("tag") ?: return@composable
            HashtagScreen(
                tag = tag,
                onNavigateBack = { navController.navigateUp() },
                onNavigateToPostDetail = { postId -> navController.navigate("postDetail/$postId") },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("trending") {
            TrendingScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToHashtag = { tag -> navController.navigate("hashtag/$tag") },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }

        composable("forgotPassword") {
            ForgotPasswordScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }

        composable("messageSearch") {
            MessageSearchScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToConversation = { conversationId ->
                    navController.navigate("conversation/$conversationId")
                },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }

        // Faz 5 Dalga 2C: Hikayeler — FeedScreen'in hikaye çubuğundan
        // ("storyViewer/{userId}") ve oradaki "+" / StoryBar'ın "Hikaye Ekle"
        // öğesinden ("storyCreate") erişilir. "highlights/{userId}" artık
        // ProfileScreen'in highlight-bar'ından bağlanıyor (Dalga 4B, yukarıdaki
        // composable("profile/{username}") içindeki onNavigateToHighlights).
        // "highlightView/{highlightId}" HighlightsScreen'in KENDİ içinde
        // kullanılıyor (dıştan doğrudan navigate edilmiyor).
        composable(
            route = "storyViewer/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            StoryViewerScreen(
                userId = userId,
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable("storyCreate") {
            StoryCreateScreen(
                onNavigateBack = { navController.navigateUp() },
                onStoryCreated = {
                    // Kullanıcı raporu: hikaye paylaşıldıktan sonra feed'deki hikaye
                    // çubuğu yenilenmiyordu (StoryBarViewModel Feed sekmesinde
                    // korunduğu için sadece bir kez `init { loadBar() }` çağırıyor).
                    // Compose Navigation'ın standart "geri dönüşte sinyal" deseni:
                    // previousBackStackEntry (ROUTE_MAIN'in kendisi) üzerine bir
                    // savedStateHandle bayrağı bırakılır, MainScaffold bunu
                    // navController.currentBackStackEntryAsState() ile dinleyip
                    // StoryBarViewModel.loadBar()'ı tetikler (bkz. MainScaffold.kt).
                    navController.previousBackStackEntry?.savedStateHandle?.set("story_created", true)
                    navController.navigateUp()
                },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "highlights/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            HighlightsScreen(
                userId = userId,
                onNavigateBack = { navController.navigateUp() },
                onSessionExpired = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }

        // 1:1 sesli/görüntülü arama (native görev) — arayan tarafın rotası,
        // ConversationScreen'in YENİ (isGroup==false iken görünen) arama
        // ikonundan gelir. otherName/otherAvatar path segment olarak
        // URL-encode edilip taşınır (rest of the codebase gibi SADECE path
        // segment kullanılıyor, query-string YOK — tutarlılık için).
        composable(
            route = "oneOnOneCall/{conversationId}/{otherUserId}/{isVideo}/{otherName}/{otherAvatar}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType },
                navArgument("isVideo") { type = NavType.BoolType },
                navArgument("otherName") { type = NavType.StringType },
                navArgument("otherAvatar") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val conversationId = args?.getString("conversationId") ?: return@composable
            val otherUserId = args.getString("otherUserId") ?: return@composable
            val isVideo = args.getBoolean("isVideo")
            val otherName = args.getString("otherName")?.let { URLDecoder.decode(it, "UTF-8") }
            val otherAvatarRaw = args.getString("otherAvatar")?.let { URLDecoder.decode(it, "UTF-8") }
            OneOnOneCallScreen(
                conversationId = conversationId,
                otherUserId = otherUserId,
                isVideo = isVideo,
                otherName = otherName,
                otherAvatar = otherAvatarRaw?.takeIf { it != "_" },
                onNavigateBack = { navController.navigateUp() },
            )
        }
        // Aranan tarafın rotası — SADECE gelen-arama overlay'inin "Kabul et"
        // butonundan gelir, argüman TAŞIMAZ (offer/otherId/conversationId
        // zaten CallSessionManager'da bekliyor, bkz. o sınıfın
        // pendingOfferSdp'si + IncomingRinging fazı).
        composable("oneOnOneCallIncoming") {
            OneOnOneCallScreen(onNavigateBack = { navController.navigateUp() })
        }

        // FAZ5_COMPOSABLE_MARKER — her ajan kendi composable("route") { ... }
        // bloğunu bu satırın HEMEN ÜSTÜNE ekler (dalga başına ayrı ajan
        // çakışmasın diye).
    }

        // Gelen-arama global overlay — NavHost'un ÜSTÜNDE (hangi route
        // gösteriliyorsa gösterilsin üstte belirir), CallSessionManager.phase
        // IncomingRinging olduğu sürece görünür.
        IncomingCallOverlay(
            onAccept = { navController.navigate("oneOnOneCallIncoming") },
        )
    }
}

/** Kabul/reddet — Reddet DOĞRUDAN CallSessionManager.rejectIncoming()'i
 * çağırır (navigasyon YOK, kullanıcı bulunduğu ekranda kalır); Kabul et
 * "oneOnOneCallIncoming" rotasına navigate eder, gerçek WebRTC accept
 * akışı OneOnOneCallScreen'de (izin verildikten SONRA) tetiklenir. */
@Composable
private fun IncomingCallOverlay(onAccept: () -> Unit) {
    val phase by ServiceLocator.callSessionManager.phase.collectAsState()
    val incoming = phase as? CallPhase.IncomingRinging ?: return
    // "Kabul et" navigasyonu ATINCA phase hâlâ bir süre IncomingRinging kalır
    // (WebRTC kurulumu asenkron, bkz. CallSessionManager.acceptIncoming) — bu
    // overlay o kısa pencerede YENİ navigate edilen OneOnOneCallScreen'in
    // ÜSTÜNDE görünmeye devam ederdi, ikinci bir dokunuş (Kabul et'e tekrar
    // basıp navigasyonu TEKRAR tetiklemek ya da o sırada Reddet'e basmak) hâlâ
    // MÜMKÜN olurdu. `remember(incoming)` her YENİ gelen arama için sıfırlanan
    // yerel bir "zaten ele alındı" bayrağıyla, ilk dokunuştan sonra overlay
    // KENDİSİNİ gizler (phase'in gerçekten değişmesini BEKLEMEDEN).
    var handled by remember(incoming) { mutableStateOf(false) }
    if (handled) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!incoming.callerAvatar.isNullOrBlank()) {
                AsyncImage(
                    model = incoming.callerAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Text(
                text = incoming.callerName,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = if (incoming.isVideo) "Görüntülü arama..." else "Sesli arama...",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                IconButton(
                    onClick = {
                        handled = true
                        ServiceLocator.callSessionManager.rejectIncoming()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "Reddet")
                }
                IconButton(
                    onClick = {
                        handled = true
                        onAccept()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Kabul et")
                }
            }
        }
    }
}
