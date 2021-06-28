package com.endeavride.endeavrideuser

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endeavride.endeavrideuser.data.MapDataSource
import com.endeavride.endeavrideuser.data.model.Ride
import com.endeavride.endeavrideuser.data.Result
import com.endeavride.endeavrideuser.data.model.DriveRecord
import com.endeavride.endeavrideuser.data.model.RideRequest
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.LocationBias
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

//@HiltViewModel
class MapsViewModel(
//    private val placesClient: PlacesClient,
    val dataSource: MapDataSource
) : ViewModel() {
    private val _events = MutableLiveData<PlacesSearchEvent>()
    val events: LiveData<PlacesSearchEvent> = _events

    private val _mapDirectionResult = MutableLiveData<MutableList<List<LatLng>>>()
    val mapDirectionResult: LiveData<MutableList<List<LatLng>>> = _mapDirectionResult

    private val _currentRide = MutableLiveData<Ride?>()
    val currentRide: LiveData<Ride?> = _currentRide

    private val _latestDriverLocation = MutableLiveData<DriveRecord>()
    val latestDriverLocation: LiveData<DriveRecord> = _latestDriverLocation

    private var searchJob: Job? = null

    fun getDirection(origin:LatLng, dest: LatLng) {
        viewModelScope.launch {
            val result = dataSource.getDirection(origin, dest)
            _mapDirectionResult.value = result
        }
    }

    fun createRide(type: Int, origin:LatLng?, destination: LatLng, uid: String) {
        viewModelScope.launch {
            val userLocation = if (origin != null) {
                Utils.encodeLocationString(origin)
            } else {
                ""
            }
            val rideRequest = RideRequest(type, userLocation, Utils.encodeLocationString(destination), uid)
            val result = dataSource.createRideRequest(rideRequest)
            if (result is Result.Success) {
                _currentRide.value = result.data
            } else {
                _currentRide.value = null
            }
        }
    }

    fun cancelRide(rid: String) {
        viewModelScope.launch {
            val result = dataSource.cancelRideRequest(rid)
            if (result is Result.Success) {
                _currentRide.value = result.data
            } else {
                _currentRide.value = null
            }
        }
    }

    fun getCurrentRide(delayTime: Long = 0) {
        viewModelScope.launch {
            delay(delayTime)
            val result = dataSource.checkIfCurrentRideAvailable()
            println("#K_check current ride result: $result")
            if (result is Result.Success) {
                _currentRide.value = result.data
            } else {
                _currentRide.value = null
            }
        }
    }

    fun refreshRide(rid: String, delayTime: Long = 0) {
        viewModelScope.launch {
            delay(delayTime)
            val result = dataSource.refreshCurrentRide(rid)
            println("#K_check current ride result: $result")
            if (result is Result.Success) {
                _currentRide.value = result.data
            } else {
                _currentRide.value = null
            }
        }
    }

    fun pollDriveRecord(rid: String) {
        viewModelScope.launch {
            val result = dataSource.pollDriveRecord(rid)
            delay(3000)
            if (result is Result.Success) {
                _latestDriverLocation.value = result.data
            }
        }
    }

//    fun onSearchQueryChanged(query: String) {
//        searchJob?.cancel()
//
//        _events.value = PlacesSearchEventLoading
//
//        val handler = CoroutineExceptionHandler { _, throwable ->
//            _events.value = PlacesSearchEventError(throwable)
//        }
//        searchJob = viewModelScope.launch(handler) {
//            // Add delay so that network call is performed only after there is a 300 ms pause in the
//            // search query. This prevents network calls from being invoked if the user is still
//            // typing.
//            delay(300)
//
//            val bias: LocationBias = RectangularBounds.newInstance(
//                LatLng(37.7576948, -122.4727051), // SW lat, lng
//                LatLng(37.808300, -122.391338) // NE lat, lng
//            )
//
//            val request = FindAutocompletePredictionsRequest
//                .builder()
//                .setLocationBias(bias)
//                .setTypeFilter(TypeFilter.ESTABLISHMENT)
//                .setQuery(query)
//                .setCountries(listOf("US"))
//                .build()
//
////            placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
////                // Handle response
////                _events.value = PlacesSearchEventFound(response.autocompletePredictions)
////            }.addOnFailureListener { exception ->
////                // Handle exception
////                Log.e("Places Client", exception.message.toString())
////            }
//
//        }
//    }
}