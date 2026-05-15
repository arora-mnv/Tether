# AGENTS.md

## Investigation
- Start from `AGENTS.md`, root Gradle files, `app/build.gradle.kts`, and directly related sources.
- For bugs: identify entry point, trace execution, stop at failing logic; report root cause, line/logic, fix rationale.
- Trust executable config files over prose if they conflict.

## Project Shape
- Single module `:app`; namespace/applicationId `com.anantva.tether`.
- Kotlin + Jetpack Compose + Hilt + Room + DataStore + Firebase Auth + Firestore.
- Source root: `app/src/main/java/com/anantva/tether/`. UI screens in `ui_elements/screens/`; theme in `ui/theme/`.
- App entry: `TetherApplication` (`@HiltAndroidApp`) → `MainActivity` (`@AndroidEntryPoint`, Compose Navigation via NavHost in `setContent`).
- Key dirs: `auth/` (FirebaseAuthManager), `calculator/`, `data/` (local, model, parser, repository), `di/` (Hilt modules), `insights/`, `lifecycle/` (AppForegroundTracker), `services/` (notification listener + helpers), `state/` (AppStartState), `transactionpopup/` (PendingSnoozeStore).

## Commands
- Build debug: `./gradlew assembleDebug` (`app/build.gradle.kts` sets compileSdk=35, minSdk=29, targetSdk=35).
- Build release (minified + obfuscated): `./gradlew assembleRelease`.
- Unit tests: `./gradlew test` or `./gradlew testDebugUnitTest --tests 'fully.qualified.TestName'`.
- Instrumented tests: `./gradlew connectedAndroidTest` (needs device/emulator).
- CI (`.github/workflows/ci.yml`): `./gradlew assembleDebug test` on push/PR to `main`.
- Get SHA-1 for Firebase: `./gradlew signingReport`.
- Clean: `./gradlew clean` (only when needed).

## Gradle / Toolchain
- Gradle 9 requires **Java 17+** — set `JAVA_HOME`.
- Plugin order in `app/build.gradle.kts`: Android app, Kotlin Android, Kotlin Compose, KSP, Hilt, Google Services.
- All dependency versions in `gradle/libs.versions.toml` (AGP 8.7.3, Kotlin 2.0.21, Hilt 2.52, Room 2.6.1, Compose BOM 2024.10.01).
- Lint: `abortOnError = false`, `ignoreTestSources = true`.
- Room `exportSchema = true` → `app/schemas/`. Change entities → update version + add migration + schema JSON regenerated on next build (must be committed).
- ProGuard: `proguard-rules.pro` covers Room, Hilt/Dagger, Firebase, Compose, Kotlin coroutines.

## Source File Naming
- Most data-layer files use **camelCase** (`transactionDataSource.kt`, `syncOrchestrator.kt`, `authRepository.kt`, `tetherNotificationListener.kt`).
- Top-level app files and UI files use **PascalCase** (`TetherApplication.kt`, `MainActivity.kt`, `CategoryEngine.kt`, `DeduplicationEngine.kt` in `deDuplicateEngine.kt`).
- Don't assume naming convention from class name alone; check the actual filename.

## Runtime Flow
- `MainActivity`: `installSplashScreen` (min 3s delay enforced via `LaunchedEffect`), reads DataStore flags → navigates: `onboarding` → `setup` → `dashboard`.
- Dashboard redirects to `auth` when `isCloudSyncEnabled && !isLoggedIn`; auth flow includes `nameInput` screen.
- Preferences in DataStore `tether_prefs` (`DataStoreModule` + `UserPreferencesRepository`).
- `SyncOrchestrator` started in `TetherApplication.onCreate()`, not coordinated by `MainActivity`.
- `AppForegroundTracker` registered via `ProcessLifecycleOwner`; calls `pendingSnoozeStore.clearAllNotificationSuppress()` on each `onStart` (every foreground event, not just cold start).

## Data Source Architecture
- `TransactionDataSource` interface → `LocalTransactionDataSource` (Room) + `CloudTransactionDataSource` (Firestore).
- `TransactionDataSourceRouter` selects source at call time (`useCloud()` checks DataStore + auth).
- Transaction CRUD is delegated to the router. However, `TetherRepository` **still uses `isCloud()` branching** for user profile, goal operations, `confirmAndUpdateTransaction`, and suggestion methods — only transaction CRUD goes through the router.

## Data / Sync Gotchas
- Room: `tether_database`, `AppDatabase` version 6, migrations 1→2, 2→3, 3→4, 4→5, 5→6. No `fallbackToDestructiveMigration`. Entities: `UserProfileEntity`, `TransactionEntity`, `GoalEntity`, `CategoryCorrectionEntity`.
- Pending transactions (`status = "PENDING"`) are local-only. `addPendingTransactionFromNotification()` skips cloud save.
- Banking notifications: `TetherNotificationListener` (`services/tetherNotificationListener.kt`) → `TransactionParser` + `DeduplicationEngine` (`data/parser/`). Deduplication: 60s window on amount+merchant fingerprint. Targets Indian banking SMS formats.
- Firestore date-range queries need composite indexes — tracked in `firestore.indexes.json`.
- `PendingSnoozeStore` (`transactionpopup/PendingSnoozeStore.kt`): suppresses notification popups while app is in foreground; cleared by `AppForegroundTracker`.

## Firebase / Auth
- `app/google-services.json` required and present.
- Google Sign-In: `R.string.default_web_client_id`. On auth failure code 10, use `FirebaseAuthManager.getAppSigningSha1()` and verify SHA-1 + package + OAuth config in Firebase Console.
