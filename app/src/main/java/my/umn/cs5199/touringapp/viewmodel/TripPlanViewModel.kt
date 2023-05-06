package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.common.util.concurrent.FutureCallback
import com.google.maps.android.PolyUtil
import com.google.maps.routing.v2.ComputeRoutesResponse
import com.weatherapi.api.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.grpc.RoutesClient
import my.umn.cs5199.touringapp.repository.TripRepository
import java.util.*
import kotlin.streams.toList

data class TripWaypoint(
    val location: LatLng,
    val name: String = "",
    val segment: List<LatLng> = listOf(),
    val deltaDistance: Double = 0.0,
    val totalDistance: Double = 0.0
) {}

data class TripPlan(
    val name: String = "",
    val wayPoints: List<TripWaypoint> = listOf(),
    val currentPoint: Int = -1,
    val timeStart: Long = 0,
    val tripElapsedTime: Long = 0,
    val tripDistance: Double = 0.0,
    val timeEnd: Long = 0,
    val rideRoutePoints: List<LatLng> = listOf()
) {
    val planRoutePoints = wayPoints.stream().flatMap { it.segment.stream() }.toList()
}

data class TripPlanState(
    val tripPlan: TripPlan = TripPlan(),
    val dirty: Boolean = true,
    val insertedPoint: Int,
    val refreshAllPoints: Boolean
) {}

class TripPlanViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(TripPlanState(insertedPoint = -1, refreshAllPoints = false))
    val uiState: StateFlow<TripPlanState> = _uiState.asStateFlow()
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private val repo = TripRepository()
    private var tripPlanName: String = "new trip"

    fun initialize(context: Context, tripPlan: TripPlan?) {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }

        if (tripPlan != null) {
            tripPlanName = tripPlan.name
            _uiState.update { currentState ->
                currentState.copy(
                    tripPlan = tripPlan.copy(
                        currentPoint = 0,
                    ),
                    refreshAllPoints = true
                )
            }
        } else {
            setInitialLocation()
        }
    }

    private fun toWayPoint(r: ComputeRoutesResponse, i: Int, prev: TripWaypoint): TripWaypoint {
        val route = r.getRoutes(0)
        val leg = route.getLegs(route.legsCount - 1)
        val location = LatLng(leg.endLocation.latLng.latitude, leg.endLocation.latLng.longitude)
        val name = if (route.description != null && route.description.length > 0) route.description
        else "WP" + i
        val polyList = PolyUtil.decode(route.polyline.encodedPolyline)
        val delta = route.distanceMeters.toDouble()
        val total = prev.totalDistance + delta
        return TripWaypoint(location, name, polyList, delta, total)
    }

    inner class RoutesCallback(
        private val index: Int,
        private val prev: TripWaypoint
    ) : FutureCallback<ComputeRoutesResponse> {

        override fun onSuccess(resp: ComputeRoutesResponse?) {
            _uiState.update { currentState ->
                currentState.copy(
                    tripPlan = currentState.tripPlan.copy(
                        wayPoints = Collections.unmodifiableList(
                            currentState.tripPlan.wayPoints.toMutableList() +
                                    toWayPoint(resp!!, index, prev),
                        ),
                        currentPoint = index,
                        name = tripPlanName
                    ),
                    insertedPoint = index,
                    dirty = true
                )
            }
        }

        override fun onFailure(t: Throwable) {
            //TODO: pass error to UI
            Log.e("TripPlanViewModel.RoutesCallback", t.toString())
        }
    }

    fun addWayPoint(location: LatLng) {
        val wayPoints = _uiState.value.tripPlan.wayPoints
        val i = wayPoints.size
        val callback = RoutesCallback(i, wayPoints.get(i - 1))
        viewModelScope.launch {
            val routes = RoutesClient()
            routes.computeRoute(
                wayPoints.get(i - 1).location,
                location, callback
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun setInitialLocation() {
        Log.d("touringApp.setInitialLocation", "setting initial location")
        tripPlanName = ""
        fusedLocationClient!!.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                //TODO set default location as preference
                val lat = if (location != null) location.latitude else 44.954445
                val long = if (location != null) location.longitude else -93.091301
                val tripPlan = TripPlan()
                _uiState.update { currentState ->
                    currentState.copy(
                        tripPlan = tripPlan.copy(
                            wayPoints = listOf(
                                TripWaypoint(
                                    location = LatLng(lat, long),
                                    name = "Start"
                                )
                            ),
                            currentPoint = 0,
                        ),
                        insertedPoint = 0,
                        refreshAllPoints = true,
                        dirty = true
                    )
                }
                Log.d("touringApp.setInitialLocation", "Location: " + location)
            }
    }

    public fun saveTripPlan(context: Context, onSaved: (fileName: String) -> Unit) {
        if (tripPlanName.isEmpty()) return
        viewModelScope.launch {
            var tripPlan = _uiState.value.tripPlan
            val tripPlanName = tripPlanName
            if (tripPlanName.isNotEmpty()) {
                tripPlan = tripPlan.copy(
                    name = tripPlanName
                )
            }
            val fileName = repo.saveToStorage(context, tripPlan)
            _uiState.update { currentState ->
                currentState.copy(
                    dirty = false,
                    insertedPoint = -1
                )
            }
            onSaved.invoke(fileName)
        }
    }

    fun setTripPlanName(name: String) {
        tripPlanName = name
    }

    fun getWayPoint(i: Int): TripWaypoint {
        return uiState.value.tripPlan.wayPoints.get(i)
    }

    fun getWayPointCount(): Int {
        return uiState.value.tripPlan.wayPoints.size
    }
}