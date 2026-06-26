# Java Space Combat Game

A 2D Java/Swing space combat game with multiple fleet factions, ship classes, campaign/debug tooling, and a large set of simulation harnesses for balance and UI validation.

## Requirements

- Java 21
- Git

The project uses the included Gradle wrapper, so a separate Gradle install is not required.

## Project Layout

- `src/` - game source and harness entry points
- `assets/` - game art and other runtime assets
- `config/` - runtime tuning and content-pack fixtures
- `docs/` - design and supporting notes

## Build

```powershell
./gradlew compileJava
```

## Test / Checks

```powershell
./gradlew test
./gradlew check
```

## Run

The main desktop entry point is `Main`.

From an IDE, run the `Main` class from the project root.

From PowerShell after compiling:

```powershell
java -cp "build/classes/java/main;build/resources/main" Main
```

## Package For Windows

Create a portable Windows app bundle with a bundled Java runtime:

```powershell
./gradlew packageWindowsAppImage
```

Zip that portable bundle for sharing:

```powershell
./gradlew packageWindowsZip
```

Create a Windows `.exe` installer with a bundled Java runtime:

```powershell
./gradlew packageWindowsExe
```

This installer step requires WiX on your `PATH` (`candle.exe` and `light.exe`).

Build both package types at once:

```powershell
./gradlew packageWindows
```

Generated packages are written to `build/package/windows/`. If WiX is not installed, `packageWindows` still builds the portable app bundle plus ZIP and skips the `.exe` installer.

## Package For Linux

On a Linux host, create a portable archive with its own bundled Java runtime:

```bash
./gradlew phase11LinuxPackaging
```

The resulting `EaglesRemorse-<version>-linux-x64.tar.gz` is written to
`build/package/linux/`. Extract it and run `EaglesRemorse/bin/EaglesRemorse`.

Validate the packaged build and generate checksums:

```powershell
./gradlew phase11Packaging
```

Packaged saves, settings, and logs are written to the user's application-data
folder, not the install directory. On Windows the default location is:

```text
%APPDATA%\Eagles Remorse
```

## GitHub Packaging

The repo includes native Windows and Linux packaging workflows in
`.github/workflows/`.

- Run it manually from the Actions tab with `workflow_dispatch`
- Or publish a GitHub Release to build both platform packages and attach them as release assets

## Notes

- Local saves and machine-specific IDE files are intentionally ignored and are not required to build the project.
- Several standalone harness classes in `src/` are used for diagnostics, replay checks, and UI validation.

## License

This project is source-available under the terms in [LICENSE.md](LICENSE.md).
