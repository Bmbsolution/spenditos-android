# Spenditos Android

Modern expense management Android app built with Jetpack Compose.

## Architecture
- **UI Layer**: Jetpack Compose with Material3
- **Architecture**: MVVM with ViewModel and StateFlow
- **DI**: Hilt for dependency injection
- **Networking**: Retrofit + OkHttp + Kotlin Serialization
- **Local Storage**: Room database + DataStore preferences
- **Async**: Kotlin Coroutines + Flow

## Features (in progress)
- [ ] Transaction management
- [ ] Budget tracking
- [ ] Recurring templates
- [ ] AI receipt scanning (Pro)
- [ ] CSV/Statement import (Pro)
- [ ] Gamification (points, streaks, achievements)
- [ ] RevenueCat in-app purchases
- [ ] Offline support with sync

## Project Structure
```
app/
├── data/           # Data layer (API, DB, preferences)
├── domain/         # Business logic, use cases
├── ui/             # Compose screens and ViewModels
├── di/             # Dependency injection modules
└── utils/          # Extensions and utilities
```

## API Integration
This app connects to the Spenditos backend API:
- Base URL: Configurable via BuildConfig
- Authentication: JWT tokens via Cognito
- Endpoints: See Web repository for full API spec

## Getting Started
1. Clone repository
2. Open in Android Studio (latest stable)
3. Sync Gradle
4. Add `local.properties` with API keys
5. Run on emulator or device

## License
Proprietary - Bmbsolution
