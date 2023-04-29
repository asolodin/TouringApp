package my.umn.cs5199.touringapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import my.umn.cs5199.touringapp.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.clickableJustRide.setOnClickListener {
            findNavController().navigate(R.id.action_TripSelection_to_RideDashboard)
        }
        binding.clickableAddTrip.setOnClickListener {
            findNavController().navigate(R.id.action_TripSelection_to_TripPlanningFragment)
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
}