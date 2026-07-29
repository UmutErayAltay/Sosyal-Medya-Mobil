package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.network.DailyCountDto
import com.umuterayaltay.sosyal.nativeapp.network.InsightsResponse
import com.umuterayaltay.sosyal.nativeapp.network.TopPostDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.InsightsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.InsightsViewModel

/**
 * Kendi profil istatistikleri. Web tarafi gibi YENI bir charting kutuphanesi
 * EKLENMEDI - gunluk sayilar icin en/boy orani count/maxCount olan basit
 * Box'lardan olusan bir "bar chart" yeterli (bkz. spesifikasyon: web'in basit
 * CSS bar chart'iyla AYNI felsefe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: InsightsViewModel = viewModel(),
) {
    val days by viewModel.days.collectAsState()
    val data by viewModel.data.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InsightsEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İstatistikler") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && data == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null && data == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }

            data != null -> {
                val insights = data!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        DaySelectorRow(selected = days, onSelect = viewModel::onDaysChange)
                    }
                    item {
                        StatsSection(insights)
                    }
                    item {
                        DailyBarChartSection("Beğeniler", insights.likesByDay ?: emptyList())
                    }
                    item {
                        DailyBarChartSection("Yorumlar", insights.commentsByDay ?: emptyList())
                    }
                    item {
                        DailyBarChartSection("Yeni Takipçiler", insights.followersByDay ?: emptyList())
                    }
                    if (!insights.topPosts.isNullOrEmpty()) {
                        item {
                            Text(
                                text = "En Çok Etkileşim Alan Gönderiler",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(insights.topPosts, key = { it.id }) { post -> TopPostRow(post) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySelectorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 14, 30).forEach { d ->
            FilterChip(
                selected = selected == d,
                onClick = { onSelect(d) },
                label = { Text("$d gün") },
            )
        }
    }
}

@Composable
private fun StatsSection(insights: InsightsResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatRow("Gönderi", insights.totalPosts.toString())
            StatRow("Beğeni", insights.totalLikes.toString())
            StatRow("Yorum", insights.totalComments.toString())
            StatRow("Takipçi", insights.totalFollowers.toString())
            StatRow("Takip Edilen", insights.totalFollowing.toString())
            StatRow("Ort. Etkileşim", insights.avgEngagement.toString())
            if (insights.mostActiveDay != null) {
                StatRow("En Aktif Gün", insights.mostActiveDay)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

private val CHART_MAX_HEIGHT: Dp = 100.dp

@Composable
private fun DailyBarChartSection(title: String, data: List<DailyCountDto>) {
    if (data.isEmpty()) return
    val maxCount = (data.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_MAX_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { point ->
                val fraction = (point.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((CHART_MAX_HEIGHT * fraction).coerceAtLeast(2.dp))
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
                )
            }
        }
        Text(
            text = "${data.first().date} → ${data.last().date}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TopPostRow(post: TopPostDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = post.content?.takeIf { it.isNotBlank() } ?: "(içeriksiz gönderi)",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "${post.likeCount} beğeni",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${post.commentCount} yorum",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
