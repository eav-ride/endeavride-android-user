package com.endeavride.endeavrideuser

import com.endeavride.endeavrideuser.data.model.LoggedInUser
import com.endeavride.endeavrideuser.ui.login.LoginResult
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.coroutines.awaitResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import java.io.IOException

data class RequestResultModel(val resData: String?, val error: FuelError?)

class NetworkUtils {
    companion object {

        val shared: NetworkUtils = NetworkUtils()
        var user: LoggedInUser? = null

        init{
            FuelManager.instance.baseHeaders = mapOf(
                "User-Agent" to "DemoApp ENDEAVRideUser",
                "Content-Type" to "application/json"
            )
//            FuelManager.instance.basePath = "http://ec2-18-220-53-8.us-east-2.compute.amazonaws.com:3300/"
//            FuelManager.instance.basePath = "https://10.0.2.2:8443/"
            FuelManager.instance.basePath = "http://10.0.2.2:3300/"
        }

        suspend fun getRequestWithFullpath(path: String): RequestResultModel {
            val (request, response, result) = Fuel.get(path).awaitStringResponseResult()
            println(request)
            println(response)
            val (bytes, error) = result
            if (bytes != null) {
                println("[response bytes] $bytes")
            }
            if (error != null) {
                println("fuel get request error: $error")
            }
            return RequestResultModel(bytes, error)
        }

        suspend fun getRequest(path: String, parameters: Parameters?): RequestResultModel {
            val fuelRequest = FuelManager.instance.get(path, parameters)
            user?.userId?.let { fuelRequest.appendHeader("uid", it) }
            val (request, response, result) = fuelRequest.awaitStringResponseResult()
            println(request)
            println(response)
            val (bytes, error) = result
            if (bytes != null) {
                println("[response bytes] $bytes")
            }
            if (error != null) {
                println("fuel get request error: $error")
            }
            return RequestResultModel(bytes, error)
        }

        suspend fun postRequest(path: String, body: String): RequestResultModel {
            val fuelRequest = FuelManager.instance.post(path)
            user?.userId?.let { fuelRequest.appendHeader("uid", it) }
            val (request, response, result) = fuelRequest.body(body).awaitStringResponseResult()
            println(request)
            println(response)
            val (bytes, error) = result
            if (bytes != null) {
                println("[response bytes] $bytes")
            }
            if (error != null) {
                println("fuel post request error: $error")
            }
            return RequestResultModel(bytes, error)
        }
    }

    suspend fun poll(initialDelay: Long = 5000,
                     maxDelay: Long = 30000,
                     factor: Double = 2.0,
                     block: suspend () -> Unit) {

        var currentDelay = initialDelay
        while (true) {
            try {
                currentDelay = try {
                    block()
                    initialDelay
                } catch (e: IOException) {
                    (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                }
                delay(currentDelay)
                yield()
            } catch (e: CancellationException) {
                break
            }
        }
    }
}