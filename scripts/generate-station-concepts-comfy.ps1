[CmdletBinding()]
param(
    [string]$ComfyApiUrl = "http://127.0.0.1:8188",
    [string]$OutRoot = "assets/station_concepts",
    [string]$CheckpointName = "Realistic_Vision_V6.0_NV_B1.safetensors",
    [int]$Width = 1280,
    [int]$Height = 768,
    [int]$Steps = 30,
    [double]$CfgScale = 6.5,
    [string]$SamplerName = "dpmpp_2m",
    [string]$Scheduler = "karras",
    [int]$SeedBase = 941000,
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

    throw "[station-gen-comfy] timeout waiting for prompt_id=$PromptId"
}

$apiBase = $ComfyApiUrl.TrimEnd("/")
New-Item -ItemType Directory -Path $OutRoot -Force | Out-Null

if (-not $DryRun) {
    try {
        Invoke-RestMethod -Uri "$apiBase/system_stats" -Method Get -TimeoutSec 5 | Out-Null
    } catch {
        throw ("ComfyUI API is not reachable at " + $apiBase + ". " +
               "Start ComfyUI and verify the host:port.")
    }

    $checkpoints = @()
    try {
        $checkpointResp = Invoke-RestMethod -Uri "$apiBase/models/checkpoints" -Method Get -TimeoutSec 15
        $checkpoints = @($checkpointResp | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
    } catch {
        throw "[station-gen-comfy] could not query checkpoints from $apiBase/models/checkpoints"
    }

    if ($checkpoints.Count -eq 0) {
        throw "[station-gen-comfy] no checkpoint models found in ComfyUI."
    }

    if ($checkpoints -notcontains $CheckpointName) {
        Write-Warning "[station-gen-comfy] checkpoint '$CheckpointName' not found. Falling back to '$($checkpoints[0])'."
        $CheckpointName = $checkpoints[0]
    }
}

Write-Host "[station-gen-comfy] checkpoint=$CheckpointName"

$negativePrompt = @(
    "text",
    "letters",
    "numbers",
    "watermark",
    "logo",
    "caption",
    "people",
    "character",
    "crew",
    "fighter swarm",
    "multiple stations",
    "duplicated structure",
    "cropped station",
    "out of frame",
    "low detail",
    "blurry",
    "deformed",
    "extra modules",
    "extra arms",
    "warped perspective",
    "oversaturated",
    "cartoon",
    "ground",
    "ocean",
    "sea",
    "harbor",
    "dock",
    "water",
    "shoreline",
    "city",
    "street",
    "building",
    "castle",
    "church",
    "cathedral",
    "submarine",
    "boat",
    "warship at sea",
    "terrestrial horizon",
    "skyline",
    "atmosphere",
    "cloud layer",
    "runway",
    "smoke plume"
) -join ", "

$sharedStyle = @(
    "heroic orbital space station concept art for a 2D space combat game",
    "single station only",
    "single orbital station megastructure",
    "clearly a station, not a ship",
    "floating in outer space",
    "in vacuum",
    "three-quarter orbital view",
    "large readable silhouette",
    "high detail hard-surface construction",
    "cinematic matte painting",
    "painterly-realistic sci-fi art",
    "black starfield backdrop",
    "distant planet limb in the background",
    "harsh orbital sunlight",
    "no atmosphere",
    "no ground",
    "no sea",
    "no text",
    "no watermark"
)

$stations = @(
    [pscustomobject]@{
        Key = "green_team_c"
        File = "green_team_c_station.png"
        Prompt = @(
            "green team orbital station, Aegis Lattice fleet architecture",
            "elegant orbital fortress station with shield-buttress arcs and halo structures",
            "pale celadon and ivory armor",
            "luminous emerald and teal shield-glass galleries",
            "orbital docking spines and suspended habitat modules",
            "smooth continuous station body",
            "refined bilateral symmetry",
            "advanced defensive fleet identity",
            "serene but militarized",
            "clean energy-lattice detailing",
            "clearly a sci-fi station in space, not a cathedral or ground building"
        )
    }
    [pscustomobject]@{
        Key = "red_enemy"
        File = "red_enemy_station.png"
        Prompt = @(
            "red team orbital station, disciplined kinetic navy fortress station",
            "heavily armored industrial war station with dockyard citadel modules",
            "dark gunmetal hull with deep crimson armor panels",
            "broad citadel massing",
            "layered armor belts",
            "practical military geometry",
            "orbital dockyard superstructure, trusses, and fixed defense spines",
            "intimidating but functional",
            "red tactical lighting and sparse warning stripes",
            "clearly a station in vacuum, not a seagoing ship or harbor structure"
        )
    }
    [pscustomobject]@{
        Key = "yellow_team_d"
        File = "yellow_team_d_station.png"
        Prompt = @(
            "yellow team orbital station, missile faction arsenal bastion",
            "bunker-like fortress station with arsenal silos and heavy station modules",
            "blackened steel hull with worn brown armor and amber utility lights",
            "thick armored shell segments",
            "recessed launch architecture and munitions vaults",
            "blocky industrial station silhouette",
            "munition vault character",
            "oppressive heavy mass",
            "hazard-lit docking trenches",
            "clearly a space station in orbit, not a submarine, tanker, or ground installation"
        )
    }
)

$created = 0
$skipped = 0

for ($index = 0; $index -lt $stations.Count; $index++) {
    $spec = $stations[$index]
    $outFile = Join-Path $OutRoot $spec.File
    if ((Test-Path -LiteralPath $outFile) -and -not $Overwrite) {
        $skipped++
        Write-Host "[station-gen-comfy] skipping existing file=$outFile"
        continue
    }

    $prompt = Join-NonEmpty -Parts ($sharedStyle + $spec.Prompt)
    $seed = $SeedBase + ($index * 97)
    $filenamePrefix = "station_$($spec.Key)"
    Write-Host "[station-gen-comfy] key=$($spec.Key) seed=$seed file=$outFile"
    Write-Host "[station-gen-comfy] prompt=$prompt"

    if ($DryRun) {
        continue
    }

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
        throw "[station-gen-comfy] failed to queue prompt for key=$($spec.Key)"
    }

    $result = Wait-ComfyHistory -ApiBase $apiBase -PromptId $queueResp.prompt_id -TimeoutSec $PromptTimeoutSec
    $images = @()
    $outputProps = @()
    if ($result.outputs) {
        $outputProps = @($result.outputs.PSObject.Properties | ForEach-Object { $_.Name })
        if ($outputProps -contains "9") {
            $images = @($result.outputs."9".images)
        }
        if ($images.Count -eq 0) {
            foreach ($prop in $outputProps) {
                $nodeOut = $result.outputs.$prop
                if ($nodeOut -and $nodeOut.images) {
                    $images = @($nodeOut.images)
                    break
                }
            }
        }
    }

    if ($images.Count -eq 0 -and $result.status) {
        $statusStr = ""
        if ($result.status.PSObject.Properties["status_str"]) {
            $statusStr = $result.status.status_str.ToString()
        }
        if ($statusStr -eq "error" -and $result.status.PSObject.Properties["messages"]) {
            foreach ($entry in @($result.status.messages)) {
                if ($entry.Count -ge 2 -and $entry[0] -eq "execution_error") {
                    $err = $entry[1]
                    $message = ""
                    if ($err.PSObject.Properties["exception_message"]) {
                        $message = $err.exception_message.ToString().Trim()
                    }
                    if ($message) {
                        throw "[station-gen-comfy] ComfyUI execution error for key=$($spec.Key): $message"
                    }
                }
            }
        }
    }

    if ($images.Count -eq 0) {
        throw "[station-gen-comfy] no image outputs for key=$($spec.Key)"
    }

    $imageMeta = $images[0]
    $filename = $imageMeta.filename.ToString()
    $subfolder = ""
    $imageMetaProps = @($imageMeta.PSObject.Properties | ForEach-Object { $_.Name })
    if ($imageMetaProps -contains "subfolder" -and $imageMeta.subfolder) {
        $subfolder = $imageMeta.subfolder.ToString()
    }
    $type = "output"
    if ($imageMetaProps -contains "type" -and $imageMeta.type) {
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

Write-Host "[station-gen-comfy] created=$created skipped=$skipped"
