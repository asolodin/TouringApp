package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
 *
 */
class TripPlanFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private var mapFragment: SupportMapFragment? = null
    private val viewModelTripPlan: TripPlanViewModel by activityViewModels()
    private val viewModelTripList: TripListViewModel by activityViewModels()

    private val markers: MutableMap<String, Marker> = mutableMapOf()
    private lateinit var tripWayPointListAdapter: CustomAdapter
    private val conversion = Conversion(DistanceUnit.MI)

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModelTripPlan.uiState.collect {
                    updateUiState(it)
                }
            }
        }
        val tripPlan = viewModelTripList.getEditTripPlan()
        Log.d("touringApp", "editing trip plan " + tripPlan)
        viewModelTripPlan.initialize(requireContext(), tripPlan)
        val recyclerView: RecyclerView = binding.tripWaypointList
        tripWayPointListAdapter = CustomAdapter(viewModelTripPlan)
        recyclerView.adapter = tripWayPointListAdapter


        binding.tripPlanRide.setOnClickListener {
            viewModelTripPlan.setTripPlanName(binding.tripPlanName.text.toString())
            viewModelTripPlan.saveTripPlan(requireContext()) {
                val intent = Intent(activity, DashboardActivity::class.java)
                intent.putExtra(Constants.TRIP_FILE_NAME_PROP, it)
                startActivity(intent)
            }
        }

        return binding.root
    }

    private fun updateUiState(state: TripPlanState) {
        if (state.tripPlan.wayPoints.size > 1) {
            binding.tripPlanRide.visibility = VISIBLE
        }
        if (state.refreshAllPoints) {
            tripWayPointListAdapter.notifyDataSetChanged()
        } else if (state.insertedPoint > -1) {
            tripWayPointListAdapter.notifyItemInserted(state.insertedPoint)
        }
        if (state.tripPlan.currentPoint > -1 || state.refreshAllPoints) {

            val tripName = binding.tripPlanName.text ?: ""
            if (!tripName.equals(state.tripPlan.name)) {
                binding.tripPlanName.setText(state.tripPlan.name)
            }

            mapFragment?.getMapAsync { gm ->
                var i = if (state.refreshAllPoints) 0 else state.tripPlan.currentPoint
                val to =
                    if (state.refreshAllPoints) state.tripPlan.wayPoints.size else state.tripPlan.currentPoint + 1
                while (i < to) {
                    val wayPoint = state.tripPlan.wayPoints.get(i)
                    gm.moveCamera(CameraUpdateFactory.newLatLng(wayPoint.location))
                    markers.computeIfAbsent(wayPoint.name) {
                        val marker = gm.addMarker(
                            MarkerOptions().position(wayPoint.location).title(wayPoint.name)
                        )!!
                        marker.showInfoWindow()
                        marker
                    }
                    val route: Polyline = gm.addPolyline(
                        PolylineOptions().color(
                            ContextCompat.getColor(requireContext(), R.color.route_plan)
                        )
                    )
                    route.points = wayPoint.segment
                    i += 1
                }
            }
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
            //it.uiSettings.isMapToolbarEnabled = true
            it.uiSettings.isZoomControlsEnabled = true
            it.setOnMapLongClickListener {
                viewModelTripPlan.setTripPlanName(binding.tripPlanName.text.toString())
                viewModelTripPlan.addWayPoint(it)
            }
        }
    }

    override fun onPause() {
        viewModelTripPlan.setTripPlanName(binding.tripPlanName.text.toString())
        viewModelTripPlan.saveTripPlan(requireContext()) {}
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class CustomAdapter(
        private val viewModel: TripPlanViewModel
    ) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val clickable: View
            val name: TextView
            val dist: TextView
            val total: TextView

            init {
                name = view.findViewById(R.id.waypoint_name)
                dist = view.findViewById(R.id.waypoint_dist)
                total = view.findViewById(R.id.waypoint_total_dist)
                clickable = view.findViewById(R.id.trip_waypoint)
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
            viewHolder.clickable.setOnClickListener {
                mapFragment?.getMapAsync { gm ->
                    gm.moveCamera(CameraUpdateFactory.newLatLng(wayPoint.location))
                }
            }
        }

        override fun getItemCount(): Int {
            val size = viewModel.getWayPointCount()
            return size
        }
    }
}