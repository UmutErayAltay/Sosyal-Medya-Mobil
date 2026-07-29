package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.ProfileDto
import com.umuterayaltay.sosyal.nativeapp.network.ProfileStatsDto
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileViewModelFactory

private enum class ProfileTab(val label: String) {
    Posts("Gönderiler"),
    Media("Medya"),
    Liked("Beğenilenler"),
    Archived("Arşiv"),
}

/**
 * Profil ekrani - kendi profilim (username=null, alt navigasyondaki "Profil"
 * sekmesi, onNavigateBack=null -> geri oku YOK) veya baskasinin profili
 * (username dolu, "profile/{username}" push route'u, onNavigateBack dolu ->
 * TopAppBar'da geri oku) icin AYNI composable kullanilir.
 *
 * NOT (spesifikasyon sapmasi): onNavigateToProfile parametresi imzada
 * TUTULDU ama bu ekranin govdesinde HICBIR YERDEN cagrilmiyor - kapsam disi
 * birakilan "post'a tiklama" (bkz. spesifikasyon: "Bir post'a tiklaninca
 * simdilik hicbir sey olmasin") yuzunden postun yazarina tiklanabilir bir
 * satir yok; ileride post-yazarina tiklama eklenince kullanilmasi icin hazir
 * birakildi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String?,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToFollowers: (String) -> Unit,
    onNavigateToFollowing: (String) -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToFollowRequests: () -> Unit,
    onSessionExpired: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(username)),
) {
    val profile by viewModel.profile.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val likedPosts by viewModel.likedPosts.collectAsState()
    val archivedPosts by viewModel.archivedPosts.collectAsState()
    val isSelf by viewModel.isSelf.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isPendingRequest by viewModel.isPendingRequest.collectAsState()
    val isPrivate by viewModel.isPrivate.collectAsState()
    val isBlockedByMe by viewModel.isBlockedByMe.collectAsState()
    val isDeactivated by viewModel.isDeactivated.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.username ?: "Profil") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    if (isSelf && !isDeactivated) {
                        IconButton(onClick = onNavigateToFollowRequests) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Takip İstekleri")
                        }
                        IconButton(onClick = onNavigateToInsights) {
                            Icon(Icons.Filled.BarChart, contentDescription = "İstatistikler")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && profile == null -> FullScreenCenter(padding) { CircularProgressIndicator() }

            error != null && profile == null -> FullScreenCenter(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    Button(onClick = { viewModel.loadProfile() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }

            profile == null -> FullScreenCenter(padding) { Text("Profil bulunamadı.") }

            isDeactivated -> DeactivatedProfileContent(padding, profile!!)

            else -> ProfileContent(
                padding = padding,
                profile = profile!!,
                stats = stats,
                isSelf = isSelf,
                isFollowing = isFollowing,
                isPendingRequest = isPendingRequest,
                isPrivate = isPrivate,
                isBlockedByMe = isBlockedByMe,
                posts = posts,
                likedPosts = likedPosts,
                archivedPosts = archivedPosts,
                onToggleFollow = viewModel::toggleFollow,
                onNavigateToFollowers = { profile?.username?.let(onNavigateToFollowers) },
                onNavigateToFollowing = { profile?.username?.let(onNavigateToFollowing) },
            )
        }
    }
}

@Composable
private fun FullScreenCenter(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DeactivatedProfileContent(padding: PaddingValues, profile: ProfileDto) {
    FullScreenCenter(padding) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(profile.avatarUrl, size = 72.dp)
            Text(
                text = profile.username ?: "",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Bu hesap deaktif",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ProfileAvatar(avatarUrl: String?, size: Dp = 64.dp) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Icon(imageVector = Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(size))
    }
}

@Composable
private fun ProfileContent(
    padding: PaddingValues,
    profile: ProfileDto,
    stats: ProfileStatsDto?,
    isSelf: Boolean,
    isFollowing: Boolean,
    isPendingRequest: Boolean,
    isPrivate: Boolean,
    isBlockedByMe: Boolean,
    posts: List<Post>,
    likedPosts: List<Post>,
    archivedPosts: List<Post>,
    onToggleFollow: () -> Unit,
    onNavigateToFollowers: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(ProfileTab.Posts) }
    val hidden = isPrivate && !isSelf && !isFollowing

    val tabs = remember(isSelf) {
        if (isSelf) ProfileTab.entries.toList() else ProfileTab.entries.filterNot { it == ProfileTab.Archived }
    }

    // Medya sekmesi client-side filtrelenir - PostDto.imageUrls (coklu gorsel)
    // repository.Post domain modeline TASINMADI (sadece imageUrl var), bu
    // yuzden burada spesifikasyondaki "imageUrl != null || !imageUrls.isNullOrEmpty()"
    // kontrolunun SADECE imageUrl yarisi uygulanabildi (bkz. rapor sapmasi).
    val mediaPosts = remember(posts) { posts.filter { !it.imageUrl.isNullOrBlank() } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ProfileHeader(
                profile = profile,
                stats = stats,
                isSelf = isSelf,
                isFollowing = isFollowing,
                isPendingRequest = isPendingRequest,
                isBlockedByMe = isBlockedByMe,
                onToggleFollow = onToggleFollow,
                onNavigateToFollowers = onNavigateToFollowers,
                onNavigateToFollowing = onNavigateToFollowing,
            )
        }

        if (hidden) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Bu hesap gizli — gönderileri görmek için takip etmelisin",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
                TabRow(selectedTabIndex = selectedIndex) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }

            val currentPosts = when (selectedTab) {
                ProfileTab.Posts -> posts
                ProfileTab.Media -> mediaPosts
                ProfileTab.Liked -> likedPosts
                ProfileTab.Archived -> archivedPosts
            }

            if (currentPosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Burada henüz bir şey yok.")
                    }
                }
            } else {
                items(currentPosts, key = { "${selectedTab.name}_${it.id}" }) { post -> PostCard(post) }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: ProfileDto,
    stats: ProfileStatsDto?,
    isSelf: Boolean,
    isFollowing: Boolean,
    isPendingRequest: Boolean,
    isBlockedByMe: Boolean,
    onToggleFollow: () -> Unit,
    onNavigateToFollowers: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile.avatarUrl, size = 72.dp)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = profile.username ?: "bilinmeyen", style = MaterialTheme.typography.titleLarge)
                if (!profile.fullName.isNullOrBlank()) {
                    Text(
                        text = profile.fullName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!profile.bio.isNullOrBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (stats != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn(label = "Gönderi", value = stats.posts)
                StatColumn(label = "Takipçi", value = stats.followers, onClick = onNavigateToFollowers)
                StatColumn(label = "Takip", value = stats.following, onClick = onNavigateToFollowing)
                StatColumn(label = "Beğeni", value = stats.likes)
            }
        }

        if (!isSelf) {
            when {
                isBlockedByMe -> OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("Engellendi") }

                isFollowing -> OutlinedButton(
                    onClick = onToggleFollow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("Takip Ediliyor") }

                isPendingRequest -> OutlinedButton(
                    onClick = onToggleFollow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("İstek Gönderildi") }

                else -> Button(
                    onClick = onToggleFollow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("Takip Et") }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Int, onClick: (() -> Unit)? = null) {
    Column(
        modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "$value", style = MaterialTheme.typography.titleMedium)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
