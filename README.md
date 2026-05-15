# Tether

A behavior-aware finance companion for Android. Tether automatically tracks spending by parsing banking SMS notifications, helping you understand your financial habits without manual data entry.

## Features

- **Automatic transaction detection** — parses Indian banking SMS from UPI apps, credit cards, and bank accounts
- **Spending insights** — daily breakdowns, weekly trends, and behavioral analysis
- **Streak system** — gamified discipline tracking that rewards consistent financial behavior
- **Cloud sync** — optional Firebase backup for cross-device access
- **Privacy-first** — all processing happens on-device; cloud sync is opt-in

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Local DB | Room (SQLite) |
| Preferences | DataStore |
| Auth | Firebase Authentication |
| Cloud | Cloud Firestore |
| Build | Gradle 9 + Kotlin 2.0 |

## Setup

### Prerequisites

- Android Studio Ladybug (2024.2+) or equivalent with JDK 17
- Java 17+ (`JAVA_HOME` must point to JDK 17)

### Firebase Configuration

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Register your Android app with package name `com.anantva.tether`
3. Download `google-services.json` and place it at `app/google-services.json`
4. Enable **Authentication** → **Google Sign-In**
5. Enable **Cloud Firestore** in production mode

### SHA-1 Fingerprint

Google Sign-In requires SHA-1. Get yours:

```bash
./gradlew signingReport
```

Look for the `SHA1` value under the `debug` variant. Add this SHA-1 to:
- Firebase Console → Project Settings → Your apps → SHA certificate fingerprints

### Build

```bash
# Debug
./gradlew assembleDebug

# Release (minified + obfuscated)
./gradlew assembleRelease

# Install on device
./gradlew installDebug

# Run tests
./gradlew test
```

## Architecture

```
TetherApplication
├── SyncOrchestrator        — cloud sync coordination
├── AppForegroundTracker    — lifecycle tracking
└── MainActivity
    ├── Onboarding → Setup → Dashboard
    ├── Auth (Google Sign-In)
    └── Navigation (Compose)

Data Layer
├── TransactionDataSource (interface)
│   ├── LocalTransactionDataSource (Room)
│   └── CloudTransactionDataSource (Firestore)
└── TetherRepository — delegates to data source router
```

## Screenshots

<!-- Add screenshots here -->

## License

Private — all rights reserved.
