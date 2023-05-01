package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.databinding.FragmentSecondBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private var mapFragment: SupportMapFragment? = null
    private val viewModel: TripPlanViewModel by activityViewModels()
    private val markers: MutableMap<String, Marker> = mutableMapOf()
    private lateinit var tripWayPointList: CustomAdapter
    private val conversion = Conversion(DistanceUnit.MI)

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateUiState(it)
                }
            }
        }

        viewModel.initialize(requireContext())
        val recyclerView: RecyclerView = binding.tripWaypointList
        tripWayPointList = CustomAdapter(requireContext(), viewModel)
        recyclerView.adapter = tripWayPointList

        /*binding.tripPlanSave.setOnClickListener {
            viewModel.saveTripPlan(requireContext())
            findNavController().navigate(R.id.action_TripPlanning_to_TripSelection)
        }*/

        binding.tripPlanRide.setOnClickListener {
            viewModel.saveTripPlan(requireContext()) {
                val intent = Intent(activity, FullscreenActivity::class.java)
                intent.putExtra(Constants.TRIP_FILE_NAME_PROP, it)
                startActivity(intent)
            }
            //findNavController().navigate(R.id.action_TripSelection_to_RideDashboard)
        }

        binding.tripPlanName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                viewModel.setTripPlanName(s.toString())
                Log.d("touringApp.afterTextChanged", "changed text to " + s.toString())
            }
        })

        return binding.root
    }

    private fun updateUiState(state: TripPlanState) {
        if (state.tripPlan.currentPoint < 0) {
            return
        }
        if (state.tripPlan.wayPoints.size > 1) {
            binding.tripPlanRide.visibility = VISIBLE
        }
        mapFragment?.getMapAsync { gm ->
            val i = state.tripPlan.currentPoint
            val wayPoint = state.tripPlan.wayPoints.get(i)
            gm.moveCamera(CameraUpdateFactory.newLatLng(wayPoint.location))
            markers.computeIfAbsent(wayPoint.name) {
                val m = gm.addMarker(
                    MarkerOptions().position(wayPoint.location).title(wayPoint.name)
                )!!
                m.showInfoWindow()
                tripWayPointList.notifyItemInserted(i)
                m
            }
            val route: Polyline = gm.addPolyline(PolylineOptions().color(R.color.route_plan))
            route.points = wayPoint.segment
        }

    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapFragment = childFragmentManager.findFragmentById(R.id.map_plan) as SupportMapFragment?
        mapFragment?.getMapAsync {
            it.moveCamera(CameraUpdateFactory.zoomTo(15f))
            it.isMyLocationEnabled = true
            it.uiSettings.isCompassEnabled = true
            it.uiSettings.isMapToolbarEnabled = true
            it.uiSettings.isZoomControlsEnabled = true
            it.setOnMapLongClickListener {
                viewModel.addWayPoint(it)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveTripPlan(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class CustomAdapter(
        private val context: Context,
        private val viewModel: TripPlanViewModel
    ) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView
            val dist: TextView
            val total: TextView

            init {
                name = view.findViewById(R.id.waypoint_name)
                dist = view.findViewById(R.id.waypoint_dist)
                total = view.findViewById(R.id.waypoint_total_dist)
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            // Create a new view, which defines the UI of the list item
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.trip_waypoint_list_item, viewGroup, false)

            return ViewHolder(view)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val wayPoint = viewModel.getWayPoint(position)
            viewHolder.name.text = wayPoint.name
            viewHolder.dist.text =
                String.format("%03.1f", conversion.distance(wayPoint.deltaDistance))
            viewHolder.total.text =
                String.format("%03.1f", conversion.distance(wayPoint.totalDistance))
        }

        override fun getItemCount(): Int {
            val size = viewModel.getWayPointCount()
            return size
        }
    }
}