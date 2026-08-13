# Delta (ikili yama) APK yayın betiği — bkz. plan
# C:\Users\Artemis\.claude\plans\shimmering-zooming-petal.md (Aşama 1c/2).
#
# Bugüne kadar bu akış TAMAMEN elle yapılıyordu (bkz. .context/active_context.md
# "gh release upload ... --clobber" notu). Bu betik onu tek komuta indirger VE
# delta yama üretimini ekler. `native-v0.1.0` tag'i SONSUZA KADAR YENİDEN
# KULLANILIR (--clobber ile asset'ler üzerine yazılır) — yamalar tag'e değil
# İÇERİK HASH'İNE anahtarlanıyor, bu yüzden tek-tag şeması bozulmuyor.
#
# Kullanım:  cd native-app; ..\tools\release.ps1
# (gh, bu betiğin çalıştığı dizinden REPO KÖKÜNE göre repo'yu çözer — Sosyal-
# Medya-Mobil dışından çalıştırılırsa "release not found" hatası verir.)

$ErrorActionPreference = "Stop"

$RepoRoot   = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$NativeApp  = Resolve-Path (Join-Path $PSScriptRoot "..")
$ToolsCache = Join-Path $PSScriptRoot ".cache"
$DistDir    = Join-Path $NativeApp "dist"
$HistoryDir = Join-Path $DistDir "history"

$Tag             = "native-v0.1.0"
$ApkAssetName    = "sosyal-medya-native-debug.apk"
$ManifestName    = "update-manifest.json"
$KeepPatchCount  = 3
$Codec           = "zstd-patch-from-v1"

# Aşama 0'da indirilip SHA-256 ile PİNLENDİ (facebook/zstd v1.5.6 resmi
# Windows release'i) — rastgele bir zstd.exe/libzstd.dll'e GÜVENİLMİYOR.
$ZstdExe        = Join-Path $ToolsCache "zstd.exe"
$ZstdExeSha     = "6b5c50dde7062909b69b618fae228c72090596dc254efe498fb426f5f430a1f9"
$LibZstdDll     = Join-Path $ToolsCache "libzstd.dll"
$LibZstdDllSha  = "4d540a749823e5b8a6eb56deca5715150442d45f1e8863dd185fc31002b9b5d1"
$JnaJar         = Join-Path $ToolsCache "jna.jar"
$JnaJarSha      = "a564158d28ab5127fc6a958028ed54279fe0999662c46425b6a3b09a2a52094d"
$JbrJava        = "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"

function Assert-Pin([string]$Path, [string]$ExpectedSha256, [string]$Label) {
    if (-not (Test-Path $Path)) {
        throw "$Label bulunamadı: $Path — tools/.cache/ dizini eksik veya taşınmış."
    }
    $actual = (Get-FileHash $Path -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $ExpectedSha256) {
        throw "$Label SHA-256 TUTMUYOR (beklenen $ExpectedSha256, bulunan $actual) — dosya değişmiş/bozulmuş olabilir, elle yeniden indirip pini güncelleyin."
    }
}

function Get-Sha256Hex([string]$Path) {
    return (Get-FileHash $Path -Algorithm SHA256).Hash.ToLower()
}

Write-Host "== 0. Ön kontroller ==" -ForegroundColor Cyan
Assert-Pin $ZstdExe $ZstdExeSha "zstd.exe"
Assert-Pin $LibZstdDll $LibZstdDllSha "libzstd.dll"
Assert-Pin $JnaJar $JnaJarSha "jna.jar"
if (-not (Test-Path $JbrJava)) { throw "JBR java.exe bulunamadı: $JbrJava" }
New-Item -ItemType Directory -Force -Path $DistDir, $HistoryDir | Out-Null

gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw "gh authenticate değil — 'gh auth login' çalıştırın." }

Push-Location $RepoRoot
try {
    Write-Host "== 1. Eski APK'yı yakala (clobber'dan ÖNCE) ==" -ForegroundColor Cyan
    # --clobber eski asset'i YOK EDER — bu yüzden yeni build'den ÖNCE, yayındaki
    # APK history/'ye alınır. history/ zaten doluysa (normal durum) hiçbir
    # şey indirilmez. Manifest de yoksa/silinmişse yayındaki APK'yı indirip
    # kendi kendine onarır.
    $releaseJson = gh release view $Tag --json assets 2>$null | ConvertFrom-Json
    if (-not $releaseJson) { throw "Release '$Tag' bulunamadı — bu betiği Sosyal-Medya-Mobil repo kökünden (veya native-app altından) çalıştırdığınızdan emin olun." }

    $existingManifestAsset = $releaseJson.assets | Where-Object { $_.name -eq $ManifestName }
    $existingApkAsset = $releaseJson.assets | Where-Object { $_.name -eq $ApkAssetName }

    $priorSha = $null
    if ($existingManifestAsset) {
        $tmpManifest = Join-Path $env:TEMP "release-ps1-old-manifest.json"
        gh release download $Tag --pattern $ManifestName --output $tmpManifest --clobber 2>$null
        if (Test-Path $tmpManifest) {
            $priorManifest = Get-Content $tmpManifest -Raw | ConvertFrom-Json
            $priorSha = $priorManifest.apk.sha256
        }
    }

    if ($existingApkAsset -and -not (Get-ChildItem $HistoryDir -Filter "*.apk" -ErrorAction SilentlyContinue |
            Where-Object { $priorSha -and (Get-Sha256Hex $_.FullName) -eq $priorSha })) {
        Write-Host "  Yayındaki APK history/'de yok, indiriliyor..."
        $tmpApk = Join-Path $env:TEMP "release-ps1-old-apk.apk"
        gh release download $Tag --pattern $ApkAssetName --output $tmpApk --clobber
        $sha = Get-Sha256Hex $tmpApk
        $dest = Join-Path $HistoryDir "$($sha.Substring(0,12)).apk"
        if (-not (Test-Path $dest)) { Move-Item $tmpApk $dest -Force } else { Remove-Item $tmpApk -Force }
        Write-Host "  history/$($sha.Substring(0,12)).apk kaydedildi."
    } else {
        Write-Host "  history/ zaten güncel veya yayında henüz APK yok (ilk yayın)."
    }

    Write-Host "== 2. Build (arm64-v8a) ==" -ForegroundColor Cyan
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    Push-Location $NativeApp
    try {
        & ".\gradlew.bat" assembleDebug "-PreleaseAbi=arm64-v8a"
        if ($LASTEXITCODE -ne 0) { throw "gradlew assembleDebug başarısız (kod $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
    $builtApk = Join-Path $NativeApp "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $builtApk)) { throw "Build çıktısı bulunamadı: $builtApk" }

    Write-Host "== 3. Yeni APK ==" -ForegroundColor Cyan
    $newApk = Join-Path $DistDir $ApkAssetName
    Copy-Item $builtApk $newApk -Force
    $newSha = Get-Sha256Hex $newApk
    $newSize = (Get-Item $newApk).Length
    Write-Host "  sha256=$newSha  size=$([Math]::Round($newSize/1MB,1)) MB"

    $historyApks = Get-ChildItem $HistoryDir -Filter "*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
    if ($historyApks | Where-Object { $_.BaseName -eq $newSha.Substring(0,12) }) {
        Write-Host "  Bu build zaten yayınlanmış (değişiklik yok) — çıkılıyor." -ForegroundColor Yellow
        return
    }

    Write-Host "== 4. Yama üretimi (son $KeepPatchCount taban için) ==" -ForegroundColor Cyan
    $bases = $historyApks | Select-Object -First $KeepPatchCount
    $windowLog = [Math]::Max(24, [Math]::Ceiling([Math]::Log([Math]::Max($newSize, 60MB), 2)))
    Write-Host "  windowLog=$windowLog"

    $patchEntries = @()
    $patchFiles = @()
    foreach ($base in $bases) {
        $baseSha = $base.BaseName
        $patchName = "patch-zstd-$baseSha.zst"
        $patchPath = Join-Path $DistDir $patchName
        Write-Host "  -> taban $baseSha ..."

        & $ZstdExe "--patch-from=$($base.FullName)" "-19" "--long=$windowLog" "-T1" "--force" "-o" $patchPath $newApk
        if ($LASTEXITCODE -ne 0) { throw "zstd --patch-from başarısız (taban $baseSha)" }

        # Doğrulama 1: CLI ile geri aç.
        $tmpOut = Join-Path $env:TEMP "release-ps1-roundtrip.apk"
        & $ZstdExe "-d" "--patch-from=$($base.FullName)" "--long=$windowLog" "--memory=2048MB" "-f" $patchPath "-o" $tmpOut
        if ($LASTEXITCODE -ne 0) { throw "zstd -d (CLI doğrulama) başarısız (taban $baseSha)" }
        if ((Get-Sha256Hex $tmpOut) -ne $newSha) { throw "CLI round-trip SHA TUTMADI (taban $baseSha) — YAYIN İPTAL" }
        Remove-Item $tmpOut -Force

        # Doğrulama 2: cihazın kullanacağı BİREBİR native yol (JNA + refPrefix).
        $verifyOut = & $JbrJava "--class-path" $JnaJar "$PSScriptRoot\PatchVerify.java" `
            $LibZstdDll $base.FullName $patchPath $newSha $windowLog $newSize 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host $verifyOut -ForegroundColor Red
            throw "JNA doğrulaması BAŞARISIZ (taban $baseSha) — YAYIN İPTAL, cihaz bu yamayı açamayabilir."
        }
        Write-Host "     $verifyOut"

        $patchSize = (Get-Item $patchPath).Length
        $savedPct = [Math]::Round(100 - (100.0 * $patchSize / $newSize), 1)
        Write-Host "     yama: $([Math]::Round($patchSize/1KB,0)) KB  (%$savedPct tasarruf)" -ForegroundColor Green

        $patchEntries += [ordered]@{
            codec      = $Codec
            fromSha256 = $baseSha
            toSha256   = $newSha
            asset      = $patchName
            size       = $patchSize
            windowLog  = $windowLog
        }
        $patchFiles += $patchPath
    }

    Write-Host "== 5. Manifest ==" -ForegroundColor Cyan
    $manifest = [ordered]@{
        schema = 1
        apk    = [ordered]@{
            name = $ApkAssetName
            sha256 = $newSha
            size = $newSize
            abi = "arm64-v8a"
            buildNumber = 0
        }
        patches = $patchEntries
    }
    $manifestPath = Join-Path $DistDir $ManifestName
    ($manifest | ConvertTo-Json -Depth 6) | Set-Content -Path $manifestPath -Encoding utf8

    Write-Host "== 6. Yükleme (yamalar -> APK -> manifest, bu SIRAYLA) ==" -ForegroundColor Cyan
    foreach ($p in $patchFiles) {
        gh release upload $Tag $p --clobber
    }
    gh release upload $Tag $newApk --clobber
    gh release upload $Tag $manifestPath --clobber

    Write-Host "== 7. Eski yama asset'lerini temizle ==" -ForegroundColor Cyan
    $keepAssetNames = @($ApkAssetName, $ManifestName) + ($patchEntries | ForEach-Object { $_.asset })
    $currentAssets = (gh release view $Tag --json assets | ConvertFrom-Json).assets
    foreach ($asset in $currentAssets) {
        if ($asset.name -like "patch-zstd-*" -and ($keepAssetNames -notcontains $asset.name)) {
            Write-Host "  siliniyor: $($asset.name)"
            gh release delete-asset $Tag $asset.name --yes
        }
    }

    Write-Host "== 8. history/ güncelle ==" -ForegroundColor Cyan
    Copy-Item $newApk (Join-Path $HistoryDir "$($newSha.Substring(0,12)).apk") -Force
    Get-ChildItem $HistoryDir -Filter "*.apk" | Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $KeepPatchCount | Remove-Item -Force

    Write-Host ""
    Write-Host "== Özet ==" -ForegroundColor Cyan
    Write-Host "  APK: $([Math]::Round($newSize/1MB,1)) MB  sha256=$($newSha.Substring(0,12))"
    foreach ($e in $patchEntries) {
        $pct = [Math]::Round(100 - (100.0 * $e.size / $newSize), 1)
        Write-Host "  yama <- $($e.fromSha256): $([Math]::Round($e.size/1KB,0)) KB (%$pct tasarruf)"
    }
} finally {
    Pop-Location
}
