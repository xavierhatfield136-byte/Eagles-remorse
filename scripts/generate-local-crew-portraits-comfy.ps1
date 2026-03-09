[CmdletBinding()]
param(
    [string]$ComfyApiUrl = "http://127.0.0.1:8188",
    [string]$BiblePath = "assets/ai_pipeline/crew_portrait_bible.json",
    [string]$OutRoot = "assets/crew_portraits",
    [string]$CheckpointName = "",
    [int]$Width = 1024,
    [int]$Height = 1024,
    [int]$Steps = 32,
    [double]$CfgScale = 6.5,
    [string]$SamplerName = "dpmpp_2m",
    [string]$Scheduler = "karras",
    [int]$SeedBase = 770000,
    [int]$PromptTimeoutSec = 900,
    [switch]$Overwrite,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Join-NonEmpty {
    param([object[]]$Parts)
    return ($Parts | Where-Object { $_ -and $_.ToString().Trim().Length -gt 0 }) -join ", "
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
            $history = Invoke-RestMethod -Uri "$ApiBase/history/$PromptId" -Method Get -TimeoutSec 15
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

    throw "[portrait-gen-comfy] timeout waiting for prompt_id=$PromptId"
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
$apiBase = $ComfyApiUrl.TrimEnd("/")

if (-not $DryRun) {
    try {
        Invoke-RestMethod -Uri "$apiBase/system_stats" -Method Get -TimeoutSec 5 | Out-Null
    } catch {
        throw ("ComfyUI API is not reachable at " + $apiBase + ". " +
               "Start ComfyUI and verify the host:port.")
    }
}

if ([string]::IsNullOrWhiteSpace($CheckpointName)) {
    if (-not $DryRun) {
        $checkpoints = @()
        try {
            $checkpointResp = Invoke-RestMethod -Uri "$apiBase/models/checkpoints" -Method Get -TimeoutSec 15
            $checkpoints = @($checkpointResp | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
        } catch {
            throw "[portrait-gen-comfy] could not query checkpoints from $apiBase/models/checkpoints"
        }

        if ($checkpoints.Count -eq 0) {
            throw "[portrait-gen-comfy] no checkpoint models found in ComfyUI (models/checkpoints)."
        }

        $CheckpointName = $checkpoints[0].ToString()
    } else {
        $CheckpointName = "<first checkpoint>"
    }
}

Write-Host "[portrait-gen-comfy] checkpoint=$CheckpointName"

$created = 0
$skipped = 0

for ($roleIndex = 0; $roleIndex -lt $roles.Count; $roleIndex++) {
    $roleSpec = $roles[$roleIndex]
    $role = $roleSpec.role.ToString().Trim().ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($role)) {
        Write-Warning "[portrait-gen-comfy] skipping role with empty key at index $roleIndex"
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
        Write-Host "[portrait-gen-comfy] role=$role variant=$($variant.key) seed=$seed file=$outFile"

        if ($DryRun) {
            Write-Host "[portrait-gen-comfy] prompt: $prompt"
            continue
        }

        $filenamePrefix = "crew_${role}_$($variant.key)"
        $workflow = @{
            "4" = @{
                class_type = "CheckpointLoaderSimple"
                inputs = @{
                    ckpt_name = $CheckpointName
                }
            }
            "6" = @{
                class_type = "CLIPTextEncode"
                inputs = @{
                    text = $prompt
                    clip = @("4", 1)
                }
            }
            "7" = @{
                class_type = "CLIPTextEncode"
                inputs = @{
                    text = $negativePrompt
                    clip = @("4", 1)
                }
            }
            "5" = @{
                class_type = "EmptyLatentImage"
                inputs = @{
                    width = $Width
                    height = $Height
                    batch_size = 1
                }
            }
            "3" = @{
                class_type = "KSampler"
                inputs = @{
                    seed = $seed
                    steps = $Steps
                    cfg = $CfgScale
                    sampler_name = $SamplerName
                    scheduler = $Scheduler
                    denoise = 1.0
                    model = @("4", 0)
                    positive = @("6", 0)
                    negative = @("7", 0)
                    latent_image = @("5", 0)
                }
            }
            "8" = @{
                class_type = "VAEDecode"
                inputs = @{
                    samples = @("3", 0)
                    vae = @("4", 2)
                }
            }
            "9" = @{
                class_type = "SaveImage"
                inputs = @{
                    filename_prefix = $filenamePrefix
                    images = @("8", 0)
                }
            }
        }

        $requestBody = @{
            prompt = $workflow
            client_id = [Guid]::NewGuid().ToString("N")
        } | ConvertTo-Json -Depth 30

        $queueResp = Invoke-RestMethod -Uri "$apiBase/prompt" -Method Post -ContentType "application/json" -Body $requestBody
        if (-not $queueResp -or [string]::IsNullOrWhiteSpace($queueResp.prompt_id)) {
            throw "[portrait-gen-comfy] failed to queue prompt for role=$role variant=$($variant.key)"
        }

        $result = Wait-ComfyHistory -ApiBase $apiBase -PromptId $queueResp.prompt_id -TimeoutSec $PromptTimeoutSec
        $images = @()
        if ($result.outputs) {
            if ($result.outputs.PSObject.Properties.Name -contains "9") {
                $images = @($result.outputs."9".images)
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
            throw "[portrait-gen-comfy] no image outputs for role=$role variant=$($variant.key)"
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
            $apiBase, `
            [System.Uri]::EscapeDataString($filename), `
            [System.Uri]::EscapeDataString($subfolder), `
            [System.Uri]::EscapeDataString($type)

        Invoke-WebRequest -Uri $viewUri -OutFile $outFile -TimeoutSec 120 | Out-Null
        $created++
    }
}

Write-Host "[portrait-gen-comfy] created=$created skipped=$skipped"
