# Crew Portrait AI Generator Guide

Date: 2026-03-09

This guide documents how to run the local crew portrait generator used in this repo, how prompt tuning works, and a practical workflow for getting usable results quickly.

## 1) What This Uses

- Generator UI/API: ComfyUI (WSL, local)
- Generation script: `scripts/generate-local-crew-portraits-comfy.ps1`
- Prompt config ("bible"): `assets/ai_pipeline/crew_portrait_bible.json`
- Output folder: `assets/crew_portraits`

## 2) How To Open The AI Generator

Open ComfyUI (foreground):

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "cd ~/ComfyUI && source venv/bin/activate && python main.py"
```

Open ComfyUI (background):

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "cd ~/ComfyUI && source venv/bin/activate && nohup python main.py >/tmp/comfy.log 2>&1 < /dev/null & disown"
```

Check that it is running:

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:8188/system_stats" -UseBasicParsing
```

If status is `200`, the API is ready.

## 2.1) Full C Drive Access Walkthrough (Windows Paths)

Use this section if you want to run everything from normal Windows paths on the `C:` drive.

### A) Open a PowerShell window in the project

Project path:

`C:\Users\xhatf\IdeaProjects\game`

Either:

- In File Explorer, open `C:\Users\xhatf\IdeaProjects\game`, click the address bar, type `powershell`, press Enter.
- Or open PowerShell and run:

```powershell
cd C:\Users\xhatf\IdeaProjects\game
```

### B) Start ComfyUI in WSL

Foreground (keep this window open while generating):

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "cd ~/ComfyUI && source venv/bin/activate && python main.py"
```

Background (start and return to prompt):

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "cd ~/ComfyUI && source venv/bin/activate && nohup python main.py >/tmp/comfy.log 2>&1 < /dev/null & disown"
```

### C) Confirm generator API is live

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:8188/system_stats" -UseBasicParsing
```

Expected: `StatusCode : 200`

### D) Run generation from C drive

From inside `C:\Users\xhatf\IdeaProjects\game`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-local-crew-portraits-comfy.ps1 -Overwrite
```

From anywhere on Windows (absolute path):

```powershell
powershell -ExecutionPolicy Bypass -File "C:\Users\xhatf\IdeaProjects\game\scripts\generate-local-crew-portraits-comfy.ps1" -Overwrite
```

### E) Where files are read/written

- Prompt config read from:
  `C:\Users\xhatf\IdeaProjects\game\assets\ai_pipeline\crew_portrait_bible.json`
- Portrait outputs written to:
  `C:\Users\xhatf\IdeaProjects\game\assets\crew_portraits`

### F) Open the generated portraits in Explorer

```powershell
explorer C:\Users\xhatf\IdeaProjects\game\assets\crew_portraits
```

### G) Stop ComfyUI when done

If running in foreground, press `Ctrl+C` in that ComfyUI terminal.

If running in background:

```powershell
wsl -d Ubuntu-24.04 -- bash -lc "pkill -f 'python main.py'"
```

## 3) Generate Portraits

Basic run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-local-crew-portraits-comfy.ps1 -Overwrite
```

Recommended explicit run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-local-crew-portraits-comfy.ps1 `
  -Overwrite `
  -CheckpointName "Realistic_Vision_V6.0_NV_B1.safetensors" `
  -Width 768 `
  -Height 1024 `
  -Steps 34 `
  -CfgScale 6.2 `
  -SeedBase 1510000
```

Dry run (prints prompts only):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-local-crew-portraits-comfy.ps1 -DryRun -Overwrite
```

## 4) Prompt Structure (What To Edit)

All prompt content lives in:

- `assets/ai_pipeline/crew_portrait_bible.json`

Key fields:

- `style_lock_prompt`: global art direction + framing + wardrobe rules
- `negative_prompt`: global exclusions (artifacts, NSFW, style drift)
- `quality_constraints`: always-appended guardrails
- `roles[].portrait_prompt`: role personality/identity
- `roles[].expression_prompts`: `base`, `alt_01`, `alt_02`, `alt_03`

Each generated prompt is:

`style_lock_prompt + role portrait_prompt + expression_prompt + quality_constraints`

## 5) Prompt Guidelines That Work Better

- Keep identities simple and direct: age + role + personality + clothing.
- Keep framing explicit: "upper-body from mid-torso up" or "head-and-shoulders".
- Keep clothing constraints explicit: high collar, long sleeves, full coverage.
- Use fewer stacked weighted tokens unless necessary; over-weighting often causes uncanny faces.
- Avoid contradictory instructions in the same role prompt.
- Keep negatives focused on known failure modes; overly long negatives can destabilize.

## 6) Suggested Workflow

1. Start ComfyUI and verify API health.
2. Edit the portrait bible.
3. Run `-DryRun -Overwrite` and inspect prompt text.
4. Generate one full set (`-Overwrite`).
5. Review all 20 outputs in `assets/crew_portraits`.
6. Tweak only one axis at a time:
   - model/checkpoint
   - prompt wording
   - CFG/steps
   - seed base
7. Re-run and compare.

## 7) Troubleshooting

### Faces look unsettling / uncanny

- Lower CFG (example: `6.0-6.8`)
- Use moderate steps (`30-38`)
- Simplify prompt wording (remove repeated emphasis)
- Try a different realism checkpoint

### Output ignores role traits

- Move role trait language into `roles[].portrait_prompt` as short direct sentences.
- Remove extra style words that compete with character descriptors.

### Clothing drift (too revealing / wrong style)

- Keep explicit conservative clothing constraints in `style_lock_prompt`.
- Add targeted negatives for revealing clothing terms.

### Gender drift

- State gender directly in each `roles[].portrait_prompt`.
- Keep gender-related negatives concise but clear.
- If still unstable, switch to a checkpoint/LoRA that better matches target identity constraints.

## 8) Practical Iteration Ideas

- Multi-set generation: run 3 seed bases and keep best per slot.
- Lock winners: maintain a per-file seed list for stable reruns.
- Role-first tuning: perfect one role first, then copy structure to others.
- Expression control: keep `base` neutral and reserve intensity for `alt_03`.

## 9) Optional Metadata Log (Recommended)

Track each run in a simple note:

- Date/time
- Checkpoint
- Width/Height
- Steps/CFG/Sampler
- Seed base
- Prompt bible revision
- Result notes (what improved, what regressed)

This makes future tuning much faster and reproducible.
