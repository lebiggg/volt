# Changelog

All notable changes to Volt are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/).

## [0.3.0-alpha] — unreleased

### Added
- **Auto-hibernation now actually runs.** The decision engine is wired to the
  periodic sweep and a manual "run now" button — apps are automatically
  hibernated based on their nocivity score and the configured thresholds.
- "Auto-hibernation" toggle on the Hibernate screen.
- `BootReceiver` — opt-in service start after device reboot (off by default).
- "Start on boot" toggle in Settings → Behavior.
- GitHub Actions CI (build + unit tests on every push/PR).
- `CONTRIBUTING.md`, issue templates, changelog.

## [0.2.0-alpha] — 2026-06-10

### Added
- Battery savings estimate (% / mAh / standby minutes) on the Hibernate hero card.
- "Wake all" panic button.
- Settings screen: theme (system/light/dark), dynamic color toggle,
  configurable auto-hibernation thresholds, about section.
- Theme reacts to saved preference.

## [0.1.0-alpha] — 2026-06-10

### Added
- Initial release: Hibernate module (Greenify successor).
- 5-layer auto whitelist, 0-100 nocivity scoring, 3 hibernation levels.
- Wake-on-push via `FLAG_INCLUDE_STOPPED_PACKAGES`.
- Quick Settings tile, periodic WorkManager sweep, Room persistence.
- UnifiedPush hub (experimental).
- Custom lightning-bolt launcher icon.

### Notes
- Android 16: `CHANGE_APP_IDLE_STATE` is locked by the OS — Shizuku is required.
