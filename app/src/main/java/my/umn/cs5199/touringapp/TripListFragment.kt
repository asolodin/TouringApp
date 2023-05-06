package my.umn.cs5199.touringapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.databinding.FragmentFirstBinding
import my.umn.cs5199.touringapp.repository.TripRepository
import java.text.SimpleDateFormat
import java.util.*

class TripListFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val permissions = arrayOf(
        //Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        //Manifest.permission.ACCESS_BACKGROUND_LOCATION
        //Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    )

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var viewModel: TripListViewModel
    private lateinit var tripListAdapter: TripListFragment.CustomAdapter

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermissions(multiplePermissionLauncher)
        }

        viewModel = ViewModelProvider(requireActivity())[TripListViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateUiState(it)
                }
            }
        }

        viewModel.initialize(requireContext())

        val recyclerView: RecyclerView = binding.tripList
        tripListAdapter = CustomAdapter(requireContext(), viewModel)
        recyclerView.adapter = tripListAdapter

        return binding.root
    }

    override fun onResume() {
        viewModel.initialize(requireContext())
        super.onResume()
    }

    private fun updateUiState(state: TripListState) {
        tripListAdapter.notifyDataSetChanged()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.clickableJustRide.setOnClickListener {
            findNavController().navigate(R.id.action_TripSelection_to_RideDashboard)
        }
        binding.clickableAddTrip.setOnClickListener {
            viewModel.setEditTripPlanIndex(null)
            findNavController().navigate(R.id.action_TripSelection_to_TripPlanningFragment)
        }
        binding.clickableRefresh.setOnClickListener {
            viewModel.setEditTripPlanIndex(null)
            viewModel.initialize(requireContext())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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

    private fun hasPermissions(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
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

    inner class CustomAdapter(
        private val context: Context,
        private val viewModel: TripListViewModel
    ) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView
            val del: TextView

            init {
                name = view.findViewById(R.id.trip_name)
                del = view.findViewById(R.id.trip_delete)
                //total = view.findViewById(R.id.waypoint_total_dist)
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            // Create a new view, which defines the UI of the list item
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.trip_list_item, viewGroup, false)

            return ViewHolder(view)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val tripPlan = viewModel.getTripPlan(position)
            viewHolder.name.text = tripPlan.name
            if (tripPlan.timeStart > 0) {
                viewHolder.name.append("\n")
                viewHolder.name.append(
                    SimpleDateFormat("dd/MM/yyyy hh:mma").format(Date(tripPlan.timeStart * 1000))
                )
            }
            viewHolder.name.setOnClickListener {
                val intent = Intent(activity, DashboardActivity::class.java)
                intent.putExtra(
                    Constants.TRIP_FILE_NAME_PROP,
                    TripRepository.toFileName(tripPlan)
                )
                startActivity(intent)
            }
            if (tripPlan.timeStart == 0L) {
                viewHolder.name.setOnLongClickListener {
                    viewModel.setEditTripPlanIndex(position)
                    findNavController().navigate(R.id.action_TripSelection_to_TripPlanningFragment)
                    true
                }
            }

            viewHolder.del.setOnClickListener {
                viewModel.deleteTrip(requireContext(), TripRepository.toFileName(tripPlan))
            }

            /*viewHolder.dist.text =
                String.format("%03.1f", conversion.distance(wayPoint.deltaDistance))
            viewHolder.total.text =
                String.format("%03.1f", conversion.distance(wayPoint.totalDistance))*/
        }

        override fun getItemCount(): Int {
            val size = viewModel.getTripPlanCount()
            return size
        }
    }
}