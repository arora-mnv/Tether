# AGENTS.md

## Investigation
- Do not scan the whole project; start from `AGENTS.md`, `CLAUDE.md`, root Gradle files, `app/build.gradle.kts`, and the directly related source files.
- For bugs, identify the entry point, trace only the execution path, and stop at the exact failing logic; report root cause, exact line/logic issue, and fix rationale.
- Trust executable Gradle/config files over prose if they conflict.

## Project Shape
- Single Android module `:app`; namespace/application id is `com.anantva.tether`.
- Kotlin + Jetpack Compose app with Hilt, Room, DataStore Preferences, Firebase Auth, and Firestore.
- Source root is `app/src/main/java/com/anantva/tether/`; filenames intentionally use lower camel case in many places, so do not rename to PascalCase for style cleanup.
- App entry is `TetherApplication` (`@HiltAndroidApp`) into `MainActivity` (`@AndroidEntryPoint`, Compose Navigation).

## Commands
- Build debug APK: `./gradlew assembleDebug`.
- Install on device/emulator: `./gradlew installDebug`.
- Unit tests: `./gradlew test` or focused `./gradlew testDebugUnitTest --tests 'fully.qualified.TestName'`.
- Instrumented tests require a connected device/emulator: `./gradlew connectedAndroidTest`.
- Clean only when needed: `./gradlew clean`.

## Gradle / Toolchain Gotchas
- `gradle.properties` pins Java to Android Studio JBR at `/Applications/Android Studio.app/Contents/jbr/Contents/Home`; builds expect Java 17.
- Keep `app/build.gradle.kts` plugin order: Android application, Kotlin Android, Kotlin Compose, KSP, Hilt, Google Services.
- Lint is configured with `abortOnError = false`; disabled checks are `NullSafeMutableLiveData`, `FlowOperatorInvokedInComposition`, `FrequentlyChangingValue`, and `RememberInComposition`.
- Dependencies are mixed: version catalog for core plugins/libs, hardcoded versions for Firebase, Room, DataStore, Navigation, Google Sign-In, and coroutine Play Services.

## Runtime Flow
- Startup path in `MainActivity`: splash with minimum 3 seconds, DataStore onboarding/setup flags, then onboarding → setup → dashboard; dashboard redirects to auth only when cloud sync is enabled and user is not logged in.
- Setup and app preferences live in DataStore named `tether_prefs` via `DataStoreModule` and `UserPreferencesRepository`.
- Repository routing is centralized in `TetherRepository`: when `isCloudStorage` is true and a Firebase uid exists, use Firestore; otherwise use Room.
- Login-triggered cloud sync is launched from `MainActivity` through `SyncManager.syncAll(userId)`.

## Data / Sync Gotchas
- Room database file is `tether_database`; `AppDatabase` is version 6, `exportSchema = false`, and still uses `fallbackToDestructiveMigration()` after explicit migrations.
- If changing Room entities, update `AppDatabase` entities/version/migrations and the DAO/repository path that consumes them.
- Pending transactions are local transient state; do not add Firestore sync for pending review items unless the product behavior changes.
- Banking notifications enter through `services.TetherNotificationListenerService`; parsing/category/dedup logic lives under `data/parser/`.

## Firebase / Auth
- `app/google-services.json` is required and present; Google Services plugin is applied in `app/build.gradle.kts`.
- Google Sign-In uses `R.string.default_web_client_id`; if auth fails with code 10, use `FirebaseAuthManager.getAppSigningSha1()` and check Firebase SHA-1/package/OAuth configuration.
