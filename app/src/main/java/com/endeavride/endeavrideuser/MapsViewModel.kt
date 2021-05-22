package com.endeavride.endeavrideuser

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.LocationBias
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapsViewModel @Inject constructor(
    private val placesClient: PlacesClient
) : ViewModel() {
    private val _events = MutableLiveData<PlacesSearchEvent>()
    val events: LiveData<PlacesSearchEvent> = _events

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()

        _events.value = PlacesSearchEventLoading

        val handler = CoroutineExceptionHandler { _, throwable ->
            _events.value = PlacesSearchEventError(throwable)
        }
        searchJob = viewModelScope.launch(handler) {
            // Add delay so that network call is performed only after there is a 300 ms pause in the
            // search query. This prevents network calls from being invoked if the user is still
            // typing.
            delay(300)

            val bias: LocationBias = RectangularBounds.newInstance(
                LatLng(37.7576948, -122.4727051), // SW lat, lng
                LatLng(37.808300, -122.391338) // NE lat, lng
            )

            val request = FindAutocompletePredictionsRequest
                .builder()
                .setLocationBias(bias)
                .setTypeFilter(TypeFilter.ESTABLISHMENT)
                .setQuery(query)
                .setCountries(listOf("US"))
                .build()

            placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
                // Handle response
                _events.value = PlacesSearchEventFound(response.autocompletePredictions)
            }.addOnFailureListener { exception ->
                // Handle exception
                Log.e("Places Client", exception.message.toString())
            }

        }
    }
}