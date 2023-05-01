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
    val speed : Double = loc.speed.toDouble()
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

    /*
        fun speed(other: Position): Double {
            if (other.time > time) {
                return distanceTo(other.loc) * 1000 / (other.time - time)
            } else {
                return 0.0
            }
        }
    */
    /*fun speed(): Double {
        if (loc.hasSpeed()) {
            return loc.speed.toDouble()
        }
        return 0.0
    }*/
}

data class LocationState(
    //all distances in meters, all time in seconds
    val position: Position,
    val routePoints: List<LatLng>,
    val routePlanPoints: List<LatLng>?,
    val tripStartTime: Long,
    val tripElapsedTime: Long,
    val tripDistance: Double,
    val maxSpeed: Double,
    val totalDistance: Double) {

    fun avgSpeed(): Double {
        if (tripElapsedTime > 0) {
            return tripDistance / tripElapsedTime
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
    private var initialized : Boolean = false
    private val repo = TripRepository()
    private var tripPlan : TripPlan? = null

    private val _uiState = MutableStateFlow(
        LocationState(
            Position(Location("default")),
            routePoints,
            null,
            0,
            0,
            0.0,
            0.0,
            10460.0
        )
    )
    val uiState: StateFlow<LocationState> = _uiState.asStateFlow()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        const val MIN_SPEED = 1.35 // ~3 mph
        const val TRIP_TOTAL_DIST = 7.1
    }

    fun initialize(context: Context) {
        if (initialized) return
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                updateLocation(locationResult.lastLocation)
            }
        }
        scheduleUpdateJob()
        startLocationUpdates()
        initialized = true
    }

    fun loadTripPlan(context : Context, tripPlanFileName : String) {
        viewModelScope.launch {
            tripPlan = repo.loadFromStorage(context, tripPlanFileName)
            if (tripPlan != null) {
                val thisTripPlan = tripPlan!!
                _uiState.update { currentState ->
                    currentState.copy(
                        totalDistance = thisTripPlan.wayPoints.last().totalDistance,
                        routePlanPoints = thisTripPlan.routePoints
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
        position = Position(location)
    }

    /*
    private fun updateState() {
        if (location.speed > MIN_SPEED) {
            if (firstPosition == null) {
                //start of trip
                routePoints.clear()
                routePoints.add(newPosition.asLatLng())
                firstPosition = newPosition
            } else {
                //next position
            }
        } else {
            //either paused or not started yet
        }

        //if started

        var tmpPosition = prevPosition
        if (tmpPosition != null) {
            newPosition = Position(
                location,
                tmpPosition.tripTime +
                        System.currentTimeMillis() - tmpPosition.time,
                tmpPosition.tripDistance + tmpPosition.distanceTo(location),
                tmpPosition.maxSpeed
            )
        } else {
            newPosition = Position(location, 0, 0.0, 0.0)
            firstPosition = newPosition
        }

        Log.d("touringApp","new position: " + newPosition)

        val avgSpeed = newPosition.avgSpeed()
        binding.speedAvg.text = String.format("%02.1f", avgSpeed)
        binding.speedMax.text = String.format("%02.1f", newPosition.maxSpeed)
        binding.tripDistance.text = String.format("%03.1f", newPosition.tripDistance)
            .padStart(5, '0')
        binding.tripTotalDistance.text = String.format("%03.1f", Position.TRIP_TOTAL_DIST)
            .padStart(5, '0')

        binding.tripTime.text = String.format(
            "%02d:%02d",
            (newPosition.tripTime / Position.MS_PER_HR).toInt(),
            (newPosition.tripTime / (60 * 1000) % 60).toInt()
        )

        if (avgSpeed > 0) {
            var tripEstTime = (Position.TRIP_TOTAL_DIST / avgSpeed * 60).toInt() //minutes
            if (tripEstTime > 24 * 60) {
                tripEstTime = 23 * 60 + 59
            }
            binding.tripEstTime.text = String.format(
                "%02d:%02d",
                tripEstTime / 60, tripEstTime % 60
            )
        }
        if (prevPosition != null && firstPosition != null) {
            val etaSpeed = newPosition.tripDistance * Position.MS_PER_HR /
                    (newPosition.time - firstPosition!!.time)
            val remainingDistance = Position.TRIP_TOTAL_DIST - newPosition.tripDistance
            val remainingTime = remainingDistance / etaSpeed
            val etaTime = newPosition.time + (remainingTime * Position.MS_PER_HR)
            Log.d("touringApp", "etaSpeed: " + etaSpeed + ", remDist: " +
                    remainingDistance + ", remTime: " + remainingTime)
            binding.tripEta.text = etaFormat.format(Date(etaTime.toLong()))
        }

        //val canvas = binding.altitudeChart.holder.lockCanvas()

        prevPosition = newPosition
    }*/

    private fun scheduleUpdateJob() {
        timer.schedule(StateUpdateTask(), 2000, 1000)
    }

    inner class StateUpdateTask : TimerTask() {

        var prevPosition: Position? = null

        override fun run() {
            val currentPosition = position ?: return
            val prevPosition = prevPosition
            val tripStarted = firstPosition != null

            if (!tripStarted) {
                if (currentPosition.speed >= MIN_SPEED) {
                    firstPosition = position
                    Log.d("touringApp.tripStarted", "trip is auto-started")
                }
            }

            val tripPaused = tripStarted && currentPosition.speed < MIN_SPEED &&
                    prevPosition!!.speed  < MIN_SPEED
            if (tripPaused) {
                return tripPaused()
            }

            if (currentPosition != prevPosition) {
                routePoints.add(currentPosition.asLatLng())


                val tripStartTime = if (tripStarted) firstPosition!!.time else 0
                val timeDelta =
                    if (prevPosition != null) currentPosition.time - prevPosition.time else 0
                val distDelta =
                    if (prevPosition != null) currentPosition.distanceTo(prevPosition) else 0.0
                val maxSpeed = if (prevPosition != null) Math.max(
                    currentPosition.speed,
                    prevPosition.speed
                ) else
                    currentPosition.speed

                _uiState.update { currentState ->
                    currentState.copy(
                        position = currentPosition,
                        routePoints = routePoints,
                        tripStartTime = tripStartTime,
                        tripElapsedTime = currentState.tripElapsedTime + timeDelta,
                        tripDistance = currentState.tripDistance + distDelta,
                        maxSpeed = maxSpeed
                    )
                }
                StateUpdateTask@ this.prevPosition = currentPosition
            }
        }

        private fun tripPaused() {
            _uiState.update { currentState ->
                currentState.copy(
                    position = currentState.position.copy(speed = 0.0)
                )
            }
         }
    }
}
