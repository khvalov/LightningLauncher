# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lightning Launcher is an Android app launcher for Meta Quest (and Android TV/general Android). It is written in Java (not Kotlin), targeting SDK 36 with minSdk 24.

## Modules

The repo contains three independent Android projects, each with its own `gradlew`:

- **`Launcher/`** — the main app. All development happens here.
- **`RedirectServices/`** — a small companion app for shortcut/redirect handling.
- **`LegacyRedirect/`** — backward-compatibility redirect app.

## Build Commands

Run from the relevant module directory (e.g., `Launcher/`):

```bash
# Build debug APK (sideload flavor — the default)
./gradlew assembleSideloadDebug

# Build release APK
./gradlew assembleSideloadRelease

# Run lint
./gradlew lint

# Build all flavors
./gradlew assemble
```

Product flavors: `sideload` (default/GitHub), `playstore`, `metastore` (Meta Quest store, includes Firebase Crashlytics).

## Architecture

### Two-package structure

The source lives in two Java packages inside `Launcher/App/src/main/java/`:

- **`com.threethan.launchercore`** — reusable core: app-type detection, icon loading, blur views, utility libs. Designed to be platform-agnostic.
- **`com.threethan.launcher`** — launcher-specific logic: activities, adapters, settings, updaters, Quest-specific integrations.

### Key architectural patterns

**Service-backed view persistence** (`LauncherService`): The main UI view is stored inside a bound `Service` so that when `LauncherActivity` is finished and recreated, it can reattach the existing inflated view rather than reinflating from scratch. This is what gives the launcher its "lightning fast" feel.

**Activity hierarchy**:
- `LauncherActivity` → `LauncherActivityEditable` → `LauncherActivitySearchable`
- Each layer adds a capability (editing, search). `ChainLoadActivity*` variants handle different screen sizes/orientations by immediately relaunching the appropriate activity.

**App type system** (`launchercore.util.App.Type`): Apps are classified as `PHONE`, `VR`, `TV`, `PANEL`, `SHORTCUT`, `WEB`, `UTILITY`, or `UNSUPPORTED`. Type determines default display mode (banner vs icon), group assignment, and launch behavior.

**Settings** (`SettingsManager` + `DataStoreEditor`): Preferences are stored via AndroidX DataStore. `SettingsManager` holds all preference keys and defaults. `DataStoreEditor` is a synchronous wrapper around the async DataStore API.

**Icon/metadata loading** (`launchercore.metadata`): `IconLoader` fetches icons from the `MetaMetaData` GitHub CDN for known Quest apps and falls back to the system package icon. `IconUpdater` handles background refresh.

**Groups/adapter flow**: `LauncherAppsAdapter` drives the main grid. `GroupsAdapter` drives the group tab bar. `SortHandler` and `SortableFilterPredicate` handle sorting and filtering logic.

### Quest-specific integrations

- `PlatformExt` / `Platform` — detects Quest vs Android TV vs phone, adjusts behavior accordingly.
- `QuestGameTuner` — optional integration with Quest Game Tuner app.
- `ShortcutStateProvider` / `QuestAppMenuProvider` — content providers used by Quest's shortcut/dock system.
- `PlaytimeHelper` — reads usage stats for playtime display.
