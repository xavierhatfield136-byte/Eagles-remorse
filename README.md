# Java Space Combat Game

A 2D Java/Swing space combat game with multiple fleet factions, ship classes, campaign/debug tooling, and a large set of simulation harnesses for balance and UI validation.

## Requirements

- Java 21
- Git

The project uses the included Gradle wrapper, so a separate Gradle install is not required.

## Project Layout

- `src/` - game source and harness entry points
- `assets/` - game art and other runtime assets
- `config/` - static analysis configuration
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

## Notes

- Local saves and machine-specific IDE files are intentionally ignored and are not required to build the project.
- Several standalone harness classes in `src/` are used for diagnostics, replay checks, and UI validation.

## License

This project is source-available under the terms in [LICENSE.md](LICENSE.md).
