package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

enum class DistanceUnit(val factor: Double) {
    MI(1_609.344), KM(1000.0)
}

data class Position(
    val loc: Location
) {
    val time = loc.time

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
    fun speed(): Double {
        if (loc.hasSpeed()) {
            return loc.speed.toDouble()
        }
        return 0.0
    }
}

data class LocationState(
    val position: Position,
    val routePoints: List<LatLng>,
    val tripStartTime: Long,
    val tripElapsedTime: Long,
    val tripDistance: Double,
    val prevMaxSpeed: Double,
    val totalDistance: Double,
    val etaTime: Long
) {
    val maxSpeed = if (conversion.speed(position.speed()) > prevMaxSpeed)
        conversion.speed(position.speed()) else prevMaxSpeed

    fun avgSpeed(): Double {
        if (tripElapsedTime > 0) {
            return tripDistance / tripElapsedTime
        }
        return 0.0
    }

    companion object {
        val conversion = Conversion(DistanceUnit.MI)
    }
}

class Conversion(val unit: DistanceUnit) {

    fun speed(metersPerSecond: Double): Double {
        return when (unit) {
            DistanceUnit.MI -> metersPerSecond * MPS_TO_MIPH
            DistanceUnit.KM -> metersPerSecond * MPS_TO_KPH
            //else -> throw IllegalArgumentException("unsupported")
        }
    }

    fun distance(meters: Double): Double {
        return meters / unit.factor
    }

    fun hours(timeMs: Long): Long {
        return timeMs / MS_PER_HR
    }

    fun minutes(timeMs: Long): Long {
        return timeMs / (60 * 1000) % 60
    }

    companion object {
        const val M_PER_MI = 1_609.344
        const val MS_PER_HR = 1000 * 60 * 60
        const val MPS_TO_MIPH = 2.2369362921
        const val MPS_TO_KPH = 3.6
    }
}

class LocationViewModel : ViewModel() {

    private var firstPosition: Position? = null
    var prevPosition: Position? = null
    var position: Position? = null
    private val routePoints = mutableListOf<LatLng>()
    private val timer = Timer()

    private val _uiState = MutableStateFlow(
        LocationState(
            Position(Location("default")),
            routePoints,
            0,
            0,
            0.0,
            0.0,
            10460.0,
            0
        )
    )
    val uiState: StateFlow<LocationState> = _uiState.asStateFlow()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        const val MIN_SPEED = 3.0
        const val TRIP_TOTAL_DIST = 7.1
    }

    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                updateLocation(locationResult.lastLocation)
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
        prevPosition = position
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

    private fun scheduleUpdateJob(context: Context) {
        timer.schedule(StateUpdateTask(), 0, 100)
    }

    inner class StateUpdateTask : TimerTask() {

        override fun run() {
            val newPosition = position ?: return
            val prevPosition = prevPosition

            val tripPaused = newPosition.speed() < MIN_SPEED
            val tripStarted = firstPosition != null

            if (!tripPaused) {
                if (!tripStarted) {
                    firstPosition = newPosition
                }
                routePoints.add(newPosition.asLatLng())
            }

            val time = System.currentTimeMillis()
            val tripStartTime = if (tripStarted) firstPosition!!.time else 0
            val tripTimeDelta = if (prevPosition != null) newPosition.time - prevPosition.time else 0

            _uiState.update { currentState ->
                currentState.copy(
                    position = newPosition,
                    routePoints = routePoints,
                    tripStartTime = tripStartTime,
                    tripElapsedTime = currentState.tripElapsedTime + tripTimeDelta
                )
            }
        }
    }
}
