package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.Poll
import com.umuterayaltay.sosyal.nativeapp.repository.PollOption
import com.umuterayaltay.sosyal.nativeapp.ui.components.MediaPickerSheet
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryCreateEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryCreateViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private data class StoryVisibilityOption(val value: String, val label: String, val icon: ImageVector)

private val STORY_VISIBILITY_OPTIONS = listOf(
    StoryVisibilityOption("public", "Herkese Açık", Icons.Filled.Public),
    StoryVisibilityOption("followers", "Takipçiler", Icons.Filled.Group),
    StoryVisibilityOption("close_friends", "Yakın Arkadaşlar", Icons.Filled.Star),
)

// Basılı tutma eşiği — bundan KISA bir dokunuş fotoğraf, bundan UZUN bir
// basılı-tutma video kaydı sayılır (Instagram'ın shutter davranışıyla aynı fikir).
private const val LONG_PRESS_THRESHOLD_MS = 250L

// Maksimum hikaye video kayıt süresi — Instagram'daki ~15sn sınırıyla aynı.
private const val MAX_VIDEO_RECORD_MS = 15_000L

/**
 * "Yeni Hikaye" ekranı — app/api_v1/stories.py api_create_story() sözleşmesiyle
 * AYNI BİLİNÇLİ SINIR (bkz. StoryCreateViewModel): caption + (TEK opsiyonel
 * görsel VEYA TEK opsiyonel video, mutually exclusive) + opsiyonel anket (0-4
 * seçenek) + görünürlük.
 *
 * Instagram tarzı iki adımlı akış (kullanıcı talimatıyla form-tabanlı eski
 * halinden dönüştürüldü — bkz. görev notu):
 *   1) Kamera adımı (StoryCameraStep) — ekran AÇILIR AÇILMAZ canlı kamera,
 *      kısa dokunuş=foto, basılı tutma=video (ilerleme halkasıyla), sol altta
 *      galeri kısayolu. "Yazıyla Paylaş" ile medya olmadan da devam edilebilir
 *      (backend caption/anket-only hikayeyi zaten destekliyor, bu yetenek
 *      kaybolmasın diye eklendi — YENİ bir ViewModel state İCAT EDİLMEDİ,
 *      sadece bu ekrana özel yerel bir UI bayrağı).
 *   2) Form adımı (StoryFormStep) — MEVCUT caption/görünürlük/anket editörü,
 *      medya seçildikten (kamera VEYA galeri) veya "Yazıyla Paylaş" sonrası
 *      gösterilir. StoryCreateViewModel'in submit/caption/visibility/poll
 *      mantığına DOKUNULMADI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCreateScreen(
    onNavigateBack: () -> Unit,
    onStoryCreated: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: StoryCreateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val caption by viewModel.caption.collectAsState()
    val visibility by viewModel.visibility.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val pollOptions by viewModel.pollOptions.collectAsState()
    val captionPositionX by viewModel.captionPositionX.collectAsState()
    val captionPositionY by viewModel.captionPositionY.collectAsState()
    val pollPositionX by viewModel.pollPositionX.collectAsState()
    val pollPositionY by viewModel.pollPositionY.collectAsState()
    val pollScale by viewModel.pollScale.collectAsState()
    val overlayImageUrl by viewModel.overlayImageUrl.collectAsState()
    val overlayImagePositionX by viewModel.overlayImagePositionX.collectAsState()
    val overlayImagePositionY by viewModel.overlayImagePositionY.collectAsState()
    val overlayImageScale by viewModel.overlayImageScale.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoryCreateEvent.Success -> onStoryCreated()
                is StoryCreateEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    // Kamera + ses izin durumu — ekranın KENDİSİNDE isteniyor (MainActivity'de
    // DEĞİL), çünkü bu ekrana özel bir izin, her uygulama açılışında istenmemeli.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // Galeri kısayolu — MEVCUT ActivityResultContracts.PickVisualMedia() ile
    // AYNI mekanizma (eski iki-butonlu formdan taşındı), tek bir küçük kısayol
    // butonu olduğu için görsel/video AYRIMI mime type'a bakılarak yapılıyor.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType?.startsWith("video/") == true) {
                viewModel.onVideoSelected(uri)
            } else {
                viewModel.onImageSelected(uri)
            }
        }
    }

    // "Yazıyla Paylaş" — kamera adımını atlayıp doğrudan forma geçmek için
    // yerel UI bayrağı (ViewModel state DEĞİL — sadece bu ekranın adım
    // yönlendirmesi, backend/submit mantığını etkilemiyor).
    var showTextOnlyForm by remember { mutableStateOf(false) }

    // GIF/sticker seçici — CreatePostScreen.kt'deki AYNI desen (MediaPickerSheet
    // paylaşılan bileşen, kendi state'ini tutuyor).
    var showMediaPicker by remember { mutableStateOf(false) }
    val mediaPickerSheetState = rememberModalBottomSheetState()

    val hasSelectedMedia = selectedImageUri != null || selectedVideoUri != null
    val showForm = hasSelectedMedia || showTextOnlyForm

    val filledPollOptionCount = pollOptions.count { it.isNotBlank() }
    val canSubmit = (caption.isNotBlank() || selectedImageUri != null || selectedVideoUri != null ||
        filledPollOptionCount >= 2) && !submitting

    if (showForm) {
        StoryFormStep(
            caption = caption,
            visibility = visibility,
            selectedImageUri = selectedImageUri,
            selectedVideoUri = selectedVideoUri,
            backgroundColor = backgroundColor,
            pollOptions = pollOptions,
            captionPositionX = captionPositionX,
            captionPositionY = captionPositionY,
            pollPositionX = pollPositionX,
            pollPositionY = pollPositionY,
            pollScale = pollScale,
            overlayImageUrl = overlayImageUrl,
            overlayImagePositionX = overlayImagePositionX,
            overlayImagePositionY = overlayImagePositionY,
            overlayImageScale = overlayImageScale,
            submitting = submitting,
            error = error,
            canSubmit = canSubmit,
            onCaptionChange = viewModel::onCaptionChange,
            onVisibilityChange = viewModel::onVisibilityChange,
            onBackgroundColorChange = viewModel::onBackgroundColorChange,
            onCaptionPositionChange = viewModel::onCaptionPositionChange,
            onPollPositionChange = viewModel::onPollPositionChange,
            onPollScaleChange = viewModel::onPollScaleChange,
            onAddOverlayClick = { showMediaPicker = true },
            onRemoveOverlayImage = viewModel::onOverlayImageRemoved,
            onOverlayImagePositionChange = viewModel::onOverlayImagePositionChange,
            onOverlayImageScaleChange = viewModel::onOverlayImageScaleChange,
            onRemoveImage = {
                viewModel.onImageSelected(null)
                showTextOnlyForm = false
            },
            onRemoveVideo = {
                viewModel.onVideoSelected(null)
                showTextOnlyForm = false
            },
            onAddPollOption = viewModel::addPollOption,
            onRemovePollOption = viewModel::removePollOption,
            onPollOptionChange = viewModel::onPollOptionChange,
            onBackToCamera = {
                // Geri/X'e basınca kameraya DÖN — mevcut medya seçimini temizle,
                // metin-only moddan da çık (kullanıcı fikrini değiştirip yeniden
                // çekim/galeri seçimi yapabilsin).
                viewModel.onImageSelected(null)
                viewModel.onVideoSelected(null)
                showTextOnlyForm = false
            },
            onSubmit = { viewModel.submit(context) },
        )
    } else {
        StoryCameraStep(
            hasCameraPermission = hasCameraPermission,
            hasAudioPermission = hasAudioPermission,
            onRequestPermission = {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            },
            onImageCaptured = { uri -> viewModel.onImageSelected(uri) },
            onVideoCaptured = { uri -> viewModel.onVideoSelected(uri) },
            onGalleryClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            onClose = onNavigateBack,
            onSwitchToTextOnly = { showTextOnlyForm = true },
        )
    }

    if (showMediaPicker) {
        MediaPickerSheet(
            sheetState = mediaPickerSheetState,
            onDismiss = { showMediaPicker = false },
            onGifSelected = { url ->
                showMediaPicker = false
                viewModel.onOverlayImageSelected(url)
            },
            onStickerSelected = { sticker ->
                showMediaPicker = false
                viewModel.onOverlayImageSelected(sticker.imageUrl)
            },
        )
    }
}

/**
 * Kamera adımı — Instagram tarzı: tam ekran canlı önizleme + alt ortada
 * shutter (tap=foto, basılı tutma=video) + sol altta galeri kısayolu + sağ
 * üstte kamera değiştir + sol üstte kapat / sağ üstte "Yazıyla Paylaş".
 * Kamera izni yoksa/reddedildiyse önizleme yerine bilgilendirme + galeri
 * yoluna yönlendirme gösterilir (tamamen KİLİTLEMEZ).
 */
@Composable
private fun StoryCameraStep(
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onImageCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onGalleryClick: () -> Unit,
    onClose: () -> Unit,
    onSwitchToTextOnly: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            CameraPreviewAndShutter(
                hasAudioPermission = hasAudioPermission,
                onImageCaptured = onImageCaptured,
                onVideoCaptured = onVideoCaptured,
                onGalleryClick = onGalleryClick,
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "Kamera izni gerekli",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Hikaye çekmek için kamera iznini verin, ya da galeriden bir görsel/video seçin.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRequestPermission) {
                    Text("İzin Ver")
                }
                OutlinedButton(onClick = onGalleryClick) {
                    Text("Galeriden Seç", color = Color.White)
                }
            }
        }

        // Üst bar — sol: kapat, sağ: metin-only kısayolu.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Vazgeç", tint = Color.White)
            }
            TextButton(onClick = onSwitchToTextOnly) {
                Text("Yazıyla Paylaş", color = Color.White)
            }
        }
    }
}

/**
 * CameraX önizleme + shutter — Preview + ImageCapture + VideoCapture<Recorder>
 * AYNI ANDA bağlanmaya çalışılır (foto/video AYNI shutter'dan çekilebilsin).
 * Bazı cihazlar/kalite kombinasyonları üçünü birden desteklemeyebilir — o
 * durumda Preview+ImageCapture'a düşülür, basılı-tutma video kaydı o cihazda
 * sessizce devre dışı kalır (shutter yalnızca foto çeker).
 */
@Composable
private fun CameraPreviewAndShutter(
    hasAudioPermission: Boolean,
    onImageCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onGalleryClick: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isRecording by remember { mutableStateOf(false) }
    var recordProgress by remember { mutableStateOf(0f) }

    // Shutter/flash geri bildirimi — foto cekilince kisa beyaz bir flash
    // fade-out ile (gercek kamera shutter'i hissi). [flashAlpha] ANINDA
    // (snapTo) yuksek bir degere sicrar, SONRA yumusakca 0'a soner.
    val flashAlpha = remember { Animatable(0f) }
    val flashScope = rememberCoroutineScope()

    DisposableEffect(lensFacing) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val newImageCapture = ImageCapture.Builder().build()
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.HD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)),
                )
                .build()
            val newVideoCapture = VideoCapture.withOutput(recorder)
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                // Önce ÜÇÜNÜ BİRDEN bağlamayı dene.
                provider.bindToLifecycle(lifecycleOwner, selector, preview, newImageCapture, newVideoCapture)
                imageCapture = newImageCapture
                videoCapture = newVideoCapture
            } catch (concurrentBindError: Exception) {
                // Cihaz üçünü birden desteklemiyor — foto-only'e düş.
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, newImageCapture)
                    imageCapture = newImageCapture
                    videoCapture = null
                } catch (fallbackError: Exception) {
                    imageCapture = null
                    videoCapture = null
                }
            }
        }, mainExecutor)

        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (_: Exception) {
                // Provider henüz hazır değilse veya lifecycle zaten kapandıysa yut.
            }
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        // Cekim ANINDA (async kaydetme sonucunu BEKLEMEDEN) shutter flash'i
        // tetikle — gercek bir kamera shutter'i gibi ANINDA tepki hissettirir.
        flashScope.launch {
            flashAlpha.snapTo(0.85f)
            flashAlpha.animateTo(0f, animationSpec = tween(250))
        }
        val file = createStoryMediaFile(context, ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onImageCaptured(uriForStoryFile(context, file))
                }

                override fun onError(exception: ImageCaptureException) {
                    // Sessizce yut — MVP kapsamı, kullanıcı tekrar deneyebilir
                    // (shutter tekrar dokunmaya açık kalıyor, ekran kilitlenmiyor).
                }
            },
        )
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        if (activeRecording != null) return
        val file = createStoryMediaFile(context, ".mp4")
        val outputOptions = FileOutputOptions.Builder(file).build()
        var pending = capture.output.prepareRecording(context, outputOptions)
        if (hasAudioPermission) {
            pending = pending.withAudioEnabled()
        }
        isRecording = true
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                isRecording = false
                activeRecording = null
                if (!event.hasError()) {
                    onVideoCaptured(uriForStoryFile(context, file))
                }
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
    }

    // Basılı tutma süresince ilerleme halkasını dolduran döngü — maksimum
    // süreye ulaşınca kaydı otomatik durdurur (Instagram'daki ~15sn sınırı).
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (isRecording) {
                val elapsed = System.currentTimeMillis() - start
                recordProgress = (elapsed.toFloat() / MAX_VIDEO_RECORD_MS).coerceIn(0f, 1f)
                if (elapsed >= MAX_VIDEO_RECORD_MS) {
                    stopRecording()
                    break
                }
                delay(30)
            }
        } else {
            recordProgress = 0f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Kamera değiştir (ön/arka) — sağ üst köşe, opsiyonel bir kolaylık.
        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Icon(Icons.Filled.FlipCameraAndroid, contentDescription = "Kamerayı değiştir", tint = Color.White)
        }

        // Galeri kısayolu — shutter'ın SOLUNDA, küçük bir ikon-buton (gerçek
        // "son çekilen fotoğraf" önizlemesi kapsam dışı bırakıldı, zaman
        // kısıtlaması nedeniyle sabit bir ikon yeterli görüldü).
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 44.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Galeriden seç", tint = Color.White)
        }

        // Shutter — tap: foto, basılı tutma: video (bırakınca durur).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            )
            if (isRecording) {
                RecordingProgressRing(
                    progress = recordProgress,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color.Red else Color.White)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                var startedRecording = false
                                coroutineScope {
                                    val holdJob = launch {
                                        delay(LONG_PRESS_THRESHOLD_MS)
                                        // videoCapture NULL ise cihaz üçlü bağlamayı
                                        // desteklemiyor demektir — basılı tutma foto-only
                                        // cihazda sessizce hiçbir şey yapmaz (elini kaldırınca
                                        // startedRecording false kalacağı için takePhoto çağrılır).
                                        if (videoCapture != null) {
                                            startedRecording = true
                                            startRecording()
                                        }
                                    }
                                    val released = tryAwaitRelease()
                                    holdJob.cancel()
                                    if (startedRecording) {
                                        stopRecording()
                                    } else if (released) {
                                        takePhoto()
                                    }
                                }
                            },
                        )
                    },
            )
        }

        // Shutter flash overlay — HER SEYIN USTUNDE (Box'in SON cocugu),
        // tam ekran beyaz bir katman kisaca belirip soner.
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value)),
            )
        }
    }
}

@Composable
private fun RecordingProgressRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawArc(
            color = Color.Red,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** context.cacheDir/story_media/ altına geçici bir dosya — res/xml/file_paths.xml'deki
 * cache-path("story_media") ile eşleşiyor. */
private fun createStoryMediaFile(context: Context, extension: String): File {
    val dir = File(context.cacheDir, "story_media").apply { mkdirs() }
    return File(dir, "story_${System.currentTimeMillis()}$extension")
}

private fun uriForStoryFile(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * Form adımı — MEVCUT caption/görünürlük/anket editörü (eski tek-adımlı
 * ekranın form kısmı, DEĞİŞTİRİLMEDEN taşındı). Medya seçim butonları
 * KALDIRILDI — bu rol artık StoryCameraStep'e ait, form adımına SADECE
 * medya seçildikten (kamera/galeri) veya "Yazıyla Paylaş" sonrası girilir.
 */
// Web'in feed.html'deki story-bg-swatch paletiyle BİREBİR AYNI 6 renk
// (bkz. app/templates/feed.html satır 302-307) — platform tutarlılığı.
private val STORY_BACKGROUND_SWATCHES = listOf(
    "#7c5cbf", "#e74c3c", "#27ae60", "#2980b9", "#f39c12", "#1a1a2e",
)

/**
 * Form adımı — Instagram tarzı TAM EKRAN canvas editörüne dönüştürüldü
 * (kullanıcı isteği, 2. tur: "fotoğrafı çektik ya da galeriden seçtik,
 * direkt sol kısımda butonlar olsun metin/gif/anket ekleme, ... tüm ekranı
 * fotoğraf kaplasın, üstünde düzenleme yapıyormuşuz gibi" — ilk turdaki
 * Scaffold+scroll form, canvas'ı KÜÇÜK bir 9:16 kutuya sıkıştırıp altına
 * HER ZAMAN görünen "GIF/Sticker Ekle" butonu/caption TextField'ı/poll
 * seçenek listesi ekliyordu, bu tur bunları TAMAMEN kaldırıp yerine
 * dokununca açılan araç çubuğu + modal düzenleyiciler koyuyor).
 *
 * BÜYÜK KEŞİF (ilk turdan): backend (app/api_v1/stories.py) VE web
 * (app/static/js/stories.js) bu sürükle-bırak editörünü ÇOKTAN
 * destekliyordu (caption_position_x/y, poll_position_x/y, poll_scale,
 * background_color) — SIFIRDAN bir canvas motoru İCAT EDİLMEDİ, web'in
 * stories.js'deki pointerdown/pointermove/pointerup deseni Compose'un
 * detectTransformGestures'ı (pan+zoom TEK gesture detector'da,
 * FullscreenImageViewer.kt'deki AYNI kurulan desen) ile mirror'landı —
 * bu tur SADECE ÇEVRE (chrome/layout) değişti, DraggableStoryElement/
 * pozisyon-veri modeli AYNEN korundu (canvas artık 9:16 kutu değil TÜM
 * ekran).
 *
 * Metnin GERÇEK İÇERİĞİ artık her zaman görünen bir TextField'da DEĞİL,
 * "Aa" araç butonuna dokununca açılan [StoryTextEditorOverlay]'de
 * yazılıyor — Instagram'ın "canvas üzerinde direkt yaz" hissine daha
 * yakın (tam ekran klavye + ortalanmış büyük yazı alanı), ama backend
 * sözleşmesi AYNI kaldı (tek caption alanı).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryFormStep(
    caption: String,
    visibility: String,
    selectedImageUri: Uri?,
    selectedVideoUri: Uri?,
    backgroundColor: String?,
    pollOptions: List<String>,
    captionPositionX: Float,
    captionPositionY: Float,
    pollPositionX: Float,
    pollPositionY: Float,
    pollScale: Float,
    overlayImageUrl: String?,
    overlayImagePositionX: Float,
    overlayImagePositionY: Float,
    overlayImageScale: Float,
    submitting: Boolean,
    error: String?,
    canSubmit: Boolean,
    onCaptionChange: (String) -> Unit,
    onVisibilityChange: (String) -> Unit,
    onBackgroundColorChange: (String?) -> Unit,
    onCaptionPositionChange: (Float, Float) -> Unit,
    onPollPositionChange: (Float, Float) -> Unit,
    onPollScaleChange: (Float) -> Unit,
    onAddOverlayClick: () -> Unit,
    onRemoveOverlayImage: () -> Unit,
    onOverlayImagePositionChange: (Float, Float) -> Unit,
    onOverlayImageScaleChange: (Float) -> Unit,
    onRemoveImage: () -> Unit,
    onRemoveVideo: () -> Unit,
    onAddPollOption: () -> Unit,
    onRemovePollOption: (Int) -> Unit,
    onPollOptionChange: (Int, String) -> Unit,
    onBackToCamera: () -> Unit,
    onSubmit: () -> Unit,
) {
    val hasMedia = selectedImageUri != null || selectedVideoUri != null
    val hasPoll = pollOptions.count { it.isNotBlank() } >= 2
    var showTextEditor by remember { mutableStateOf(false) }
    var showPollEditor by remember { mutableStateOf(false) }
    val pollEditorSheetState = rememberModalBottomSheetState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ---- Tam ekran canvas: arka plan (görsel/video/renk) +
        // sürüklenebilir caption/anket/overlay ----
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val canvasWidthPx = with(density) { maxWidth.toPx() }
            val canvasHeightPx = with(density) { maxHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(parseHexColor(backgroundColor) ?: MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (selectedVideoUri != null) {
                    // Canlı video önizlemesi İCAT EDİLMEDİ (görev tanımı
                    // gereği gerekmiyor, eski davranışla AYNI basit yer
                    // tutucu) — SADECE metin/anket'in konumunu seçmek için
                    // yeterli bir zemin.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }

            if (caption.isNotBlank()) {
                DraggableStoryElement(
                    positionX = captionPositionX,
                    positionY = captionPositionY,
                    scale = 1f,
                    scalable = false,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onPositionChange = onCaptionPositionChange,
                    onScaleChange = {},
                ) {
                    Text(
                        text = caption,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .clickable(enabled = !submitting) { showTextEditor = true }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            if (hasPoll) {
                // Gerçek paylaşılacak görünümle AYNI önizleme — StoryViewerScreen'in
                // KULLANDIĞI PollWidget REUSE edilir, sahte bir kutu İCAT EDİLMEDİ
                // (web'in stories.js'deki updateStoryPollPreview()'un AYNI
                // gerekçesi: "orada tam paylaşılacak hali gözüksün").
                val previewPoll = remember(pollOptions) {
                    Poll(
                        id = "preview",
                        options = pollOptions.filter { it.isNotBlank() }.mapIndexed { i, text ->
                            PollOption(id = "opt$i", text = text, votes = 0, pct = 0)
                        },
                        totalVotes = 0,
                        myVote = null,
                    )
                }
                DraggableStoryElement(
                    positionX = pollPositionX,
                    positionY = pollPositionY,
                    scale = pollScale,
                    scalable = true,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onPositionChange = onPollPositionChange,
                    onScaleChange = onPollScaleChange,
                ) {
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .clickable(enabled = !submitting) { showPollEditor = true }
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                            .padding(12.dp),
                    ) {
                        PollWidget(poll = previewPoll, onVote = {})
                    }
                }
            }

            if (!overlayImageUrl.isNullOrBlank()) {
                DraggableStoryElement(
                    positionX = overlayImagePositionX,
                    positionY = overlayImagePositionY,
                    scale = overlayImageScale,
                    scalable = true,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onPositionChange = onOverlayImagePositionChange,
                    onScaleChange = onOverlayImageScaleChange,
                ) {
                    Box {
                        AsyncImage(
                            model = overlayImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                        )
                        IconButton(
                            onClick = onRemoveOverlayImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "GIF/Sticker'ı kaldır",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        // ---- Üst bar: kapat (X) + görünürlük seçimi + Paylaş ----
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackToCamera, enabled = !submitting) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Kameraya dön",
                        tint = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(4.dp),
                    )
                }
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    TextButton(onClick = onSubmit, enabled = canSubmit) {
                        Text("Paylaş", color = Color.White)
                    }
                }
            }
            StoryVisibilityFilterRow(
                selected = visibility,
                onSelect = onVisibilityChange,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---- Sol kenar araç çubuğu: metin/GIF-sticker/anket ekle ----
        // (kullanıcı isteği: "direkt sol kısımda butonlar olsun metin
        // ekleme, gif ekleme, anket ekleme gibi") — HER ZAMAN görünen
        // buton/TextField/liste yerine dokununca açılan tek-amaçlı
        // düzenleyiciler (StoryTextEditorOverlay/ModalBottomSheet).
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StoryToolButton(
                icon = Icons.Filled.TextFields,
                contentDescription = "Yazı ekle",
                enabled = !submitting,
                onClick = { showTextEditor = true },
            )
            StoryToolButton(
                icon = Icons.Filled.Gif,
                contentDescription = "GIF/Sticker ekle",
                enabled = !submitting,
                onClick = onAddOverlayClick,
            )
            StoryToolButton(
                icon = Icons.Filled.BarChart,
                contentDescription = "Anket ekle",
                enabled = !submitting,
                onClick = {
                    // İlk dokunuşta 2 boş seçenek oluştur (web'in "anket
                    // ekle" tıklayınca AYNI davranışı) — sonraki dokunuşlar
                    // sadece var olan düzenleyiciyi tekrar açar.
                    if (pollOptions.isEmpty()) {
                        onAddPollOption()
                        onAddPollOption()
                    }
                    showPollEditor = true
                },
            )
            // Arka plan rengi — SADECE medya yokken anlamlı (backend zaten
            // medya varsa yok sayıyor, bkz. app/api_v1/stories.py).
            if (!hasMedia) {
                StoryBackgroundColorButton(
                    selectedColor = backgroundColor,
                    enabled = !submitting,
                    onColorSelected = onBackgroundColorChange,
                )
            }
        }

        if (error != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (showTextEditor) {
        StoryTextEditorOverlay(
            initialValue = caption,
            onDone = { text ->
                onCaptionChange(text.trim())
                showTextEditor = false
            },
            onDismiss = { showTextEditor = false },
        )
    }

    if (showPollEditor) {
        StoryPollEditorSheet(
            sheetState = pollEditorSheetState,
            pollOptions = pollOptions,
            submitting = submitting,
            onAddPollOption = onAddPollOption,
            onRemovePollOption = onRemovePollOption,
            onPollOptionChange = onPollOptionChange,
            onDismiss = { showPollEditor = false },
        )
    }
}

/** Sol kenar araç çubuğundaki tek bir ikon-buton — İKİ araç butonu VE
 * arka-plan-rengi butonu TARAFINDAN paylaşılan tek görsel stil (yarı
 * saydam siyah daire zemin, beyaz ikon — hangi medya/renk altta olursa
 * olsun okunabilirlik garantisi, PostCard.kt'deki fullscreen ikonunun
 * AYNI "scrim" mantığı). */
@Composable
private fun StoryToolButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

/** Araç çubuğundaki "arka plan rengi" butonu — dokununca web'in
 * feed.html'deki AYNI 6 swatch'lı paleti (bkz. STORY_BACKGROUND_SWATCHES)
 * küçük bir açılır satırda gösterir. Diğer iki araç butonunun AKSİNE
 * modal/dialog DEĞİL — palet TEK satır, ekranı kaplayacak kadar büyük
 * değil, bu yüzden basit bir AnimatedVisibility yeterli. */
@Composable
private fun StoryBackgroundColorButton(
    selectedColor: String?,
    enabled: Boolean,
    onColorSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StoryToolButton(
            icon = Icons.Filled.Palette,
            contentDescription = "Arka plan rengi",
            enabled = enabled,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            STORY_BACKGROUND_SWATCHES.forEach { hex ->
                val isSelected = selectedColor == hex
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex) ?: Color.Gray)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                            shape = CircleShape,
                        )
                        .clickable(enabled = enabled) {
                            onColorSelected(if (isSelected) null else hex)
                        },
                )
            }
        }
    }
}

/**
 * Tam ekran metin girişi — "Aa" araç butonuna (veya canvas'taki mevcut
 * yazıya) dokununca açılır, Instagram'ın "canvas üzerinde direkt yaz"
 * hissine yakın (yarı saydam siyah zemin + ortalanmış büyük TextField,
 * klavye otomatik açılır). Backend sözleşmesi TEK bir caption alanı
 * olduğu için (web'de de aynı) burada da TEK bir metin kutusu var —
 * Instagram'daki gibi birden çok bağımsız metin katmanı İCAT EDİLMEDİ.
 */
@Composable
private fun StoryTextEditorOverlay(
    initialValue: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            TextButton(onClick = { onDone(text) }) {
                Text("Tamam", color = Color.White)
            }
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .focusRequester(focusRequester)
                // Arka plandaki dismiss-clickable'a TIKLAMANIN sızmaması
                // için TextField'ın kendi tıklaması (imleç konumlandırma)
                // ayrı bir InteractionSource ile İZOLE ediliyor.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = "Bir şeyler yaz...",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                innerTextField()
            },
        )
    }
}

/**
 * Anket seçenekleri düzenleyicisi — eski her-zaman-görünen Column'un AYNI
 * mantığı (0-4 seçenek, en az 2 dolu = anket aktif), sadece dokununca
 * açılan bir ModalBottomSheet'e taşındı (MediaPickerSheet ile AYNI
 * mekanizma). "Anket ekle" araç butonu bu sheet'i açmadan ÖNCE 2 boş
 * seçenek oluşturuyor (bkz. StoryFormStep), bu yüzden burada pollOptions
 * HER ZAMAN en az 2 eleman.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryPollEditorSheet(
    sheetState: SheetState,
    pollOptions: List<String>,
    submitting: Boolean,
    onAddPollOption: () -> Unit,
    onRemovePollOption: (Int) -> Unit,
    onPollOptionChange: (Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Anket", style = MaterialTheme.typography.titleMedium)
            pollOptions.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = option,
                        onValueChange = { onPollOptionChange(index, it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Seçenek ${index + 1}") },
                        singleLine = true,
                        enabled = !submitting,
                    )
                    // İlk 2 seçenek kaldırılamaz — "en az 2 seçenek" kuralı
                    // ViewModel.removePollOption() ile AYNI.
                    if (index >= 2) {
                        IconButton(onClick = { onRemovePollOption(index) }, enabled = !submitting) {
                            Icon(Icons.Filled.Close, contentDescription = "Seçeneği kaldır")
                        }
                    }
                }
            }
            if (pollOptions.size < 4) {
                TextButton(onClick = onAddPollOption, enabled = !submitting) {
                    Text("+ Seçenek Ekle")
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text("Tamam")
            }
        }
    }
}

/**
 * Canvas üzerinde sürüklenebilir (ve opsiyonel olarak pinch ile
 * ölçeklenebilir) bir öğe — caption VE anket önizlemesi TARAFINDAN
 * paylaşılan TEK bir uygulama (web'in storyPollDragState/storyCaptionDragState
 * İKİ AYRI ama BİREBİR AYNI kod bloğu olmasının AKSİNE, burada TEK yer).
 * `detectTransformGestures` TEK bir dokunuşta hem pan (sürükleme) hem zoom
 * (pinch) raporlar — FullscreenImageViewer.kt'deki AYNI kurulmuş desen.
 * `rememberUpdatedState` ZORUNLU: `pointerInput` anahtarları (canvasWidthPx/
 * canvasHeightPx/scalable) DEĞİŞMEDİĞİ sürece jest algılama coroutine'i
 * YENİDEN BAŞLAMAZ, yani positionX/positionY/scale'i DOĞRUDAN yakalayan bir
 * closure sürüklemenin ORTASINDA hep İLK değerlerde donmuş kalırdı.
 */
@Composable
private fun DraggableStoryElement(
    positionX: Float,
    positionY: Float,
    scale: Float,
    scalable: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onPositionChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val positionXState = rememberUpdatedState(positionX)
    val positionYState = rememberUpdatedState(positionY)
    val scaleState = rememberUpdatedState(scale)

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = positionX * canvasWidthPx - size.width / 2f
                translationY = positionY * canvasHeightPx - size.height / 2f
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(canvasWidthPx, canvasHeightPx, scalable) {
                if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val newX = (positionXState.value + pan.x / canvasWidthPx).coerceIn(0f, 1f)
                    val newY = (positionYState.value + pan.y / canvasHeightPx).coerceIn(0f, 1f)
                    onPositionChange(newX, newY)
                    if (scalable) {
                        onScaleChange(scaleState.value * zoom)
                    }
                }
            },
    ) {
        content()
    }
}

@Composable
private fun StoryVisibilityFilterRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(STORY_VISIBILITY_OPTIONS, key = { it.value }) { option ->
            val isSelected = selected == option.value
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option.value) },
                label = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                // 2026-08-10: bu satır artık tam ekran fotoğraf/video/renk
                // üzerinde YÜZÜYOR (bkz. StoryFormStep) — seçilmemiş chip'ler
                // için de ARTIK sabit bir yarı saydam siyah zemin + beyaz
                // metin/ikon (Material'ın varsayılan surface/outline renkleri
                // altta parlak bir fotoğraf varken OKUNMAZ hale gelirdi).
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    labelColor = Color.White,
                    iconColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
