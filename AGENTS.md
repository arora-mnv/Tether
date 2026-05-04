# AGENTS.md — Tether

## Build Commands
```bash
./gradlew assembleDebug        # Build debug APK
./gradlew test                 # Run unit tests (JUnit)
./gradlew connectedAndroidTest # Run instrumented tests (requires device)
./gradlew installDebug         # Install on connected device
./gradlew clean                # Clean build artifacts
```
JDK path (in `gradle.properties`): `/Applications/Android Studio.app/Contents/jbr/Contents/Home`

## Plugin Order (Critical)
In `app/build.gradle.kts`, plugins must be in this order:
1. `android-application` → 2. `kotlin-android` → 3. `kotlin-compose` → 4. `kotlin-ksp` → 5. `hilt-android` → 6. `google-services`

## Architecture
- UI: Jetpack Compose, ViewModels with `StateFlow<UiState>`, `combine()` for multi-source flows
- DI: Hilt (`DatabaseModule`, `DataStoreModule`, `AuthModule` in `SingletonComponent`)
- Navigation: Compose, single Activity, routes: `splash` → `onboarding` → `setup` → `auth` → `nameInput` → `dashboard`
- Data: Room (local) + Firestore (cloud), `TetherRepository` routes via `isCloudStorage` DataStore pref
- Cloud sync: Only CONFIRMED transactions sync to Firestore; PENDING rows stay local forever

## Key Patterns
- **Repository Routing**: `TetherRepository` checks `isCloudStorage` → Room or Firestore, both expose `Flow<List<TransactionEntity>>`
- **Firestore Structure**:
  ```
  users/{userId}/transactions/{transactionId}
  users/{userId}/goals/{goalId}
  users/{userId}  (UserProfileEntity as document)
  ```
- **Pending Transactions**: SMS-derived PENDING rows are local-only, never synced

## File Naming Convention
lowercase camelCase (e.g., `dashboardScreen.kt`, `tetherRepository.kt`) — non-standard Android convention

## Auth
- `FirebaseAuthManager`: Google Sign-In + Phone OTP
- `AuthRepository`: Email/password (delegates to FirebaseAuth)
- `AuthViewModel` (`ui_elements/screens/`): Auth UI state, profile checks
- `UserRepository`: Merges FirebaseAuth + Firestore + DataStore into `StateFlow<UserData>`

## Important Conventions
- All entities have `toMap()` for Firestore writes
- `TetherRepository` has inline `toTransactionEntity()`, `toGoalEntity()`, `toUserProfileEntity()` parsers for `Map<String, Any?>`
- Lint: `ignoreTestSources = true`, `abortOnError = false`; several checks disabled (see `app/build.gradle.kts`)

## Firebase Config
- Requires `google-services.json` in `app/`
- Google Sign-In needs `default_web_client_id` in `strings.xml`
- Use `FirebaseAuthManager.getAppSigningSha1(context)` for SHA-1

## Testing
- Unit tests: `app/src/test/` (e.g., `TransactionParserTest`)
- Instrumented tests: `app/src/androidTest/`
- No mocking framework beyond JUnit

## Firestore Safety Rules
- No `!!` (unsafe null assertions) in data/Firestore code
- Wrap all `.get().await()`, `.set().await()` in try-catch for `FirebaseFirestoreException` (offline, permission denied, API disabled)
- Snapshot listeners: Check `snapshot?.exists() == true` before access; log errors with `Log.e(TAG, "message", error)`; never crash app
- Firestore calls must not crash the app; return empty data or error state to UI

## UI Rules
- Show user-friendly error messages (Toast/Snackbar) for backend failures
- UI must not crash if data fetching fails; show fallback/empty state
