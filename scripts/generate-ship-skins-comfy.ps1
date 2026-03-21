[CmdletBinding()]
param(
    [ValidateSet("enemy", "team_c", "team_d", "all")]
    [string]$Faction = "team_c",
    [string]$ComfyApiUrl = "http://127.0.0.1:8188",
    [string]$PromptFile = "assets/ship_skins/dropbox/HULL_PROMPTS.md",
    [string]$StyleLockPath = "",
    [string]$ReferenceMapPath = "",
    [string]$ComfyInputDir = "\\wsl.localhost\\Ubuntu-24.04\\home\\xhatf\\ComfyUI\\input",
    [string[]]$IncludeFilenames = @(),
    [string]$OutputRoot = "assets/ship_skins/dropbox",
    [string]$ScratchRoot = "build/ship_skin_generation",
    [string]$UnetName = "z_image_turbo_bf16.safetensors",
    [string]$ClipName = "qwen_3_4b.safetensors",
    [string]$VaeName = "ae.safetensors",
    [string]$ClipType = "lumina2",
    [int]$Width = 1280,
    [int]$Height = 720,
    [int]$Steps = 8,
    [double]$CfgScale = 1.0,
    [double]$Shift = 3.0,
    [string]$SamplerName = "res_multistep",
    [string]$Scheduler = "simple",
    [int]$SeedBase = 910000,
    [int]$AttemptsPerPrompt = 2,
    [int]$PromptTimeoutSec = 900,
    [int]$MaxPrompts = 0,
    [int]$BackgroundThreshold = 42,
    [string]$ReviewFolderName = "review",
    [string]$PromptSuffix = "",
    [string]$NegativePrompt = "",
    [switch]$Overwrite,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$shipSkinOps = @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

public sealed class ShipImageQcResult {
    public int Width { get; set; }
    public int Height { get; set; }
    public int BackgroundPixels { get; set; }
    public int NonBackgroundPixels { get; set; }
    public int MinX { get; set; }
    public int MinY { get; set; }
    public int MaxX { get; set; }
    public int MaxY { get; set; }
    public int Components { get; set; }
    public int LargestComponentPixels { get; set; }
    public bool HasTransparency { get; set; }
}

public static class ShipImageOps {
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

    private static int DistSq(byte r, byte g, byte b, int tr, int tg, int tb) {
        int dr = r - tr;
        int dg = g - tg;
        int db = b - tb;
        return dr * dr + dg * dg + db * db;
    }

    public static ShipImageQcResult KeyBackgroundAndMeasure(string inputPath, string outputPath, int threshold) {
        using (Bitmap source = new Bitmap(inputPath))
        using (Bitmap bmp = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb)) {
            using (Graphics g = Graphics.FromImage(bmp)) {
                g.DrawImage(source, 0, 0, source.Width, source.Height);
            }

            int stride;
            byte[] raw = ReadBytes(bmp, out stride);
            int w = bmp.Width;
            int h = bmp.Height;
            int pixelCount = w * h;
            bool[] isBackground = new bool[pixelCount];
            bool[] visited = new bool[pixelCount];
            Queue<int> queue = new Queue<int>(Math.Max(1024, w * 2 + h * 2));

            Func<int, int, int> indexOf = (x, y) => (y * w) + x;

            byte[] bgR = new byte[4];
            byte[] bgG = new byte[4];
            byte[] bgB = new byte[4];
            Point[] corners = new Point[] {
                new Point(0, 0),
                new Point(w - 1, 0),
                new Point(0, h - 1),
                new Point(w - 1, h - 1)
            };

            for (int i = 0; i < corners.Length; i++) {
                int p = corners[i].Y * stride + corners[i].X * 4;
                bgB[i] = raw[p + 0];
                bgG[i] = raw[p + 1];
                bgR[i] = raw[p + 2];
            }

            int targetR = (bgR[0] + bgR[1] + bgR[2] + bgR[3]) / 4;
            int targetG = (bgG[0] + bgG[1] + bgG[2] + bgG[3]) / 4;
            int targetB = (bgB[0] + bgB[1] + bgB[2] + bgB[3]) / 4;
            int limit = threshold * threshold;

            Action<int, int> enqueueIfMatch = (x, y) => {
                if (x < 0 || x >= w || y < 0 || y >= h) return;
                int idx = indexOf(x, y);
                if (visited[idx]) return;
                visited[idx] = true;
                int p = y * stride + x * 4;
                if (raw[p + 3] == 0 || DistSq(raw[p + 2], raw[p + 1], raw[p + 0], targetR, targetG, targetB) <= limit) {
                    queue.Enqueue(idx);
                }
            };

            for (int x = 0; x < w; x++) {
                enqueueIfMatch(x, 0);
                enqueueIfMatch(x, h - 1);
            }
            for (int y = 0; y < h; y++) {
                enqueueIfMatch(0, y);
                enqueueIfMatch(w - 1, y);
            }

            while (queue.Count > 0) {
                int idx = queue.Dequeue();
                if (isBackground[idx]) continue;
                isBackground[idx] = true;
                int x = idx % w;
                int y = idx / w;
                enqueueIfMatch(x - 1, y);
                enqueueIfMatch(x + 1, y);
                enqueueIfMatch(x, y - 1);
                enqueueIfMatch(x, y + 1);
            }

            int nonBg = 0;
            int bgCount = 0;
            int minX = w;
            int minY = h;
            int maxX = -1;
            int maxY = -1;
            bool hasTransparency = false;

            for (int y = 0; y < h; y++) {
                int row = y * stride;
                for (int x = 0; x < w; x++) {
                    int idx = indexOf(x, y);
                    int p = row + x * 4;
                    if (isBackground[idx]) {
                        raw[p + 3] = 0;
                        bgCount++;
                        hasTransparency = true;
                        continue;
                    }

                    if (raw[p + 3] > 0) {
                        nonBg++;
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }

            int components = 0;
            int largest = 0;
            if (nonBg > 0) {
                bool[] componentVisited = new bool[pixelCount];
                Queue<int> componentQueue = new Queue<int>();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int idx = indexOf(x, y);
                        if (isBackground[idx] || componentVisited[idx]) continue;
                        int p = y * stride + x * 4;
                        if (raw[p + 3] == 0) continue;

                        int size = 0;
                        componentVisited[idx] = true;
                        componentQueue.Enqueue(idx);
                        while (componentQueue.Count > 0) {
                            int current = componentQueue.Dequeue();
                            size++;
                            int cx = current % w;
                            int cy = current / w;

                            Action<int, int> visitNeighbor = (nx, ny) => {
                                if (nx < 0 || nx >= w || ny < 0 || ny >= h) return;
                                int ni = indexOf(nx, ny);
                                if (componentVisited[ni] || isBackground[ni]) return;
                                int np = ny * stride + nx * 4;
                                if (raw[np + 3] == 0) return;
                                componentVisited[ni] = true;
                                componentQueue.Enqueue(ni);
                            };

                            visitNeighbor(cx - 1, cy);
                            visitNeighbor(cx + 1, cy);
                            visitNeighbor(cx, cy - 1);
                            visitNeighbor(cx, cy + 1);
                        }

                        components++;
                        if (size > largest) largest = size;
                    }
                }
            }

            Directory.CreateDirectory(Path.GetDirectoryName(outputPath) ?? ".");
            WriteBytes(bmp, raw);
            bmp.Save(outputPath, ImageFormat.Png);

            return new ShipImageQcResult {
                Width = w,
                Height = h,
                BackgroundPixels = bgCount,
                NonBackgroundPixels = nonBg,
                MinX = minX,
                MinY = minY,
                MaxX = maxX,
                MaxY = maxY,
                Components = components,
                LargestComponentPixels = largest,
                HasTransparency = hasTransparency
            };
        }
    }
}
"@

Add-Type -TypeDefinition $shipSkinOps -ReferencedAssemblies @("System.Drawing.dll")

function Join-NonEmpty {
    param([object[]]$Parts)
    return ($Parts | Where-Object { $_ -and $_.ToString().Trim().Length -gt 0 }) -join " "
}

function Get-FactionConfigs {
    return @{
        enemy = [pscustomobject]@{
            Key = "enemy"
            Header = "Red Faction / Team B / *_enemy_albedo.png"
            Folder = "red_enemy"
            FilenameSuffix = "_enemy_albedo.png"
        }
        team_c = [pscustomobject]@{
            Key = "team_c"
            Header = "Green Faction / Team C / *_team_c_albedo.png"
            Folder = "green_team_c"
            FilenameSuffix = "_team_c_albedo.png"
        }
        team_d = [pscustomobject]@{
            Key = "team_d"
            Header = "Missile Faction / Team D / *_team_d_albedo.png"
            Folder = "missile_team_d"
            FilenameSuffix = "_team_d_albedo.png"
        }
    }
}

function Parse-HullPrompts {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Prompt file missing: $Path"
    }

    $configs = Get-FactionConfigs
    $headerLookup = @{}
    foreach ($entry in $configs.GetEnumerator()) {
        $headerLookup[$entry.Value.Header] = $entry.Value
    }

    $sharedPrefixParts = @()
    $entries = @()
    $lines = Get-Content -LiteralPath $Path
    $currentFaction = $null
    $inSharedPrefix = $false
    $i = 0

    while ($i -lt $lines.Count) {
        $line = ([string]$lines[$i]).Trim()

        if ($line.Length -eq 0) {
            $i++
            continue
        }

        if ($line -eq "[Shared Prefix]") {
            $inSharedPrefix = $true
            $i++
            continue
        }

        if ($null -ne $headerLookup[$line]) {
            $currentFaction = $headerLookup[$line]
            $inSharedPrefix = $false
            $i++
            continue
        }

        if ($inSharedPrefix) {
            $sharedPrefixParts += $line
            $i++
            continue
        }

        if ($line -match '^\d+\.\s+([a-z0-9_]+\.png)$') {
            if ($null -eq $currentFaction) {
                throw "Encountered prompt entry before faction header: $line"
            }

            $filename = $Matches[1]
            $descriptionParts = @()
            $j = $i + 1
            while ($j -lt $lines.Count) {
                $next = ([string]$lines[$j]).Trim()
                if ($next.Length -eq 0) {
                    if ($descriptionParts.Count -gt 0) {
                        break
                    }
                    $j++
                    continue
                }
                if (($null -ne $headerLookup[$next]) -or $next -match '^\d+\.\s+') {
                    break
                }
                $descriptionParts += $next
                $j++
            }

            $entries += [pscustomobject]@{
                Faction = $currentFaction.Key
                Folder = $currentFaction.Folder
                Filename = $filename
                Description = ($descriptionParts -join " ")
            }

            $i = $j
            continue
        }

        $i++
    }

    return [pscustomobject]@{
        SharedPrefix = ($sharedPrefixParts -join " ")
        Entries = @($entries)
    }
}

function Get-StyleLockText {
    param(
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return ""
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Style lock file missing: $Path"
    }

    return ((Get-Content -LiteralPath $Path) | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ }) -join " "
}

function Get-ReferenceMap {
    param(
        [string]$Path
    )

    $map = @{}
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $map
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Reference map file missing: $Path"
    }

    $json = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    foreach ($prop in $json.PSObject.Properties) {
        $map[$prop.Name.ToLowerInvariant()] = $prop.Value
    }
    return $map
}

function Stage-ReferenceImage {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$InputDir,
        [Parameter(Mandatory = $true)][hashtable]$Cache
    )

    $resolved = (Resolve-Path -LiteralPath $SourcePath).Path
    if ($null -ne $Cache[$resolved]) {
        return $Cache[$resolved]
    }

    if (-not (Test-Path -LiteralPath $InputDir)) {
        throw "ComfyUI input directory missing: $InputDir"
    }

    $leaf = [IO.Path]::GetFileName($resolved)
    $targetLeaf = "codex_shipref_{0}" -f $leaf
    $targetPath = Join-Path $InputDir $targetLeaf
    Copy-Item -LiteralPath $resolved -Destination $targetPath -Force
    $Cache[$resolved] = $targetLeaf
    return $targetLeaf
}

function Wait-ComfyHistory {
    param(
        [Parameter(Mandatory = $true)][string]$ApiBase,
        [Parameter(Mandatory = $true)][string]$PromptId,
        [int]$TimeoutSec = 900
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 700
        try {
            $history = Invoke-RestMethod -Uri "$ApiBase/history/$PromptId" -Method Get -TimeoutSec 20
        } catch {
            continue
        }

        if ($history) {
            $prop = $history.PSObject.Properties[$PromptId]
            if ($null -ne $prop) {
                return $prop.Value
            }
        }
    }

    throw "[ship-skin-comfy] timeout waiting for prompt_id=$PromptId"
}

function Get-Workflow {
    param(
        [Parameter(Mandatory = $true)][string]$Prompt,
        [Parameter(Mandatory = $true)][string]$FilenamePrefix,
        [Parameter(Mandatory = $true)][int]$Seed,
        [string]$NegativePrompt = "",
        [string]$ReferenceImageName = "",
        [double]$Denoise = 1.0
    )

    $workflow = @{
        "1" = @{
            class_type = "UNETLoader"
            inputs = @{
                unet_name = $UnetName
                weight_dtype = "default"
            }
        }
        "2" = @{
            class_type = "CLIPLoader"
            inputs = @{
                clip_name = $ClipName
                type = $ClipType
                device = "default"
            }
        }
        "3" = @{
            class_type = "VAELoader"
            inputs = @{
                vae_name = $VaeName
            }
        }
        "5" = @{
            class_type = "ModelSamplingAuraFlow"
            inputs = @{
                model = @("1", 0)
                shift = $Shift
            }
        }
        "6" = @{
            class_type = "CLIPTextEncode"
            inputs = @{
                clip = @("2", 0)
                text = $Prompt
            }
        }
        "8" = @{
            class_type = "KSampler"
            inputs = @{
                model = @("5", 0)
                positive = @("6", 0)
                seed = $Seed
                steps = $Steps
                cfg = $CfgScale
                sampler_name = $SamplerName
                scheduler = $Scheduler
                denoise = $Denoise
            }
        }
        "9" = @{
            class_type = "VAEDecode"
            inputs = @{
                samples = @("8", 0)
                vae = @("3", 0)
            }
        }
        "10" = @{
            class_type = "SaveImage"
            inputs = @{
                filename_prefix = $FilenamePrefix
                images = @("9", 0)
            }
        }
    }

    if ([string]::IsNullOrWhiteSpace($NegativePrompt)) {
        $workflow["7"] = @{
            class_type = "ConditioningZeroOut"
            inputs = @{
                conditioning = @("6", 0)
            }
        }
    } else {
        $workflow["7"] = @{
            class_type = "CLIPTextEncode"
            inputs = @{
                clip = @("2", 0)
                text = $NegativePrompt
            }
        }
    }
    $workflow["8"].inputs.negative = @("7", 0)

    if ([string]::IsNullOrWhiteSpace($ReferenceImageName)) {
        $workflow["4"] = @{
            class_type = "EmptySD3LatentImage"
            inputs = @{
                width = $Width
                height = $Height
                batch_size = 1
            }
        }
        $workflow["8"].inputs.latent_image = @("4", 0)
    } else {
        $workflow["11"] = @{
            class_type = "LoadImage"
            inputs = @{
                image = $ReferenceImageName
            }
        }
        $workflow["12"] = @{
            class_type = "VAEEncode"
            inputs = @{
                pixels = @("11", 0)
                vae = @("3", 0)
            }
        }
        $workflow["8"].inputs.latent_image = @("12", 0)
    }

    return $workflow
}

function Invoke-ComfyGeneration {
    param(
        [Parameter(Mandatory = $true)][string]$ApiBase,
        [Parameter(Mandatory = $true)][string]$Prompt,
        [Parameter(Mandatory = $true)][string]$FilenamePrefix,
        [Parameter(Mandatory = $true)][int]$Seed,
        [string]$NegativePrompt = "",
        [string]$ReferenceImageName = "",
        [double]$Denoise = 1.0,
        [Parameter(Mandatory = $true)][string]$RawOutputPath
    )

    $workflow = Get-Workflow -Prompt $Prompt -FilenamePrefix $FilenamePrefix -Seed $Seed -NegativePrompt $NegativePrompt -ReferenceImageName $ReferenceImageName -Denoise $Denoise
    $requestBody = @{
        prompt = $workflow
        client_id = [Guid]::NewGuid().ToString("N")
    } | ConvertTo-Json -Depth 30

    $queueResp = Invoke-RestMethod -Uri "$ApiBase/prompt" -Method Post -ContentType "application/json" -Body $requestBody
    if (-not $queueResp -or [string]::IsNullOrWhiteSpace($queueResp.prompt_id)) {
        throw "[ship-skin-comfy] failed to queue prompt"
    }

    $result = Wait-ComfyHistory -ApiBase $ApiBase -PromptId $queueResp.prompt_id -TimeoutSec $PromptTimeoutSec
    $images = @()
    if ($result.outputs) {
        if ($result.outputs.PSObject.Properties.Name -contains "10") {
            $images = @($result.outputs."10".images)
        }
        if ($images.Count -eq 0) {
            foreach ($prop in $result.outputs.PSObject.Properties.Name) {
                $nodeOut = $result.outputs.$prop
                if ($nodeOut -and $nodeOut.images) {
                    $images = @($nodeOut.images)
                    break
                }
            }
        }
    }

    if ($images.Count -eq 0) {
        throw "[ship-skin-comfy] no image outputs found"
    }

    $imageMeta = $images[0]
    $filename = $imageMeta.filename.ToString()
    $subfolder = ""
    if ($imageMeta.PSObject.Properties.Name -contains "subfolder" -and $imageMeta.subfolder) {
        $subfolder = $imageMeta.subfolder.ToString()
    }
    $type = "output"
    if ($imageMeta.PSObject.Properties.Name -contains "type" -and $imageMeta.type) {
        $type = $imageMeta.type.ToString()
    }

    $viewUri = "{0}/view?filename={1}&subfolder={2}&type={3}" -f `
        $ApiBase, `
        [System.Uri]::EscapeDataString($filename), `
        [System.Uri]::EscapeDataString($subfolder), `
        [System.Uri]::EscapeDataString($type)

    New-Item -ItemType Directory -Path (Split-Path -Parent $RawOutputPath) -Force | Out-Null
    Invoke-WebRequest -Uri $viewUri -OutFile $RawOutputPath -TimeoutSec 180 | Out-Null
}

function Measure-ShipQuality {
    param(
        [Parameter(Mandatory = $true)]$ImageResult
    )

    $issues = New-Object System.Collections.Generic.List[string]
    $score = 100

    if ($ImageResult.NonBackgroundPixels -le 0 -or $ImageResult.MaxX -lt $ImageResult.MinX -or $ImageResult.MaxY -lt $ImageResult.MinY) {
        $issues.Add("empty silhouette after background removal")
        return [pscustomobject]@{
            Score = 0
            Pass = $false
            Issues = @($issues)
            Coverage = 0.0
            LargestComponentShare = 0.0
            WidthHeightRatio = 0.0
            Padding = @{
                left = 0.0
                right = 0.0
                top = 0.0
                bottom = 0.0
            }
            CenterOffset = @{
                x = 1.0
                y = 1.0
            }
        }
    }

    $bboxWidth = [double](($ImageResult.MaxX - $ImageResult.MinX) + 1)
    $bboxHeight = [double](($ImageResult.MaxY - $ImageResult.MinY) + 1)
    $coverage = [double]$ImageResult.NonBackgroundPixels / [double]($ImageResult.Width * $ImageResult.Height)
    $largestShare = if ($ImageResult.NonBackgroundPixels -gt 0) {
        [double]$ImageResult.LargestComponentPixels / [double]$ImageResult.NonBackgroundPixels
    } else {
        0.0
    }
    $ratio = if ($bboxHeight -gt 0) { $bboxWidth / $bboxHeight } else { 0.0 }
    $padding = @{
        left = [double]$ImageResult.MinX / [double]$ImageResult.Width
        right = [double](($ImageResult.Width - 1) - $ImageResult.MaxX) / [double]$ImageResult.Width
        top = [double]$ImageResult.MinY / [double]$ImageResult.Height
        bottom = [double](($ImageResult.Height - 1) - $ImageResult.MaxY) / [double]$ImageResult.Height
    }
    $centerOffset = @{
        x = [math]::Abs((($ImageResult.MinX + $ImageResult.MaxX) / 2.0) - (($ImageResult.Width - 1) / 2.0)) / [double]$ImageResult.Width
        y = [math]::Abs((($ImageResult.MinY + $ImageResult.MaxY) / 2.0) - (($ImageResult.Height - 1) / 2.0)) / [double]$ImageResult.Height
    }

    if (-not $ImageResult.HasTransparency) {
        $issues.Add("background was not keyed to transparency")
        $score -= 40
    }
    if ($coverage -lt 0.05) {
        $issues.Add("ship silhouette is too small on the canvas")
        $score -= 25
    }
    if ($coverage -gt 0.72) {
        $issues.Add("ship silhouette is too large for the canvas")
        $score -= 25
    }
    if ($ratio -lt 1.02) {
        $issues.Add("silhouette does not read as right-facing horizontal hull")
        $score -= 20
    }
    foreach ($edge in @("left", "right", "top", "bottom")) {
        if ($padding[$edge] -lt 0.01) {
            $issues.Add("silhouette touches or nearly touches the $edge edge")
            $score -= 15
        }
    }
    if ($centerOffset.x -gt 0.14 -or $centerOffset.y -gt 0.14) {
        $issues.Add("ship is not centered cleanly in frame")
        $score -= 15
    }
    if ($ImageResult.Components -gt 8) {
        $issues.Add("silhouette is fragmented into too many separate components")
        $score -= 15
    }
    if ($largestShare -lt 0.90) {
        $issues.Add("largest silhouette component is too small relative to the whole image")
        $score -= 15
    }

    $pass = ($issues.Count -eq 0)

    return [pscustomobject]@{
        Score = [math]::Max(0, $score)
        Pass = $pass
        Issues = @($issues)
        Coverage = [math]::Round($coverage, 4)
        LargestComponentShare = [math]::Round($largestShare, 4)
        WidthHeightRatio = [math]::Round($ratio, 4)
        Padding = $padding
        CenterOffset = $centerOffset
    }
}

$apiBase = $ComfyApiUrl.TrimEnd("/")
if (-not $DryRun) {
    try {
        Invoke-RestMethod -Uri "$apiBase/system_stats" -Method Get -TimeoutSec 10 | Out-Null
    } catch {
        throw "ComfyUI API is not reachable at $apiBase"
    }
}

$parsed = Parse-HullPrompts -Path $PromptFile
$styleLock = Get-StyleLockText -Path $StyleLockPath
$referenceMap = Get-ReferenceMap -Path $ReferenceMapPath
$referenceCache = @{}
$allEntries = @($parsed.Entries)
if ($Faction -ne "all") {
    $allEntries = @($allEntries | Where-Object { $_.Faction -eq $Faction })
}
if ($IncludeFilenames.Count -gt 0) {
    $nameSet = @{}
    foreach ($name in $IncludeFilenames) {
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            $nameSet[$name.Trim().ToLowerInvariant()] = $true
        }
    }
    $allEntries = @($allEntries | Where-Object { $null -ne $nameSet[$_.Filename.ToLowerInvariant()] })
}
if ($MaxPrompts -gt 0) {
    $allEntries = @($allEntries | Select-Object -First $MaxPrompts)
}
if ($allEntries.Count -eq 0) {
    throw "No prompt entries matched faction '$Faction'"
}

New-Item -ItemType Directory -Path $ScratchRoot -Force | Out-Null

$runStamp = Get-Date -Format "yyyyMMdd_HHmmss"
$reportRows = New-Object System.Collections.Generic.List[object]
$created = 0
$reviewed = 0
$skipped = 0

for ($entryIndex = 0; $entryIndex -lt $allEntries.Count; $entryIndex++) {
    $entry = $allEntries[$entryIndex]
    $teamFolder = Join-Path $OutputRoot $entry.Folder
    $reviewFolder = Join-Path $teamFolder $ReviewFolderName
    $finalPath = Join-Path $teamFolder $entry.Filename
    $reviewPath = Join-Path $reviewFolder $entry.Filename

    if ((Test-Path -LiteralPath $finalPath) -and -not $Overwrite) {
        Write-Host "[ship-skin-comfy] skipping existing $finalPath"
        $skipped++
        continue
    }

    New-Item -ItemType Directory -Path $teamFolder -Force | Out-Null
    New-Item -ItemType Directory -Path $reviewFolder -Force | Out-Null

    $referenceSpec = $referenceMap[$entry.Filename.ToLowerInvariant()]
    $referenceImageName = ""
    $referenceSourcePath = ""
    $referenceDenoise = 1.0
    $referencePromptSuffix = ""
    if ($null -ne $referenceSpec) {
        if (($referenceSpec.PSObject.Properties.Name -contains "reference") -and $referenceSpec.reference) {
            $referenceSourcePath = [string]$referenceSpec.reference
            if (-not [IO.Path]::IsPathRooted($referenceSourcePath)) {
                $referenceSourcePath = Join-Path (Split-Path -Parent $PromptFile) $referenceSourcePath
            }
            $referenceImageName = Stage-ReferenceImage -SourcePath $referenceSourcePath -InputDir $ComfyInputDir -Cache $referenceCache
        }
        if (($referenceSpec.PSObject.Properties.Name -contains "denoise") -and $referenceSpec.denoise) {
            $referenceDenoise = [double]$referenceSpec.denoise
        }
        if (($referenceSpec.PSObject.Properties.Name -contains "prompt_suffix") -and $referenceSpec.prompt_suffix) {
            $referencePromptSuffix = [string]$referenceSpec.prompt_suffix
        }
    }

    $prompt = Join-NonEmpty -Parts @(
        $parsed.SharedPrefix,
        $styleLock,
        $entry.Description,
        $referencePromptSuffix,
        $PromptSuffix
    )

    Write-Host ("[ship-skin-comfy] {0}/{1} generating {2}" -f ($entryIndex + 1), $allEntries.Count, $entry.Filename)
    if ($DryRun) {
        Write-Host "[ship-skin-comfy] prompt: $prompt"
        continue
    }

    $bestCandidate = $null
    for ($attempt = 1; $attempt -le $AttemptsPerPrompt; $attempt++) {
        $seed = $SeedBase + ($entryIndex * 137) + ($attempt * 17)
        $scratchBase = Join-Path $ScratchRoot ("{0}_{1}_{2}" -f $runStamp, [IO.Path]::GetFileNameWithoutExtension($entry.Filename), $attempt)
        $rawPath = "$scratchBase.raw.png"
        $keyedPath = "$scratchBase.keyed.png"
        $filenamePrefix = "shipskin_{0}_{1}_{2}" -f $entry.Faction, [IO.Path]::GetFileNameWithoutExtension($entry.Filename), $attempt

        Invoke-ComfyGeneration -ApiBase $apiBase -Prompt $prompt -FilenamePrefix $filenamePrefix -Seed $seed -NegativePrompt $NegativePrompt -ReferenceImageName $referenceImageName -Denoise $referenceDenoise -RawOutputPath $rawPath
        $imageResult = [ShipImageOps]::KeyBackgroundAndMeasure($rawPath, $keyedPath, $BackgroundThreshold)
        $qc = Measure-ShipQuality -ImageResult $imageResult
        $candidate = [pscustomobject]@{
            Attempt = $attempt
            Seed = $seed
            RawPath = $rawPath
            KeyedPath = $keyedPath
            ImageResult = $imageResult
            Qc = $qc
        }

        if ($null -eq $bestCandidate -or $candidate.Qc.Score -gt $bestCandidate.Qc.Score) {
            $bestCandidate = $candidate
        }

        $issueText = if ($qc.Issues.Count -gt 0) { $qc.Issues -join "; " } else { "pass" }
        Write-Host ("[ship-skin-comfy] attempt={0} seed={1} score={2} {3}" -f $attempt, $seed, $qc.Score, $issueText)

        if ($qc.Pass) {
            break
        }
    }

    if ($null -eq $bestCandidate) {
        throw "No candidate generated for $($entry.Filename)"
    }

    $destination = if ($bestCandidate.Qc.Pass) { $finalPath } else { $reviewPath }
    Copy-Item -LiteralPath $bestCandidate.KeyedPath -Destination $destination -Force

    if ($bestCandidate.Qc.Pass) {
        $created++
    } else {
        $reviewed++
    }

    $reportRows.Add([pscustomobject]@{
        faction = $entry.Faction
        staged_folder = $entry.Folder
        filename = $entry.Filename
        status = if ($bestCandidate.Qc.Pass) { "passed" } else { "review" }
        attempts = $bestCandidate.Attempt
        seed = $bestCandidate.Seed
        score = $bestCandidate.Qc.Score
        issues = $bestCandidate.Qc.Issues
        output_path = $destination
        raw_path = $bestCandidate.RawPath
        negative_prompt = $NegativePrompt
        reference_image = $referenceImageName
        reference_source = $referenceSourcePath
        reference_denoise = $referenceDenoise
        coverage = $bestCandidate.Qc.Coverage
        width_height_ratio = $bestCandidate.Qc.WidthHeightRatio
        largest_component_share = $bestCandidate.Qc.LargestComponentShare
        components = $bestCandidate.ImageResult.Components
        padding = $bestCandidate.Qc.Padding
        center_offset = $bestCandidate.Qc.CenterOffset
    })
}

if (-not $DryRun) {
    $reportName = if ($Faction -eq "all") { "_qc_report_all_$runStamp.json" } else { "_qc_report_${Faction}_$runStamp.json" }
    $reportPath = Join-Path $OutputRoot $reportName
    $reportRows | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Host "[ship-skin-comfy] report=$reportPath"
}

Write-Host "[ship-skin-comfy] created=$created review=$reviewed skipped=$skipped"
