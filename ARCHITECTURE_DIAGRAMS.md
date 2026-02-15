# MVVM Architecture Diagram

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        USER INTERFACE (View)                 │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                    MainDashboard.kt                   │ │
│  │                    (AppCompatActivity)                │ │
│  │                                                        │ │
│  │  - MapView, Buttons, TextViews, BottomSheet          │ │
│  │  - User interactions (clicks, searches)              │ │
│  │  - Observes LiveData from ViewModel                  │ │
│  │  - Updates UI based on ViewModel state               │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────┬──────────────────────────────────────┘
                       │ (Uses ViewModelProvider)
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    VIEWMODEL LAYER                          │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         MainDashboardViewModel.kt                    │ │
│  │              (ViewModel)                              │ │
│  │                                                        │ │
│  │  LiveData:                                            │ │
│  │  - ferries: List<Ferry>                             │ │
│  │  - ports: List<Port>                                │ │
│  │  - firestorePorts: Result<List<FirestorePort>>      │ │
│  │  - selectedMarkerDetail: MarkerDetail?              │ │
│  │  - lastUpdateTime: String                           │ │
│  │  - liveIndicatorAlpha: Float                        │ │
│  │                                                        │ │
│  │  Methods:                                             │ │
│  │  - loadFirestorePorts()                             │ │
│  │  - startLiveUpdates()                               │ │
│  │  - performSearch(query)                             │ │
│  │  - onMarkerClick()                                  │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────┬──────────────────────────────────────┘
                       │ (Uses Repositories)
                       │
         ┌─────────────┼─────────────┐
         │             │             │
┌────────▼──┐  ┌──────▼────┐  ┌────▼──────┐
│REPOSITORY │  │REPOSITORY │  │REPOSITORY │
│    LAYER  │  │   LAYER   │  │   LAYER   │
│           │  │           │  │           │
│┌────────┐ │  │┌────────┐ │  │┌────────┐ │
││ Port   │ │  ││ Ferry  │ │  ││ Search │ │
││Repo.kt │ │  ││Repo.kt │ │  ││Repo.kt │ │
│└────────┘ │  │└────────┘ │  │└────────┘ │
└────┬──────┘  └──────┬─────┘  └────┬──────┘
     │                │             │
     │                │             │
┌────▼──────────────┬─▼──────────┬──▼──────────┐
│   FIRESTORE       │  LOCAL     │ SHARED      │
│   DATABASE        │  MEMORY    │ PREFERENCES │
│                   │            │             │
│ - ports coll.     │ - Ferry    │ - Search    │
│ - real-time data  │   list     │   history   │
└────────────────────┴────────────┴─────────────┘
```

## Data Flow: Loading Firestore Ports

```
User opens app
        │
        ▼
MainDashboard.onCreate()
        │
        ├─► Initialize ViewModel
        │
        ├─► setupViewModelObservers()
        │        │
        │        └─► Observe firestorePorts LiveData
        │
        └─► viewModel.loadFirestorePorts()
                 │
                 ▼
        MainDashboardViewModel
                 │
                 ├─► _firestorePorts.value = Result.Loading
                 │
                 ├─► viewModelScope.launch(Dispatchers.IO)
                 │
                 └─► PortRepository.loadPorts()
                      │
                      ├─► firestore.collection("ports").get()
                      │
                      ├─► Parse documents safely
                      │
                      └─► Return Result.Success<List<FirestorePort>>
                           │
                           ▼
                  _firestorePorts.postValue(result)
                           │
                           ▼
                  setupViewModelObservers() receives update
                           │
                           ├─► Clear old Firestore markers
                           │
                           ├─► For each port:
                           │   └─► MapHelper.createFirestorePortMarker()
                           │
                           ├─► Add marker to mapView
                           │
                           └─► mapView.invalidate()
                                   │
                                   ▼
                          Markers displayed on map ✓
```

## Data Flow: Live Updates

```
viewModel.startLiveUpdates() called
        │
        ▼
Launch coroutine in viewModelScope
        │
        ├─► Loop every 3 seconds:
        │
        ├─► updateFerryPositions()
        │    │
        │    ├─► For each ferry:
        │    │   └─► Simulate movement (random offset)
        │    │
        │    └─► _ferries.value = updated list
        │
        ├─► updateStatusOverlay()
        │    │
        │    ├─► _lastUpdateTime.value = current time
        │    │
        │    └─► _liveIndicatorAlpha.value = toggle (blink)
        │
        └─► delay(3000L)
            │
            └─► Go back to loop (if isActive)
                    │
                    ▼
        setupViewModelObservers() receives updates
                    │
                    ├─► For each ferry marker:
                    │   └─► Update position with MapHelper
                    │
                    ├─► Update tvLastUpdate.text
                    │
                    ├─► Update tvLiveStatus.alpha
                    │
                    └─► mapView.invalidate()
                        │
                        ▼
            UI updated with live data ✓
```

## Data Flow: Search Operation

```
User enters search text & presses search
        │
        ▼
etSearch.setOnEditorActionListener()
        │
        ├─► Extract query
        │
        └─► viewModel.performSearch(query)
                 │
                 ▼
        MainDashboardViewModel
                 │
                 ├─► searchRepository.saveSearchQuery(query)
                 │
                 ├─► Search in ferries (_ferries.value)
                 │
                 ├─► Search in ports (_ports.value)
                 │
                 ├─► Search in Firestore ports
                 │   (firestorePorts LiveData)
                 │
                 ├─► Count total results
                 │
                 ├─► _searchResultsCount.value = count
                 │
                 ├─► Navigate to first result:
                 │   └─► onMarkerClick(firstResult)
                 │        │
                 │        └─► _selectedMarkerDetail.value = detail
                 │
                 └─► Emit: showBottomSheetForMarkerDetail(detail)
                        │
                        ▼
        setupViewModelObservers() receives updates
                        │
                        ├─► Show bottom sheet with details
                        │
                        └─► Center map on marker location
                            │
                            ▼
            User sees search result ✓
```

## Component Interaction Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           MainDashboard Activity                   │   │
│  │                                                     │   │
│  │  ┌──────────────────────────────────────────────┐  │   │
│  │  │         UI Components                        │  │   │
│  │  │  - MapView                                   │  │   │
│  │  │  - Buttons (Menu, Layers, Compass, etc)    │  │   │
│  │  │  - TextViews (Status, Time, Details)       │  │   │
│  │  │  - BottomSheet (Marker Details)            │  │   │
│  │  │  - SearchView                               │  │   │
│  │  └──────────────────────────────────────────────┘  │   │
│  │              ▲                    │                 │   │
│  │              │ observes           │ calls           │   │
│  │              │                    │                 │   │
│  │  ┌──────────┴────────────────────▼──────────────┐  │   │
│  │  │      MainDashboardViewModel                 │  │   │
│  │  │                                              │  │   │
│  │  │  LiveData<Result<Ports>>                    │  │   │
│  │  │  LiveData<List<Ferries>>                    │  │   │
│  │  │  LiveData<MarkerDetail?>                    │  │   │
│  │  │  LiveData<String> (time, search, etc)       │  │   │
│  │  │                                              │  │   │
│  │  │  + startLiveUpdates()                        │  │   │
│  │  │  + loadFirestorePorts()                      │  │   │
│  │  │  + performSearch(query)                      │  │   │
│  │  │  + onMarkerClick()                           │  │   │
│  │  └──────────────┬───────────────────────────────┘  │   │
│  └────────────────┼──────────────────────────────────┘   │
│                   │ uses Repositories                    │
│                   │                                      │
└───────────────────┼──────────────────────────────────────┘
                    │
     ┌──────────────┼──────────────┐
     │              │              │
┌────▼──────┐ ┌────▼──────┐ ┌────▼──────┐
│PortRepo   │ │FerryRepo  │ │SearchRepo │
│           │ │           │ │           │
│+ loadPorts│ │+ getFerry │ │+ saveQuery│
│+ parse()  │ │+ update() │ │+ getQuery │
│+ validate │ │           │ │           │
└────┬──────┘ └─────────────┘ └────┬──────┘
     │                             │
┌────▼─────────────────────────────▼──────┐
│  External Data Sources                  │
│  - Firestore Cloud Database             │
│  - SharedPreferences                    │
│  - Local Memory (Ferry list)            │
└─────────────────────────────────────────┘
```

## Lifecycle Diagram

```
App Start
    │
    ▼
SplashScreen (3 seconds)
    │
    ▼
MainDashboard.onCreate()
    │
    ├─► Create ViewModel via Factory
    │
    ├─► Initialize Views
    │
    ├─► Setup Map
    │
    ├─► setupViewModelObservers()
    │    └─► All LiveData observers registered
    │
    ├─► viewModel.startLiveUpdates()
    │    └─► Coroutine loop starts (3-second interval)
    │
    └─► viewModel.loadFirestorePorts()
         └─► Async load from Firestore
         
App Running (Active)
    │
    ├─► User interactions
    │   ├─► Click marker → ViewModel → Update selectedMarkerDetail
    │   ├─► Search → ViewModel → Update searchResultsCount
    │   └─► Buttons → Direct map operations
    │
    └─► Live updates running
        └─► Every 3 seconds: ferry positions, time, indicator
        
User navigates away
    │
    ▼
onPause()
    │
    ├─► mapView.onPause()
    │
    └─► viewModel.stopLiveUpdates()
        └─► Coroutine job cancelled
        
User returns
    │
    ▼
onResume()
    │
    ├─► mapView.onResume()
    │
    └─► (ViewModel persists due to lifecycle awareness)
    
App Destroyed
    │
    ▼
onDestroy()
    │
    ├─► viewModel.onCleared()
    │
    └─► All coroutine jobs cancelled
        All observers cleared
```

## Class Dependency Graph

```
MainDashboard (Activity)
    └─► depends on ─► MainDashboardViewModel
                        └─► depends on ─┬─► PortRepository
                                       ├─► FerryRepository
                                       └─► SearchRepository
                                           └─► Android Context

PortRepository
    └─► depends on ─► FirebaseFirestore

FerryRepository
    └─► depends on ─► List<Ferry>

SearchRepository
    └─► depends on ─► SharedPreferences

MapHelper
    └─► depends on ─► MapView
                      Ferry, Port, FirestorePort

DateFormatter
    └─► depends on ─► SimpleDateFormat

Result<T>
    └─► used by ─► PortRepository
                   MainDashboardViewModel

MarkerDetail
    └─► used by ─► MainDashboardViewModel
                   MainDashboard
```

This architecture ensures:
- ✅ Clear separation of concerns
- ✅ Unidirectional data flow
- ✅ Testability (dependencies can be mocked)
- ✅ Reusability (ViewModel not tied to Activity lifecycle)
- ✅ Maintainability (easy to modify or extend)
