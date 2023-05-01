package my.umn.cs5199.touringapp

import android.content.Context
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.repository.TripRepository

data class TripListState(
    val trips : List<TripPlan> = listOf()
) {}

class TripListViewModel: ViewModel()  {
    private val _uiState = MutableStateFlow(TripListState())
    val uiState: StateFlow<TripListState> = _uiState.asStateFlow()
    private val repo = TripRepository()

    fun initialize(context: Context) {

        viewModelScope.launch {
            val tripList = repo.loadFromStorage(context)
            _uiState.update { currentState ->
                currentState.copy(
                    trips = tripList
                )
            }
        }
    }

    fun getTripPlan(i : Int) : TripPlan {
        return uiState.value.trips.get(i)
    }

    fun getTripPlanCount() : Int {
        return uiState.value.trips.size
    }

    fun deleteTrip(context : Context, fileName : String) {
        viewModelScope.launch {
            repo.deleteFromStorage(context, fileName)
            val tripList = repo.loadFromStorage(context)
            _uiState.update { currentState ->
                currentState.copy(
                    trips = tripList
                )
            }
        }
    }
}