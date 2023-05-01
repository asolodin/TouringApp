package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.databinding.ActivityFullscreenBinding
import java.text.SimpleDateFormat
import java.util.*


/**
 *
 */
class FullscreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var fullscreenContent: TextView
    private lateinit var fullscreenContentControls: LinearLayout
    private val hideHandler = Handler(Looper.myLooper()!!)
    private var mapFragment :SupportMapFragment? = null
    private val etaFormat = SimpleDateFormat("hh:mma")
    private val conversion = Conversion(DistanceUnit.MI)

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

    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
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
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync {
            it.moveCamera(CameraUpdateFactory.zoomTo(19f))
            it.isMyLocationEnabled = true
            it.uiSettings.isCompassEnabled = true
        }

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        //binding.dummyButton.setOnTouchListener(delayHideTouchListener)

        val viewModel: LocationViewModel by lazy {
            ViewModelProvider(this).get(LocationViewModel::class.java)
        }

        viewModel.initialize(this.applicationContext)

        val fileName = intent.extras?.getString(Constants.TRIP_FILE_NAME_PROP)
        if (fileName != null) {
            viewModel.loadTripPlan(applicationContext, fileName)
        }

        binding.tripStop.setOnClickListener {
            //TODO: save trip data
            startActivity(Intent(this, MainActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateView(it)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateView(state: LocationState) {

        mapFragment?.getMapAsync {
            it.moveCamera(CameraUpdateFactory.newLatLng(state.position.asLatLng()))
            val route: Polyline = it.addPolyline(PolylineOptions().color(R.color.route_actual))
            route.points = state.routePoints
            if (state.routePlanPoints != null) {
                val route: Polyline = it.addPolyline(PolylineOptions().color(R.color.route_plan))
                route.points = state.routePlanPoints
            }
        }

        binding.speed.text = String.format("%02.1f", conversion.speed(state.position.speed))
        binding.speedAvg.text = String.format("%02.1f", conversion.speed(state.avgSpeed()))
        binding.speedMax.text = String.format("%02.1f", conversion.speed(state.maxSpeed))
        binding.tripDistance.text = String.format("%03.1f", conversion.distance(state.tripDistance))
            .padStart(5, '0')
        binding.tripTotalDistance.text = String.format(
            "%03.1f", conversion.distance(
                state.totalDistance
            )
        )
            .padStart(5, '0')

        binding.tripTime.text = String.format(
            "%02d:%02d",
            conversion.hours(state.tripElapsedTime),
            conversion.minutes(state.tripElapsedTime)
        )
        if (state.tripStartTime > 0 && state.tripDistance > 0) {
            val now = System.currentTimeMillis() / 1000 // to s
            binding.tripStart.text = etaFormat.format(Date(state.tripStartTime * 1000))
                .dropLast(1)
            val remDist = state.totalDistance - state.tripDistance

            if (remDist > 0) {
                if (state.avgSpeed() > 0) {
                    val tripEstTime = (remDist / state.avgSpeed()).toLong()
                    binding.tripEstTime.text = String.format(
                        "%02d:%02d",
                        conversion.hours(tripEstTime),
                        conversion.minutes(tripEstTime))
                }
                val overalSpeed = state.tripDistance / (now - state.tripStartTime)
                val remainTime = (remDist / overalSpeed).toLong()
                val remainHrs = conversion.hours(remainTime)

                if (remainHrs < 24) {
                    val etaTime = System.currentTimeMillis() + remainTime * 1000
                    //Log.d("touringApp", "etaSpeed: " + etaSpeed + ", remDist: " +
                    //        remainingDistance + ", remTime: " + remainingTime)
                    binding.tripEta.text = etaFormat.format(Date(etaTime.toLong())).dropLast(1)
                } else {
                    binding.tripEta.text = "00:00a"
                }
            }
        }
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