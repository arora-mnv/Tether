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

## Tech Stack

- **Package**: `com.anantva.tether`
- **Module**: `:app` (single-module)
- **Min/Target SDK**: 29 / 35
- **Kotlin**: 2.0.21, JVM target 17
- **Room DB version**: 5
- **Firebase BOM**: 33.5.1 (auth, firestore)

## Plugin Order Matters

In `app/build.gradle.kts`, plugins must be declared in this order (comments in file):
1. `android-application` → 2. `kotlin-android` → 3. `kotlin-compose` → 4. `kotlin-ksp` → 5. `hilt-android` → 6. `google-services`

## Architecture

- **UI**: Jetpack Compose, ViewModels with `StateFlow<UiState>`, `combine()` for multi-source flows
- **DI**: Hilt — `DatabaseModule`, `DataStoreModule`, `AuthModule` (all `@InstallIn(SingletonComponent)`)
- **Navigation**: Compose Navigation, single Activity, string routes: `splash` → `onboarding` → `setup` → `auth` → `nameInput` → `dashboard`
- **Data**: Room (local) + Firestore (cloud) — `TetherRepository` routes based on `isCloudStorage` DataStore preference
- **Cloud sync**: Confirmed transactions sync to Firestore; PENDING rows always stay local

## Key Patterns

**Repository Routing**: `TetherRepository` checks `isCloudStorage` → uses Room or Firestore. Both paths expose `Flow<List<TransactionEntity>>`.

**Firestore Structure**:
```
users/{userId}/transactions/{transactionId}
users/{userId}/goals/{goalId}
users/{userId}  (UserProfileEntity as document)
```

**Pending transactions**: SMS-derived PENDING rows are transient — always local, never synced. Only CONFIRMED transactions go to Firestore.

## File Naming Convention

lowercase with camelCase (e.g., `dashboardScreen.kt`, `tetherRepository.kt`) — differs from typical Android convention

## Auth

`FirebaseAuthManager`: Google Sign-In + Phone OTP
`AuthRepository`: Email/password signup/login (delegates to FirebaseAuth)
`AuthViewModel` (`ui_elements/screens/`): Handles auth UI state, profile checks
`UserRepository`: Merges user data from FirebaseAuth + Firestore + DataStore into `StateFlow<UserData>`

## Important Conventions

- **Entity serialization**: All entities have `toMap()` for Firestore writes
- **Firestore parsing**: `TetherRepository` has inline `toTransactionEntity()`, `toGoalEntity()`, `toUserProfileEntity()` on `Map<String, Any?>`
- **Lint**: `ignoreTestSources = true`, `abortOnError = false`, several checks disabled (see `app/build.gradle.kts` lint block)

## Firebase Config

Requires `google-services.json` in `app/`. Google Sign-In needs `default_web_client_id` in `strings.xml`. Use `FirebaseAuthManager.getAppSigningSha1(context)` for SHA-1.

## Testing

- Unit tests: `app/src/test/` (e.g., `TransactionParserTest`)
- Instrumented tests: `app/src/androidTest/`
- No mocking framework beyond JUnit
