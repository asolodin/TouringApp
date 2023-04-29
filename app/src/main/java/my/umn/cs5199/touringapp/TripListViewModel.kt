package my.umn.cs5199.touringapp

import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng

data class TripListState(
    val trips : List<TripPlan>
) {}

class TripListViewModel: ViewModel()  {
}