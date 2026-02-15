# MVVM Architecture Implementation for AquaRoute System

## Overview
This document explains the MVVM (Model-View-ViewModel) architecture implemented in the AquaRoute System application.

## Architecture Layers

### 1. **Model Layer** (`data/models/`)
Contains all data classes representing the application's entities:

- **Ferry.kt**: Represents a ferry with name, route, position, status, and ETA
- **Port.kt**: Represents a local port terminal with coordinates and primary status
- **FirestorePort.kt**: Represents a port loaded from Firebase Firestore with additional metadata
- **MarkerDetail.kt**: Sealed class for type-safe bottom sheet detail handling
- **Result.kt**: Sealed class for handling asynchronous operation results (Success/Error/Loading)

### 2. **Repository Layer** (`data/repository/`)
Abstracts data access and business logic:

- **PortRepository.kt**: Handles Firestore operations for loading ports
  - Parses Firestore documents safely
  - Validates coordinates and data integrity
  - Handles errors gracefully
  
- **FerryRepository.kt**: Manages local ferry data
  - Provides ferry list retrieval
  - Handles position updates for simulation
  
- **SearchRepository.kt**: Manages search history via SharedPreferences
  - Saves/retrieves last search query
  - Clears search history

### 3. **ViewModel Layer** (`ui/viewmodel/`)
Orchestrates business logic and manages UI state:

- **MainDashboardViewModel.kt**: Main ViewModel for the dashboard
  - Manages LiveData for ferries, ports, and Firestore ports
  - Handles live update scheduling using Coroutines
  - Manages marker detail selection
  - Performs search operations
  - Manages UI state (time, live indicator, error messages)

- **MainDashboardViewModelFactory.kt**: Factory for creating ViewModel instances
  - Injects repositories into ViewModel
  - Ensures single instance per activity

### 4. **View Layer** (`View/`)
Displays UI and handles user interactions:

- **MainDashboard.kt**: Activity that observes ViewModel
  - Initializes UI components
  - Observes LiveData from ViewModel
  - Responds to user interactions by calling ViewModel methods
  - No direct Firebase/SharedPreferences access
  - Uses MapHelper for marker creation

- **SplashScreen.kt**: Splash activity (unchanged)

### 5. **Utility Classes** (`util/`)

- **MapHelper.kt**: Creates and manages OSMDroid markers
  - Factory methods for different marker types (Ferry, Port, FirestorePort)
  - Marker customization with icons and colors
  
- **DateFormatter.kt**: Centralized date/time formatting
  - Consistent time format: HH:mm
  - Consistent datetime format: MMM dd, yyyy HH:mm
  
- **LiveUpdateManager.kt**: Manages coroutine-based live updates
  - Replaces Handler-based scheduling
  - Survives configuration changes
  - Proper lifecycle management

## Data Flow

### Loading Firestore Ports:
```
MainDashboard (View)
    ↓ calls
MainDashboardViewModel.loadFirestorePorts()
    ↓ calls
PortRepository.loadPorts()
    ↓
FirebaseFirestore.collection("ports").get()
    ↓ returns
Result.Success<List<FirestorePort>>
    ↓ posts to
firestorePorts LiveData
    ↓ observes
MainDashboard setupViewModelObservers()
    ↓ updates UI
MapView with markers
```

### Live Updates:
```
MainDashboard.onCreate()
    ↓ calls
viewModel.startLiveUpdates()
    ↓ launches coroutine
Loop every 3 seconds:
  - updateFerryPositions() (simulates movement)
  - updateStatusOverlay() (updates time, blink indicator)
    ↓ updates
Ferries LiveData
    ↓ observes
MainDashboard setupViewModelObservers()
    ↓ updates UI
MapView marker positions
```

### Search Operation:
```
User enters search query
    ↓
etSearch.setOnEditorActionListener()
    ↓ calls
viewModel.performSearch(query)
    ↓ searches in
Ferries, Ports, FirestorePorts
    ↓ updates
searchResultsCount and selectedMarkerDetail LiveData
    ↓ observes
MainDashboard setupViewModelObservers()
    ↓ updates UI
Bottom sheet with details and map center
```

## Key Features

### 1. **Separation of Concerns**
- View layer only handles UI and user input
- ViewModel manages state and coordinates operations
- Repository handles data access
- Models are simple data containers

### 2. **Reactive Updates with LiveData**
- All UI updates flow through LiveData observers
- UI automatically updates when data changes
- No manual state management in Activity

### 3. **Error Handling**
- Result sealed class provides consistent error handling
- Repositories catch and log exceptions
- ViewModel posts error messages to LiveData
- UI displays user-friendly error messages

### 4. **Lifecycle Management**
- ViewModel survives configuration changes (orientation, etc.)
- Coroutine jobs are cancelled in onCleared()
- Live updates stop when Activity stops
- Proper resource cleanup

### 5. **Type Safety**
- Sealed classes (MarkerDetail, Result) for type-safe operations
- No null safety issues with marker details
- Compile-time guarantees for data handling

## Dependencies Added

```gradle
// MVVM Architecture
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

## Testing Guide

### Unit Testing
```kotlin
// Test ViewModel without Android dependencies
class MainDashboardViewModelTest {
    @Test
    fun testPerformSearch() {
        // Mock repositories
        val portRepo = mockk<PortRepository>()
        val ferryRepo = mockk<FerryRepository>()
        val searchRepo = mockk<SearchRepository>()
        
        val viewModel = MainDashboardViewModel(portRepo, ferryRepo, searchRepo)
        viewModel.performSearch("Manila")
        
        // Verify LiveData updates
        Assert.assertEquals(viewModel.searchResultsCount.value, 1)
    }
}
```

### Integration Testing
```kotlin
// Test Activity with real ViewModel
@RunWith(AndroidJUnit4::class)
class MainDashboardTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainDashboard::class.java)
    
    @Test
    fun testFirestoreMarkersLoad() {
        // Verify markers appear on map
    }
}
```

## Migration Notes

### What Changed:
✅ All business logic moved to ViewModel
✅ Direct Firestore calls replaced with Repository pattern
✅ Handler-based scheduling replaced with Coroutines
✅ SharedPreferences access moved to SearchRepository
✅ Activity is now view-only layer

### What Stayed the Same:
✅ All UI layouts and designs
✅ Map implementation (OSMDroid)
✅ Firestore database queries
✅ Ferry and port data
✅ Search functionality
✅ Live update mechanism

## Best Practices Applied

1. **Single Responsibility Principle**: Each class has one reason to change
2. **Dependency Injection**: Repositories injected via Factory
3. **Observable Pattern**: LiveData for reactive UI updates
4. **Coroutine Management**: Proper job cancellation and scope handling
5. **Resource Management**: Cleanup in onCleared() and lifecycle methods
6. **Error Handling**: Graceful error messages to users
7. **Testability**: Repositories can be mocked for testing

## Future Improvements

1. **Dependency Injection Framework**: Add Hilt for automated DI
2. **Caching**: Implement Room database for offline support
3. **Pagination**: Load Firestore ports with pagination
4. **Real-time Updates**: Add Firestore listeners for real-time sync
5. **Unit Tests**: Comprehensive test suite for ViewModel and Repositories
6. **UI Tests**: Espresso tests for Activity interactions

## Troubleshooting

### Q: MarkerDetail is null in ViewModel
**A**: Make sure to call `viewModel.onMarkerClick()` from marker click listeners

### Q: Live updates not stopping
**A**: Call `viewModel.stopLiveUpdates()` in onPause() and onDestroy()

### Q: Search not working
**A**: Verify SearchRepository is initialized in ViewModelFactory

### Q: Firestore ports not loading
**A**: Check Firestore rules and network connectivity, error messages in Toast

## File Structure
```
app/src/main/java/com/example/aquaroute_system/
├── data/
│   ├── models/
│   │   ├── Ferry.kt
│   │   ├── Port.kt
│   │   ├── FirestorePort.kt
│   │   ├── MarkerDetail.kt
│   │   └── Result.kt
│   └── repository/
│       ├── PortRepository.kt
│       ├── FerryRepository.kt
│       └── SearchRepository.kt
├── ui/
│   ├── viewmodel/
│   │   ├── MainDashboardViewModel.kt
│   │   └── MainDashboardViewModelFactory.kt
│   └── (compose folder removed - using XML layouts)
├── util/
│   ├── MapHelper.kt
│   ├── DateFormatter.kt
│   └── LiveUpdateManager.kt
└── View/
    ├── MainDashboard.kt
    └── SplashScreen.kt
```

## Summary
This MVVM implementation provides a clean, maintainable, testable, and scalable architecture for the AquaRoute System while preserving all existing functionality. The separation of concerns makes the codebase easier to understand, modify, and test.
