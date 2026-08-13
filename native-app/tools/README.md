# Yayın araçları

`release.ps1` delta (ikili yama) güncelleme akışının TEK giriş noktası —
build → yama üret → doğrula → `gh release upload`. Detaylı tasarım/ölçüm
gerekçesi: `C:\Users\Artemis\.claude\plans\shimmering-zooming-petal.md`
(veya `native-app\.context\active_context.md`).

## Kullanım

```powershell
cd native-app
..\tools\release.ps1
```

`gh` bu betiğin çalıştığı dizinden REPO'yu çözer — `Sosyal-Medya-Mobil`
dışından çalıştırılırsa "release not found" hatası verir.

## `tools/.cache/` — ilk çalıştırmadan önce elle kurulmalı (gitignore'lu)

`release.ps1` üç SHA-256 ile pinlenmiş ikili/jar bekliyor (pin'ler
`release.ps1` içinde sabit). Taze bir checkout'ta bunlar YOK, elle indirilip
yerleştirilmeli:

| Dosya | Kaynak | Not |
|---|---|---|
| `tools/.cache/zstd.exe` | [facebook/zstd v1.5.6 Windows release](https://github.com/facebook/zstd/releases/tag/v1.5.6), `zstd-v1.5.6-win64.zip` içindeki `zstd.exe` | Gerçek `--patch-from` üretimi |
| `tools/.cache/libzstd.dll` | AYNI zip'in `dll/libzstd.dll`'i | Yayın öncesi JNA doğrulaması için — **`zstd-jni`'nin kendi Windows DLL'i DEĞİL**, o sadece JNI giriş noktalarını dışa açıyor, ham `ZSTD_*` C API'sini açmıyor (bkz. plan Aşama 0) |
| `tools/.cache/jna.jar` | [Maven Central `net.java.dev.jna:jna:5.15.0`](https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.15.0/jna-5.15.0.jar) | `PatchVerify.java`'nın classpath'i |

SHA-256'lar tutmazsa `release.ps1` HEMEN durur (Assert-Pin) — dosyaları
rastgele bir kaynaktan indirmeyin, yukarıdaki resmi linkleri kullanın.

## `PatchVerify.java`

Tek dosyalık (derleme adımı yok) — `release.ps1`'in ürettiği HER yamayı,
cihazın kullanacağı BİREBİR native yolla (`ZSTD_DCtx_refPrefix`, JNA ile)
açıp beklenen SHA-256 ile karşılaştırır. Herhangi bir yama bu kontrolden
geçemezse `release.ps1` **hiçbir şey yayınlamaz**.

## `dist/` (gitignore'lu)

`release.ps1`'in çalışma alanı — `dist/<apk>`, `dist/patch-*.zst`,
`dist/update-manifest.json` (hepsi GitHub Releases'e yüklenip repoda
tutulmaz, `*.apk`'nın zaten tabi olduğu kuralla AYNI) ve
`dist/history/<sha12>.apk` (yama üretimi için son `KeepPatchCount` sürümün
yerel önbelleği — `--clobber` eski asset'i yok ettiği için BU önbellek
olmadan eski sürümlere karşı yama üretilemez).
