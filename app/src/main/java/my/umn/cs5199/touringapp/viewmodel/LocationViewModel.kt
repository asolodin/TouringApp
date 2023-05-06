package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.weatherapi.api.WeatherAPIClient
import com.weatherapi.api.http.client.APICallBack
import com.weatherapi.api.http.client.HttpContext
import com.weatherapi.api.models.Current
import com.weatherapi.api.models.Forecast1
import com.weatherapi.api.models.ForecastJsonResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.repository.TripRepository
import java.util.*

enum class DistanceUnit(val factor: Double) {
    MI(1_609.344), KM(1000.0)
}

data class Position(
    val loc: Location,
    val speed: Double = loc.speed.toDouble()
) {
    val time = loc.time / 1000

    fun asLatLng(): LatLng {
        return LatLng(loc.latitude, loc.longitude)
    }

    fun distanceTo(otherLoc: Location): Double {
        return loc.distanceTo(otherLoc).toDouble()
    }

    fun distanceTo(otherPos: Position): Double {
        return distanceTo(otherPos.loc)
    }

    fun secondsSince(): Long {
        return System.currentTimeMillis() - loc.time
    }
}

data class LocationState(
    //all distances in meters, all time in seconds
    val position: Position,
    val rideRoutePoints: List<LatLng>,
    val routePlanPoints: List<LatLng>?,
    val tripStartTime: Long,
    val rideElapsedTime: Long,
    val rideDistance: Double,
    val maxSpeed: Double,
    val totalDistance: Double,
    val paused: Boolean,
    val tripPlan: TripPlan? = null,
    val weatherCur: Current? = null,
    val weatherFor: Forecast1? = null
) {

    fun avgSpeed(): Double {
        Log.d(
            "touringApp.avgSpeed",
            " rideDistance ${rideDistance} / rideElapsedTime ${rideElapsedTime}"
        )
        if (rideElapsedTime > 0) {
            return rideDistance / rideElapsedTime
        }
        return 0.0
    }
}

class Conversion(val unit: DistanceUnit) {

    fun speed(metersPerSecond: Double): Double {
        return when (unit) {
            DistanceUnit.MI -> metersPerSecond * MPS_TO_MIPH
            DistanceUnit.KM -> metersPerSecond * MPS_TO_KMPH
            //else -> throw IllegalArgumentException("unsupported")
        }
    }

    fun distance(meters: Double): Double {
        return meters / unit.factor
    }

    fun hours(seconds: Long): Long {
        return seconds / S_PER_HR
    }

    fun minutes(seconds: Long): Long {
        return seconds / 60 % 60
    }

    companion object {
        const val S_PER_HR = 60 * 60
        const val MPS_TO_MIPH = 2.2369363
        const val MPS_TO_KMPH = 3.6
    }
}

class LocationViewModel : ViewModel() {

    private var firstPosition: Position? = null
    var position: Position? = null
    private val routePoints = mutableListOf<LatLng>()
    private val timer = Timer()
    private val repo = TripRepository()
    private var tripPlan: TripPlan? = null
    private val weatherClient = WeatherAPIClient()

    private val _uiState = MutableStateFlow(
        LocationState(
            Position(Location("default")),
            routePoints,
            null,
            0,
            0,
            0.0,
            0.0,
            0.0,
            true
        )
    )
    val uiState: StateFlow<LocationState> = _uiState.asStateFlow()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                updateLocation(locationResult.lastLocation)
            }
        }
        scheduleUpdateJob()
        startLocationUpdates()
    }

    fun saveTripPlan(context: Context) {
        with(uiState.value) {
            if (tripPlan != null && tripStartTime != null) {
                viewModelScope.launch {
                    val tripPlan = tripPlan.copy(
                        timeStart = tripStartTime,
                        tripElapsedTime = rideElapsedTime,
                        tripDistance = rideDistance,
                        rideRoutePoints = rideRoutePoints
                    )
                    repo.saveToStorage(context, tripPlan)
                }
            }
        }
    }

    fun stopUpdates() {
        timer.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun loadTripPlan(context: Context, tripPlanFileName: String) {
        viewModelScope.launch {
            tripPlan = repo.loadFromStorage(context, tripPlanFileName)
            if (tripPlan != null) {
                val thisTripPlan = tripPlan!!
                _uiState.update { currentState ->
                    currentState.copy(
                        tripPlan = thisTripPlan,
                        totalDistance = thisTripPlan.wayPoints.last().totalDistance,
                        rideDistance = thisTripPlan.tripDistance,
                        rideElapsedTime = thisTripPlan.tripElapsedTime,
                        routePlanPoints = thisTripPlan.planRoutePoints,
                        rideRoutePoints = thisTripPlan.rideRoutePoints
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        var locationRequest = LocationRequest.Builder(1000)
            .setMinUpdateDistanceMeters(10f).setGranularity(Granularity.GRANULARITY_FINE)
            .build()
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun updateLocation(location: Location?) {
        Log.d("touringApp.updateLocation", "location: " + location)
        if (location == null) {
            return
        }
        val initWeather: Boolean = (position == null)
        position = Position(location)
        if (initWeather) {
            getWeather(position!!.asLatLng())
        }
    }

    private fun scheduleUpdateJob() {
        timer.schedule(LocationUpdateTask(), 5000, 1000)
        timer.schedule(WeatherUpdateTask(), 5000, 10 * 60 * 1000)
    }

    fun updatePositionState(prevPosition: Position?): Position? {
        val currentPosition = position ?: return null
        val tripStarted = firstPosition != null

        if (prevPosition == null) {
            setInitialLocation(currentPosition)
            return currentPosition
        }

        if (!tripStarted) {
            if (prevPosition.speed >= Constants.MIN_AUTO_START_SPEED &&
                currentPosition.speed >= Constants.MIN_AUTO_START_SPEED
            ) {
                firstPosition = position
                Log.d("touringApp.updatePositionState", "trip is auto-started")
            }
        }

        // two consecutive updates with speed below threshold triggers auto-pause mode
        val tripPaused = !tripStarted || (currentPosition.speed < Constants.MIN_AUTO_START_SPEED &&
                prevPosition.speed < Constants.MIN_AUTO_START_SPEED) ||
                (currentPosition.secondsSince() > Constants.MIN_AUTO_PAUSE_TIME)

        if (tripPaused) {
            Log.d("touringApp.updatePositionState", "trip is auto-paused")
            // routePoints.clear()
        } else {
            routePoints.add(currentPosition.asLatLng())
        }

        val tripStartTime = if (tripStarted) firstPosition!!.time else 0
        val timeDelta = if (tripPaused) 0 else currentPosition.time - prevPosition.time
        val distDelta = if (tripPaused) 0.0 else currentPosition.distanceTo(prevPosition)
        val maxSpeed = Math.max(currentPosition.speed, uiState.value.maxSpeed)

        _uiState.update { currentState ->
            currentState.copy(
                position = currentPosition,
                rideRoutePoints = routePoints,
                tripStartTime = tripStartTime,
                rideElapsedTime = currentState.rideElapsedTime + timeDelta,
                rideDistance = currentState.rideDistance + distDelta,
                maxSpeed = maxSpeed,
                paused = tripPaused
            )
        }
        return currentPosition
    }

    private fun setInitialLocation(currentPosition: Position) {
        _uiState.update { currentState ->
            currentState.copy(
                position = currentPosition,
            )
        }
    }

    inner class LocationUpdateTask : TimerTask() {

        var prevPosition: Position? = null

        override fun run() {
            prevPosition = updatePositionState(prevPosition)
        }
    }

    inner class WeatherUpdateTask : TimerTask() {

        override fun run() {
            val location = position?.asLatLng()
            if (location != null) {
                getWeather(location)
            }
        }
    }

    private fun getWeather(location: LatLng) {
        Log.d("touringApp.getWeather", "getting weather...")
        viewModelScope.launch {
            weatherClient.getAPIs().getForecastWeatherAsync(
                location.latitude.toString() + "," + location.longitude.toString(),
                1,
                null,
                null,
                null,
                null,
                object : APICallBack<ForecastJsonResponse?> {
                    override fun onSuccess(context: HttpContext, response: ForecastJsonResponse?) {
                        _uiState.update { currentState ->
                            currentState.copy(
                                weatherCur = response?.current,
                                weatherFor = response?.forecast
                            )
                        }
                    }

                    override fun onFailure(context: HttpContext, error: Throwable) {
                        Log.e("touringApp.getWeatherForecast", error.toString())
                    }
                })
        }
    }
}
