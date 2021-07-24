package com.endeavride.endeavrideuser

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
import kotlinx.coroutines.*

class MapsViewModel(
    val dataSource: MapDataSource
) : ViewModel() {
    private val _mapDirectionResult = MutableLiveData<MutableList<List<LatLng>>>()
    val mapDirectionResult: LiveData<MutableList<List<LatLng>>> = _mapDirectionResult

    private val _currentRide = MutableLiveData<Ride?>()
    val currentRide: LiveData<Ride?> = _currentRide

    private val _latestDriverLocation = MutableLiveData<DriveRecord>()
    val latestDriverLocation: LiveData<DriveRecord> = _latestDriverLocation

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
            println("Checking current ride result: $result")
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
            println("Checking current ride result: $result")
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
}