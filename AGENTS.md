# AGENTS.md

## Investigation
- Start from `AGENTS.md`, `CLAUDE.md`, root Gradle files, `app/build.gradle.kts`, and directly related sources.
- For bugs, identify the entry point, trace the execution path, and stop at the exact failing logic; report root cause, line/logic issue, and fix rationale.
- Trust executable Gradle/config files over prose if they conflict.

## Project Shape
- Single Android module `:app`; namespace/applicationId is `com.anantva.tether`.
- Kotlin + Jetpack Compose with Hilt, Room, DataStore Preferences, Firebase Auth, and Firestore.
- Source root: `app/src/main/java/com/anantva/tether/`. Filenames use lower camel case (`tetherRepository.kt`, `appDatabase.kt`, `firestoreRepository.kt`, `syncManager.kt`, etc.) — do not rename to PascalCase.
- App entry: `TetherApplication` (`@HiltAndroidApp`) → `MainActivity` (`@AndroidEntryPoint`, Compose Navigation).
- UI theme in `ui/theme/`; screens in `ui_elements/screens/`.
- The `receiver/` and `ocr/` directories are empty (planned/stub); all receivers live in `services/`.
- A 0-byte `CalculateDailyLimitUseCase.kt` exists at project root — ignore it; the real one is at `calculator/use_case/calculateDailyLimitUseCase.kt`.

## Commands
- Build debug APK: `./gradlew assembleDebug`.
- Build release APK (minified + obfuscated): `./gradlew assembleRelease`.
- Install on device/emulator: `./gradlew installDebug`.
- Unit tests: `./gradlew test` or `./gradlew testDebugUnitTest --tests 'fully.qualified.TestName'`.
- Instrumented tests need a connected device: `./gradlew connectedAndroidTest`.
- Clean only when needed: `./gradlew clean`.

## Gradle / Toolchain Gotchas
- `gradle.properties` used to pin Java to `/Applications/Android Studio.app/...` — that hardcoded path was removed. Gradle 9 requires **Java 17+**; set `JAVA_HOME` or ensure `java` on PATH is ≥ 17.
- Keep `app/build.gradle.kts` plugin order: Android application, Kotlin Android, Kotlin Compose, KSP, Hilt, Google Services.
- Lint: `abortOnError = false`; disabled checks: `NullSafeMutableLiveData`, `FlowOperatorInvokedInComposition`, `FrequentlyChangingValue`, `RememberInComposition`.
- Dependencies mixed: version catalog for core plugins/libs, hardcoded versions for Firebase, Room, DataStore, Navigation, Google Sign-In, and coroutine Play Services.
- `proguard-rules.pro` is essentially empty (defaults only).
- Room `exportSchema = true` with output at `app/schemas/`. When changing entities, update version + migrations + schema JSON.

## Runtime Flow
- Startup in `MainActivity`: installSplashScreen (min 3s), read DataStore flags, then onboarding → setup → dashboard. Dashboard redirects to `auth` when `isCloudSyncEnabled && !isLoggedIn`.
- Preferences in DataStore `tether_prefs` via `DataStoreModule` and `UserPreferencesRepository`.
- Repository routing in `TetherRepository`: `isCloud()` checks `isCloudStorage` + `uid()` — if both true, route to Firestore; otherwise use Room DAOs directly.
- Login-triggered sync launched from `MainActivity` via `LaunchedEffect(isLoggedIn, isCloudSyncEnabled)` → `SyncManager.syncAll(userId)` does bidirectional reconciliation between Room and Firestore.
- `AppForegroundTracker` (in `lifecycle/`) uses `ProcessLifecycleOwner` to track foreground state and clears `PendingSnoozeStore` on app start.

## Data / Sync Gotchas
- Room file `tether_database`; `AppDatabase` version 6, `exportSchema = true`, migrations: 1→2, 2→3, 3→4, 4→5, 5→6 (full chain). No `fallbackToDestructiveMigration`. Entities: `UserProfileEntity`, `TransactionEntity`, `GoalEntity`, `CategoryCorrectionEntity`. `CategoryPatternDao`/`CategoryPatternEntity` exist but are NOT registered in `AppDatabase` (dead code / planned).
- If changing Room entities, update `AppDatabase` entities list, version, and migrations. Schema JSON exports in `app/schemas/` must be regenerated (automatic on next build) and committed.
- Pending transactions (status=`"PENDING"`) are local transient state — never synced to Firestore. `addPendingTransactionFromNotification()` skips cloud save entirely.
- Banking notifications parsed by `TetherNotificationListenerService` (in `services/tetherNotificationListener.kt`); parsing in `data/parser/` via `TransactionParser` + `CategoryEngine` + `DeduplicationEngine`. Targets Indian banking SMS formats.
- `BootReceiver` (in `services/bootReceiver.kt`) is a stub with only a TODO comment.
- Firestore date-range queries (`whereGreaterThanOrEqualTo` + `whereLessThanOrEqualTo`) may require composite indexes.

## Firebase / Auth
- `app/google-services.json` is required and present.
- Google Sign-In uses `R.string.default_web_client_id`. On auth failure code 10, use `FirebaseAuthManager.getAppSigningSha1()` and verify SHA-1/package/OAuth config in Firebase Console.
