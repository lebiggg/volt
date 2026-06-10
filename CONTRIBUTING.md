# Contributing to Volt

Thanks for your interest in Volt. This is a FOSS project for the GrapheneOS / privacy community — contributions are welcome.

## Ground rules

- **Zero telemetry, zero trackers.** Any PR adding analytics, crash reporting to third parties, or network calls beyond the user-configured UnifiedPush endpoint will be rejected.
- **No proprietary dependencies.** F-Droid compatibility is a hard requirement. Stick to FOSS, Apache/MIT/GPL-compatible libraries.
- **Privacy by default.** No logging of user content (notification payloads, package activity) outside `if (BuildConfig.DEBUG)`.

## Development setup

```bash
git clone https://github.com/lebiggg/volt
cd volt
./gradlew :app:assembleDebug
```

Requires JDK 17, Android SDK 36. Open in Android Studio (Hedgehog+) or build via CLI.

### Running tests

```bash
./gradlew :app:testDebugUnitTest      # pure-JVM model tests
./gradlew :app:connectedAndroidTest   # device/emulator required
```

## How to contribute

### Adding an app to the curated whitelist

The most common contribution. If a 2FA app, password manager, or critical encrypted messenger could be broken by hibernation and isn't protected, add it to
[`CuratedWhitelist.kt`](app/src/main/java/com/tonnomdeved/volt/data/hibernation/whitelist/CuratedWhitelist.kt).

Your PR must include:
- The exact `packageName`
- A link to the app's source repository
- A one-line justification (e.g. "loses 2FA push notifications when hibernated")

### Code contributions

1. Fork and branch from `main`
2. Keep the architecture: all bucket manipulation goes through `HibernationController` — never call `setAppStandbyBucket` or Shizuku directly elsewhere
3. Add a JVM test for any new pure logic (see `app/src/test/`)
4. Run `./gradlew :app:testDebugUnitTest` before pushing
5. Open a PR describing what changed and why

### Reporting bugs

Use the issue templates. For a mis-hibernated app, include:
- `adb shell dumpsys package <pkg> | grep flags`
- The protection reason shown in the app's detail sheet
- The decomposed nocivity score

## Architecture overview

See the [README](README.md#️-architecture). The golden rule: `HibernationController` is the single entry point for all standby-bucket and force-stop operations. This keeps the surface that touches privileged APIs to one auditable file.

## License

By contributing, you agree your contributions are licensed under GPL-3.0.
