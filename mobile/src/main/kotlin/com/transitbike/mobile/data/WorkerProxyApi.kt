package com.transitbike.mobile.data

import com.transitbike.shared.model.RouteModel
import com.transitbike.shared.nav.TransitV4ResponseMapper
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class AdminQuotaResponse(
    val adminUid: String,
    val quota: AdminQuotaSnapshot
)

@Serializable
data class AdminQuotaSnapshot(
    val monthKey: String = "",
    val monthlyUsed: Int = 0,
    val monthlyLimit: Int = 1480,
    val minuteUsed: Int = 0,
    val minuteLimit: Int = 4,
    val perUserDailyLimit: Int = 40
)

class WorkerProxyApi(
    private val baseUrl: String = "https://transit-bike-proxy.workers.dev",
    private val authManager: AuthManager = AuthManager()
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun planBikeRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        locale: String = "en"
    ): Result<List<RouteModel>> {
        val url = "$baseUrl/v1/plan?from_lat=$fromLat&from_lon=$fromLon&to_lat=$toLat&to_lon=$toLon&mode=personal_bike&should_include_directions=true&should_include_safety=true&num_result=3&locale=$locale"

        val auth = authManager.refreshAuthTokens()
        val requestBuilder = Request.Builder().url(url)

        auth.idToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        auth.appCheckToken?.let { requestBuilder.header("X-Firebase-AppCheck", it) }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("Worker plan API failed with HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return Result.failure(IOException("Empty response body"))
            val routes = TransitV4ResponseMapper.parseAndMap(body)
            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAdminQuota(): Result<AdminQuotaResponse> {
        val url = "$baseUrl/v1/admin/quota"
        val auth = authManager.refreshAuthTokens()
        val requestBuilder = Request.Builder().url(url)

        auth.idToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        auth.appCheckToken?.let { requestBuilder.header("X-Firebase-AppCheck", it) }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("Admin quota failed with HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return Result.failure(IOException("Empty body"))
            val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<AdminQuotaResponse>(body)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
