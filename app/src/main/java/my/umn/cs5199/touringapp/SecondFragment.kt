package my.umn.cs5199.touringapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.databinding.FragmentSecondBinding
import java.util.function.Function

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private var mapFragment: SupportMapFragment? = null
    private val viewModel: TripPlanViewModel by activityViewModels()
    private val markers: MutableMap<String, Marker> = mutableMapOf()
    private lateinit var tripWayPointList: CustomAdapter

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

        return binding.root
    }

    private fun updateUiState(state: TripPlanState) {
        if (!state.tripPlan.wayPoints.isEmpty()) {
            mapFragment?.getMapAsync { gm ->
                val i = state.tripPlan.currentPoint
                val waypoint = state.tripPlan.wayPoints.get(i)
                gm.moveCamera(CameraUpdateFactory.newLatLng(waypoint.location))
                markers.computeIfAbsent(waypoint.name) {
                    val m = gm.addMarker(
                        MarkerOptions().position(waypoint.location).title(waypoint.name)
                    )!!
                    m.showInfoWindow()
                    tripWayPointList.notifyItemInserted(i)
                    m
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
            it.uiSettings.isMapToolbarEnabled = true
            it.uiSettings.isZoomControlsEnabled = true
            it.setOnMapLongClickListener {
                viewModel.addWayPoint(it)
            }
        }
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

            init {
                name = view.findViewById(R.id.waypoint_name)
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
            val tripPlan = viewModel.uiState.value.tripPlan
            val wayPoint = tripPlan.wayPoints.get(position)
            viewHolder.name.text = wayPoint.name

            /*
            val spentList = surveyInstance.optionAmountSpent ?: listOf()
            if (option != null) {
                val amountSpent = spentList.find { it.optionId == option.optionID }
                if (amountSpent != null) {
                    if (amountSpent.amountSpent == 0) {
                        viewHolder.amount.text = ""
                    } else {
                        viewHolder.amount.text = amountSpent.amountSpent.toString()
                    }
                }
            }*/
            /*
            viewHolder.name.addTextChangedListener(object : TextWatcher {
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
                }
            })*/
            /*onFocusChangeListener =
                OnFocusChangeListener { v, hasFocus ->
                    val amountTextView = v as TextView
                    if (!hasFocus &&
                        option != null &&
                        option.optionID != null) {
                        //updateAmountSpent(option.optionID, amountTextView)
                        updateTotalAmountSpent2()
                    }
                }*/
        }

        override fun getItemCount(): Int {
            val size = viewModel.uiState.value.tripPlan.wayPoints.size
            return size
        }
    }
}