package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
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
                        // Uc grafik AYNI eksen degil ama her biri PostCard'daki
                        // ilgili aksiyonun rengiyle eslesiyor (beğeni=error/
                        // kalp rengi, yeni takipci=primary/marka rengi) -
                        // yorumlar icin notr secondary (teal) kullanildi.
                        DailyBarChartSection("Beğeniler", insights.likesByDay ?: emptyList(), MaterialTheme.colorScheme.error)
                    }
                    item {
                        DailyBarChartSection("Yorumlar", insights.commentsByDay ?: emptyList(), MaterialTheme.colorScheme.secondary)
                    }
                    item {
                        DailyBarChartSection("Yeni Takipçiler", insights.followersByDay ?: emptyList(), MaterialTheme.colorScheme.primary)
                    }
                    if (!insights.topPosts.isNullOrEmpty()) {
                        item {
                            Text(
                                text = "En Çok Etkileşim Alan Gönderiler",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        itemsIndexed(insights.topPosts, key = { _, post -> post.id }) { index, post ->
                            TopPostRow(rank = index + 1, post = post)
                        }
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
            val isSelected = selected == d
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(d) },
                label = { Text("$d gün") },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun StatsSection(insights: InsightsResponse) {
    val rows = buildList {
        add("Gönderi" to insights.totalPosts.toString())
        add("Beğeni" to insights.totalLikes.toString())
        add("Yorum" to insights.totalComments.toString())
        add("Takipçi" to insights.totalFollowers.toString())
        add("Takip Edilen" to insights.totalFollowing.toString())
        add("Ort. Etkileşim" to insights.avgEngagement.toString())
        if (insights.mostActiveDay != null) {
            add("En Aktif Gün" to insights.mostActiveDay)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                StatRow(label, value)
                if (index < rows.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
private val CHART_BAR_SHAPE = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)

/**
 * Gorsel cila: her cubuk artik tam yukseklikte hafif bir "track" (zemin)
 * uzerinde oturuyor - boylece maksimum olcek gorsel olarak da okunuyor
 * (onceki halde sadece cubuklarin kendisi vardi, bos alan referans
 * saglamiyordu). [barColor] cagiran tarafindan verilir (bkz. cagri
 * yerlerindeki renk-anlam eslemesi notu).
 */
@Composable
private fun DailyBarChartSection(title: String, data: List<DailyCountDto>, barColor: Color) {
    if (data.isEmpty()) return
    val maxCount = (data.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

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
                        .fillMaxHeight()
                        .clip(CHART_BAR_SHAPE)
                        .background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((CHART_MAX_HEIGHT * fraction).coerceAtLeast(3.dp))
                            .background(color = barColor, shape = CHART_BAR_SHAPE),
                    )
                }
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
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TopPostRow(rank: Int, post: TopPostDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            RankBadge(rank)
            Column(modifier = Modifier.padding(start = 10.dp)) {
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
}
