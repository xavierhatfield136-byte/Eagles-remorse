param(
    [string[]]$Stems = @(),
    [switch]$Overwrite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies @("System.Drawing") -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class DestroyedPartMaskHelper
{
    public sealed class MaskInfo
    {
        public Rectangle Bounds { get; set; }
        public PointF[] EdgePoints { get; set; }
        public PointF[] InteriorPoints { get; set; }
    }

    public static MaskInfo Analyze(Bitmap bitmap, byte alphaThreshold)
    {
        if (bitmap == null)
        {
            return new MaskInfo
            {
                Bounds = Rectangle.Empty,
                EdgePoints = Array.Empty<PointF>(),
                InteriorPoints = Array.Empty<PointF>()
            };
        }

        Rectangle rect = new Rectangle(0, 0, bitmap.Width, bitmap.Height);
        BitmapData data = bitmap.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        try
        {
            int stride = data.Stride;
            int length = Math.Abs(stride) * bitmap.Height;
            byte[] pixels = new byte[length];
            Marshal.Copy(data.Scan0, pixels, 0, length);

            int minX = bitmap.Width;
            int minY = bitmap.Height;
            int maxX = -1;
            int maxY = -1;
            List<PointF> edge = new List<PointF>();
            List<PointF> interior = new List<PointF>();

            for (int y = 0; y < bitmap.Height; y++)
            {
                int row = y * stride;
                int left = -1;
                int right = -1;
                for (int x = 0; x < bitmap.Width; x++)
                {
                    int alpha = pixels[row + (x * 4) + 3];
                    if (alpha <= alphaThreshold)
                    {
                        continue;
                    }

                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;

                    if (left < 0) left = x;
                    right = x;
                }

                if (left >= 0)
                {
                    if ((y % 4) == 0)
                    {
                        edge.Add(new PointF(left, y));
                        if (right > left)
                        {
                            edge.Add(new PointF(right, y));
                        }
                    }

                    int span = right - left;
                    if (span > 8 && (y % 6) == 0)
                    {
                        interior.Add(new PointF(left + span * 0.32f, y));
                        interior.Add(new PointF(left + span * 0.50f, y));
                        interior.Add(new PointF(left + span * 0.68f, y));
                    }
                }
            }

            for (int x = 0; x < bitmap.Width; x += 4)
            {
                int top = -1;
                int bottom = -1;
                for (int y = 0; y < bitmap.Height; y++)
                {
                    int alpha = pixels[(y * stride) + (x * 4) + 3];
                    if (alpha <= alphaThreshold)
                    {
                        continue;
                    }

                    if (top < 0) top = y;
                    bottom = y;
                }

                if (top >= 0)
                {
                    edge.Add(new PointF(x, top));
                    if (bottom > top)
                    {
                        edge.Add(new PointF(x, bottom));
                    }
                }
            }

            Rectangle bounds = (maxX < minX || maxY < minY)
                ? Rectangle.Empty
                : Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);

            return new MaskInfo
            {
                Bounds = bounds,
                EdgePoints = edge.ToArray(),
                InteriorPoints = interior.ToArray()
            };
        }
        finally
        {
            bitmap.UnlockBits(data);
        }
    }

    public static void ApplySourceAlphaMask(Bitmap source, Bitmap target)
    {
        if (source == null || target == null)
        {
            return;
        }

        Rectangle rect = new Rectangle(0, 0, Math.Min(source.Width, target.Width), Math.Min(source.Height, target.Height));
        BitmapData sourceData = source.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        BitmapData targetData = target.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        try
        {
            int sourceStride = sourceData.Stride;
            int targetStride = targetData.Stride;
            int sourceLength = Math.Abs(sourceStride) * rect.Height;
            int targetLength = Math.Abs(targetStride) * rect.Height;
            byte[] sourcePixels = new byte[sourceLength];
            byte[] targetPixels = new byte[targetLength];
            Marshal.Copy(sourceData.Scan0, sourcePixels, 0, sourceLength);
            Marshal.Copy(targetData.Scan0, targetPixels, 0, targetLength);

            for (int y = 0; y < rect.Height; y++)
            {
                int sourceRow = y * sourceStride;
                int targetRow = y * targetStride;
                for (int x = 0; x < rect.Width; x++)
                {
                    int sourceIndex = sourceRow + (x * 4);
                    int targetIndex = targetRow + (x * 4);
                    int sourceAlpha = sourcePixels[sourceIndex + 3];
                    if (sourceAlpha <= 0)
                    {
                        targetPixels[targetIndex + 0] = 0;
                        targetPixels[targetIndex + 1] = 0;
                        targetPixels[targetIndex + 2] = 0;
                        targetPixels[targetIndex + 3] = 0;
                        continue;
                    }

                    targetPixels[targetIndex + 3] = (byte)((targetPixels[targetIndex + 3] * sourceAlpha + 127) / 255);
                }
            }

            Marshal.Copy(targetPixels, 0, targetData.Scan0, targetLength);
        }
        finally
        {
            source.UnlockBits(sourceData);
            target.UnlockBits(targetData);
        }
    }
}
"@

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot "assets\ship_parts"
$outputDir = $sourceDir

foreach ($arg in @($args)) {
    if ($null -eq $arg) { continue }
    $argText = $arg.ToString().Trim()
    if ($argText.ToLowerInvariant() -eq "--overwrite") {
        $Overwrite = $true
    }
}

$variantProfiles = @(
    [pscustomobject]@{
        Name = "damaged"
        Suffix = "damaged"
        SootAlphaMin = 8
        SootAlphaRange = 10
        RoomDivisor = 13500.0
        RoomMin = 1
        RoomMax = 3
        RoomBaseMin = 0.045
        RoomBaseRange = 0.045
        ImpactDivisor = 9800.0
        ImpactMin = 2
        ImpactMax = 6
        ImpactRadiusMin = 0.045
        ImpactRadiusRange = 0.045
        ImpactOffsetMin = 0.02
        ImpactOffsetRange = 0.07
        ClusterChance = 0.20
        ClusterScaleMin = 0.42
        ClusterScaleRange = 0.14
        ClusterCopiesMin = 1
        ClusterCopiesMax = 1
        FocusChance = 0.00
        FocusRadiusMin = 0.16
        FocusRadiusRange = 0.08
        ExtraImpactMin = 0
        ExtraImpactMax = 0
        ExtraRoomMin = 0
        ExtraRoomMax = 0
        EdgeBreachMin = 0
        EdgeBreachMax = 0
        EdgeBreachRadiusMin = 0.06
        EdgeBreachRadiusRange = 0.04
        StreakMin = 0
        StreakMax = 1
        StreakAlphaMin = 16
        StreakAlphaRange = 12
        StreakLengthMin = 0.10
        StreakLengthRange = 0.10
        RoomStretchXMin = 0.88
        RoomStretchXRange = 0.26
        RoomStretchYMin = 0.88
        RoomStretchYRange = 0.26
        FocusHorizontalBias = 0.65
    },
    [pscustomobject]@{
        Name = "critical"
        Suffix = "critical"
        SootAlphaMin = 16
        SootAlphaRange = 18
        RoomDivisor = 7600.0
        RoomMin = 2
        RoomMax = 6
        RoomBaseMin = 0.06
        RoomBaseRange = 0.08
        ImpactDivisor = 5200.0
        ImpactMin = 4
        ImpactMax = 12
        ImpactRadiusMin = 0.06
        ImpactRadiusRange = 0.08
        ImpactOffsetMin = 0.03
        ImpactOffsetRange = 0.12
        ClusterChance = 0.45
        ClusterScaleMin = 0.58
        ClusterScaleRange = 0.20
        ClusterCopiesMin = 1
        ClusterCopiesMax = 2
        FocusChance = 0.24
        FocusRadiusMin = 0.20
        FocusRadiusRange = 0.10
        ExtraImpactMin = 1
        ExtraImpactMax = 3
        ExtraRoomMin = 0
        ExtraRoomMax = 1
        EdgeBreachMin = 0
        EdgeBreachMax = 1
        EdgeBreachRadiusMin = 0.08
        EdgeBreachRadiusRange = 0.05
        StreakMin = 1
        StreakMax = 3
        StreakAlphaMin = 28
        StreakAlphaRange = 20
        StreakLengthMin = 0.14
        StreakLengthRange = 0.12
        RoomStretchXMin = 0.88
        RoomStretchXRange = 0.26
        RoomStretchYMin = 0.88
        RoomStretchYRange = 0.26
        FocusHorizontalBias = 0.68
    },
    [pscustomobject]@{
        Name = "destroyed"
        Suffix = "destroyed"
        SootAlphaMin = 24
        SootAlphaRange = 24
        RoomDivisor = 8400.0
        RoomMin = 1
        RoomMax = 4
        RoomBaseMin = 0.055
        RoomBaseRange = 0.07
        ImpactDivisor = 1700.0
        ImpactMin = 14
        ImpactMax = 40
        ImpactRadiusMin = 0.075
        ImpactRadiusRange = 0.10
        ImpactOffsetMin = 0.02
        ImpactOffsetRange = 0.10
        ClusterChance = 0.96
        ClusterScaleMin = 0.52
        ClusterScaleRange = 0.24
        ClusterCopiesMin = 2
        ClusterCopiesMax = 4
        FocusChance = 0.90
        FocusRadiusMin = 0.22
        FocusRadiusRange = 0.12
        ExtraImpactMin = 5
        ExtraImpactMax = 12
        ExtraRoomMin = 1
        ExtraRoomMax = 3
        EdgeBreachMin = 2
        EdgeBreachMax = 5
        EdgeBreachRadiusMin = 0.10
        EdgeBreachRadiusRange = 0.08
        StreakMin = 3
        StreakMax = 7
        StreakAlphaMin = 42
        StreakAlphaRange = 28
        StreakLengthMin = 0.18
        StreakLengthRange = 0.16
        RoomStretchXMin = 0.88
        RoomStretchXRange = 0.26
        RoomStretchYMin = 0.88
        RoomStretchYRange = 0.26
        FocusHorizontalBias = 0.70
    }
)

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

function Save-Png {
    param([System.Drawing.Bitmap]$Bitmap, [string]$Path)
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Get-StableSeed {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return 1337
    }
    $hash = 17
    foreach ($ch in $Text.ToCharArray()) {
        $hash = (($hash * 31) + [int][char]$ch) -band 0x7fffffff
    }
    return [Math]::Abs($hash)
}

function Copy-ProfileObject {
    param($Profile)

    $copy = [pscustomobject]@{}
    if ($null -eq $Profile) { return $copy }
    foreach ($prop in $Profile.PSObject.Properties) {
        Add-Member -InputObject $copy -MemberType NoteProperty -Name $prop.Name -Value $prop.Value
    }
    return $copy
}

function Resolve-RoleDamageStyle {
    param([string]$SeedText)

    if ([string]::IsNullOrWhiteSpace($SeedText)) {
        return [pscustomobject]@{ Key = "default" }
    }

    $roleKey = ($SeedText -replace '_(ally|enemy|team_c|team_d)$', '').ToLowerInvariant()
    switch -Regex ($roleKey) {
        '^(carrier|drone_carrier)$' {
            return [pscustomobject]@{
                Key = "carrier"
                RoomCountScale = 1.35
                ImpactCountScale = 0.82
                EdgeCountScale = 1.35
                StreakCountScale = 1.55
                FocusChanceScale = 1.08
                FocusRadiusScale = 1.18
                SootAlphaBonus = 8
                RoomStretchXScale = 1.55
                RoomStretchYScale = 0.72
                FocusHorizontalBias = 0.88
            }
        }
        '^(battlecruiser|battleship|dreadnought|supership)$' {
            return [pscustomobject]@{
                Key = "heavy_capital"
                RoomCountScale = 0.82
                ImpactCountScale = 1.30
                EdgeCountScale = 1.18
                StreakCountScale = 0.92
                FocusChanceScale = 1.05
                FocusRadiusScale = 0.96
                SootAlphaBonus = 6
                RoomStretchXScale = 0.94
                RoomStretchYScale = 0.94
                FocusHorizontalBias = 0.72
            }
        }
        '^(base)$' {
            return [pscustomobject]@{
                Key = "station"
                RoomCountScale = 1.28
                ImpactCountScale = 0.94
                EdgeCountScale = 1.60
                StreakCountScale = 0.72
                FocusChanceScale = 0.66
                FocusRadiusScale = 1.28
                SootAlphaBonus = 10
                RoomStretchXScale = 1.05
                RoomStretchYScale = 1.05
                FocusHorizontalBias = 0.44
            }
        }
        '^(transport|hauler|miner)$' {
            return [pscustomobject]@{
                Key = "industrial"
                RoomCountScale = 1.30
                ImpactCountScale = 0.86
                EdgeCountScale = 1.08
                StreakCountScale = 1.20
                FocusChanceScale = 1.00
                FocusRadiusScale = 1.10
                SootAlphaBonus = 4
                RoomStretchXScale = 1.30
                RoomStretchYScale = 0.84
                FocusHorizontalBias = 0.74
            }
        }
        '^(stealth_ship)$' {
            return [pscustomobject]@{
                Key = "stealth"
                RoomCountScale = 0.70
                ImpactCountScale = 1.16
                EdgeCountScale = 0.92
                StreakCountScale = 1.05
                FocusChanceScale = 1.00
                FocusRadiusScale = 0.90
                SootAlphaBonus = 2
                RoomStretchXScale = 0.88
                RoomStretchYScale = 0.88
                FocusHorizontalBias = 0.72
            }
        }
        default {
            return [pscustomobject]@{ Key = "default" }
        }
    }
}

function Get-StyledProfile {
    param(
        $Profile,
        [string]$SeedText
    )

    $styled = Copy-ProfileObject -Profile $Profile
    $style = Resolve-RoleDamageStyle -SeedText $SeedText
    Add-Member -InputObject $styled -MemberType NoteProperty -Name RoleStyleKey -Value $style.Key -Force
    if ($style.Key -eq "default") {
        return $styled
    }

    $roomScale = if ($null -ne $style.RoomCountScale) { [double]$style.RoomCountScale } else { 1.0 }
    $impactScale = if ($null -ne $style.ImpactCountScale) { [double]$style.ImpactCountScale } else { 1.0 }
    $edgeScale = if ($null -ne $style.EdgeCountScale) { [double]$style.EdgeCountScale } else { 1.0 }
    $streakScale = if ($null -ne $style.StreakCountScale) { [double]$style.StreakCountScale } else { 1.0 }
    $focusChanceScale = if ($null -ne $style.FocusChanceScale) { [double]$style.FocusChanceScale } else { 1.0 }
    $focusRadiusScale = if ($null -ne $style.FocusRadiusScale) { [double]$style.FocusRadiusScale } else { 1.0 }
    $roomStretchXScale = if ($null -ne $style.RoomStretchXScale) { [double]$style.RoomStretchXScale } else { 1.0 }
    $roomStretchYScale = if ($null -ne $style.RoomStretchYScale) { [double]$style.RoomStretchYScale } else { 1.0 }
    $sootBonus = if ($null -ne $style.SootAlphaBonus) { [int]$style.SootAlphaBonus } else { 0 }

    $styled.RoomMin = [Math]::Max(0, [int][Math]::Round([double]$styled.RoomMin * $roomScale))
    $styled.RoomMax = [Math]::Max($styled.RoomMin, [int][Math]::Round([double]$styled.RoomMax * $roomScale))
    $styled.ImpactMin = [Math]::Max(0, [int][Math]::Round([double]$styled.ImpactMin * $impactScale))
    $styled.ImpactMax = [Math]::Max($styled.ImpactMin, [int][Math]::Round([double]$styled.ImpactMax * $impactScale))
    $styled.EdgeBreachMin = [Math]::Max(0, [int][Math]::Round([double]$styled.EdgeBreachMin * $edgeScale))
    $styled.EdgeBreachMax = [Math]::Max($styled.EdgeBreachMin, [int][Math]::Round([double]$styled.EdgeBreachMax * $edgeScale))
    $styled.StreakMin = [Math]::Max(0, [int][Math]::Round([double]$styled.StreakMin * $streakScale))
    $styled.StreakMax = [Math]::Max($styled.StreakMin, [int][Math]::Round([double]$styled.StreakMax * $streakScale))
    $styled.FocusChance = Clamp-Value -Value ([double]$styled.FocusChance * $focusChanceScale) -Min 0.0 -Max 1.0
    $styled.FocusRadiusMin = [double]$styled.FocusRadiusMin * $focusRadiusScale
    $styled.FocusRadiusRange = [double]$styled.FocusRadiusRange * $focusRadiusScale
    $styled.SootAlphaMin = [Math]::Max(0, [int]$styled.SootAlphaMin + $sootBonus)
    $styled.RoomStretchXMin = [double]$styled.RoomStretchXMin * $roomStretchXScale
    $styled.RoomStretchXRange = [double]$styled.RoomStretchXRange * $roomStretchXScale
    $styled.RoomStretchYMin = [double]$styled.RoomStretchYMin * $roomStretchYScale
    $styled.RoomStretchYRange = [double]$styled.RoomStretchYRange * $roomStretchYScale
    if ($null -ne $style.FocusHorizontalBias) {
        $styled.FocusHorizontalBias = Clamp-Value -Value ([double]$style.FocusHorizontalBias) -Min 0.10 -Max 0.95
    }
    return $styled
}

function New-PointF {
    param([double]$X, [double]$Y)
    return New-Object System.Drawing.PointF ([single]$X), ([single]$Y)
}

function New-Color {
    param(
        [int]$A,
        [int]$R,
        [int]$G,
        [int]$B
    )
    return [System.Drawing.Color]::FromArgb(
        [Math]::Max(0, [Math]::Min(255, $A)),
        [Math]::Max(0, [Math]::Min(255, $R)),
        [Math]::Max(0, [Math]::Min(255, $G)),
        [Math]::Max(0, [Math]::Min(255, $B))
    )
}

function Get-RandomPoint {
    param(
        [System.Drawing.PointF[]]$Points,
        [System.Random]$Random,
        [System.Drawing.PointF]$Fallback
    )

    if ($null -eq $Points -or $Points.Length -eq 0) {
        return $Fallback
    }
    return $Points[$Random.Next($Points.Length)]
}

function Get-ImpactAnchor {
    param(
        [System.Drawing.PointF[]]$InteriorPoints,
        [System.Drawing.PointF[]]$EdgePoints,
        [System.Random]$Random,
        [System.Drawing.PointF]$Fallback
    )

    $hasInterior = $null -ne $InteriorPoints -and $InteriorPoints.Length -gt 0
    $hasEdge = $null -ne $EdgePoints -and $EdgePoints.Length -gt 0
    if ($hasInterior) {
        if (-not $hasEdge -or $Random.NextDouble() -lt 0.78) {
            return $InteriorPoints[$Random.Next($InteriorPoints.Length)]
        }
    }
    if ($hasEdge) {
        return $EdgePoints[$Random.Next($EdgePoints.Length)]
    }
    return $Fallback
}

function Merge-PointArrays {
    param(
        [System.Drawing.PointF[]]$First,
        [System.Drawing.PointF[]]$Second
    )

    $out = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
    if ($null -ne $First) {
        foreach ($p in $First) { $out.Add($p) }
    }
    if ($null -ne $Second) {
        foreach ($p in $Second) { $out.Add($p) }
    }
    return $out.ToArray()
}

function New-DamageFocus {
    param(
        [System.Drawing.Rectangle]$Bounds,
        [System.Random]$Random,
        $Profile
    )

    if ($Bounds.Width -le 0 -or $Bounds.Height -le 0 -or $null -eq $Profile) {
        return $null
    }

    $chance = [double]$Profile.FocusChance
    if ($chance -le 1e-6 -or $Random.NextDouble() -gt $chance) {
        return $null
    }

    $cx = $Bounds.Left + ($Bounds.Width * 0.50)
    $cy = $Bounds.Top + ($Bounds.Height * 0.50)
    $focusX = $cx
    $focusY = $cy
    $horizontalBiasChance = 0.65
    if ($null -ne $Profile.PSObject.Properties["FocusHorizontalBias"]) {
        $horizontalBiasChance = [double]$Profile.FocusHorizontalBias
    }
    $horizontalBias = ($Bounds.Width -ge ($Bounds.Height * 1.10)) -or ($Random.NextDouble() -lt $horizontalBiasChance)
    if ($horizontalBias) {
        $side = if ($Random.NextDouble() -lt 0.5) { -1.0 } else { 1.0 }
        $focusX = $cx + $side * $Bounds.Width * (0.20 + $Random.NextDouble() * 0.14)
        $focusY = $cy + ($Random.NextDouble() - 0.5) * $Bounds.Height * 0.34
    } else {
        $side = if ($Random.NextDouble() -lt 0.5) { -1.0 } else { 1.0 }
        $focusX = $cx + ($Random.NextDouble() - 0.5) * $Bounds.Width * 0.28
        $focusY = $cy + $side * $Bounds.Height * (0.16 + $Random.NextDouble() * 0.18)
    }

    $radius = [Math]::Max(
        6.0,
        [Math]::Min($Bounds.Width, $Bounds.Height) * ([double]$Profile.FocusRadiusMin + $Random.NextDouble() * [double]$Profile.FocusRadiusRange)
    )

    $normX = 0.0
    $normY = 0.0
    if ($Bounds.Width -gt 0) {
        $normX = ($focusX - $cx) / ([Math]::Max(1.0, $Bounds.Width * 0.5))
    }
    if ($Bounds.Height -gt 0) {
        $normY = ($focusY - $cy) / ([Math]::Max(1.0, $Bounds.Height * 0.5))
    }

    return [pscustomobject]@{
        Center = (New-PointF -X $focusX -Y $focusY)
        Radius = $radius
        FocusXNorm = (Clamp-Value -Value $normX -Min -1.0 -Max 1.0)
        FocusYNorm = (Clamp-Value -Value $normY -Min -1.0 -Max 1.0)
    }
}

function Get-FocusedPoint {
    param(
        [System.Drawing.PointF[]]$Points,
        [System.Drawing.PointF]$Center,
        [double]$Radius,
        [System.Random]$Random,
        [System.Drawing.PointF]$Fallback
    )

    if ($null -eq $Points -or $Points.Length -eq 0 -or $null -eq $Center) {
        return $Fallback
    }

    $candidates = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
    $best = $Fallback
    $bestDistSq = [double]::PositiveInfinity
    $radiusSq = $Radius * $Radius
    foreach ($point in $Points) {
        $dx = $point.X - $Center.X
        $dy = $point.Y - $Center.Y
        $distSq = ($dx * $dx) + ($dy * $dy)
        if ($distSq -le $radiusSq) {
            $candidates.Add($point)
        }
        if ($distSq -lt $bestDistSq) {
            $bestDistSq = $distSq
            $best = $point
        }
    }

    if ($candidates.Count -gt 0) {
        return $candidates[$Random.Next($candidates.Count)]
    }
    return $best
}

function Clamp-Value {
    param(
        [double]$Value,
        [double]$Min,
        [double]$Max
    )
    if ($Value -lt $Min) { return $Min }
    if ($Value -gt $Max) { return $Max }
    return $Value
}

function Add-RoomVoids {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Random]$Random,
        [System.Drawing.Rectangle]$Bounds,
        [System.Drawing.PointF[]]$InteriorPoints,
        $Profile,
        $Focus = $null
    )

    if ($Bounds.Width -le 0 -or $Bounds.Height -le 0 -or $null -eq $Profile) {
        return
    }

    $area = $Bounds.Width * $Bounds.Height
    $count = [Math]::Max([int]$Profile.RoomMin, [Math]::Min([int]$Profile.RoomMax, [int][Math]::Round($area / [double]$Profile.RoomDivisor)))
    if ($null -ne $Focus) {
        $count += [int]$Profile.ExtraRoomMin
        if ([int]$Profile.ExtraRoomMax -gt [int]$Profile.ExtraRoomMin) {
            $count += $Random.Next(([int]$Profile.ExtraRoomMax - [int]$Profile.ExtraRoomMin) + 1)
        }
    }
    $fallback = New-PointF -X ($Bounds.Left + $Bounds.Width * 0.50) -Y ($Bounds.Top + $Bounds.Height * 0.50)
    $shadowBrush = New-Object System.Drawing.SolidBrush (New-Color 86 8 8 10)
    $coreBrush = New-Object System.Drawing.SolidBrush (New-Color 228 0 0 0)
    try {
        for ($i = 0; $i -lt $count; $i++) {
            if ($null -ne $Focus -and $Random.NextDouble() -lt 0.78) {
                $anchor = Get-FocusedPoint -Points $InteriorPoints -Center $Focus.Center -Radius $Focus.Radius -Random $Random -Fallback $fallback
            } else {
                $anchor = Get-RandomPoint -Points $InteriorPoints -Random $Random -Fallback $fallback
            }
            $base = [Math]::Max(6.0, [Math]::Min(
                    [Math]::Min($Bounds.Width, $Bounds.Height) * 0.18,
                    [Math]::Min($Bounds.Width, $Bounds.Height) * ([double]$Profile.RoomBaseMin + $Random.NextDouble() * [double]$Profile.RoomBaseRange)
            ))
            $rw = $base * ([double]$Profile.RoomStretchXMin + $Random.NextDouble() * [double]$Profile.RoomStretchXRange)
            $rh = $base * ([double]$Profile.RoomStretchYMin + $Random.NextDouble() * [double]$Profile.RoomStretchYRange)
            $rx = Clamp-Value -Value ($anchor.X - $rw * (0.40 + $Random.NextDouble() * 0.20)) -Min ($Bounds.Left + 1) -Max ($Bounds.Right - $rw - 1)
            $ry = Clamp-Value -Value ($anchor.Y - $rh * (0.40 + $Random.NextDouble() * 0.20)) -Min ($Bounds.Top + 1) -Max ($Bounds.Bottom - $rh - 1)

            $Graphics.FillRectangle($shadowBrush, [single]($rx - 1.5), [single]($ry - 1.5), [single]($rw + 3), [single]($rh + 3))
            $Graphics.FillRectangle($coreBrush, [single]$rx, [single]$ry, [single]$rw, [single]$rh)
        }
    } finally {
        $shadowBrush.Dispose()
        $coreBrush.Dispose()
    }
}

function Add-ImpactCraters {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Random]$Random,
        [System.Drawing.Rectangle]$Bounds,
        [System.Drawing.PointF[]]$EdgePoints,
        [System.Drawing.PointF[]]$InteriorPoints,
        $Profile,
        $Focus = $null
    )

    if ($Bounds.Width -le 0 -or $Bounds.Height -le 0 -or $null -eq $Profile) {
        return
    }

    $area = $Bounds.Width * $Bounds.Height
    $count = [Math]::Max([int]$Profile.ImpactMin, [Math]::Min([int]$Profile.ImpactMax, [int][Math]::Round($area / [double]$Profile.ImpactDivisor)))
    if ($null -ne $Focus) {
        $count += [int]$Profile.ExtraImpactMin
        if ([int]$Profile.ExtraImpactMax -gt [int]$Profile.ExtraImpactMin) {
            $count += $Random.Next(([int]$Profile.ExtraImpactMax - [int]$Profile.ExtraImpactMin) + 1)
        }
    }
    $center = New-PointF -X ($Bounds.Left + $Bounds.Width * 0.50) -Y ($Bounds.Top + $Bounds.Height * 0.50)
    $allPoints = Merge-PointArrays -First $InteriorPoints -Second $EdgePoints
    $coreBrush = New-Object System.Drawing.SolidBrush (New-Color 238 0 0 0)
    try {
        for ($i = 0; $i -lt $count; $i++) {
            if ($null -ne $Focus -and $Random.NextDouble() -lt 0.82) {
                $anchor = Get-FocusedPoint -Points $allPoints -Center $Focus.Center -Radius $Focus.Radius -Random $Random -Fallback $center
            } else {
                $anchor = Get-ImpactAnchor -InteriorPoints $InteriorPoints -EdgePoints $EdgePoints -Random $Random -Fallback $center
            }

            $outerR = [Math]::Max(5.0, [Math]::Min(
                    [Math]::Min($Bounds.Width, $Bounds.Height) * 0.24,
                    [Math]::Min($Bounds.Width, $Bounds.Height) * ([double]$Profile.ImpactRadiusMin + $Random.NextDouble() * [double]$Profile.ImpactRadiusRange)
            ))
            $outerR *= 0.33
            $jitter = $outerR * (0.04 + $Random.NextDouble() * 0.12)
            $cx = $anchor.X + ($Random.NextDouble() - 0.5) * $jitter
            $cy = $anchor.Y + ($Random.NextDouble() - 0.5) * $jitter
            $coreR = [Math]::Max(2.4, $outerR * (0.56 + $Random.NextDouble() * 0.12))

            $Graphics.FillEllipse($coreBrush, [single]($cx - $coreR), [single]($cy - $coreR), [single]($coreR * 2.0), [single]($coreR * 2.0))

            if ($Random.NextDouble() -lt [double]$Profile.ClusterChance) {
                $clusterMin = [Math]::Max(1, [int]$Profile.ClusterCopiesMin)
                $clusterMax = [Math]::Max($clusterMin, [int]$Profile.ClusterCopiesMax)
                $clusterCopies = $clusterMin
                if ($clusterMax -gt $clusterMin) {
                    $clusterCopies += $Random.Next($clusterMax - $clusterMin + 1)
                }

                for ($clusterIndex = 0; $clusterIndex -lt $clusterCopies; $clusterIndex++) {
                    $clusterAngle = $Random.NextDouble() * [Math]::PI * 2.0
                    $clusterDx = [Math]::Cos($clusterAngle)
                    $clusterDy = [Math]::Sin($clusterAngle)
                    $clusterSign = 1.0
                    if ($Random.NextDouble() -lt 0.5) {
                        $clusterSign = -1.0
                    }
                    $clusterOffset = $outerR * (0.52 + $Random.NextDouble() * 0.72) * $clusterSign
                    $clusterScale = [double]$Profile.ClusterScaleMin + $Random.NextDouble() * [double]$Profile.ClusterScaleRange
                    $clusterCoreR = $coreR * $clusterScale
                    $clusterX = $cx + $clusterDx * $clusterOffset
                    $clusterY = $cy + $clusterDy * $clusterOffset
                    $Graphics.FillEllipse($coreBrush, [single]($clusterX - $clusterCoreR), [single]($clusterY - $clusterCoreR), [single]($clusterCoreR * 2.0), [single]($clusterCoreR * 2.0))
                }
            }
        }
    } finally {
        $coreBrush.Dispose()
    }
}

function Add-EdgeBreaches {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Random]$Random,
        [System.Drawing.Rectangle]$Bounds,
        [System.Drawing.PointF[]]$EdgePoints,
        $Profile,
        $Focus = $null
    )

    if ($Bounds.Width -le 0 -or $Bounds.Height -le 0 -or $null -eq $Profile) {
        return
    }

    $count = [Math]::Max([int]$Profile.EdgeBreachMin, [int]$Profile.EdgeBreachMin)
    if ([int]$Profile.EdgeBreachMax -gt [int]$Profile.EdgeBreachMin) {
        $count += $Random.Next(([int]$Profile.EdgeBreachMax - [int]$Profile.EdgeBreachMin) + 1)
    }
    if ($count -le 0) { return }

    $fallback = New-PointF -X ($Bounds.Left + $Bounds.Width * 0.50) -Y ($Bounds.Top + $Bounds.Height * 0.50)
    $oldMode = $Graphics.CompositingMode
    try {
        $Graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $eraseBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::Transparent)
        try {
            for ($i = 0; $i -lt $count; $i++) {
                if ($null -ne $Focus -and $Random.NextDouble() -lt 0.72) {
                    $anchor = Get-FocusedPoint -Points $EdgePoints -Center $Focus.Center -Radius ($Focus.Radius * 1.15) -Random $Random -Fallback $fallback
                } else {
                    $anchor = Get-RandomPoint -Points $EdgePoints -Random $Random -Fallback $fallback
                }
                $radius = [Math]::Max(
                    5.0,
                    [Math]::Min($Bounds.Width, $Bounds.Height) * ([double]$Profile.EdgeBreachRadiusMin + $Random.NextDouble() * [double]$Profile.EdgeBreachRadiusRange)
                )
                $w = $radius * (1.1 + $Random.NextDouble() * 0.7)
                $h = $radius * (0.8 + $Random.NextDouble() * 0.8)
                $x = $anchor.X - $w * (0.40 + $Random.NextDouble() * 0.32)
                $y = $anchor.Y - $h * (0.40 + $Random.NextDouble() * 0.32)
                $Graphics.FillEllipse($eraseBrush, [single]$x, [single]$y, [single]$w, [single]$h)
            }
        } finally {
            $eraseBrush.Dispose()
        }
    } finally {
        $Graphics.CompositingMode = $oldMode
    }
}

function Add-HeatStreaks {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Random]$Random,
        [System.Drawing.Rectangle]$Bounds,
        [System.Drawing.PointF[]]$EdgePoints,
        [System.Drawing.PointF[]]$InteriorPoints,
        $Profile,
        $Focus = $null
    )

    if ($Bounds.Width -le 0 -or $Bounds.Height -le 0 -or $null -eq $Profile) {
        return
    }

    $count = [Math]::Max([int]$Profile.StreakMin, [int]$Profile.StreakMin)
    if ([int]$Profile.StreakMax -gt [int]$Profile.StreakMin) {
        $count += $Random.Next(([int]$Profile.StreakMax - [int]$Profile.StreakMin) + 1)
    }
    if ($count -le 0) { return }

    $fallback = New-PointF -X ($Bounds.Left + $Bounds.Width * 0.50) -Y ($Bounds.Top + $Bounds.Height * 0.50)
    try {
        for ($i = 0; $i -lt $count; $i++) {
            if ($null -ne $Focus -and $Random.NextDouble() -lt 0.74) {
                $start = Get-FocusedPoint -Points (Merge-PointArrays -First $InteriorPoints -Second $EdgePoints) -Center $Focus.Center -Radius ($Focus.Radius * 1.10) -Random $Random -Fallback $fallback
            } else {
                $start = Get-ImpactAnchor -InteriorPoints $InteriorPoints -EdgePoints $EdgePoints -Random $Random -Fallback $fallback
            }

            $angle = $Random.NextDouble() * [Math]::PI * 2.0
            if ($null -ne $Focus) {
                $angle = [Math]::Atan2($start.Y - $Focus.Center.Y, $start.X - $Focus.Center.X) + ($Random.NextDouble() - 0.5) * 0.9
            }
            $length = [Math]::Max(
                8.0,
                [Math]::Min($Bounds.Width, $Bounds.Height) * ([double]$Profile.StreakLengthMin + $Random.NextDouble() * [double]$Profile.StreakLengthRange)
            )
            $endX = $start.X + [Math]::Cos($angle) * $length
            $endY = $start.Y + [Math]::Sin($angle) * $length
            $alpha = ([int]$Profile.StreakAlphaMin) + $Random.Next(([int]$Profile.StreakAlphaRange) + 1)
            $pen = New-Object System.Drawing.Pen -ArgumentList (New-Color $alpha 4 4 5), ([single](1.4 + $Random.NextDouble() * 2.4))
            try {
                $Graphics.DrawLine($pen, [single]$start.X, [single]$start.Y, [single]$endX, [single]$endY)
            } finally {
                $pen.Dispose()
            }
        }
    } finally {
    }
}

function Bake-PartVariant {
    param(
        [System.Drawing.Bitmap]$Source,
        [string]$SeedText,
        $Profile
    )

    $width = $Source.Width
    $height = $Source.Height
    $out = New-TransparentBitmap -Width $width -Height $height
    $g = [System.Drawing.Graphics]::FromImage($out)
    $styledProfile = Get-StyledProfile -Profile $Profile -SeedText $SeedText
    $rng = New-Object System.Random (Get-StableSeed -Text ($SeedText + "|" + $styledProfile.Name + "|" + $styledProfile.RoleStyleKey))
    $analysis = [DestroyedPartMaskHelper]::Analyze($Source, [byte]10)
    try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
        $g.DrawImage($Source, 0, 0, $width, $height)

        if ($analysis.Bounds.Width -gt 0 -and $analysis.Bounds.Height -gt 0) {
            $oldClip = $g.Clip
            try {
                $g.SetClip($analysis.Bounds)

                $sootBrush = New-Object System.Drawing.SolidBrush (New-Color (([int]$styledProfile.SootAlphaMin) + $rng.Next([int]$styledProfile.SootAlphaRange + 1)) 10 10 12)
                try {
                    $g.FillRectangle($sootBrush, $analysis.Bounds)
                } finally {
                    $sootBrush.Dispose()
                }

                $focus = New-DamageFocus -Bounds $analysis.Bounds -Random $rng -Profile $styledProfile
                Add-EdgeBreaches -Graphics $g -Random $rng -Bounds $analysis.Bounds -EdgePoints $analysis.EdgePoints -Profile $styledProfile -Focus $focus
                Add-RoomVoids -Graphics $g -Random $rng -Bounds $analysis.Bounds -InteriorPoints $analysis.InteriorPoints -Profile $styledProfile -Focus $focus
                Add-ImpactCraters -Graphics $g -Random $rng -Bounds $analysis.Bounds -EdgePoints $analysis.EdgePoints -InteriorPoints $analysis.InteriorPoints -Profile $styledProfile -Focus $focus
                Add-HeatStreaks -Graphics $g -Random $rng -Bounds $analysis.Bounds -EdgePoints $analysis.EdgePoints -InteriorPoints $analysis.InteriorPoints -Profile $styledProfile -Focus $focus
            } finally {
                $g.Clip = $oldClip
            }
        }
    } finally {
        $g.Dispose()
    }

    [DestroyedPartMaskHelper]::ApplySourceAlphaMask($Source, $out)
    return [pscustomobject]@{
        Bitmap = $out
        FocusXNorm = $(if ($null -ne $focus) { [double]$focus.FocusXNorm } else { 0.0 })
        FocusYNorm = $(if ($null -ne $focus) { [double]$focus.FocusYNorm } else { 0.0 })
    }
}

function New-ArgbClone {
    param([System.Drawing.Bitmap]$Bitmap)
    $clone = New-Object System.Drawing.Bitmap $Bitmap.Width, $Bitmap.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($clone)
    try {
        $g.DrawImage($Bitmap, 0, 0, $Bitmap.Width, $Bitmap.Height)
    } finally {
        $g.Dispose()
    }
    return $clone
}

if (-not (Test-Path $sourceDir)) {
    throw "Missing part directory: $sourceDir"
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

$sourceFiles = @(Get-ChildItem -Path $sourceDir -File "*_part_*.png" |
    Where-Object { $_.BaseName -notmatch "_(damaged|critical|destroyed|wreck)_part_" } |
    Where-Object {
        if ($requestedStems.Count -eq 0) { return $true }
        $stem = $_.BaseName -replace "_part_\d+$", ""
        return $requestedStems.ContainsKey($stem.ToLowerInvariant())
    } |
    Sort-Object Name)

$created = 0
$skipped = 0
$focusManifestMap = @{}
$focusManifestPath = Join-Path $outputDir "damage_focus_manifest.txt"
if (Test-Path $focusManifestPath) {
    foreach ($line in Get-Content -Path $focusManifestPath) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line.Split('|')
        if ($parts.Length -lt 3) { continue }
        $focusManifestMap[$parts[0]] = $line
    }
}

foreach ($file in $sourceFiles) {
    $bitmap = [System.Drawing.Bitmap]::FromFile($file.FullName)
    try {
        $argbBitmap = New-ArgbClone -Bitmap $bitmap
        try {
            foreach ($profile in $variantProfiles) {
                $targetName = $file.Name -replace "_part_(\d+)\.png$", ("_" + $profile.Suffix + "_part_`$1.png")
                $targetPath = Join-Path $outputDir $targetName
                if ((Test-Path $targetPath) -and -not $Overwrite) {
                    $skipped++
                    continue
                }

                $variantResult = Bake-PartVariant -Source $argbBitmap -SeedText $file.BaseName -Profile $profile
                try {
                    Save-Png -Bitmap $variantResult.Bitmap -Path $targetPath
                    $focusManifestMap[$targetName] = ("{0}|{1}|{2}" -f
                        $targetName,
                        ([double]$variantResult.FocusXNorm).ToString("0.0000", [System.Globalization.CultureInfo]::InvariantCulture),
                        ([double]$variantResult.FocusYNorm).ToString("0.0000", [System.Globalization.CultureInfo]::InvariantCulture))
                    $created++
                } finally {
                    if ($null -ne $variantResult -and $null -ne $variantResult.Bitmap) {
                        $variantResult.Bitmap.Dispose()
                    }
                }
            }
        } finally {
            $argbBitmap.Dispose()
        }
    } finally {
        $bitmap.Dispose()
    }
}

$manifestPath = Join-Path $outputDir "manifest.txt"
[string[]]$manifest = @(Get-ChildItem -Path $outputDir -Filter *.png -File | Sort-Object Name | Select-Object -ExpandProperty Name)
[System.IO.File]::WriteAllLines($manifestPath, $manifest)

[string[]]$focusManifest = @($focusManifestMap.Keys | Sort-Object | ForEach-Object { $focusManifestMap[$_] })
[System.IO.File]::WriteAllLines($focusManifestPath, $focusManifest)

Write-Output ("[destroyed-ship-part-gen] source={0} variants={1} created={2} skipped={3} out={4}" -f @($sourceFiles).Count, $variantProfiles.Count, $created, $skipped, $outputDir)
