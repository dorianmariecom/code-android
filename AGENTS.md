# Repository Guidelines

## Project Structure & Module Organization

This repo is an Android app built from a single `app/` module with product flavors for `test`, `localhost`, `development`, `staging`, and `production`. Shared code lives in `app/src/main/`, while environment-specific overrides live in flavor source sets such as `app/src/development/` and `app/src/production/`. Release automation and Play Store uploads are defined in `fastlane/`, and helper scripts live in `bin/`.

## Build, Test, and Development Commands

- `./gradlew :app:assembleDevelopmentRelease` builds the Development APK.
- `./gradlew :app:assembleStagingRelease` builds the Staging APK.
- `./gradlew :app:assembleProductionRelease` builds the Production APK.
- `./gradlew :app:bundleProductionRelease` builds the Production AAB for Play upload.
- `bin/sync` is now a no-op because shared code already lives in `app/`.
- `bin/build "release notes"` runs all fastlane lanes for every environment using the provided changelog text.
- `bin/fastlane android development` (or `test`, `localhost`, `staging`, `production`) uploads the selected build to Google Play.

## Coding Style & Naming Conventions

Kotlin code uses 4-space indentation and standard Android naming conventions (PascalCase classes like `MainActivity`, camelCase functions/vars, `snake_case` XML resources such as `activity_main.xml`). Keep file names aligned with class names, and match package names under `com.codedorian`.

## Testing Guidelines

No dedicated test modules or frameworks are visible here. If adding tests, use standard Android locations like `src/test/java` for unit tests and `src/androidTest/java` for instrumentation tests within the relevant module. Document any new test commands in this file.

## Commit & Pull Request Guidelines

Recent commits use short, lowercase, imperative-style summaries (e.g., “bump to 2.7”, “format”). Follow that convention and keep messages concise. PRs should state the target environment module, list any build/run commands executed, and include screenshots for UI changes. If uploading via fastlane, include the release notes text used.

## Configuration & Secrets

Keep local credentials and SDK paths in `local.properties` and environment-specific keystores under `app/keystores/`. Do not commit real secrets; use placeholders or CI-managed secrets where possible.
