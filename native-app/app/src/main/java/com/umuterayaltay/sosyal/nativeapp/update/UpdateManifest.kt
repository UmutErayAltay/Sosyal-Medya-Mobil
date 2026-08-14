package com.umuterayaltay.sosyal.nativeapp.update

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

/** `release.ps1`'in her yayında ürettiği `update-manifest.json` — bkz. delta
 * güncelleme planı. Cihaz kendi kurulu APK'sının SHA-256'sını hesaplayıp
 * [ManifestApkDto.sha256] ile karşılaştırır (GERÇEK sürüm kimliği,
 * `versionCode` donuk olduğu için); eşleşmiyorsa [patches] içinde
 * `fromSha256`'sı kurulu SHA'ya eşit bir kayıt arar. */
data class UpdateManifestDto(
    val schema: Int,
    val apk: ManifestApkDto,
    val patches: List<PatchEntryDto> = emptyList(),
)

data class ManifestApkDto(
    val name: String,
    val sha256: String,
    val size: Long,
    val abi: String,
    @SerializedName("buildNumber") val buildNumber: Int = 0,
)

data class PatchEntryDto(
    /** Bilinmeyen codec'ler [parseManifest] tarafından SESSİZCE elenir —
     * ileride yeni bir yama şeması (örn. dex-açık File-by-File) eklenirse
     * eski istemciler kırılmaz, sadece o kaydı görmezden gelip tam
     * indirmeye düşer. */
    val codec: String,
    val fromSha256: String,
    val toSha256: String,
    val asset: String,
    val size: Long,
    val windowLog: Int,
)

/** Bu istemcinin bildiği tek codec — bkz. [PatchEntryDto.codec] KDoc'u. */
const val SUPPORTED_PATCH_CODEC = "zstd-patch-from-v1"

private val gson = Gson()

/** Bozuk/eksik JSON'da exception fırlatmak yerine `null` döner — çağıran
 * taraf (bkz. UpdateRepository) bunu "manifest yok" gibi ele alıp bugünkü
 * (manifestsiz) davranışa düşer. */
@Suppress("SENSELESS_COMPARISON") // Gson reflection'ı Kotlin'in null-safety'sini es geçebilir — eksik "apk"/"patches" alanı gerçekten null bırakılabilir.
fun parseUpdateManifest(json: String): UpdateManifestDto? = try {
    val dto = gson.fromJson(json, UpdateManifestDto::class.java)
    if (dto?.schema == 1 && dto.apk != null) {
        // Gson, Kotlin'in `patches: List<...> = emptyList()` VARSAYILANINI
        // JSON'da alan HİÇ YOKSA (veya `null` ise) UYGULAMIYOR — reflection
        // ile inşa edip alanı boş bırakıyor, non-null tipe rağmen gerçek
        // çalışma zamanı null'u üretiyor. Bir birim testi bunu YAKALADI
        // (patches alanı olmadan manifest parse edilince .patches.size çağrısı
        // NullPointerException fırlattı) — çağıran taraf (UpdateRepository)
        // `manifest.patches.firstOrNull{}` diye güvenle yazabilsin diye burada
        // savunmacı olarak boş listeye çevriliyor.
        if (dto.patches == null) dto.copy(patches = emptyList()) else dto
    } else {
        null
    }
} catch (e: JsonSyntaxException) {
    null
}
