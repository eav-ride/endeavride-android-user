package com.endeavride.endeavrideuser

import com.google.android.gms.maps.model.LatLng

object Utils {
    @JvmStatic
    fun decodeRideDirection(direction: String):LatLng? {
        val dir = direction.split(";")
        if (dir.size != 2) return null
        val point = dir[1].split(",")
        if (point.size != 2) return null
        return LatLng(point[0].toDouble(), point[1].toDouble())
    }
}