package my.umn.cs5199.touringapp

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.umn.cs5199.touringapp.repository.TripRepository

data class TripListState(
    val trips: List<TripPlan> = listOf(),
) {}

class TripListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TripListState())
    val uiState: StateFlow<TripListState> = _uiState.asStateFlow()
    private val repo = TripRepository()
    private var editTripPlanIndex: Int? = null

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

    fun getTripPlan(i: Int): TripPlan {
        return uiState.value.trips.get(i)
    }

    fun getEditTripPlan(): TripPlan? {
        val index = editTripPlanIndex
        //Log.d("touringApp", "get edit trip plan " + index + ", viewModel " + System.identityHashCode(this))
        if (index != null) {
            return getTripPlan(index)
        }
        return null
    }

    fun getTripPlanCount(): Int {
        return uiState.value.trips.size
    }

    fun setEditTripPlanIndex(i: Int?) {
        //Log.d("touringApp", "set edit trip plan " + i + ", viewModel " + System.identityHashCode(this))
        editTripPlanIndex = i
    }

    fun deleteTrip(context: Context, fileName: String) {
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