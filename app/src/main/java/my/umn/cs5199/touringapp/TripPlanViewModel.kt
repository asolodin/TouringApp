package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.common.util.concurrent.FutureCallback
import com.google.maps.android.PolyUtil
import com.google.maps.routing.v2.ComputeRoutesResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.grpc.RoutesClient
import java.util.*
import java.util.concurrent.Future
import kotlin.streams.toList

data class TripWaypoint(
    val location : LatLng,
    val name : String = "",
    val segment : List<LatLng>  = listOf(),
    val deltaDistance : Double = 0.0,
    val totalDistance : Double = 0.0
) {}

data class TripPlan(
    val name : String = "",
    val wayPoints: List<TripWaypoint> = listOf(),
    val currentPoint : Int = -1,
    val timeStart : Long = 0,
    val timeEnd : Long = 0
) {
    val routePoints = wayPoints.stream().flatMap { it.segment.stream() }.toList()
}

data class TripPlanState(
    val tripPlan: TripPlan = TripPlan(),
    val error : String = ""
) {}

class TripPlanViewModel : ViewModel()  {

    private val _uiState = MutableStateFlow(TripPlanState())
    val uiState: StateFlow<TripPlanState> = _uiState.asStateFlow()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var initialized : Boolean = false
    private val repo = TripRepository()

    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        if (_uiState.value.tripPlan.wayPoints.isEmpty()) {
            setInitialLocation()
        }

        initialized = true
    }

    private fun toWayPoint(r : ComputeRoutesResponse, i : Int, prev : TripWaypoint) : TripWaypoint {
        val route = r.getRoutes(0)
        val leg = route.getLegs(route.legsCount - 1)
        val location = LatLng(leg.endLocation.latLng.latitude, leg.endLocation.latLng.longitude)
        val name = route.description ?: "WP" + i
        val polyList = PolyUtil.decode(route.polyline.encodedPolyline)
        val delta = route.distanceMeters.toDouble()
        val total = prev.totalDistance + delta
        return TripWaypoint(location, name, polyList, delta, total)
    }

    inner class RoutesCallback (index : Int, prev : TripWaypoint) : FutureCallback<ComputeRoutesResponse> {

        val index = index
        val prev = prev

        override fun onSuccess(resp: ComputeRoutesResponse?) {
            _uiState.update { currentState ->
                currentState.copy(
                    tripPlan = currentState.tripPlan.copy(
                        wayPoints = Collections.unmodifiableList(
                            currentState.tripPlan.wayPoints.toMutableList() +
                                    toWayPoint(resp!!, index, prev)
                        ),
                        currentPoint = index
                    )
                )
            }
        }

        override fun onFailure(t: Throwable) {
            //TODO: pass error to UI
            Log.e("TripPlanViewModel.RoutesCallback", t.toString())
        }
    }

    fun addWayPoint(location : LatLng) {
        val wayPoints = _uiState.value.tripPlan.wayPoints
        val i = wayPoints.size
        val callback = RoutesCallback(i, wayPoints.get(i - 1))
        viewModelScope.launch {
            val routes = RoutesClient()
            routes.computeRoute(wayPoints.get(i - 1).location,
                location, callback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setInitialLocation() {
        Log.d("touringApp.setInitialLocation", "setting initial location")

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                //TODO set default location as preference
                val lat = if (location != null) location.latitude else 44.954445
                val long = if (location != null) location.longitude else -93.091301
                _uiState.update { currentState ->
                    currentState.copy(
                        tripPlan = currentState.tripPlan.copy(
                            wayPoints = listOf(TripWaypoint(location = LatLng(lat, long),
                                name="Start")),
                            currentPoint = 0
                        )
                    )
                }
                Log.d("touringApp.setInitialLocation", "Location: " + location)
            }
    }

    public fun saveTripPlan(context : Context) {
        viewModelScope.launch {
            val tripPlan = _uiState.value.tripPlan
            repo.saveToStorage(context, tripPlan)
        }
    }

    public fun saveTripPlan(context : Context, onSaved : (fileName : String) -> Unit) {
        viewModelScope.launch {
            val tripPlan = _uiState.value.tripPlan
            val fileName = repo.saveToStorage(context, tripPlan)
            onSaved.invoke(fileName)
        }
    }

    fun getTripPlan() : TripPlan {
        return _uiState.value.tripPlan
    }
}