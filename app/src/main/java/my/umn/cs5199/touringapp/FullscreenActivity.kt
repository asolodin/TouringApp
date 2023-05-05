package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.toSpanned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.weatherapi.api.models.Hour
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.Constants.DIR_CODE2
import my.umn.cs5199.touringapp.Constants.FUN_DIR_TO_ARROW_INDEX
import my.umn.cs5199.touringapp.Constants.FUN_RELATIVE_WIND_ARROW_INDEX
import my.umn.cs5199.touringapp.Constants.FUN_WIND_SPEED_TO_COLOR
import my.umn.cs5199.touringapp.Constants.SPEED_TO_COLOR_CODE
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
    private var mapFragment: SupportMapFragment? = null
    private val etaFormat = SimpleDateFormat("hh:mma")
    private val conversion = Conversion(DistanceUnit.MI)
    private lateinit var viewModel: LocationViewModel

    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        isFullscreen = true

        fullscreenContentControls = binding.fullscreenContentControls
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync {
            it.moveCamera(CameraUpdateFactory.zoomTo(19f))
            it.isMyLocationEnabled = true
            it.uiSettings.isZoomControlsEnabled = true
            it.uiSettings.isCompassEnabled = true
        }

        viewModel = ViewModelProvider(this).get(LocationViewModel::class.java)

        viewModel.initialize(this.applicationContext)

        val fileName = intent.extras?.getString(Constants.TRIP_FILE_NAME_PROP)
        if (fileName != null) {
            viewModel.loadTripPlan(applicationContext, fileName)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateView(it)
                }
            }
        }
    }

    override fun onStop() {
        Log.d("touringApp.FullScreen.onStop", "activity stopped")
        viewModel.saveTripPlan(this)
        viewModel.stopUpdates()
        super.onStop()
    }

    @SuppressLint("MissingPermission")
    private fun updateView(state: LocationState) {

        Log.d("touringApp.FullScreen.updateView", "Location is " + state.position)

        mapFragment?.getMapAsync {
            it.moveCamera(CameraUpdateFactory.newLatLng(state.position.asLatLng()))
            val route: Polyline = it.addPolyline(
                PolylineOptions().color(
                    ContextCompat.getColor(applicationContext, R.color.route_actual)
                )
            )
            route.points = state.routePoints
            if (state.routePlanPoints != null) {
                val routePlan: Polyline = it.addPolyline(
                    PolylineOptions().color(
                        ContextCompat.getColor(applicationContext, R.color.route_plan)
                    )
                )
                routePlan.points = state.routePlanPoints
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

        if (state.paused) {
            val anim: Animation = AlphaAnimation(0.0f, 1.0f)
            anim.duration = 500
            anim.startOffset = 20
            anim.repeatMode = Animation.REVERSE
            anim.repeatCount = Animation.INFINITE
            binding.speed.startAnimation(anim)
        } else {
            binding.speed.clearAnimation()
        }

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
                        conversion.minutes(tripEstTime)
                    )
                }
                val overallSpeed = state.tripDistance / (now - state.tripStartTime)
                val remainTime = (remDist / overallSpeed).toLong()
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
        updateCurrentWeather(state)
        updateForcastWeather(state)
    }

    private fun updateCurrentWeather(state: LocationState) {
        val cur = state.weatherCur ?: return
        val bearing = if (state.position.loc.hasBearing()) state.position.loc.bearing else null

        binding.temp.text = cur.tempF.toInt().toString()
        binding.feels.text = cur.feelslikeF.toInt().toString()
        val winDirIndex = FUN_DIR_TO_ARROW_INDEX(cur.windDegree)
        binding.windDir.text = Constants.ARROWS[winDirIndex].toString()
        val wspd = Math.min(cur.windMph, cur.gustMph).toInt()
        val wgust = Math.max(cur.windMph, cur.gustMph).toInt()
        binding.windSpeed.text = wspd.toString()
        binding.windGust.text = wgust.toString()

        // only show apparent wind dir when moving
        if (!state.paused && bearing != null) {
            val bearingDirIndex = FUN_DIR_TO_ARROW_INDEX(bearing.toInt())
            binding.windApparentDir.text = Constants.ARROWS[
                    FUN_RELATIVE_WIND_ARROW_INDEX(winDirIndex, bearingDirIndex)].toString()
            binding.windApparentDir.setTextColor(FUN_WIND_SPEED_TO_COLOR(wspd))
        }
    }

    private fun createTimeScale(state: LocationState): String {
        var timeScale = StringBuilder()
        var time = Calendar.getInstance()
        val lastUpdated = if (state.weatherCur?.lastUpdated != null)
            SimpleDateFormat("yyyy-MM-dd HH:mm").parse(state.weatherCur.lastUpdated!!) else
                Date()
        time.time = lastUpdated
        time.add(Calendar.MINUTE, 15)
        for (i in 0..25) {
            val minutes = time.get(Calendar.MINUTE) / 15 * 15 % 60
            if (minutes == 0) {
                val hour = time.get(Calendar.HOUR)
                timeScale.append(" ").append(if (hour == 0) "12" else hour.toString())
            } else {
                timeScale.append(" .")
            }
            time.add(Calendar.MINUTE, 15)
        }
        return timeScale.toString()
    }

    abstract class TimeSeries<T>(
        val interval: Int,
        val max: Int,
        var last: T,
        val lastTime: Calendar
    ) {
        private var count = 1
        private val series = SpannableStringBuilder()

        abstract fun convert(v: T): CharSequence

        init {
            series.append(convert(last))
        }

        fun add(t: Calendar, v: T) {
            var lastMinutes = lastTime.get(Calendar.MINUTE) / interval * interval % 60
            var lastHour = lastTime.get(Calendar.HOUR_OF_DAY)
            val addMinutes = t.get(Calendar.MINUTE) / interval * interval % 60
            val addHour = t.get(Calendar.HOUR_OF_DAY)
            while (lastHour < addHour ||
                (lastHour == addHour && lastMinutes < addMinutes)
            ) {
                series.append(convert(last))
                lastTime.add(Calendar.MINUTE, interval)
                lastMinutes = lastTime.get(Calendar.MINUTE) / interval * interval % 60
                lastHour = lastTime.get(Calendar.HOUR_OF_DAY)
            }
            series.append(convert(v))
            last = v
            count += 1
        }

        fun end(t: Calendar) {
            while (count < max) {
                add(t, last)
                count += 1
            }
        }

        fun toSpanned(): Spanned {
            return series.toSpanned()
        }

        override fun toString(): String {
            val str = series.toString()
            if (str.length > max) {
                return str.substring(0, max)
            }
            return str
        }
    }

    class ConditionTimeSeries(v: Int, t: Calendar) : TimeSeries<Int>(15, 24, v, t) {

        override fun convert(v: Int): String {
            return when (v) {
                1000 -> "☀"
                1003 -> "⛅"
                1006, 1009 -> "☁"
                // light rain
                1030, 1063, 1050, 1153, 1080, 1183, 1240 -> "\uD83C\uDF26"
                //thunder
                1273, 1276, 1279, 1282 -> "\uD83C\uDF29"
                // heavy rain
                else -> "\uD83C\uDF27"
            }
        }
    }

    class WindTimeSeries(v: Pair<Int,Int>, t: Calendar) : TimeSeries<Pair<Int,Int>>(15, 24, v, t) {

        @Suppress("DEPRECATION")
        override fun convert(v: Pair<Int,Int>): CharSequence {
            val index = FUN_DIR_TO_ARROW_INDEX(v.first)
            val char = DIR_CODE2[index]
            var color = FUN_WIND_SPEED_TO_COLOR(v.second) and 0x00ffffff.toInt()
            val value = Html.fromHtml("<font color='#${Integer.toHexString(color)}'>${char}</font>")
            return value
        }
    }

    class TempTimeSeries(v: Int, t: Calendar) : TimeSeries<Int>(12, 24, v, t) {

        @Suppress("DEPRECATION")
        override fun convert(v: Int): CharSequence {
            var color: Int
            if (v > Constants.MAX_TEMP) {
                color = SPEED_TO_COLOR_CODE.last().toInt()
            } else if (v < Constants.MIN_TEMP) {
                color = SPEED_TO_COLOR_CODE[0].toInt()
            } else {
                val adjutedTemp = v - Constants.MIN_TEMP
                val tempToRage = (adjutedTemp / Constants.TEMP_RANGE).toInt()
                color = SPEED_TO_COLOR_CODE[
                        Math.min(
                            tempToRage,
                            SPEED_TO_COLOR_CODE.size - 1
                        )].toInt() and 0x00ffffff.toInt()
            }
            val value = Html.fromHtml("<font color='#${Integer.toHexString(color)}'>■</font>")
            return value
        }
    }

    private fun makeConditionsTimeSeries(state: LocationState): ConditionTimeSeries {
        val startTime = Calendar.getInstance()
        startTime.time = Date(state.weatherCur!!.lastUpdatedEpoch * 1000L)
        return ConditionTimeSeries(state.weatherCur.condition.code, startTime)
    }

    private fun makeWindTimeSeries(state: LocationState): WindTimeSeries {
        val startTime = Calendar.getInstance()
        startTime.time = Date(state.weatherCur!!.lastUpdatedEpoch * 1000L)
        return WindTimeSeries(Pair(state.weatherCur.windDegree,
            state.weatherCur.windMph.toInt()), startTime)
    }

    private fun makeTempTimeSeries(state: LocationState): TempTimeSeries {
        val startTime = Calendar.getInstance()
        startTime.time = Date(state.weatherCur!!.lastUpdatedEpoch * 1000L)
        return TempTimeSeries(state.weatherCur.tempF.toInt(), startTime)
    }

    private fun makeFeelsTimeSeries(state: LocationState): TempTimeSeries {
        val startTime = Calendar.getInstance()
        startTime.time = Date(state.weatherCur!!.lastUpdatedEpoch * 1000L)
        return TempTimeSeries(state.weatherCur.feelslikeF.toInt(), startTime)
    }

    private fun updateForcastWeather(state: LocationState) {
        val current = state.weatherCur ?: return
        val forcast = state.weatherFor ?: return
        binding.includeWeatherLayout.wfTime.text = createTimeScale(state)
        val cond = makeConditionsTimeSeries(state)
        val wind = makeWindTimeSeries(state)
        val temp = makeTempTimeSeries(state)
        val feels = makeFeelsTimeSeries(state)

        val startTime = current.lastUpdatedEpoch
        val hours = forcast.forecastday[0].hour.iterator()
        var hour: Hour? = null
        while (hours.hasNext()) {
            hour = hours.next()
            if (hour.timeEpoch > startTime) {
                break
            }
        }

        val time = Calendar.getInstance()
        if (hour != null) {
            for (i in 0..5) {
                time.time = Date(hour!!.timeEpoch * 1000L)
                cond.add(time, hour.condition.code)
                wind.add(time, Pair(hour.windDegree, hour.windMph.toInt()))
                temp.add(time, hour.tempF.toInt())
                feels.add(time, hour.feelslikeF.toInt())
                if (hours.hasNext()) {
                    hour = hours.next()
                } else {
                    break
                }
            }
        }
        binding.includeWeatherLayout.wfCond.text = cond.toString()
        binding.includeWeatherLayout.wfWind.text = wind.toSpanned()
        binding.includeWeatherLayout.wfTemp.text = temp.toSpanned()
        binding.includeWeatherLayout.wfFeels.text = feels.toSpanned()
    }


    /**
     * Everything below was automatically added by Android Studio when creating a full-screen
     * activity. I am not sure what any of it is doing but I am too scared to mess with
     * because I don't want to break anything.
     */

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