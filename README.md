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

## GitHub Packaging

The repo includes a GitHub Actions workflow at `.github/workflows/windows-package.yml`.

- Run it manually from the Actions tab with `workflow_dispatch`
- Or publish a GitHub Release to have it build Windows packages and attach them as release assets

## Notes

- Local saves and machine-specific IDE files are intentionally ignored and are not required to build the project.
- Several standalone harness classes in `src/` are used for diagnostics, replay checks, and UI validation.

## License

This project is source-available under the terms in [LICENSE.md](LICENSE.md).
