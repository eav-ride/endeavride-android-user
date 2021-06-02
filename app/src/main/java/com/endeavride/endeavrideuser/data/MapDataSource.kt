package com.endeavride.endeavrideuser.data

import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.endeavride.endeavrideuser.NetworkUtils
import com.endeavride.endeavrideuser.data.model.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.IOException

class MapDataSource {

    private val mapsKey = "AIzaSyAxxnazPy8mIAROs-chSCrDknDvzyB3Vho"

    suspend fun createRideRequest(direction: String, uid: String): Result<Ride> {
        try {
            val result = NetworkUtils.postRequest("r", Json.encodeToString(RideRequest(direction, uid)))

            if (result.resData != null) {
                val ride = Json.decodeFromString<Ride>(result.resData)
                return Result.Success(ride)
            }
            return Result.Error(IOException(result.error))
        } catch (e: Throwable) {
            return Result.Error(IOException("create ride failed $e", e))
        }
    }

    suspend fun checkIfCurrentRideAvailable(): Result<Ride> {
        try {
            val result = NetworkUtils.getRequest("r")
            if (result.resData != null) {
                val ride = Json.decodeFromString<Ride>(result.resData)
                return Result.Success(ride)
            }
            return Result.Error(IOException(result.error))
        } catch (e: Throwable) {
            return Result.Error(IOException("check in progress (current) ride failed $e", e))
        }
    }

    fun getRideRequest(origin: LatLng, dest: LatLng) {

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
            Log.d("Test", "#K_points: $points")
            path.add(decodePoint(points))
        }
        return path
    }

    private fun decodePoint(point: String): List<LatLng> {
        val ps = point.split(",")
        return listOf(LatLng(ps[0].toDouble(), ps[1].toDouble()))
    }
}