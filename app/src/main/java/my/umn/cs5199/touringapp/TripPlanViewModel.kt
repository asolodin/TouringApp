package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

data class TripWaypoint(
    val location : LatLng,
    val name : String = "",
    val deltaDistance : Double = 0.0,
    val totalDistance : Double = 0.0
) {}

data class TripPlan(
    val name : String = "",
    val wayPoints: List<TripWaypoint> = listOf(),
    val currentPoint : Int = -1,
    val timeStart : Long = 0,
    val timeEnd : Long = 0
) {}

data class TripPlanState(
    val tripPlan: TripPlan = TripPlan()
) {}

class TripPlanViewModel : ViewModel()  {

    private val _uiState = MutableStateFlow(TripPlanState())
    val uiState: StateFlow<TripPlanState> = _uiState.asStateFlow()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var initialized : Boolean = false

    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        if (_uiState.value.tripPlan.wayPoints.isEmpty()) {
            setInitialLocation()
        }

        initialized = true
    }

    fun addWayPoint(location : LatLng) {
        val i = _uiState.value.tripPlan.wayPoints.size
        _uiState.update { currentState ->
            currentState.copy(
                tripPlan = currentState.tripPlan.copy(
                    wayPoints = Collections.unmodifiableList(
                        currentState.tripPlan.wayPoints.toMutableList() +
                            TripWaypoint(location = location,
                                name="WP" + i
                            )
                    ),
                    currentPoint = i
                )
            )
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
}