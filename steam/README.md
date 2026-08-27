# Steam Build Support

This directory contains checked-in, credential-free SteamPipe templates. Generated
content, manifests, and final VDF files are written under ignored `build/steam/`.

To build and validate the Windows depot locally:

```powershell
./scripts/prepare-steam-windows.ps1
```

After Steamworks assigns the non-secret AppID and Windows depot ID:

```powershell
./scripts/prepare-steam-windows.ps1 -SkipBuild -AppId <APP_ID> -DepotId <DEPOT_ID>
```

Add `-SetLiveBranch release-candidate` only when that branch already exists and
you deliberately want SteamPipe to assign a successful upload to it. The script
does not upload anything. It never accepts a username, password, or Steam Guard
code and no credential belongs in this repository.

Review `build/steam/scripts/app_build_<APP_ID>.vdf`, then invoke the current
Steamworks SDK `ContentBuilder/builder/steamcmd.exe` from outside the repository
using Valve's documented upload command. Owner authentication happens directly
in SteamCMD.
