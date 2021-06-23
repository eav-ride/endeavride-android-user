package com.endeavride.endeavrideuser

import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.maps.model.LatLng

object Utils {

    fun decodeLocationString(location: String):LatLng? {
        val point = location.split(",")
        if (point.size != 2) return null
        return LatLng(point[0].toDouble(), point[1].toDouble())
    }

    /**
     * Provides access to SharedPreferences for location to Activities and Services.
     */

    const val KEY_FOREGROUND_ENABLED = "tracking_foreground_location"

    /**
     * Returns true if requesting location updates, otherwise returns false.
     *
     * @param context The [Context].
     */
    fun getStringPref(context: Context, key: String): String? =
        context.getSharedPreferences(
            key, Context.MODE_PRIVATE)
            .getString(KEY_FOREGROUND_ENABLED, null)

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun saveStringPref(context: Context, value: String, key: String) =
        context.getSharedPreferences(
            key,
            Context.MODE_PRIVATE).edit {
            putString(KEY_FOREGROUND_ENABLED, value).commit()
        }
}