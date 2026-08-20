package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Uygulama kilidi (2026-08-21) — soğuk başlangıçta, kullanıcı Ayarlar'dan
 * kilidi açmışsa MainActivity'nin `isUnlocked` state'i `false` iken
 * AppNavHost YERİNE bu ekran gösterilir. `LaunchedEffect(Unit)` ile prompt
 * İLK açılışta OTOMATİK tetiklenir (kullanıcı önce bir butona basmak zorunda
 * kalmaz) — biyometrik diyalog iptal edilir/başarısız olursa kullanıcı
 * "Kilidi Aç" butonuyla elle tekrar deneyebilir (sonsuz döngüde otomatik
 * tekrar deneme YOK, rahatsız edici olurdu).
 */
@Composable
fun AppLockScreen(onUnlockClick: () -> Unit) {
    LaunchedEffect(Unit) { onUnlockClick() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Uygulama kilitli", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Devam etmek için kimliğini doğrula.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onUnlockClick) {
            Text("Kilidi Aç")
        }
    }
}
