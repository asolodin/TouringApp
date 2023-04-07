package my.umn.cs5199.touringapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import my.umn.cs5199.touringapp.databinding.ActivityFullscreenBinding
import java.text.SimpleDateFormat
import java.util.*


data class Position(
    val loc: Location, val tripTime: Long, val tripDistance: Double,
    val prevMaxSpeed: Double
) {
    val time = System.currentTimeMillis()
    val maxSpeed = if (loc.speed > prevMaxSpeed) loc.speed * MPS_TO_MIPH else prevMaxSpeed

    companion object {
        //const val R = 3_950.0 //radius of earth in mi
        //const val DEG_TO_RAD = Math.PI / 180
        const val M_PER_MI = 1_609.344
        const val MS_PER_HR = 1000.0 * 60 * 60
        const val TRIP_TOTAL_DIST = 76.5
        const val MPS_TO_MIPH = 2.2369362921
        const val MIN_SPEED = 3.0
    }

    fun avgSpeed(): Double {
        if (tripTime > 0) {
            return tripDistance * MS_PER_HR / tripTime
        }
        return 0.0
    }

    fun distanceTo(otherLoc: Location): Double {
        return loc.distanceTo(otherLoc) / M_PER_MI
    }

    /*
fun distanceFrom(otherLoc: Location): Double {
    return R * DEG_TO_RAD *
            Math.sqrt(
                Math.pow(
                    Math.cos(otherLoc.latitude * DEG_TO_RAD) *
                            (otherLoc.longitude - loc.longitude), 2.0
                )
                        + Math.pow(otherLoc.latitude - loc.latitude, 2.0)
            )
}
*/
    fun speed(other: Position): Double {
        if (other.time > loc.time) {
            return distanceTo(other.loc) * MS_PER_HR / (other.time - loc.time)
        } else {
            return 0.0
        }
    }
}

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class FullscreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var fullscreenContent: TextView
    private lateinit var fullscreenContentControls: LinearLayout
    private val hideHandler = Handler(Looper.myLooper()!!)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val permissions = arrayOf(
        //Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        //Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    )

    private var prevPosition: Position? = null
    private val routePoints = mutableListOf<LatLng>()
    val timeFormat = SimpleDateFormat("hh:mm")
    val etaFormat = SimpleDateFormat("hh:mm aa")

    private lateinit var locationCallback: LocationCallback

    private fun askPermissions(multiplePermissionLauncher: ActivityResultLauncher<Array<String>>) {
        if (!hasPermissions(permissions)) {
            Log.d(
                "PERMISSIONS",
                "Launching multiple contract permission launcher for ALL required permissions"
            )
            multiplePermissionLauncher.launch(permissions)
        } else {
            Log.d("PERMISSIONS", "All permissions are already granted")
        }
    }

    private fun hasPermissions(permissions: Array<String>?): Boolean {
        if (permissions != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("PERMISSIONS", "Permission is not granted: $permission")
                    return false
                }
                Log.d("PERMISSIONS", "Permission already granted: $permission")
            }
            return true
        }
        return false
    }

    @SuppressLint("InlinedApi")
    private val hidePart2Runnable = Runnable {
        // Delayed removal of status and navigation bar
        //fullscreenContent.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    }
    private val showPart2Runnable = Runnable {
        // Delayed display of UI elements
        supportActionBar?.show()
        fullscreenContentControls.visibility = View.VISIBLE
    }
    private var isFullscreen: Boolean = false

    private val hideRunnable = Runnable { hide() }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private val delayHideTouchListener = View.OnTouchListener { view, motionEvent ->
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS)
            }
            MotionEvent.ACTION_UP -> view.performClick()
            else -> {
            }
        }
        false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        isFullscreen = true

        // Set up the user interaction to manually show or hide the system UI.
        //fullscreenContent = binding.fullscreenContent
        //fullscreenContent.setOnClickListener { toggle() }

        fullscreenContentControls = binding.fullscreenContentControls

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        //binding.dummyButton.setOnTouchListener(delayHideTouchListener)

        //val webview = binding.web
        //webview.setWebViewClient(WebViewClient())
        //webview.getSettings().setJavaScriptEnabled(true)
        //webview.loadUrl("https://maps.google.com/maps?" + "saddr=43.0054446,-87.9678884" + "&daddr=42.9257104,-88.0508355")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val multiplePermissionsContract = ActivityResultContracts.RequestMultiplePermissions()
        val multiplePermissionLauncher =
            registerForActivityResult(multiplePermissionsContract) { isGranted ->
                Log.d("PERMISSIONS", "Launcher result: $isGranted")
                if (isGranted.containsValue(false)) {
                    Log.d(
                        "PERMISSIONS",
                        "At least one of the permissions was not granted, launching again..."
                    )
                }
            }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermissions(multiplePermissionLauncher)
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                val mapFragment =
                    supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
                mapFragment?.getMapAsync {
                    it.moveCamera(CameraUpdateFactory.zoomTo(20f))
                    it.isMyLocationEnabled = true
                    it.uiSettings.isCompassEnabled = true
                    updateView(location)
                }
            }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                updateView(locationResult.lastLocation)
            }
        }
        startLocationUpdates()
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

    private fun updateView(location: Location?) {
        if (location == null) {
            Log.w("touringApp", "no location")
            return
        }
        binding.tripTotalDistance.text = String.format("%03.1f", Position.TRIP_TOTAL_DIST)
        Log.d("touringApp", "location is: " + location)
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync {
            val latLng = LatLng(location.latitude, location.longitude)
            //it.addMarker(MarkerOptions().position(latLng))
            it.moveCamera(CameraUpdateFactory.newLatLng(latLng))
            it.uiSettings.isCompassEnabled = true
            val mapPoint = LatLng(location.latitude, location.longitude)
            routePoints.add(mapPoint)
            val route: Polyline = it.addPolyline(PolylineOptions())
            route.points = routePoints
        }

        var speed = 0.0
        if (location.hasSpeed()) {
            speed = location.speed * Position.MPS_TO_MIPH
            Log.d("touringApp", "got speed: " + speed)
        } else if (prevPosition != null) {
            val tmp = Position(location, 0, 0.0, 0.0)
            speed = (prevPosition!!.speed(tmp) * 100).toInt().toDouble() / 100
            Log.d("touringApp", "calculated speed: " + speed)
        }

        binding.speed.text = String.format("%02.1f", speed)

        if (speed < Position.MIN_SPEED) {
            return
        }

        var newPosition: Position
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
        }
        val avgSpeed = newPosition.avgSpeed()
        binding.speedAvg.text = String.format("%02.1f", avgSpeed)
        binding.speedMax.text = String.format("%02.1f", newPosition.maxSpeed)
        binding.tripDistance.text = String.format("%03.1f", newPosition.tripDistance)
            .padStart(4, '0')

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
        val etaTime = System.currentTimeMillis() + (Position.TRIP_TOTAL_DIST -
                newPosition.tripDistance) / speed * Position.MS_PER_HR
        binding.tripEta.text = etaFormat.format(Date(etaTime.toLong()))

        val canvas = binding.altitudeChart.holder.lockCanvas()

        prevPosition = newPosition
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100)
    }

    private fun toggle() {
        if (isFullscreen) {
            hide()
        } else {
            show()
        }
    }

    private fun hide() {
        // Hide UI first
        supportActionBar?.hide()
        fullscreenContentControls.visibility = View.GONE
        isFullscreen = false

        // Schedule a runnable to remove the status and navigation bar after a delay
        hideHandler.removeCallbacks(showPart2Runnable)
        hideHandler.postDelayed(hidePart2Runnable, UI_ANIMATION_DELAY.toLong())
    }

    private fun show() {
        // Show the system bar
        fullscreenContent.windowInsetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        isFullscreen = true

        // Schedule a runnable to display UI elements after a delay
        hideHandler.removeCallbacks(hidePart2Runnable)
        hideHandler.postDelayed(showPart2Runnable, UI_ANIMATION_DELAY.toLong())
    }

    /**
     * Schedules a call to hide() in [delayMillis], canceling any
     * previously scheduled calls.
     */
    private fun delayedHide(delayMillis: Int) {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, delayMillis.toLong())
    }

    companion object {
        /**
         * Whether or not the system UI should be auto-hidden after
         * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
         */
        private const val AUTO_HIDE = true

        /**
         * If [AUTO_HIDE] is set, the number of milliseconds to wait after
         * user interaction before hiding the system UI.
         */
        private const val AUTO_HIDE_DELAY_MILLIS = 3000

        /**
         * Some older devices needs a small delay between UI widget updates
         * and a change of the status and navigation bar.
         */
        private const val UI_ANIMATION_DELAY = 300

    }
}