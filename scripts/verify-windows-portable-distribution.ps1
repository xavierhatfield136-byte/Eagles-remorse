param(
    [string]$AppImageDir = "build/package/windows/EaglesRemorse",
    [string]$ZipPath = "",
    [string]$ExtractDir = "build/package/windows/clean-extract",
    [string]$ReportsDir = "build/reports/distribution-verification",
    [switch]$SkipAssetDecode
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string]$PathText) {
    if ([System.IO.Path]::IsPathRooted($PathText)) {
        return [System.IO.Path]::GetFullPath($PathText)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $PathText))
}

function Get-RelativePath([string]$Root, [string]$Path) {
    $rootUri = [Uri](([System.IO.Path]::GetFullPath($Root).TrimEnd('\') + '\'))
    $pathUri = [Uri]([System.IO.Path]::GetFullPath($Path))
    return [Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace('/', '\')
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ContentCategory([string]$RelativePath) {
    $rel = $RelativePath.Replace('\', '/').ToLowerInvariant()
    if ($rel -eq "eaglesremorse.exe") { return "launcher" }
    if ($rel -like "app/*.jar") { return "application-jar" }
    if ($rel -like "app/*.cfg" -or $rel -like "app/*.xml") { return "launch-config" }
    if ($rel -like "runtime/*") { return "bundled-java-runtime" }
    if ($rel -like "*.dll" -or $rel -like "*.so" -or $rel -like "*.dylib") { return "native-library" }
    if ($rel -like "*.md" -or $rel -like "*.txt") { return "documentation" }
    if ($rel -like "*.json") { return "manifest-or-data" }
    if ($rel -like "*.bat" -or $rel -like "*.ps1") { return "install-verifier" }
    return "package-file"
}

function Get-AssetCategory([string]$EntryName) {
    $name = $EntryName.Replace('\', '/').ToLowerInvariant()
    if ($name.EndsWith("/.gitkeep") -or $name -eq ".gitkeep") { return "" }
    foreach ($prefix in @(
        "ship_skins/", "ship_parts/", "ship_wrecks/", "turret_skins/",
        "station_modules/", "projectile_skins/", "ship_damage_patches/",
        "environment_overhaul_dropzone/", "hud_panels/", "ui/", "ui_theme/",
        "audio/", "voice/"
    )) {
        if ($name.StartsWith($prefix)) { return $prefix.TrimEnd('/') }
    }
    if ($name -eq "version") { return "version" }
    if ($name.EndsWith(".json")) { return "data" }
    return ""
}

function Write-JsonFile($Object, [string]$Path) {
    $json = $Object | ConvertTo-Json -Depth 8
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($Path)) | Out-Null
    [System.IO.File]::WriteAllText($Path, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

function New-Readme([string]$Root) {
    $readme = @"
Eagles Remorse - Portable Windows Install

1. Download the complete ZIP.
2. Extract the entire ZIP into a normal folder.
3. Do not move EaglesRemorse.exe out of that folder.
4. Do not launch the game from inside the ZIP preview.
5. Start EaglesRemorse.exe from the extracted folder.

Before reporting missing assets, run verify-install.bat from this folder.
"@
    Set-Content -LiteralPath (Join-Path $Root "README_INSTALL.txt") -Value $readme -Encoding UTF8
}

function New-InstallVerifier([string]$Root) {
    $ps1 = @'
param(
    [string]$Root = $PSScriptRoot,
    [string]$Manifest = (Join-Path $PSScriptRoot "package_content_manifest.json"),
    [string]$Report = (Join-Path $PSScriptRoot "verification-report.txt")
)

$ErrorActionPreference = "Stop"

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

if (!(Test-Path -LiteralPath $Manifest)) {
    "INSTALL INCOMPLETE: package_content_manifest.json is missing." | Tee-Object -FilePath $Report
    exit 2
}

$manifestData = Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
$errors = New-Object System.Collections.Generic.List[string]
$checked = 0

foreach ($file in $manifestData.files) {
    $relative = [string]$file.path
    if ([string]::IsNullOrWhiteSpace($relative)) { continue }
    $path = Join-Path $Root $relative
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
        $errors.Add("missing: $relative")
        continue
    }
    $item = Get-Item -LiteralPath $path
    if ([int64]$file.size -ne $item.Length) {
        $errors.Add("size mismatch: $relative expected=$($file.size) actual=$($item.Length)")
        continue
    }
    $hash = Get-Sha256 $path
    if ($hash -ne ([string]$file.sha256).ToLowerInvariant()) {
        $errors.Add("sha256 mismatch: $relative")
        continue
    }
    $checked++
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("Package root: $Root")
$lines.Add("Manifest: $Manifest")
$lines.Add("Checked: $checked / $($manifestData.files.Count)")
foreach ($errorItem in $errors) { $lines.Add($errorItem) }

if ($errors.Count -eq 0) {
    $summary = "INSTALL VERIFIED: $checked / $($manifestData.files.Count) required files match."
    $summary | Tee-Object -FilePath $Report
    Add-Content -LiteralPath $Report -Value $lines
    exit 0
}

$summary = "INSTALL INCOMPLETE: $($errors.Count) required files are missing or corrupted. See verification-report.txt."
$summary | Tee-Object -FilePath $Report
Add-Content -LiteralPath $Report -Value $lines
exit 1
'@
    Set-Content -LiteralPath (Join-Path $Root "verify-install.ps1") -Value $ps1 -Encoding UTF8

    $bat = @"
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-install.ps1" %*
exit /b %ERRORLEVEL%
"@
    Set-Content -LiteralPath (Join-Path $Root "verify-install.bat") -Value $bat -Encoding ASCII
}

function New-PackageManifest([string]$Root, [string]$OutputPath, [string]$Version) {
    $files = Get-ChildItem -LiteralPath $Root -Recurse -File |
        Where-Object { $_.Name -ne "package_content_manifest.json" -and $_.Name -ne "verification-report.txt" } |
        Sort-Object FullName |
        ForEach-Object {
            $rel = Get-RelativePath $Root $_.FullName
            [ordered]@{
                path = $rel.Replace('\', '/')
                size = $_.Length
                sha256 = Get-Sha256 $_.FullName
                category = Get-ContentCategory $rel
                required = $true
            }
        }
    $manifest = [ordered]@{
        schema = "package-content-manifest-v1"
        version = $Version
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        rootName = (Split-Path $Root -Leaf)
        fileCount = @($files).Count
        totalBytes = (@($files) | ForEach-Object { [int64]$_['size'] } | Measure-Object -Sum).Sum
        files = @($files)
    }
    Write-JsonFile $manifest $OutputPath
    return $manifest
}

function New-RuntimeAssetManifest([string]$JarPath, [string]$OutputPath, [string]$Version) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entries = foreach ($entry in ($zip.Entries | Sort-Object FullName)) {
            if ([string]::IsNullOrWhiteSpace($entry.Name)) { continue }
            $category = Get-AssetCategory $entry.FullName
            if ([string]::IsNullOrWhiteSpace($category)) { continue }
            $stream = $entry.Open()
            try {
                $hashBytes = $sha.ComputeHash($stream)
            } finally {
                $stream.Dispose()
            }
            [ordered]@{
                path = $entry.FullName
                size = $entry.Length
                sha256 = ([System.BitConverter]::ToString($hashBytes).Replace("-", "").ToLowerInvariant())
                category = $category
                required = $true
                container = ("app/" + (Split-Path $JarPath -Leaf))
            }
        }
    } finally {
        $zip.Dispose()
        $sha.Dispose()
    }
    $manifest = [ordered]@{
        schema = "runtime-asset-manifest-v1"
        version = $Version
        generatedAtUtc = [DateTime]::UtcNow.ToString("o")
        sourceJar = ("app/" + (Split-Path $JarPath -Leaf))
        assetCount = @($entries).Count
        totalBytes = (@($entries) | ForEach-Object { [int64]$_['size'] } | Measure-Object -Sum).Sum
        files = @($entries)
    }
    Write-JsonFile $manifest $OutputPath
    return $manifest
}

function Test-RuntimeAssetLoadability([string]$JarPath, [string]$ReportPath, [switch]$SkipDecode) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (!$SkipDecode) {
        Add-Type -AssemblyName System.Drawing
    }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    $errors = New-Object System.Collections.Generic.List[string]
    $checked = 0
    try {
        foreach ($entry in ($zip.Entries | Sort-Object FullName)) {
            if ([string]::IsNullOrWhiteSpace($entry.Name)) { continue }
            $category = Get-AssetCategory $entry.FullName
            if ([string]::IsNullOrWhiteSpace($category)) { continue }
            $checked++
            $ext = [System.IO.Path]::GetExtension($entry.FullName).ToLowerInvariant()
            $stream = $entry.Open()
            try {
                if (!$SkipDecode -and $ext -in @(".png", ".jpg", ".jpeg", ".bmp", ".gif")) {
                    $img = [System.Drawing.Image]::FromStream($stream, $false, $true)
                    $img.Dispose()
                } elseif ($ext -eq ".json") {
                    $reader = New-Object System.IO.StreamReader($stream)
                    try { $null = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
                } elseif ($ext -in @(".wav", ".ogg", ".mp3")) {
                    $buffer = New-Object byte[] 12
                    $read = $stream.Read($buffer, 0, $buffer.Length)
                    if ($read -lt 4) { throw "audio header too short" }
                    $header = [System.Text.Encoding]::ASCII.GetString($buffer, 0, [Math]::Min($read, 4))
                    if ($header -ne "RIFF" -and $header -ne "OggS" -and !$header.StartsWith("ID3")) {
                        if (!($buffer[0] -eq 0xff -and (($buffer[1] -band 0xe0) -eq 0xe0))) {
                            throw "unknown audio header $header"
                        }
                    }
                } else {
                    if ($entry.Length -le 0 -and $entry.FullName.ToLowerInvariant() -ne "version") {
                        throw "empty required asset"
                    }
                }
            } catch {
                $errors.Add("$($entry.FullName): $($_.Exception.Message)")
            } finally {
                $stream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
    $report = [ordered]@{
        checked = $checked
        errors = @($errors)
        passed = ($errors.Count -eq 0)
        decoded = (!$SkipDecode)
    }
    Write-JsonFile $report $ReportPath
    return $report
}

function Test-PackageManifest([string]$Root, [string]$ManifestPath, [string]$ReportPath) {
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    $errors = New-Object System.Collections.Generic.List[string]
    $checked = 0
    foreach ($file in $manifest.files) {
        $path = Join-Path $Root ([string]$file.path)
        if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
            $errors.Add("missing: $($file.path)")
            continue
        }
        $item = Get-Item -LiteralPath $path
        if ([int64]$file.size -ne $item.Length) {
            $errors.Add("size mismatch: $($file.path)")
            continue
        }
        if ((Get-Sha256 $path) -ne ([string]$file.sha256).ToLowerInvariant()) {
            $errors.Add("sha256 mismatch: $($file.path)")
            continue
        }
        $checked++
    }
    $report = [ordered]@{
        root = $Root
        manifest = $ManifestPath
        checked = $checked
        expected = $manifest.files.Count
        passed = ($errors.Count -eq 0)
        errors = @($errors)
    }
    Write-JsonFile $report $ReportPath
    return $report
}

function Test-ZipManifest([string]$ZipPath, [string]$ManifestPath, [string]$ReportPath) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
    $errors = New-Object System.Collections.Generic.List[string]
    $checked = 0
    try {
        $entries = @{}
        foreach ($entry in $zip.Entries) {
            if (![string]::IsNullOrWhiteSpace($entry.Name)) {
                $entries[$entry.FullName.Replace('\', '/')] = $entry
            }
        }
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            foreach ($file in $manifest.files) {
                $rel = [string]$file.path
                if (!$entries.ContainsKey($rel)) {
                    $errors.Add("missing in zip: $rel")
                    continue
                }
                $entry = $entries[$rel]
                if ([int64]$file.size -ne $entry.Length) {
                    $errors.Add("zip size mismatch: $rel")
                    continue
                }
                $stream = $entry.Open()
                try {
                    $hash = [System.BitConverter]::ToString($sha.ComputeHash($stream)).Replace("-", "").ToLowerInvariant()
                } finally {
                    $stream.Dispose()
                }
                if ($hash -ne ([string]$file.sha256).ToLowerInvariant()) {
                    $errors.Add("zip sha256 mismatch: $rel")
                    continue
                }
                $checked++
            }
        } finally {
            $sha.Dispose()
        }
    } finally {
        $zip.Dispose()
    }
    $report = [ordered]@{
        zip = $ZipPath
        manifest = $ManifestPath
        checked = $checked
        expected = $manifest.files.Count
        passed = ($errors.Count -eq 0)
        errors = @($errors)
    }
    Write-JsonFile $report $ReportPath
    return $report
}

$appRoot = Resolve-RepoPath $AppImageDir
$reportsRoot = Resolve-RepoPath $ReportsDir
$extractRoot = Resolve-RepoPath $ExtractDir
$version = (Get-Content -LiteralPath (Resolve-RepoPath "VERSION") -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($ZipPath)) {
    $ZipPath = "build/package/windows/EaglesRemorse-$version-windows-x64-full.zip"
}
$zipFullPath = Resolve-RepoPath $ZipPath

if (!(Test-Path -LiteralPath $appRoot -PathType Container)) {
    throw "App image not found: $appRoot"
}

[System.IO.Directory]::CreateDirectory($reportsRoot) | Out-Null
New-Readme $appRoot
Copy-Item -LiteralPath (Resolve-RepoPath "LICENSE.md") -Destination (Join-Path $appRoot "LICENSE.txt") -Force
New-InstallVerifier $appRoot

$jar = Get-ChildItem -LiteralPath (Join-Path $appRoot "app") -Filter "*.jar" | Select-Object -First 1
if ($null -eq $jar) { throw "Packaged app JAR missing under $appRoot\app" }

$runtimeAssetManifestPath = Join-Path $appRoot "runtime_asset_manifest.json"
$packageManifestPath = Join-Path $appRoot "package_content_manifest.json"
$assetReportPath = Join-Path $reportsRoot "runtime_asset_loadability.json"

$assetManifest = New-RuntimeAssetManifest $jar.FullName $runtimeAssetManifestPath $version
Copy-Item -LiteralPath $runtimeAssetManifestPath -Destination (Join-Path $reportsRoot "runtime_asset_manifest.json") -Force

$loadability = Test-RuntimeAssetLoadability $jar.FullName $assetReportPath -SkipDecode:$SkipAssetDecode

$packageManifest = New-PackageManifest $appRoot $packageManifestPath $version
Copy-Item -LiteralPath $packageManifestPath -Destination (Join-Path $reportsRoot "package_content_manifest.json") -Force

$stagedReport = Test-PackageManifest $appRoot $packageManifestPath (Join-Path $reportsRoot "windows-staged-folder-vs-package-manifest.json")

if (Test-Path -LiteralPath $zipFullPath) { Remove-Item -LiteralPath $zipFullPath -Force }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($zipFullPath)) | Out-Null
[System.IO.Compression.ZipFile]::CreateFromDirectory($appRoot, $zipFullPath, [System.IO.Compression.CompressionLevel]::Optimal, $false)

$zipReport = Test-ZipManifest $zipFullPath $packageManifestPath (Join-Path $reportsRoot "windows-zip-vs-package-manifest.json")

if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force }
[System.IO.Directory]::CreateDirectory($extractRoot) | Out-Null
[System.IO.Compression.ZipFile]::ExtractToDirectory($zipFullPath, $extractRoot)
$extractedReport = Test-PackageManifest $extractRoot (Join-Path $extractRoot "package_content_manifest.json") (Join-Path $reportsRoot "windows-extracted-vs-package-manifest.json")

$zipHash = Get-Sha256 $zipFullPath
$shaLine = "$zipHash  $([System.IO.Path]::GetFileName($zipFullPath))"
Set-Content -LiteralPath (Join-Path ([System.IO.Path]::GetDirectoryName($zipFullPath)) "SHA256SUMS-windows.txt") -Value $shaLine -Encoding ASCII

$releaseApproved = $stagedReport.passed -and $zipReport.passed -and $extractedReport.passed -and $loadability.passed
$gate = [ordered]@{
    version = $version
    commit = (git rev-parse --short HEAD)
    platform = "windows-x64"
    package = [System.IO.Path]::GetFileName($zipFullPath)
    packageSha256 = $zipHash
    packageBytes = (Get-Item -LiteralPath $zipFullPath).Length
    manifestMatch = ($stagedReport.passed -and $zipReport.passed -and $extractedReport.passed)
    runtimeAssetLoadabilityPassed = $loadability.passed
    cleanInstallPassed = $extractedReport.passed
    tutorialRegressionPassed = $false
    externalMachinePassed = $false
    releaseApproved = $false
    localPackageVerificationPassed = $releaseApproved
}
Write-JsonFile $gate (Join-Path $reportsRoot "release_candidate_gate.json")

$md = @"
# Windows Portable Distribution Verification

- Version: $version
- Commit: $($gate.commit)
- Package: $($gate.package)
- SHA-256: $zipHash
- Package bytes: $($gate.packageBytes)
- Staged manifest match: $($stagedReport.passed)
- ZIP manifest match: $($zipReport.passed)
- Extracted manifest match: $($extractedReport.passed)
- Runtime asset loadability: $($loadability.passed)
- Local package verification passed: $releaseApproved
- Tutorial regression from extracted package: not automated in this script
- External machine passed: false
- Release approved: false
"@
Set-Content -LiteralPath (Join-Path $reportsRoot "release_candidate_gate.md") -Value $md -Encoding UTF8

Write-Host "PACKAGE: $zipFullPath"
Write-Host "SHA256:  $zipHash"
Write-Host "REPORT:  $reportsRoot"
if (!$releaseApproved) {
    throw "Local package verification failed. See $reportsRoot"
}
