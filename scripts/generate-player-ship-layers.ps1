param(
    [int]$Size = 1024,
    [switch]$WriteLegacyAllyPng = $true
)

$ErrorActionPreference = "Stop"

if ($Size -lt 256) {
    throw "Size must be >= 256"
}

Add-Type -AssemblyName System.Drawing

$csharp = @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class ShipSkinOps {
    private static int ClampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static double Clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static uint Hash(int x, int y, int seed) {
        unchecked {
            uint h = (uint)(x * 374761393 + y * 668265263 + seed * 982451653);
            h = (h ^ (h >> 13)) * 1274126177u;
            return h ^ (h >> 16);
        }
    }

    private static double Hash01(int x, int y, int seed) {
        return (Hash(x, y, seed) & 0x00FFFFFF) / 16777215.0;
    }

    private static byte[] ReadBytes(Bitmap bmp, out int stride) {
        Rectangle rect = new Rectangle(0, 0, bmp.Width, bmp.Height);
        BitmapData data = bmp.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        stride = data.Stride;
        int bytes = Math.Abs(stride) * bmp.Height;
        byte[] raw = new byte[bytes];
        Marshal.Copy(data.Scan0, raw, 0, bytes);
        bmp.UnlockBits(data);
        return raw;
    }

    private static void WriteBytes(Bitmap bmp, byte[] raw) {
        Rectangle rect = new Rectangle(0, 0, bmp.Width, bmp.Height);
        BitmapData data = bmp.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(raw, 0, data.Scan0, raw.Length);
        bmp.UnlockBits(data);
    }

    public static byte[] ExtractAlpha(Bitmap bmp) {
        int stride;
        byte[] raw = ReadBytes(bmp, out stride);
        int w = bmp.Width;
        int h = bmp.Height;
        byte[] alpha = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * stride;
            int arow = y * w;
            for (int x = 0; x < w; x++) {
                alpha[arow + x] = raw[row + x * 4 + 3];
            }
        }
        return alpha;
    }

    public static void ApplyAlphaMask(Bitmap bmp, byte[] alpha) {
        int stride;
        byte[] raw = ReadBytes(bmp, out stride);
        int w = bmp.Width;
        int h = bmp.Height;
        for (int y = 0; y < h; y++) {
            int row = y * stride;
            int arow = y * w;
            for (int x = 0; x < w; x++) {
                int p = row + x * 4;
                byte m = alpha[arow + x];
                if (m == 0) {
                    raw[p + 0] = 0;
                    raw[p + 1] = 0;
                    raw[p + 2] = 0;
                    raw[p + 3] = 0;
                    continue;
                }
                if (raw[p + 3] > m) raw[p + 3] = m;
            }
        }
        WriteBytes(bmp, raw);
    }

    public static void StylizeAlbedo(Bitmap bmp, byte[] alpha, int seed) {
        int stride;
        byte[] raw = ReadBytes(bmp, out stride);
        int w = bmp.Width;
        int h = bmp.Height;
        double cx = (w - 1) * 0.5;
        double cy = (h - 1) * 0.5;
        double invW = 1.0 / Math.Max(1.0, w - 1.0);
        double invH = 1.0 / Math.Max(1.0, h - 1.0);

        for (int y = 0; y < h; y++) {
            int row = y * stride;
            int arow = y * w;
            double ny = (y - cy) * 2.0 * invH;
            for (int x = 0; x < w; x++) {
                int p = row + x * 4;
                byte a = alpha[arow + x];
                if (a == 0) {
                    raw[p + 0] = 0;
                    raw[p + 1] = 0;
                    raw[p + 2] = 0;
                    raw[p + 3] = 0;
                    continue;
                }

                double nx = (x - cx) * 2.0 * invW;
                double lum = (raw[p + 2] * 0.2126 + raw[p + 1] * 0.7152 + raw[p + 0] * 0.0722) / 255.0;

                double light = 0.08 + nx * 0.18 - ny * 0.09;
                double grid = 0.0;
                if (((x + seed) % 37) == 0) grid += 0.08;
                if (((y + seed * 3) % 43) == 0) grid += 0.06;

                double noise = (Hash01(x, y, seed + 91) - 0.5) * 0.09;
                double wear = (Hash01(x * 2 + 11, y * 2 + 17, seed + 313) > 0.993) ? -0.24 : 0.0;
                double factor = 1.0 + light + grid + noise + wear;

                double baseR = 82 + lum * 94;
                double baseG = 98 + lum * 102;
                double baseB = 114 + lum * 112;

                if (Math.Abs(ny) < 0.018 && nx > -0.15 && nx < 0.85) {
                    baseR += 8;
                    baseG += 3;
                    baseB += 1;
                }

                int nr = ClampInt((int)Math.Round(baseR * factor), 0, 255);
                int ng = ClampInt((int)Math.Round(baseG * factor), 0, 255);
                int nb = ClampInt((int)Math.Round(baseB * factor), 0, 255);

                raw[p + 0] = (byte)nb;
                raw[p + 1] = (byte)ng;
                raw[p + 2] = (byte)nr;
                raw[p + 3] = a;
            }
        }
        WriteBytes(bmp, raw);
    }

    public static void BuildPanel(Bitmap bmp, byte[] alpha, int seed) {
        int stride;
        byte[] raw = ReadBytes(bmp, out stride);
        int w = bmp.Width;
        int h = bmp.Height;

        for (int y = 0; y < h; y++) {
            int row = y * stride;
            int arow = y * w;
            for (int x = 0; x < w; x++) {
                int p = row + x * 4;
                byte a = alpha[arow + x];
                if (a == 0) {
                    raw[p + 0] = 0;
                    raw[p + 1] = 0;
                    raw[p + 2] = 0;
                    raw[p + 3] = 0;
                    continue;
                }

                int panel = 0;
                if (((x + seed) % 41) == 0) panel += 75;
                if (((y + seed * 2) % 53) == 0) panel += 60;
                if ((((x >> 3) + (y >> 3) + seed) % 37) == 0) panel += 45;
                if (Hash01(x, y, seed + 177) > 0.996) panel += 125;
                if (panel <= 0) {
                    raw[p + 3] = 0;
                    continue;
                }

                int pa = Math.Min(a, panel);
                raw[p + 0] = 142;
                raw[p + 1] = 156;
                raw[p + 2] = 178;
                raw[p + 3] = (byte)pa;
            }
        }
        WriteBytes(bmp, raw);
    }

    public static void BuildAo(Bitmap bmp, byte[] alpha, int seed) {
        int stride;
        byte[] raw = ReadBytes(bmp, out stride);
        int w = bmp.Width;
        int h = bmp.Height;
        double cx = (w - 1) * 0.5;
        double cy = (h - 1) * 0.5;
        double invW = 1.0 / Math.Max(1.0, w - 1.0);
        double invH = 1.0 / Math.Max(1.0, h - 1.0);

        for (int y = 0; y < h; y++) {
            int row = y * stride;
            int arow = y * w;
            double ny = (y - cy) * 2.0 * invH;
            for (int x = 0; x < w; x++) {
                int p = row + x * 4;
                byte a = alpha[arow + x];
                if (a == 0) {
                    raw[p + 0] = 0;
                    raw[p + 1] = 0;
                    raw[p + 2] = 0;
                    raw[p + 3] = 0;
                    continue;
                }

                double nx = (x - cx) * 2.0 * invW;
                double radial = Math.Sqrt(nx * nx + ny * ny);
                double edge = 0.0;

                int left2 = (x > 1) ? alpha[arow + (x - 2)] : (byte)0;
                int right2 = (x < w - 2) ? alpha[arow + (x + 2)] : (byte)0;
                int up2 = (y > 1) ? alpha[(y - 2) * w + x] : (byte)0;
                int down2 = (y < h - 2) ? alpha[(y + 2) * w + x] : (byte)0;
                if (left2 == 0) edge += 0.28;
                if (right2 == 0) edge += 0.28;
                if (up2 == 0) edge += 0.22;
                if (down2 == 0) edge += 0.22;

                double aftShadow = Clamp01((-nx - 0.08) / 0.95);
                double trench = (((x + seed) % 41) == 0 || ((y + seed * 2) % 53) == 0) ? 0.18 : 0.0;
                double ao = 20 + radial * 36 + edge * 92 + aftShadow * 44 + trench * 24;

                int aa = ClampInt((int)Math.Round(ao), 0, a);
                raw[p + 0] = 0;
                raw[p + 1] = 0;
                raw[p + 2] = 0;
                raw[p + 3] = (byte)aa;
            }
        }
        WriteBytes(bmp, raw);
    }

    private static void PutMax(byte[] raw, int stride, int x, int y, byte r, byte g, byte b, byte a) {
        int p = y * stride + x * 4;
        if (a <= raw[p + 3]) return;
        raw[p + 0] = b;
        raw[p + 1] = g;
        raw[p + 2] = r;
        raw[p + 3] = a;
    }

    public static void BuildEmissive(Bitmap bmp, byte[] alpha, int seed) {
        int stride;
        byte[] raw = ReadBytes(bmp, out stride);
        int w = bmp.Width;
        int h = bmp.Height;
        double cx = (w - 1) * 0.5;
        double cy = (h - 1) * 0.5;
        double invW = 1.0 / Math.Max(1.0, w - 1.0);
        double invH = 1.0 / Math.Max(1.0, h - 1.0);

        for (int y = 0; y < h; y++) {
            int arow = y * w;
            double ny = (y - cy) * 2.0 * invH;
            for (int x = 0; x < w; x++) {
                byte a = alpha[arow + x];
                if (a == 0) continue;
                double nx = (x - cx) * 2.0 * invW;
                bool inWindowBand = Math.Abs(ny) < 0.28 && nx > -0.12 && nx < 0.92;
                bool window = inWindowBand && Hash01(x, y, seed + 401) > 0.992;
                if (window) {
                    byte wa = (byte)Math.Min((int)a, 192);
                    PutMax(raw, stride, x, y, 124, 220, 255, wa);
                    continue;
                }

                bool spineGlow = Math.Abs(ny) < 0.02 && nx > 0.06 && nx < 0.92 && Hash01(x, y, seed + 757) > 0.84;
                if (spineGlow) {
                    byte sa = (byte)Math.Min((int)a, 92);
                    PutMax(raw, stride, x, y, 110, 198, 255, sa);
                    continue;
                }

                bool engine = nx < -0.32 && Math.Abs(ny) < 0.26;
                if (engine && Hash01(x, y, seed + 1337) > 0.66) {
                    double e = (0.26 - Math.Abs(ny)) / 0.26;
                    int ea = ClampInt((int)Math.Round(70 + e * 150), 0, a);
                    PutMax(raw, stride, x, y, 128, 238, 255, (byte)ea);
                }
            }
        }
        WriteBytes(bmp, raw);
    }

    public static void BuildDamage(Bitmap bmp, byte[] alpha, int seed) {
        int stride;
        byte[] raw = ReadBytes(bmp, out stride);
        int w = bmp.Width;
        int h = bmp.Height;
        double cx = (w - 1) * 0.5;
        double cy = (h - 1) * 0.5;
        double invW = 1.0 / Math.Max(1.0, w - 1.0);

        for (int y = 0; y < h; y++) {
            int arow = y * w;
            for (int x = 0; x < w; x++) {
                int p = y * stride + x * 4;
                byte a = alpha[arow + x];
                if (a == 0) {
                    raw[p + 0] = 0;
                    raw[p + 1] = 0;
                    raw[p + 2] = 0;
                    raw[p + 3] = 0;
                    continue;
                }

                double nx = (x - cx) * 2.0 * invW;
                int edgeBoost = 0;
                if ((x > 1 && alpha[arow + x - 2] == 0) || (x < w - 2 && alpha[arow + x + 2] == 0)) edgeBoost += 24;
                if ((y > 1 && alpha[(y - 2) * w + x] == 0) || (y < h - 2 && alpha[(y + 2) * w + x] == 0)) edgeBoost += 24;

                int micro = 0;
                if (Hash01(x, y, seed + 1919) > 0.996) micro = 118;
                if (((x + y + seed) % 173) == 0) micro = Math.Max(micro, 94);
                if (nx > 0.18 && Hash01(x * 5 + 9, y * 7 + 3, seed + 2129) > 0.992) micro = Math.Max(micro, 140);

                int da = ClampInt(edgeBoost + micro, 0, a);
                if (da <= 0) {
                    raw[p + 3] = 0;
                    continue;
                }

                raw[p + 0] = 28;
                raw[p + 1] = 34;
                raw[p + 2] = 40;
                raw[p + 3] = (byte)da;
            }
        }

        Random rng = new Random(seed * 1009 + 17);
        int craters = 18;
        for (int i = 0; i < craters; i++) {
            int cxp = rng.Next(0, w);
            int cyp = rng.Next(0, h);
            int tries = 0;
            while (tries++ < 20 && alpha[cyp * w + cxp] == 0) {
                cxp = rng.Next(0, w);
                cyp = rng.Next(0, h);
            }
            if (alpha[cyp * w + cxp] == 0) continue;
            int rr = rng.Next(8, 24);
            int rsq = rr * rr;
            int minX = Math.Max(0, cxp - rr - 1);
            int maxX = Math.Min(w - 1, cxp + rr + 1);
            int minY = Math.Max(0, cyp - rr - 1);
            int maxY = Math.Min(h - 1, cyp + rr + 1);
            for (int y = minY; y <= maxY; y++) {
                int arow = y * w;
                for (int x = minX; x <= maxX; x++) {
                    if (alpha[arow + x] == 0) continue;
                    int dx = x - cxp;
                    int dy = y - cyp;
                    int d2 = dx * dx + dy * dy;
                    if (d2 > rsq) continue;
                    double d = Math.Sqrt(d2);
                    if (d < rr * 0.62) {
                        int aa = ClampInt((int)Math.Round(100 + (1.0 - d / (rr * 0.62)) * 85), 0, alpha[arow + x]);
                        PutMax(raw, stride, x, y, 18, 22, 28, (byte)aa);
                    } else if (d < rr * 0.95) {
                        int aa = ClampInt((int)Math.Round(26 + (1.0 - (d - rr * 0.62) / (rr * 0.33)) * 52), 0, alpha[arow + x]);
                        PutMax(raw, stride, x, y, 255, 166, 92, (byte)aa);
                    }
                }
            }
        }

        WriteBytes(bmp, raw);
    }
}
"@

Add-Type -TypeDefinition $csharp -ReferencedAssemblies @("System.Drawing.dll")

function Get-RoleList {
    return @(
        "picket",
        "patrol",
        "stealth_ship",
        "fighter",
        "bomber",
        "pd_craft",
        "drone",
        "frigate",
        "missile_boat",
        "ciws_corvette",
        "light_cruiser",
        "medium_cruiser",
        "cruiser",
        "battlecruiser",
        "battleship",
        "dreadnought",
        "supership",
        "carrier",
        "drone_carrier",
        "transport",
        "miner",
        "hauler",
        "base",
        "static_turret"
    )
}

function Get-FallbackMap {
    return @{
        bomber = "fighter"
        pd_craft = "picket"
        drone = "fighter"
        ciws_corvette = "picket"
        medium_cruiser = "light_cruiser"
        cruiser = "light_cruiser"
        supership = "dreadnought"
        transport = "carrier"
        miner = "frigate"
        hauler = "transport"
        base = "carrier"
        static_turret = "picket"
    }
}

function Resolve-SourceRole {
    param(
        [string]$Role,
        [hashtable]$Fallbacks,
        [string]$SkinDir,
        [System.Collections.Generic.HashSet[string]]$Available,
        [System.Collections.Generic.HashSet[string]]$NativeRoles
    )
    $seen = New-Object "System.Collections.Generic.HashSet[string]"
    $current = $Role
    while ($true) {
        if (-not $seen.Add($current)) { return "frigate" }
        $candidates = @(
            (Join-Path $SkinDir "${current}_ally.png"),
            (Join-Path $SkinDir "${current}.png")
        )
        foreach ($p in $candidates) {
            if ($Available.Contains([IO.Path]::GetFileName($p)) -and $NativeRoles.Contains($current)) { return $current }
        }
        if (-not $Fallbacks.ContainsKey($current)) { return "frigate" }
        $current = [string]$Fallbacks[$current]
    }
}

function Open-Image32 {
    param([string]$Path)
    $tmp = [System.Drawing.Image]::FromFile($Path)
    try {
        $bmp = New-Object -TypeName System.Drawing.Bitmap -ArgumentList @($tmp.Width, $tmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        try {
            $g.Clear([System.Drawing.Color]::Transparent)
            $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $g.DrawImage($tmp, 0, 0, $tmp.Width, $tmp.Height)
        } finally {
            $g.Dispose()
        }
        return $bmp
    } finally {
        $tmp.Dispose()
    }
}

function Resize-ToCanvas {
    param(
        [System.Drawing.Bitmap]$Source,
        [int]$CanvasSize
    )
    $dst = New-Object -TypeName System.Drawing.Bitmap -ArgumentList @($CanvasSize, $CanvasSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($dst)
    try {
        $g.Clear([System.Drawing.Color]::Transparent)
        $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.DrawImage($Source, 0, 0, $CanvasSize, $CanvasSize)
    } finally {
        $g.Dispose()
    }
    return $dst
}

function New-TransparentBitmap {
    param([int]$CanvasSize)
    return New-Object -TypeName System.Drawing.Bitmap -ArgumentList @($CanvasSize, $CanvasSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Save-Png {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string]$Path
    )
    $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Seed-ForRole {
    param([string]$Role)
    $h = 2166136261
    foreach ($ch in $Role.ToCharArray()) {
        $h = ($h -bxor [int][char]$ch)
        $h = [int](($h * 16777619) -band 0x7fffffff)
    }
    return [int]$h
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$skinDir = Join-Path $root "assets/ship_skins"
if (-not (Test-Path $skinDir)) {
    throw "Skin directory not found: $skinDir"
}

$roles = Get-RoleList
$fallbacks = Get-FallbackMap
$available = New-Object "System.Collections.Generic.HashSet[string]"
Get-ChildItem $skinDir -File | ForEach-Object { [void]$available.Add($_.Name) }
$nativeRoles = New-Object "System.Collections.Generic.HashSet[string]"
foreach ($role in $roles) {
    if (
        $available.Contains("${role}.png") -or
        $available.Contains("${role}_enemy.png") -or
        $available.Contains("${role}_team_c.png") -or
        $available.Contains("${role}_team_d.png")
    ) {
        [void]$nativeRoles.Add($role)
    }
}
$generated = @()

foreach ($role in $roles) {
    $resolved = Resolve-SourceRole -Role $role -Fallbacks $fallbacks -SkinDir $skinDir -Available $available -NativeRoles $nativeRoles
    $sourcePath = Join-Path $skinDir "${resolved}_ally.png"
    if (-not (Test-Path $sourcePath)) {
        $sourcePath = Join-Path $skinDir "${resolved}.png"
    }
    if (-not (Test-Path $sourcePath)) {
        throw "No source image found for role '$role' (resolved '$resolved')"
    }

    $seed = Seed-ForRole $role
    $src = Open-Image32 $sourcePath
    try {
        $base = Resize-ToCanvas -Source $src -CanvasSize $Size
        $albedo = $null
        $panel = $null
        $ao = $null
        $emissive = $null
        $damage = $null
        try {
            $alpha = [ShipSkinOps]::ExtractAlpha($base)

            $albedo = [System.Drawing.Bitmap]$base.Clone()
            [ShipSkinOps]::StylizeAlbedo($albedo, $alpha, $seed)
            [ShipSkinOps]::ApplyAlphaMask($albedo, $alpha)

            $panel = New-TransparentBitmap -CanvasSize $Size
            [ShipSkinOps]::BuildPanel($panel, $alpha, ($seed + 101))
            [ShipSkinOps]::ApplyAlphaMask($panel, $alpha)

            $ao = New-TransparentBitmap -CanvasSize $Size
            [ShipSkinOps]::BuildAo($ao, $alpha, ($seed + 211))
            [ShipSkinOps]::ApplyAlphaMask($ao, $alpha)

            $emissive = New-TransparentBitmap -CanvasSize $Size
            [ShipSkinOps]::BuildEmissive($emissive, $alpha, ($seed + 307))
            [ShipSkinOps]::ApplyAlphaMask($emissive, $alpha)

            $damage = New-TransparentBitmap -CanvasSize $Size
            [ShipSkinOps]::BuildDamage($damage, $alpha, ($seed + 401))
            [ShipSkinOps]::ApplyAlphaMask($damage, $alpha)

            $prefix = Join-Path $skinDir "${role}_ally"
            Save-Png -Bitmap $albedo -Path "${prefix}_albedo.png"
            Save-Png -Bitmap $panel -Path "${prefix}_panel.png"
            Save-Png -Bitmap $ao -Path "${prefix}_ao.png"
            Save-Png -Bitmap $emissive -Path "${prefix}_emissive.png"
            Save-Png -Bitmap $damage -Path "${prefix}_damage.png"
            if ($WriteLegacyAllyPng) {
                Save-Png -Bitmap $albedo -Path "${prefix}.png"
            }

            $generated += [PSCustomObject]@{
                Role = $role
                Source = [IO.Path]::GetFileName($sourcePath)
            }
        } finally {
            if ($null -ne $base) { $base.Dispose() }
            if ($null -ne $albedo) { $albedo.Dispose() }
            if ($null -ne $panel) { $panel.Dispose() }
            if ($null -ne $ao) { $ao.Dispose() }
            if ($null -ne $emissive) { $emissive.Dispose() }
            if ($null -ne $damage) { $damage.Dispose() }
        }
    } finally {
        $src.Dispose()
    }
}

Write-Host "Generated layered ally skins ($Size x $Size):"
$generated | Sort-Object Role | Format-Table -AutoSize
