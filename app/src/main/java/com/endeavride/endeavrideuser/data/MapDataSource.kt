package com.endeavride.endeavrideuser.data

import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.endeavride.endeavrideuser.MapsFragment
import com.endeavride.endeavrideuser.NetworkUtils
import com.endeavride.endeavrideuser.data.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.IOException

class MapDataSource {

    companion object {
        private const val TAG = "MapDataSource"
    }
    private val mapsKey = "AIzaSyBQLhQPNU5UFQczahI4ZHX4CReuH1D5o8U"

    suspend fun createRideRequest(rideRequest: RideRequest): Result<Ride> {
        try {
            val result = NetworkUtils.postRequest("r", Json.encodeToString(rideRequest))

            if (result.resData != null) {
                val ride = Json.decodeFromString<Ride>(result.resData)
                return Result.Success(ride)
            }
            Log.e(TAG, "[createRideRequest] decode error: " + result.error.toString())
            return Result.Error(IOException(result.error))
        } catch (e: Throwable) {
            Log.e(TAG, "[createRideRequest] network error: $e")
            return Result.Error(IOException("create ride failed $e", e))
        }
    }

    suspend fun checkIfCurrentRideAvailable(): Result<Ride> {
        try {
            val result = NetworkUtils.getRequest("r", null)
            if (result.resData != null) {
                val ride = Json.decodeFromString<Ride>(result.resData)
                return Result.Success(ride)
            }
            Log.e(TAG, "[checkIfCurrentRideAvailable] decode error: " + result.error.toString())
            return Result.Error(IOException(result.error))
        } catch (e: Throwable) {
            Log.e(TAG, "[checkIfCurrentRideAvailable] network error: $e")
            return Result.Error(IOException("check in progress (current) ride failed $e", e))
        }
    }

    suspend fun refreshCurrentRide(rid: String): Result<Ride> {
        try {
            val result = NetworkUtils.getRequest("r/$rid", null)
            if (result.resData != null) {
                val ride = Json.decodeFromString<Ride>(result.resData)
                return Result.Success(ride)
            }
            Log.e(TAG, "[checkIfCurrentRideAvailable] decode error: " + result.error.toString())
            return Result.Error(IOException(result.error))
        } catch (e: Throwable) {
            Log.e(TAG, "[checkIfCurrentRideAvailable] network error: $e")
            return Result.Error(IOException("check in progress (current) ride failed $e", e))
        }
    }

    suspend fun getDirection(origin: LatLng, dest: LatLng): MutableList<List<LatLng>> {
        val result = NetworkUtils.getRequestWithFullpath("https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${dest.latitude},${dest.longitude}&key=$mapsKey")
        val path: MutableList<List<LatLng>> = ArrayList()
        if (result.resData == null) {
            return path
        }
        val jsonResponse = JSONObject(result.resData)
        // Get routes
        val routes = jsonResponse.getJSONArray("routes")
        if (routes.length() < 1) {
            return path
        }
        val legs = routes.getJSONObject(0).getJSONArray("legs")
        val steps = legs.getJSONObject(0).getJSONArray("steps")
        for (i in 0 until steps.length()) {
            val points = steps.getJSONObject(i).getJSONObject("polyline").getString("points")
            Log.d(TAG, "Points: $points")
            path.add(PolyUtil.decode(points))
        }
        return path
    }

    suspend fun cancelRideRequest(rid: String): Result<Ride> {
        try {
            val result = NetworkUtils.postRequest("r/$rid", Json.encodeToString(mapOf("status" to MapsFragment.OrderStatus.CANCELLED.value)))

            if (result.resData != null) {
                val ride = Json.decodeFromString<Ride>(result.resData)
                return Result.Success(ride)
            }
            Log.e(TAG, "[cancelRideRequest] decode error: " + result.error.toString())
            return Result.Error(IOException(result.error))
        } catch (e: Throwable) {
            Log.e(TAG, "[cancelRideRequest] network error: $e")
            return Result.Error(IOException("cancel ride failed $e", e))
        }
    }

    // drive record
    suspend fun pollDriveRecord(rid: String): Result<DriveRecord> {
        try {
            val result = NetworkUtils.getRequest("dr/$rid", null)

            if (result.resData != null) {
                val record = Json.decodeFromString<DriveRecord>(result.resData)
                return Result.Success(record)
            }
            Log.e(TAG, "[pollDriveRecord] decode error: " + result.error.toString())
            return Result.Error(IOException(result.error))
        } catch (e: Throwable) {
            Log.e(TAG, "[pollDriveRecord] network error: $e")
            return Result.Error(IOException("poll drive record failure with error: $e", e))
        }
    }
}