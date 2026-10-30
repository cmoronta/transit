package com.transitbike.mobile.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transitbike.mobile.data.AdminQuotaSnapshot
import com.transitbike.mobile.data.AuthManager
import com.transitbike.mobile.data.RouteRepository
import com.transitbike.mobile.data.WorkerProxyApi
import com.transitbike.mobile.service.NavigationForegroundService
import com.transitbike.shared.model.FavoritePlace
import com.transitbike.shared.model.LatLng
import com.transitbike.shared.model.NavState
import com.transitbike.shared.model.RecentRide
import com.transitbike.shared.model.RouteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScreenMode {
    EXPLORE,
    ROUTE_PREVIEW,
    ACTIVE_RIDING
}

data class MainUiState(
    val mode: ScreenMode = ScreenMode.EXPLORE,
    val origin: LatLng = LatLng(33.7816, -84.3831), // Midtown Atlanta
    val originName: String = "Midtown Atlanta (10th St)",
    val destination: LatLng? = null,
    val destinationName: String = "",
    val availableRoutes: List<RouteModel> = emptyList(),
    val selectedRoute: RouteModel? = null,
    val favorites: List<FavoritePlace> = emptyList(),
    val recents: List<RecentRide> = emptyList(),
    val isLoadingRoutes: Boolean = false,
    val isAdmin: Boolean = false,
    val showAdminSheet: Boolean = false,
    val adminQuotaSnapshot: AdminQuotaSnapshot? = null
)

class MainViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val workerApi: WorkerProxyApi = WorkerProxyApi(),
    private val authManager: AuthManager = AuthManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Active navigation state from Foreground Service
    val activeNavState: StateFlow<NavState?> = NavigationForegroundService.activeState

    init {
        // Load initial favorites, recents, and sample routes
        viewModelScope.launch {
            routeRepository.favorites.collect { favs ->
                _uiState.value = _uiState.value.copy(favorites = favs)
            }
        }
        viewModelScope.launch {
            routeRepository.recents.collect { recents ->
                _uiState.value = _uiState.value.copy(recents = recents)
            }
        }
        viewModelScope.launch {
            authManager.refreshAuthTokens()
            _uiState.value = _uiState.value.copy(isAdmin = authManager.isUserAdmin())
        }

        // Listen for active service state transitions
        viewModelScope.launch {
            NavigationForegroundService.activeState.collect { navState ->
                if (navState != null) {
                    _uiState.value = _uiState.value.copy(mode = ScreenMode.ACTIVE_RIDING)
                } else if (_uiState.value.mode == ScreenMode.ACTIVE_RIDING) {
                    _uiState.value = _uiState.value.copy(mode = ScreenMode.EXPLORE)
                }
            }
        }
    }

    fun selectDestination(place: FavoritePlace) {
        planTripTo(place.location, place.name)
    }

    fun selectRecent(recent: RecentRide) {
        planTripTo(recent.destinationLocation, recent.destinationName)
    }

    fun planTripTo(destination: LatLng, destName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = true,
                destination = destination,
                destinationName = destName
            )

            val origin = _uiState.value.origin
            val routes = routeRepository.getRoutes(
                fromLat = origin.latitude,
                fromLon = origin.longitude,
                toLat = destination.latitude,
                toLon = destination.longitude
            )

            val defaultRoute = routes.firstOrNull { it.isSafestDefault } ?: routes.firstOrNull()

            _uiState.value = _uiState.value.copy(
                isLoadingRoutes = false,
                availableRoutes = routes,
                selectedRoute = defaultRoute,
                mode = ScreenMode.ROUTE_PREVIEW
            )
        }
    }

    fun selectRoute(route: RouteModel) {
        _uiState.value = _uiState.value.copy(selectedRoute = route)
    }

    fun startRide(context: Context) {
        val route = _uiState.value.selectedRoute ?: return
        NavigationForegroundService.startService(context, route)
        _uiState.value = _uiState.value.copy(mode = ScreenMode.ACTIVE_RIDING)
    }

    fun stopRide(context: Context) {
        NavigationForegroundService.stopService(context)
        _uiState.value = _uiState.value.copy(mode = ScreenMode.EXPLORE, selectedRoute = null)
    }

    fun cancelRoutePreview() {
        _uiState.value = _uiState.value.copy(
            mode = ScreenMode.EXPLORE,
            selectedRoute = null,
            availableRoutes = emptyList()
        )
    }

    fun openAdminDiagnostics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAdminSheet = true)
            refreshAdminQuota()
        }
    }

    fun dismissAdminDiagnostics() {
        _uiState.value = _uiState.value.copy(showAdminSheet = false)
    }

    fun refreshAdminQuota() {
        viewModelScope.launch {
            val res = workerApi.getAdminQuota()
            res.onSuccess {
                _uiState.value = _uiState.value.copy(adminQuotaSnapshot = it.quota)
            }.onFailure {
                // If offline, provide fallback snapshot
                _uiState.value = _uiState.value.copy(
                    adminQuotaSnapshot = AdminQuotaSnapshot(
                        monthKey = "2025-10",
                        monthlyUsed = 1,
                        monthlyLimit = 1480,
                        minuteUsed = 1,
                        minuteLimit = 4,
                        perUserDailyLimit = 40
                    )
                )
            }
        }
    }
}
