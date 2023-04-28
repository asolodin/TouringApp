package my.umn.cs5199.touringapp

import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng

data class TripPlan(
    val name : String,
    val totalDistance : Double,
    val locationStart : LatLng,
    val locationEnd : LatLng,
    val timeStart : Long,
    val timeEnd : Long
) {}

data class TripListState(
    val trips : List<TripPlan>
) {}

class TripListViewModel: ViewModel()  {
}