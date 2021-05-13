package com.endeavride.endeavrideuser.data

import com.endeavride.endeavrideuser.data.model.LoggedInUser
import java.io.IOException
import java.util.*

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource {

    fun login(username: String, password: String): Result<LoggedInUser> {
        try {
            // TODO: handle loggedInUser authentication
            val fakeUser = LoggedInUser(UUID.randomUUID().toString(), "Jane Doe")
            return Result.Success(fakeUser)
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    fun loadUserInfoIfAvailable(): Result<LoggedInUser> {
//        try {
//            // TODO: read user token and valid from server
//            val fakeUser = LoggedInUser(UUID.randomUUID().toString(), "Jane Doe")
//            return Result.Success(fakeUser)
//        } catch (e: Throwable) {
            return Result.Error(IOException("No available user"))
//        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}