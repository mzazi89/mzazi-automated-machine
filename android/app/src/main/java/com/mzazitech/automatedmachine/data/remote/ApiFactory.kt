package com.mzazitech.automatedmachine.data.remote

import com.mzazitech.automatedmachine.BuildConfig
import com.mzazitech.automatedmachine.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the single Retrofit client used by the app.
 *
 * Only HTTPS base URLs are accepted outside of local development, and the app never
 * embeds an API secret: the bearer token is obtained at runtime and stored encrypted.
 */
object ApiFactory {

    fun create(session: SessionManager, baseUrl: String = BuildConfig.API_BASE_URL): MzaziApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("X-Mzazi-Client", "android")
                .header("X-Mzazi-App-Version", BuildConfig.VERSION_NAME)

            session.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
            session.deviceId.value?.let { builder.header("X-Device-Id", it) }
            session.deviceKey()?.let { builder.header("X-Device-Key", it) }

            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            // Never log headers or bodies in release builds: they can carry tokens.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MzaziApi::class.java)
    }
}

/** Thrown by the repository layer with a message that is safe to show to an operator. */
class ApiException(
    val statusCode: Int,
    override val message: String,
    val code: String? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    val isAuthError: Boolean get() = statusCode == 401 || statusCode == 403
    val isNetwork: Boolean get() = statusCode == 0
}

object ApiErrorMapper {

    fun from(t: Throwable): ApiException = when (t) {
        is ApiException -> t
        is retrofit2.HttpException -> {
            val body = runCatching { t.response()?.errorBody()?.string() }.getOrNull()
            val parsed = runCatching {
                com.google.gson.Gson().fromJson(body, ApiErrorBody::class.java)
            }.getOrNull()
            ApiException(
                statusCode = t.code(),
                message = parsed?.message ?: parsed?.error ?: defaultMessage(t.code()),
                code = parsed?.code
            )
        }
        is java.net.UnknownHostException ->
            ApiException(0, "Cannot reach the MZAZI server. Check the internet connection.", "network")
        is java.net.SocketTimeoutException ->
            ApiException(0, "The server took too long to respond. Please retry.", "timeout")
        is java.io.IOException ->
            ApiException(0, "Network error: ${t.message ?: "connection failed"}", "network")
        else -> ApiException(0, t.message ?: "Unexpected error", "unknown", t)
    }

    private fun defaultMessage(code: Int): String = when (code) {
        400 -> "The request was rejected. Check the details and retry."
        401 -> "Your session has expired. Please sign in again."
        403 -> "This account is not allowed to perform that action."
        404 -> "That record no longer exists."
        409 -> "This action conflicts with an existing record."
        429 -> "Too many attempts. Please wait a moment."
        in 500..599 -> "The server had a problem. Please retry shortly."
        else -> "Request failed ($code)."
    }
}
