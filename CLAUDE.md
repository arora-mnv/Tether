# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the app
./gradlew assembleDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Install on connected device
./gradlew installDebug

# Clean build
./gradlew clean
```

## Architecture Overview

Tether is a budget tracking Android app that automatically parses banking SMS notifications to track spending. The app supports both local-only storage and cloud sync via Firebase.

### Core Architecture

- **UI Layer**: Jetpack Compose screens with ViewModels using StateFlow
- **Data Layer**: Repository pattern with dual storage (Room local + Firestore cloud)
- **DI**: Hilt for dependency injection
- **Navigation**: Compose Navigation with a single-activity architecture
- **Preferences**: DataStore for user settings and onboarding state

### Key Flows

**App Start Flow** (MainActivity):
1. Splash screen (minimum 3 seconds)
2. Check onboarding/setup completion from DataStore
3. Navigate to: onboarding → setup → auth (if cloud enabled) → dashboard

**Transaction Flow**:
1. `TetherNotificationListener` receives banking SMS notifications
2. `TransactionParser` extracts amount, merchant, and type from SMS text
3. Pending transactions stored locally with status="PENDING"
4. User reviews via `TransactionConfirmationSheet`
5. Confirmed transactions sync to cloud if enabled

**Cloud Sync Pattern**:
- `TetherRepository` methods check `isCloudStorage` preference
- If cloud enabled + authenticated: use Firestore
- Otherwise: use Room
- Pending transactions always stay local (transient state)

### Important Files

- `MainActivity.kt` - Navigation and app start state management
- `TetherRepository.kt` - Central data access with cloud/local routing
- `TransactionParser.kt` - SMS parsing logic for Indian banking formats
- `TetherNotificationListener.kt` - Notification listener service
- `FirebaseAuthManager.kt` - Google Sign-In and phone auth
- `AppDatabase.kt` - Room database with migration support

### Data Models

- `TransactionEntity` - Transactions with status (PENDING/CONFIRMED)
- `GoalEntity` - Savings goals with active flag
- `UserProfileEntity` - User profile with streak and balances
- `AppStartState` - Onboarding/setup completion state

### Key Patterns

**Repository Routing**: All data access goes through `TetherRepository` which routes between local (Room) and cloud (Firestore) based on `isCloudStorage` preference.

**StateFlow UI**: ViewModels expose `StateFlow<UiState>` that combines multiple data sources using `combine()`.

**Notification Parsing**: Banking SMS notifications are parsed using regex patterns in `TransactionParser` - supports major Indian banks and UPI apps.

**Streak Logic**: Daily streak is updated once per day based on whether daily spending stayed within the calculated limit.

### Firebase Configuration

Google Sign-In requires:
1. `google-services.json` in `app/`
2. `default_web_client_id` in `strings.xml` (replace placeholder)
3. SHA-1 fingerprint added to Firebase Console

Use `FirebaseAuthManager.getAppSigningSha1()` to get the SHA-1 for debugging auth issues.
