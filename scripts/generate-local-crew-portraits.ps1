[CmdletBinding()]
param(
    [string]$SdApiUrl = "http://127.0.0.1:7860",
    [string]$BiblePath = "assets/ai_pipeline/crew_portrait_bible.json",
    [string]$OutRoot = "assets/crew_portraits",
    [string]$Checkpoint = "",
    [int]$Width = 1024,
    [int]$Height = 1024,
    [int]$Steps = 32,
    [double]$CfgScale = 6.5,
    [string]$SamplerName = "DPM++ 2M Karras",
    [int]$SeedBase = 770000,
    [switch]$Overwrite,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Join-NonEmpty {
    param([object[]]$Parts)
    return ($Parts | Where-Object { $_ -and $_.ToString().Trim().Length -gt 0 }) -join ", "
}

if (-not (Test-Path -LiteralPath $BiblePath)) {
    throw "Portrait bible missing: $BiblePath"
}

New-Item -ItemType Directory -Path $OutRoot -Force | Out-Null

$bible = Get-Content -Raw -LiteralPath $BiblePath | ConvertFrom-Json
$roles = @($bible.roles)
if ($roles.Count -eq 0) {
    throw "No roles found in portrait bible: $BiblePath"
}

$qualityConstraints = @($bible.quality_constraints | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
$apiBase = $SdApiUrl.TrimEnd("/")

if (-not $DryRun) {
    try {
        Invoke-RestMethod -Uri "$apiBase/sdapi/v1/progress" -Method Get -TimeoutSec 5 | Out-Null
    } catch {
        throw ("Stable Diffusion API is not reachable at " + $apiBase + ". " +
               "Start AUTOMATIC1111/Forge with API enabled, or pass -SdApiUrl with the correct host:port.")
    }
}

if (-not [string]::IsNullOrWhiteSpace($Checkpoint) -and -not $DryRun) {
    $optionBody = @{ sd_model_checkpoint = $Checkpoint } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$apiBase/sdapi/v1/options" -Method Post -ContentType "application/json" -Body $optionBody | Out-Null
        Write-Host "[portrait-gen] checkpoint set: $Checkpoint"
    } catch {
        Write-Warning "[portrait-gen] could not set checkpoint '$Checkpoint'. Continuing with current model."
    }
}

$created = 0
$skipped = 0

for ($roleIndex = 0; $roleIndex -lt $roles.Count; $roleIndex++) {
    $roleSpec = $roles[$roleIndex]
    $role = $roleSpec.role.ToString().Trim().ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($role)) {
        Write-Warning "[portrait-gen] skipping role with empty key at index $roleIndex"
        continue
    }

    $variants = @(
        @{ key = "base"; file = "$role.png" },
        @{ key = "alt_01"; file = "${role}_alt_01.png" },
        @{ key = "alt_02"; file = "${role}_alt_02.png" },
        @{ key = "alt_03"; file = "${role}_alt_03.png" }
    )

    for ($variantIndex = 0; $variantIndex -lt $variants.Count; $variantIndex++) {
        $variant = $variants[$variantIndex]
        $outFile = Join-Path $OutRoot $variant.file
        if ((Test-Path -LiteralPath $outFile) -and -not $Overwrite) {
            $skipped++
            continue
        }

        $expressionPrompt = ""
        if ($roleSpec.expression_prompts -and ($roleSpec.expression_prompts.PSObject.Properties.Name -contains $variant.key)) {
            $expressionPrompt = $roleSpec.expression_prompts.($variant.key).ToString().Trim()
        }

        $promptParts = @(
            $bible.style_lock_prompt,
            $roleSpec.portrait_prompt,
            $expressionPrompt
        ) + $qualityConstraints
        $prompt = Join-NonEmpty -Parts $promptParts
        $negativePrompt = ""
        if ($bible.negative_prompt) {
            $negativePrompt = $bible.negative_prompt.ToString()
        }

        $seed = $SeedBase + ($roleIndex * 100) + ($variantIndex * 17)
        Write-Host "[portrait-gen] role=$role variant=$($variant.key) seed=$seed file=$outFile"

        if ($DryRun) {
            Write-Host "[portrait-gen] prompt: $prompt"
            continue
        }

        $requestBody = @{
            prompt = $prompt
            negative_prompt = $negativePrompt
            steps = $Steps
            cfg_scale = $CfgScale
            width = $Width
            height = $Height
            sampler_name = $SamplerName
            seed = $seed
            n_iter = 1
            batch_size = 1
            restore_faces = $true
            send_images = $true
            save_images = $false
        } | ConvertTo-Json -Depth 16

        $response = Invoke-RestMethod -Uri "$apiBase/sdapi/v1/txt2img" -Method Post -ContentType "application/json" -Body $requestBody
        if (-not $response -or -not $response.images -or $response.images.Count -lt 1) {
            throw "[portrait-gen] no image returned for role=$role variant=$($variant.key)"
        }

        $image64 = $response.images[0].ToString()
        if ($image64.Contains(",")) {
            $image64 = $image64.Split(",", 2)[1]
        }
        $bytes = [Convert]::FromBase64String($image64)
        [System.IO.File]::WriteAllBytes($outFile, $bytes)
        $created++
    }
}

Write-Host "[portrait-gen] created=$created skipped=$skipped"
