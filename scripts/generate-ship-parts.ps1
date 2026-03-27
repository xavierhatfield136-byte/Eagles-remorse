Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot "assets\ship_skins"
$outputDir = Join-Path $repoRoot "assets\ship_parts"

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
        $points.Add((New-Object System.Drawing.PointF ([single]([double]$pair[0] * $Width)), ([single]([double]$pair[1] * $Height))))
    }
    return $points.ToArray()
}

function Save-Png {
    param([System.Drawing.Bitmap]$Bitmap, [string]$Path)
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function New-PartBitmap {
    param(
        [System.Drawing.Bitmap]$Source,
        [object[]]$NormalizedPolygon
    )

    $width = $Source.Width
    $height = $Source.Height
    $out = New-TransparentBitmap -Width $width -Height $height
    $g = [System.Drawing.Graphics]::FromImage($out)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $texture = New-Object System.Drawing.TextureBrush $Source
    try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $poly = ConvertTo-Polygon -NormalizedPoints $NormalizedPolygon -Width $width -Height $height
        $path.AddPolygon($poly)
        $g.FillPath($texture, $path)
    } finally {
        $texture.Dispose()
        $path.Dispose()
        $g.Dispose()
    }
    return $out
}

if (-not (Test-Path $sourceDir)) {
    throw "Missing source skin directory: $sourceDir"
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$profiles = @{
    "base" = @(
        @((0.02,0.18),(0.24,0.10),(0.34,0.34),(0.20,0.84),(0.04,0.76)),
        @((0.20,0.10),(0.46,0.08),(0.50,0.36),(0.38,0.92),(0.14,0.78)),
        @((0.44,0.08),(0.74,0.10),(0.76,0.40),(0.60,0.92),(0.34,0.58)),
        @((0.70,0.16),(0.98,0.22),(0.94,0.84),(0.76,0.92),(0.54,0.48))
    )
    "miner" = @(
        @((0.02,0.26),(0.26,0.14),(0.38,0.34),(0.24,0.80),(0.04,0.72)),
        @((0.24,0.14),(0.58,0.14),(0.60,0.46),(0.42,0.86),(0.18,0.66)),
        @((0.54,0.18),(0.98,0.24),(0.94,0.80),(0.68,0.88),(0.44,0.48))
    )
    "hauler" = @(
        @((0.02,0.24),(0.24,0.12),(0.38,0.34),(0.24,0.82),(0.04,0.74)),
        @((0.22,0.12),(0.58,0.12),(0.60,0.46),(0.42,0.88),(0.18,0.68)),
        @((0.54,0.18),(0.98,0.24),(0.94,0.82),(0.70,0.90),(0.44,0.50))
    )
    "transport" = @(
        @((0.02,0.24),(0.24,0.12),(0.36,0.34),(0.22,0.80),(0.04,0.72)),
        @((0.22,0.12),(0.56,0.12),(0.58,0.46),(0.40,0.88),(0.18,0.68)),
        @((0.52,0.18),(0.98,0.24),(0.94,0.80),(0.68,0.88),(0.42,0.48))
    )
    "patrol" = @(
        @((0.02,0.26),(0.28,0.12),(0.40,0.34),(0.24,0.82),(0.04,0.72)),
        @((0.26,0.12),(0.62,0.14),(0.60,0.48),(0.42,0.86),(0.18,0.64)),
        @((0.58,0.18),(0.98,0.22),(0.92,0.78),(0.70,0.84),(0.46,0.46))
    )
    "picket" = @(
        @((0.02,0.26),(0.28,0.12),(0.40,0.34),(0.24,0.82),(0.04,0.72)),
        @((0.26,0.12),(0.62,0.14),(0.60,0.48),(0.42,0.86),(0.18,0.64)),
        @((0.58,0.18),(0.98,0.22),(0.92,0.78),(0.70,0.84),(0.46,0.46))
    )
    "pd_craft" = @(
        @((0.02,0.22),(0.30,0.10),(0.44,0.34),(0.24,0.88),(0.04,0.72)),
        @((0.28,0.10),(0.64,0.14),(0.60,0.52),(0.42,0.88),(0.18,0.60)),
        @((0.58,0.16),(0.98,0.24),(0.90,0.80),(0.68,0.88),(0.42,0.46))
    )
    "ciws_corvette" = @(
        @((0.02,0.20),(0.24,0.10),(0.36,0.34),(0.22,0.82),(0.04,0.74)),
        @((0.22,0.10),(0.54,0.12),(0.58,0.42),(0.42,0.88),(0.18,0.68)),
        @((0.50,0.16),(0.98,0.22),(0.94,0.80),(0.70,0.88),(0.40,0.48))
    )
    "static_turret" = @(
        @((0.06,0.18),(0.44,0.12),(0.48,0.50),(0.30,0.88),(0.06,0.72)),
        @((0.36,0.12),(0.74,0.16),(0.72,0.54),(0.48,0.88),(0.24,0.48)),
        @((0.64,0.18),(0.98,0.24),(0.92,0.74),(0.72,0.82),(0.48,0.44))
    )
    "frigate" = @(
        @((0.02,0.24),(0.30,0.12),(0.42,0.34),(0.28,0.78),(0.04,0.70)),
        @((0.28,0.14),(0.62,0.14),(0.60,0.50),(0.42,0.86),(0.20,0.64)),
        @((0.56,0.18),(0.98,0.24),(0.92,0.74),(0.68,0.82),(0.46,0.46))
    )
    "missile_boat" = @(
        @((0.04,0.20),(0.34,0.10),(0.50,0.38),(0.34,0.92),(0.06,0.80)),
        @((0.30,0.10),(0.70,0.12),(0.66,0.52),(0.46,0.92),(0.22,0.68)),
        @((0.56,0.12),(0.98,0.22),(0.92,0.84),(0.66,0.92),(0.48,0.42))
    )
    "cruiser" = @(
        @((0.02,0.20),(0.24,0.10),(0.34,0.34),(0.20,0.84),(0.04,0.74)),
        @((0.20,0.10),(0.46,0.10),(0.48,0.38),(0.34,0.90),(0.14,0.72)),
        @((0.42,0.10),(0.72,0.12),(0.70,0.42),(0.54,0.86),(0.34,0.56)),
        @((0.66,0.18),(0.98,0.22),(0.94,0.78),(0.72,0.88),(0.54,0.46))
    )
    "light_cruiser" = @(
        @((0.02,0.22),(0.22,0.10),(0.34,0.32),(0.22,0.80),(0.04,0.72)),
        @((0.20,0.12),(0.44,0.10),(0.48,0.38),(0.34,0.88),(0.16,0.70)),
        @((0.40,0.10),(0.70,0.12),(0.70,0.42),(0.54,0.86),(0.34,0.54)),
        @((0.66,0.18),(0.98,0.24),(0.92,0.80),(0.72,0.88),(0.52,0.46))
    )
    "medium_cruiser" = @(
        @((0.02,0.20),(0.24,0.08),(0.36,0.34),(0.22,0.84),(0.04,0.76)),
        @((0.20,0.10),(0.46,0.10),(0.50,0.40),(0.36,0.92),(0.16,0.72)),
        @((0.42,0.10),(0.72,0.12),(0.72,0.44),(0.56,0.88),(0.34,0.56)),
        @((0.66,0.18),(0.98,0.22),(0.94,0.82),(0.72,0.90),(0.52,0.48))
    )
    "battlecruiser" = @(
        @((0.02,0.18),(0.24,0.08),(0.38,0.38),(0.24,0.86),(0.04,0.78)),
        @((0.18,0.08),(0.48,0.08),(0.50,0.42),(0.34,0.94),(0.12,0.74)),
        @((0.40,0.10),(0.72,0.10),(0.70,0.46),(0.54,0.88),(0.34,0.56)),
        @((0.64,0.16),(0.98,0.22),(0.94,0.84),(0.72,0.92),(0.56,0.46))
    )
    "carrier" = @(
        @((0.02,0.18),(0.20,0.08),(0.34,0.32),(0.24,0.82),(0.04,0.72)),
        @((0.18,0.08),(0.44,0.08),(0.48,0.36),(0.36,0.92),(0.14,0.76)),
        @((0.40,0.08),(0.70,0.10),(0.72,0.42),(0.56,0.90),(0.34,0.54)),
        @((0.66,0.14),(0.98,0.20),(0.94,0.84),(0.72,0.92),(0.52,0.46))
    )
    "drone_carrier" = @(
        @((0.02,0.18),(0.20,0.08),(0.34,0.32),(0.22,0.82),(0.04,0.74)),
        @((0.18,0.08),(0.42,0.08),(0.48,0.34),(0.36,0.92),(0.14,0.78)),
        @((0.38,0.08),(0.68,0.10),(0.72,0.42),(0.56,0.90),(0.32,0.54)),
        @((0.64,0.14),(0.98,0.20),(0.94,0.84),(0.74,0.92),(0.50,0.48))
    )
    "battleship" = @(
        @((0.02,0.16),(0.18,0.08),(0.30,0.28),(0.22,0.82),(0.04,0.72)),
        @((0.14,0.08),(0.34,0.08),(0.42,0.32),(0.34,0.92),(0.12,0.78)),
        @((0.30,0.08),(0.54,0.08),(0.58,0.40),(0.46,0.94),(0.24,0.74)),
        @((0.50,0.10),(0.76,0.12),(0.78,0.42),(0.62,0.90),(0.42,0.58)),
        @((0.72,0.18),(0.98,0.24),(0.94,0.84),(0.78,0.92),(0.60,0.48))
    )
    "dreadnought" = @(
        @((0.02,0.14),(0.18,0.06),(0.30,0.28),(0.22,0.84),(0.04,0.74)),
        @((0.14,0.08),(0.34,0.06),(0.44,0.32),(0.34,0.94),(0.12,0.80)),
        @((0.30,0.08),(0.54,0.08),(0.58,0.42),(0.46,0.96),(0.24,0.74)),
        @((0.50,0.10),(0.78,0.12),(0.78,0.44),(0.62,0.90),(0.42,0.58)),
        @((0.74,0.16),(0.98,0.22),(0.94,0.86),(0.78,0.92),(0.60,0.48))
    )
    "stealth_ship" = @(
        @((0.02,0.24),(0.30,0.08),(0.42,0.28),(0.26,0.84),(0.04,0.72)),
        @((0.28,0.08),(0.56,0.10),(0.58,0.36),(0.42,0.90),(0.22,0.62)),
        @((0.52,0.12),(0.80,0.16),(0.78,0.42),(0.60,0.88),(0.40,0.54)),
        @((0.74,0.18),(0.98,0.24),(0.94,0.78),(0.78,0.88),(0.58,0.46))
    )
    "supership" = @(
        @((0.01,0.16),(0.18,0.06),(0.32,0.34),(0.22,0.88),(0.04,0.74)),
        @((0.14,0.08),(0.36,0.06),(0.46,0.34),(0.34,0.94),(0.12,0.78)),
        @((0.32,0.08),(0.56,0.08),(0.58,0.42),(0.46,0.94),(0.26,0.72)),
        @((0.52,0.10),(0.78,0.12),(0.76,0.44),(0.62,0.88),(0.44,0.58)),
        @((0.72,0.18),(0.98,0.24),(0.94,0.84),(0.78,0.92),(0.62,0.48))
    )
}

$skinFiles = foreach ($roleKey in $profiles.Keys) {
    Get-ChildItem $sourceDir -File ($roleKey + "*_albedo.png")
}
$skinFiles = $skinFiles | Sort-Object Name
$manifest = New-Object 'System.Collections.Generic.List[string]'
$generated = 0

foreach ($file in $skinFiles) {
    $stem = $file.BaseName -replace "_albedo$", ""
    $roleKey = $null
    foreach ($candidate in ($profiles.Keys | Sort-Object Length -Descending)) {
        if ($stem.StartsWith($candidate + "_")) {
            $roleKey = $candidate
            break
        }
    }
    if (-not $roleKey) { continue }
    $polygons = $profiles[$roleKey]
    $bitmap = [System.Drawing.Bitmap]::FromFile($file.FullName)
    try {
        for ($i = 0; $i -lt $polygons.Count; $i++) {
            $part = New-PartBitmap -Source $bitmap -NormalizedPolygon $polygons[$i]
            try {
                $target = Join-Path $outputDir ("{0}_part_{1:D2}.png" -f $stem, ($i + 1))
                Save-Png -Bitmap $part -Path $target
                $manifest.Add((Split-Path -Leaf $target))
                $generated++
            } finally {
                $part.Dispose()
            }
        }
    } finally {
        $bitmap.Dispose()
    }
}

$manifestPath = Join-Path $outputDir "manifest.txt"
[System.IO.File]::WriteAllLines($manifestPath, $manifest)

Write-Output ("[ship-part-gen] skins={0} generated={1} out={2}" -f $skinFiles.Count, $generated, $outputDir)
