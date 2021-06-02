package com.endeavride.endeavrideuser

import com.endeavride.endeavrideuser.data.model.LoggedInUser
import com.endeavride.endeavrideuser.ui.login.LoginResult
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.coroutines.awaitResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
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

        suspend fun getRequest(path: String): RequestResultModel {
            val fuelRequest = FuelManager.instance.get(path)
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

    //this method builds the query using the appropriate request params
//    fun getSearchUrl(term: String, skip: Int, take: Int): String {
//        val BaseUrl = "https://en.wikipedia.org/w/api.php"
//        return BaseUrl + "?action=query" +
//                "&formatversion=2" +
//                "&generator=prefixsearch" +
//                "&gpssearch=$term" +
//                "&gpslimit=$take" +
//                "&gpsoffset=$skip" +
//                "&prop=pageimages|info" +
//                "&piprop=thumbnail|url" +
//                "&pithumbsize=200" +
//                "&pilimit=$take" +
//                "&wbptterms=description" +
//                "&format=json" +
//                "&inprop=url"
//    }

    //This methods makes the HTTP request asynchronously with Fuel and returns the deserialized result if request is successful
//    fun search(term: String, skip: Int, take: Int, responseHandler : (result: WikiResult) -> Unit?){
//        Urls.getSearchUrl(term, skip, take).httpGet()
//            .responseObject(WikipediaDataDeserializer()){ _, _, result->
//
//                when(result){
//                    is Result.Failure ->{
//                        Log.i("ErrorMsg", result.getException().message)
//                        result.getException().stackTrace
//                        throw Exception(result.getException())
//                    }
//
//                    is Result.Success ->{
//                        val(data, _) = result
//                        responseHandler.invoke(data as WikiResult)
//                    }
//                }
//            }
//    }
//
//    //This class deserializes the result using Gson
//    class WikipediaDataDeserializer : ResponseDeserializable<WikiResult>{
//        override fun deserialize(reader: Reader) = Gson().fromJson(reader, WikiResult::class.java)
//    }
}