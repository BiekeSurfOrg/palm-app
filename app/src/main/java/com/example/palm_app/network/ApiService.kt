package com.example.palm_app.network // Adjust package name as needed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject // For creating the JSON request body
import java.io.IOException

object ApiService {

    // Sealed class for fetchIdentity result
    sealed class FetchIdentityResult {
        data class Success(val jsonResponse: String) : FetchIdentityResult()
        data class Error(val errorMessage: String) : FetchIdentityResult()
    }

    // <<< --- NEW: Sealed class for postBleId result --- >>>
    sealed class PostBleIdResult {
        data class Success(val jsonResponse: String) : PostBleIdResult()
        data class Error(val errorMessage: String) : PostBleIdResult()
    }
    // <<< --- END NEW --- >>>


    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType() // Corrected charset


    suspend fun fetchIdentity(userId: Int): FetchIdentityResult {
        val url = "https://palm-central-7d7e7aad638d.herokuapp.com/Palmki/api/identity/$userId"
        val request = Request.Builder().url(url).header("Content-Type", "application/json").build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        FetchIdentityResult.Error("Network error: ${response.code} ${response.message}")
                    } else {
                        val body = response.body?.string()
                        if (body != null) {
                            FetchIdentityResult.Success(body)
                        } else {
                            FetchIdentityResult.Error("Response body was null for fetchIdentity")
                        }
                    }
                }
            } catch (e: IOException) {
                FetchIdentityResult.Error("Network request failed for fetchIdentity: ${e.message}")
            } catch (e: Exception) {
                FetchIdentityResult.Error("An unexpected error occurred for fetchIdentity: ${e.message}")
            }
        }
    }

    // <<< --- NEW: Function to post BLE ID --- >>>
    suspend fun postBleId(token: String): PostBleIdResult {
        val url = "https://palm-central-7d7e7aad638d.herokuapp.com/Palmki/api/BLEid"

        // Create the JSON request body
        val jsonRequestBody = JSONObject()
        jsonRequestBody.put("token", token)
        val requestBodyString = jsonRequestBody.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyString.toRequestBody(jsonMediaType))
            // .header("Content-Type", "application/json") // OkHttp infers this from RequestBody's media type
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // Consider logging response.body?.string() here for more debug info if needed
                        PostBleIdResult.Error("Network error for postBleId: ${response.code} ${response.message}")
                    } else {
                        val body = response.body?.string()
                        if (body != null) {
                            PostBleIdResult.Success(body)
                        } else {
                            PostBleIdResult.Error("Response body was null for postBleId")
                        }
                    }
                }
            } catch (e: IOException) {
                PostBleIdResult.Error("Network request failed for postBleId: ${e.message}")
            } catch (e: Exception) { // Catch any other unexpected errors
                PostBleIdResult.Error("An unexpected error occurred for postBleId: ${e.message}")
            }
        }
    }
    // <<< --- END NEW --- >>>

}
