package my.umn.cs5199.touringapp

import androidx.lifecycle.ViewModel

data class AppState(
    val speed: Float = 0f
)

class AppViewMode : ViewModel() {
}