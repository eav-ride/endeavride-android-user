package com.endeavride.endeavrideuser.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Ride (
    val rid: String,
    val status: Int,
    val uid: String,
    val did: String? = null,
    val user_location: String,
    val destination: String,
    val create_time: String? = null,
    val start_time: String? = null,
    val finish_time: String? = null
)

@Serializable
data class RideRequest (
    val user_location: String,
    val destination: String,
    val uid: String
)

@Serializable
data class DriveRecord (
    val rid: String,
    val uid: String,
    val did: String,
    val status: Int,
    val driver_location: String,
    val create_time: String
)