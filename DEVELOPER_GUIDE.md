# MVVM Migration Guide for Developers

## Introduction

This guide explains how the AquaRoute System was migrated from a monolithic architecture to MVVM, and provides instructions for developers working with the new codebase.

## Before & After Code Examples

### Before: Direct Firebase Access in Activity

```kotlin
// ❌ OLD - Monolithic Activity
class MainDashboard : AppCompatActivity() {
    private val firestore = FirebaseFirestore.getInstance()
    private val portsCollection = firestore.collection("ports")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPortsFromFirestore()  // Business logic in Activity
    }
    
    private fun loadPortsFromFirestore() {
        portsCollection.get()
            .addOnSuccessListener { querySnapshot ->
                // Parse and display data directly
                for (document in querySnapshot.documents) {
                    val marker = Marker(mapView)
                    // Create marker manually
                    mapView.overlays.add(marker)
                }
            }
    }
}
```

### After: MVVM with Reactive Updates

```kotlin
// ✅ NEW - MVVM View Layer
class MainDashboard : AppCompatActivity() {
    private lateinit var viewModel: MainDashboardViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewModel
        val factory = MainDashboardViewModelFactory(this)
        viewModel = ViewModelProvider(this, factory).get(MainDashboardViewModel::class.java)
        
        // Observe LiveData
        setupViewModelObservers()
        
        // Call ViewModel methods
        viewModel.loadFirestorePorts()
    }
    
    private fun setupViewModelObservers() {
        viewModel.firestorePorts.observe(this) { result ->
            when (result) {
                is Result.Loading -> { /* Show loading */ }
                is Result.Success -> {
                    result.data.forEach { port ->
                        val marker = MapHelper.createFirestorePortMarker(mapView, port) { }
                        mapView.overlays.add(marker)
                    }
                }
                is Result.Error -> { /* Show error */ }
            }
        }
    }
}
```

## Migration Path

### Step 1: Extract Model Classes
```kotlin
// Move data classes to data/models/
data class FirestorePort(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val type: String,
    val status: String,
    val source: String,
    val createdAt: Timestamp
)
```

### Step 2: Create Repository Layer
```kotlin
// Move data access to data/repository/
class PortRepository {
    suspend fun loadPorts(): Result<List<FirestorePort>> {
        return try {
            val querySnapshot = firestore.collection("ports").get().await()
            val ports = querySnapshot.documents.mapNotNull { parseFirestorePort(it) }
            Result.Success(ports)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
```

### Step 3: Create ViewModel
```kotlin
// Move business logic to ui/viewmodel/
class MainDashboardViewModel(
    private val portRepository: PortRepository
) : ViewModel() {
    
    private val _firestorePorts = MutableLiveData<Result<List<FirestorePort>>>()
    val firestorePorts: LiveData<Result<List<FirestorePort>>> = _firestorePorts
    
    fun loadFirestorePorts() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = portRepository.loadPorts()
            _firestorePorts.postValue(result)
        }
    }
}
```

### Step 4: Refactor Activity to View Layer
```kotlin
// Keep only UI code in Activity
class MainDashboard : AppCompatActivity() {
    private lateinit var viewModel: MainDashboardViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this, factory).get(MainDashboardViewModel::class.java)
        
        // Observe and update UI
        setupViewModelObservers()
        viewModel.loadFirestorePorts()
    }
}
```

## Working with the New Architecture

### Adding a New Feature

#### Scenario: Add ferry filtering by status

#### 1. Add Method to ViewModel
```kotlin
// ui/viewmodel/MainDashboardViewModel.kt
private val _filteredFerries = MutableLiveData<List<Ferry>>()
val filteredFerries: LiveData<List<Ferry>> = _filteredFerries

fun filterFerries(status: String) {
    val ferries = _ferries.value ?: emptyList()
    _filteredFerries.value = ferries.filter { it.status == status }
}
```

#### 2. Observe in Activity
```kotlin
// View/MainDashboard.kt
viewModel.filteredFerries.observe(this) { ferries ->
    ferries.forEach { ferry ->
        val marker = MapHelper.createFerryMarker(mapView, ferry) { }
        mapView.overlays.add(marker)
    }
}
```

#### 3. Call from UI
```kotlin
// In event listener
btnFilterFerries.setOnClickListener {
    viewModel.filterFerries("on_time")
}
```

### Modifying Data Access

#### Scenario: Add caching to PortRepository

```kotlin
// data/repository/PortRepository.kt
class PortRepository {
    private var cachedPorts: List<FirestorePort>? = null
    
    suspend fun loadPorts(forceRefresh: Boolean = false): Result<List<FirestorePort>> {
        // Return cached data if available
        if (!forceRefresh && cachedPorts != null) {
            return Result.Success(cachedPorts!!)
        }
        
        return try {
            val ports = fetchFromFirestore()
            cachedPorts = ports
            Result.Success(ports)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private suspend fun fetchFromFirestore(): List<FirestorePort> {
        val querySnapshot = firestore.collection("ports").get().await()
        return querySnapshot.documents.mapNotNull { parseFirestorePort(it) }
    }
}
```

### Error Handling Pattern

```kotlin
// ViewModel handles all errors
fun loadPorts() {
    _firestorePorts.value = Result.Loading
    
    viewModelScope.launch(Dispatchers.IO) {
        val result = portRepository.loadPorts()
        
        if (result is Result.Error) {
            _errorMessage.postValue("Failed to load: ${result.exception.message}")
            Log.e(TAG, "Error loading ports", result.exception)
        }
        
        _firestorePorts.postValue(result)
    }
}
```

### Lifecycle Management

```kotlin
// ViewModel properly manages coroutines
class MainDashboardViewModel : ViewModel() {
    private var liveUpdateJob: Job? = null
    
    fun startLiveUpdates() {
        liveUpdateJob = viewModelScope.launch {
            while (isActive) {
                updateData()
                delay(3000)
            }
        }
    }
    
    fun stopLiveUpdates() {
        liveUpdateJob?.cancel()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopLiveUpdates()  // Cleanup
    }
}

// Activity manages ViewModel lifecycle
class MainDashboard : AppCompatActivity() {
    override fun onPause() {
        super.onPause()
        viewModel.stopLiveUpdates()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopLiveUpdates()
    }
}
```

## Testing Your Changes

### Unit Testing a Repository

```kotlin
@RunWith(MockKRunner::class)
class PortRepositoryTest {
    
    private lateinit var repository: PortRepository
    private val mockFirestore = mockk<FirebaseFirestore>()
    
    @Before
    fun setup() {
        repository = PortRepository()
    }
    
    @Test
    fun testLoadPorts_Success() = runBlocking {
        val ports = listOf(
            FirestorePort("1", "Manila Port", 14.594, 120.970, "port", "open", "Public", Timestamp.now())
        )
        
        val result = repository.loadPorts()
        
        assert(result is Result.Success)
        assertEquals((result as Result.Success).data.size, 1)
    }
}
```

### Unit Testing a ViewModel

```kotlin
@RunWith(MockKRunner::class)
class MainDashboardViewModelTest {
    
    private lateinit var viewModel: MainDashboardViewModel
    private val mockPortRepo = mockk<PortRepository>()
    
    @Before
    fun setup() {
        viewModel = MainDashboardViewModel(mockPortRepo, mockk(), mockk())
    }
    
    @Test
    fun testLoadPorts() = runBlocking {
        coEvery { mockPortRepo.loadPorts() } returns Result.Success(emptyList())
        
        viewModel.loadFirestorePorts()
        
        verify { viewModel.firestorePorts }
    }
}
```

### UI Testing

```kotlin
@RunWith(AndroidJUnit4::class)
class MainDashboardTest {
    
    @get:Rule
    val rule = ActivityScenarioRule(MainDashboard::class.java)
    
    @Test
    fun testMapDisplays() {
        onView(withId(R.id.mapView))
            .check(matches(isDisplayed()))
    }
    
    @Test
    fun testMarkersLoad() {
        // Verify markers appear on map
        Thread.sleep(2000)  // Wait for async load
        onView(withId(R.id.mapView))
            .check(matches(hasDrawable()))
    }
}
```

## Common Patterns

### Pattern 1: Loading Data with Error Handling

```kotlin
private fun loadData() {
    _loadingState.value = true
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val result = repository.getData()
            when (result) {
                is Result.Success -> {
                    _data.postValue(result.data)
                    _errorMessage.postValue(null)
                }
                is Result.Error -> {
                    _errorMessage.postValue(result.exception.message)
                }
                is Result.Loading -> { }
            }
        } catch (e: Exception) {
            _errorMessage.postValue(e.message)
            Log.e(TAG, "Error loading data", e)
        } finally {
            _loadingState.postValue(false)
        }
    }
}
```

### Pattern 2: Real-time Updates

```kotlin
fun startRealtimeUpdates() {
    updateJob = viewModelScope.launch(Dispatchers.Main) {
        while (isActive) {
            updateMarkerPositions()
            updateStatusInfo()
            delay(UPDATE_INTERVAL)
        }
    }
}

private suspend fun updateMarkerPositions() {
    withContext(Dispatchers.Default) {
        ferries.forEach { ferry ->
            ferry.lat += (Math.random() - 0.5) * 0.001
            ferry.lon += (Math.random() - 0.5) * 0.001
        }
    }
    _ferries.postValue(ferries)
}
```

### Pattern 3: Filtering and Search

```kotlin
fun performSearch(query: String) {
    viewModelScope.launch(Dispatchers.Default) {
        val results = mutableListOf<SearchResult>()
        
        // Search in ferries
        ferries.value?.forEach { ferry ->
            if (ferry.name.contains(query, ignoreCase = true)) {
                results.add(SearchResult.Ferry(ferry))
            }
        }
        
        // Search in ports
        ports.value?.forEach { port ->
            if (port.name.contains(query, ignoreCase = true)) {
                results.add(SearchResult.Port(port))
            }
        }
        
        _searchResults.postValue(results)
    }
}
```

## Debugging MVVM Apps

### Enable Logging

```kotlin
// In ViewModel
private fun log(message: String) {
    Log.d(TAG, message)
    _debugLogs.value = (_debugLogs.value ?: emptyList()) + message
}
```

### Monitor LiveData

```kotlin
// In Activity
viewModel.firestorePorts.observe(this) { result ->
    Log.d(TAG, "Ports LiveData updated: $result")
}
```

### Inspect Repository State

```kotlin
// In Repository
suspend fun loadPorts(): Result<List<FirestorePort>> {
    Log.d(TAG, "Loading ports...")
    val result = try {
        val ports = fetchFromFirestore()
        Log.d(TAG, "Loaded ${ports.size} ports")
        Result.Success(ports)
    } catch (e: Exception) {
        Log.e(TAG, "Error loading ports", e)
        Result.Error(e)
    }
    Log.d(TAG, "Result: $result")
    return result
}
```

## Performance Considerations

### Avoiding Memory Leaks

```kotlin
// ✅ CORRECT - ViewModel properly cancels coroutines
override fun onCleared() {
    super.onCleared()
    viewModelScope.launch { }  // Auto-cancelled
}

// ❌ WRONG - Manual Job not cancelled
private val manualJob = Job()
override fun onCleared() {
    super.onCleared()
    // Forgot to cancel manualJob!
}
```

### Efficient LiveData Updates

```kotlin
// ✅ CORRECT - Only post when data changes
fun updateFerry(ferry: Ferry) {
    if (_selectedFerry.value != ferry) {
        _selectedFerry.value = ferry
    }
}

// ❌ WRONG - Unnecessary updates
fun updateFerry(ferry: Ferry) {
    _selectedFerry.value = ferry  // Posts even if same ferry
}
```

### Coroutine Scope

```kotlin
// ✅ CORRECT - Use viewModelScope
viewModelScope.launch {
    // Auto-cancelled when ViewModel cleared
    val data = loadData()
}

// ❌ WRONG - Using GlobalScope
GlobalScope.launch {
    // Continues even after Activity destroyed!
    val data = loadData()
}
```

## Troubleshooting Guide

### Q: LiveData not updating UI
**A:** Ensure you're observing LiveData with `.observe(this)` not `.value`

```kotlin
// ✅ CORRECT
viewModel.data.observe(this) { data ->
    updateUI(data)
}

// ❌ WRONG
val data = viewModel.data.value  // Doesn't observe changes
```

### Q: ViewModel lost on orientation change
**A:** That shouldn't happen with ViewModelProvider, verify you're using it

```kotlin
// ✅ CORRECT
viewModel = ViewModelProvider(this, factory).get(MainDashboardViewModel::class.java)

// ❌ WRONG
viewModel = MainDashboardViewModel()  // Create new instance
```

### Q: Coroutine not running
**A:** Check if Job is already cancelled or isActive

```kotlin
// ✅ CORRECT
if (updateJob?.isActive == false) {
    startUpdates()
}

// ❌ WRONG
viewModelScope.launch { }  // Might use wrong scope
```

### Q: Repository throws NullPointerException
**A:** Check for null safety in data parsing

```kotlin
// ✅ CORRECT
val lat = data["lat"] as? Double ?: return null

// ❌ WRONG
val lat = (data["lat"] as Double)  // Throws NPE if null
```

## Best Practices Summary

1. **Always use ViewModelProvider** with Factory
2. **Observe LiveData** don't read `.value`
3. **Use viewModelScope** for coroutines
4. **Implement onCleared()** for cleanup
5. **Make repositories testable** (can be mocked)
6. **Use Result<T>** for operation outcomes
7. **Log errors** for debugging
8. **Handle null safely** in parsers
9. **Update LiveData** from background threads
10. **Cancel jobs** properly on lifecycle events

## Resources

- [Android Architecture: MVVM Guide](https://developer.android.com/jetpack/guide)
- [LiveData Documentation](https://developer.android.com/topic/libraries/architecture/livedata)
- [ViewModel Documentation](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)

## Next Steps

1. Build and run the app
2. Verify all features work
3. Add unit tests
4. Add UI tests
5. Monitor performance
6. Gather feedback
7. Plan enhancements

Happy coding! 🚀
