package com.umuterayaltay.sosyal.nativeapp.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.twilio.audioswitch.AudioDevice

/**
 * Arama ses çıkışı (hoparlör/kulaklık/Bluetooth) seçim butonu — 1:1 (WebRTC,
 * OneOnOneCallScreen) VE grup (LiveKit, CallScreen) aramada AYNI şekilde
 * kullanılır. İkisi de altta AYNI io.livekit.android.audio.AudioSwitchHandler
 * soyutlamasına (com.twilio:audioswitch) dayandığı için tek bir paylaşılan
 * bileşen — cihaz listesi/ikon eşlemesi İKİ YERDE tekrar yazılmasın diye
 * (bkz. WebRtcCallManager.kt ve CallViewModel.kt'deki AudioSwitchHandler
 * kullanımı).
 *
 * Diğer arama kontrol butonlarıyla (CallControlButton) AYNI 56dp/yuvarlak
 * stil — ama tek dokunuşla aç/kapat DEĞİL, dokununca mevcut cihazları
 * listeleyen bir DropdownMenu açar (genelde 2-4 seçenek, tam bir bottom
 * sheet gereksiz).
 */
@Composable
fun RowScope.AudioOutputButton(
    availableDevices: List<AudioDevice>,
    selectedDevice: AudioDevice?,
    onSelectDevice: (AudioDevice) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { if (availableDevices.isNotEmpty()) menuExpanded = true },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White,
        ),
        modifier = Modifier
            .clip(CircleShape)
            .size(56.dp),
    ) {
        Icon(audioDeviceIcon(selectedDevice), contentDescription = "Ses çıkışını değiştir")
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            availableDevices.forEach { device ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = audioDeviceLabel(device),
                            fontWeight = if (device == selectedDevice) {
                                androidx.compose.ui.text.font.FontWeight.Bold
                            } else {
                                androidx.compose.ui.text.font.FontWeight.Normal
                            },
                        )
                    },
                    leadingIcon = { Icon(audioDeviceIcon(device), contentDescription = null) },
                    onClick = {
                        onSelectDevice(device)
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

/** [AudioDevice] alt tipleri veri sınıfı (BluetoothHeadset/WiredHeadset/vb.)
 * — tip eşleşmesi `is` ile yapılır, `device.name` sadece ekran/cihaz adı
 * (ör. "AirPods Pro") taşır, TÜRÜ değil. */
private fun audioDeviceIcon(device: AudioDevice?): ImageVector = when (device) {
    is AudioDevice.BluetoothHeadset -> Icons.Filled.Bluetooth
    is AudioDevice.WiredHeadset -> Icons.Filled.Headset
    is AudioDevice.Speakerphone -> Icons.Filled.VolumeUp
    is AudioDevice.Earpiece, null -> Icons.Filled.Phone
}

private fun audioDeviceLabel(device: AudioDevice): String = when (device) {
    is AudioDevice.BluetoothHeadset -> device.name.ifBlank { "Bluetooth" }
    is AudioDevice.WiredHeadset -> "Kulaklık"
    is AudioDevice.Speakerphone -> "Hoparlör"
    is AudioDevice.Earpiece -> "Telefon"
}
