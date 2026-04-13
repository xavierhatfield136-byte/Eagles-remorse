param(
    [string[]]$Stems = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot "assets\ship_skins"
$outputDir = Join-Path $repoRoot "assets\ship_wrecks"

$layerSuffixPattern = '_(albedo|ao|panel|emissive|damage)$'
$factionSuffixPattern = '_(ally|enemy|team_c|team_d)$'

function New-Color {
    param(
        [int]$A,
        [int]$R,
        [int]$G,
        [int]$B
    )
    return [System.Drawing.Color]::FromArgb($A, $R, $G, $B)
}

function Get-RoleKey {
    param([string]$Stem)
    return ($Stem -replace $factionSuffixPattern, "")
}

function Get-ProfileName {
    param([string]$RoleKey)
    if ($RoleKey -eq "base") { return "station" }

    $small = @(
        "fighter", "bomber", "drone", "patrol", "picket", "missile_boat",
        "ciws_corvette", "pd_craft", "miner", "hauler", "transport", "static_turret"
    )
    $medium = @(
        "frigate", "cruiser", "light_cruiser", "medium_cruiser", "battlecruiser",
        "drone_carrier"
    )
    $large = @(
        "carrier", "battleship", "dreadnought", "supership", "base", "stealth_ship"
    )

    if ($small -contains $RoleKey) { return "small" }
    if ($medium -contains $RoleKey) { return "medium" }
    if ($large -contains $RoleKey) { return "large" }
    return "medium"
}

function New-PointF {
    param([double]$X, [double]$Y)
    return New-Object System.Drawing.PointF ([single]$X), ([single]$Y)
}

function Get-ChunkProfiles {
    param([string]$ProfileName)

    switch ($ProfileName) {
        "small" {
            return @{
                Chunks = @(
                    @((0.04,0.18),(0.52,0.10),(0.60,0.36),(0.42,0.88),(0.10,0.80)),
                    @((0.44,0.14),(0.96,0.22),(0.90,0.84),(0.56,0.92),(0.36,0.48))
                )
                Breaches = @(
                    @{ X = 0.56; Y = 0.44; W = 0.18; H = 0.18; Angle = -12.0 }
                )
            }
        }
        "station" {
            return @{
                Chunks = @(
                    @((0.01,0.16),(0.24,0.06),(0.34,0.34),(0.28,0.72),(0.03,0.78)),
                    @((0.18,0.09),(0.42,0.07),(0.48,0.36),(0.38,0.92),(0.14,0.84)),
                    @((0.36,0.08),(0.64,0.10),(0.60,0.46),(0.46,0.76),(0.30,0.34)),
                    @((0.52,0.10),(0.84,0.12),(0.80,0.58),(0.62,0.94),(0.46,0.54)),
                    @((0.70,0.18),(0.99,0.28),(0.94,0.86),(0.70,0.94),(0.58,0.50))
                )
                Breaches = @(
                    @{ X = 0.32; Y = 0.42; W = 0.18; H = 0.18; Angle = -18.0 },
                    @{ X = 0.66; Y = 0.56; W = 0.24; H = 0.20; Angle = 14.0 }
                )
            }
        }
        "large" {
            return @{
                Chunks = @(
                    @((0.01,0.16),(0.28,0.06),(0.40,0.38),(0.25,0.86),(0.03,0.74)),
                    @((0.24,0.10),(0.56,0.08),(0.58,0.42),(0.42,0.92),(0.18,0.78)),
                    @((0.46,0.08),(0.80,0.12),(0.74,0.44),(0.52,0.70),(0.40,0.36)),
                    @((0.60,0.18),(0.98,0.28),(0.94,0.86),(0.68,0.96),(0.54,0.54))
                )
                Breaches = @(
                    @{ X = 0.34; Y = 0.44; W = 0.18; H = 0.18; Angle = -18.0 },
                    @{ X = 0.64; Y = 0.56; W = 0.24; H = 0.20; Angle = 14.0 }
                )
            }
        }
        default {
            return @{
                Chunks = @(
                    @((0.02,0.18),(0.34,0.08),(0.48,0.38),(0.36,0.88),(0.08,0.80)),
                    @((0.28,0.12),(0.66,0.10),(0.62,0.52),(0.42,0.92),(0.18,0.72)),
                    @((0.54,0.12),(0.98,0.22),(0.90,0.86),(0.62,0.94),(0.46,0.44))
                )
                Breaches = @(
                    @{ X = 0.52; Y = 0.48; W = 0.20; H = 0.18; Angle = -10.0 }
                )
            }
        }
    }
}

function New-TransparentBitmap {
    param([int]$Width, [int]$Height)
    $bmp = New-Object System.Drawing.Bitmap $Width, $Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.Clear([System.Drawing.Color]::Transparent)
    } finally {
        $g.Dispose()
    }
    return $bmp
}

function ConvertTo-Polygon {
    param(
        [object[]]$NormalizedPoints,
        [int]$Width,
        [int]$Height
    )

    $points = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
    foreach ($pair in $NormalizedPoints) {
        $px = [double]$pair[0] * $Width
        $py = [double]$pair[1] * $Height
        $points.Add((New-PointF -X $px -Y $py))
    }
    return $points.ToArray()
}

function Save-Png {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string]$Path
    )
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Add-CrackStripes {
    param(
        [System.Drawing.Graphics]$Graphics,
        [int]$Width,
        [int]$Height,
        [int]$Variant
    )

    $pens = @(
        (New-Object System.Drawing.Pen -ArgumentList ((New-Color 70 18 16 16), [single]([Math]::Max(2, $Width * 0.012)))),
        (New-Object System.Drawing.Pen -ArgumentList ((New-Color 48 255 180 96), [single]([Math]::Max(1, $Width * 0.004))))
    )
    try {
        $Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $offset = ($Variant % 3) * 0.06
        $Graphics.DrawLine($pens[0], [single]($Width * (0.16 + $offset)), [single]($Height * 0.18), [single]($Width * (0.68 + $offset * 0.25)), [single]($Height * 0.82))
        $Graphics.DrawLine($pens[1], [single]($Width * (0.22 + $offset)), [single]($Height * 0.28), [single]($Width * (0.54 + $offset * 0.20)), [single]($Height * 0.72))
    } finally {
        foreach ($pen in $pens) {
            $pen.Dispose()
        }
    }
}

function New-ChunkBitmap {
    param(
        [System.Drawing.Bitmap]$Source,
        [object[]]$NormalizedPolygon,
        [int]$Variant
    )

    $width = $Source.Width
    $height = $Source.Height
    $out = New-TransparentBitmap -Width $width -Height $height
    $g = [System.Drawing.Graphics]::FromImage($out)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $poly = ConvertTo-Polygon -NormalizedPoints $NormalizedPolygon -Width $width -Height $height
    $texture = New-Object System.Drawing.TextureBrush $Source

    try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
        $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

        $path.AddPolygon($poly)
        $g.FillPath($texture, $path)

        $shadowBrush = New-Object System.Drawing.SolidBrush (New-Color 72 10 10 10)
        $glowBrush = New-Object System.Drawing.SolidBrush (New-Color 54 255 170 90)
        $outlinePen = New-Object System.Drawing.Pen -ArgumentList ((New-Color 110 32 26 22), [single]([Math]::Max(2, $width * 0.012)))
        try {
            $g.FillEllipse($shadowBrush, [single]($width * 0.40), [single]($height * (0.38 + ($Variant % 2) * 0.05)), [single]($width * 0.20), [single]($height * 0.18))
            $g.FillEllipse($glowBrush, [single]($width * 0.45), [single]($height * (0.42 + ($Variant % 2) * 0.04)), [single]($width * 0.08), [single]($height * 0.07))
            $g.DrawPath($outlinePen, $path)
        } finally {
            $outlinePen.Dispose()
            $shadowBrush.Dispose()
            $glowBrush.Dispose()
        }

        Add-CrackStripes -Graphics $g -Width $width -Height $height -Variant $Variant
    } finally {
        $texture.Dispose()
        $path.Dispose()
        $g.Dispose()
    }

    return $out
}

function New-BreachBitmap {
    param(
        [System.Drawing.Bitmap]$Source,
        [hashtable]$BreachSpec,
        [int]$Variant
    )

    $width = $Source.Width
    $height = $Source.Height
    $out = New-TransparentBitmap -Width $width -Height $height
    $g = [System.Drawing.Graphics]::FromImage($out)
    try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

        $cx = [single]($width * [double]$BreachSpec.X)
        $cy = [single]($height * [double]$BreachSpec.Y)
        $bw = [single]($width * [double]$BreachSpec.W)
        $bh = [single]($height * [double]$BreachSpec.H)
        $angle = [single]([double]$BreachSpec.Angle + ($Variant * 4.0))

        $state = $g.Save()
        $g.TranslateTransform($cx, $cy)
        $g.RotateTransform($angle)

        $soot = New-Object System.Drawing.SolidBrush (New-Color 138 12 12 14)
        $core = New-Object System.Drawing.SolidBrush (New-Color 92 0 0 0)
        $glow = New-Object System.Drawing.SolidBrush (New-Color 66 255 184 96)
        $ring = New-Object System.Drawing.Pen -ArgumentList ((New-Color 128 188 106 54), [single]([Math]::Max(2, $width * 0.012)))
        try {
            $g.FillEllipse($soot, -$bw * 0.62, -$bh * 0.58, $bw * 1.24, $bh * 1.16)
            $g.FillEllipse($core, -$bw * 0.44, -$bh * 0.42, $bw * 0.88, $bh * 0.84)
            $g.FillEllipse($glow, -$bw * 0.14, -$bh * 0.12, $bw * 0.28, $bh * 0.24)
            $g.DrawEllipse($ring, -$bw * 0.52, -$bh * 0.48, $bw * 1.04, $bh * 0.96)
        } finally {
            $ring.Dispose()
            $glow.Dispose()
            $core.Dispose()
            $soot.Dispose()
        }

        $g.Restore($state)
    } finally {
        $g.Dispose()
    }
    return $out
}

if (-not (Test-Path $sourceDir)) {
    throw "Missing source skin directory: $sourceDir"
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$requestedStems = @{}
foreach ($stemEntry in @($Stems)) {
    if ([string]::IsNullOrWhiteSpace($stemEntry)) { continue }
    foreach ($stem in $stemEntry.Split(',')) {
        if ([string]::IsNullOrWhiteSpace($stem)) { continue }
        $requestedStems[$stem.Trim().ToLowerInvariant()] = $true
    }
}

$skinFiles = @(Get-ChildItem -Path $sourceDir -Filter *.png -File |
    Where-Object {
        $_.BaseName -notmatch $layerSuffixPattern -and
        ($requestedStems.Count -eq 0 -or $requestedStems.ContainsKey($_.BaseName.ToLowerInvariant()))
    } |
    Sort-Object Name)

$generated = 0

foreach ($file in $skinFiles) {
    $stem = $file.BaseName
    $roleKey = Get-RoleKey -Stem $stem
    $profileName = Get-ProfileName -RoleKey $roleKey
    $profile = Get-ChunkProfiles -ProfileName $profileName

    $sourceBitmap = [System.Drawing.Bitmap]::FromFile($file.FullName)
    try {
        for ($i = 0; $i -lt $profile.Chunks.Count; $i++) {
            $chunk = New-ChunkBitmap -Source $sourceBitmap -NormalizedPolygon $profile.Chunks[$i] -Variant $i
            try {
                $target = Join-Path $outputDir ("{0}_chunk_{1:D2}.png" -f $stem, ($i + 1))
                Save-Png -Bitmap $chunk -Path $target
                $generated++
            } finally {
                $chunk.Dispose()
            }
        }

        for ($i = 0; $i -lt $profile.Breaches.Count; $i++) {
            $breach = New-BreachBitmap -Source $sourceBitmap -BreachSpec $profile.Breaches[$i] -Variant $i
            try {
                $target = Join-Path $outputDir ("{0}_breach_{1:D2}.png" -f $stem, ($i + 1))
                Save-Png -Bitmap $breach -Path $target
                $generated++
            } finally {
                $breach.Dispose()
            }
        }
    } finally {
        $sourceBitmap.Dispose()
    }
}

$manifestPath = Join-Path $outputDir "manifest.txt"
$manifest = @(Get-ChildItem -Path $outputDir -Filter *.png -File | Sort-Object Name | ForEach-Object { $_.Name })
[System.IO.File]::WriteAllLines($manifestPath, $manifest)

Write-Output ("[ship-wreck-gen] skins={0} generated={1} out={2}" -f @($skinFiles).Count, $generated, $outputDir)
