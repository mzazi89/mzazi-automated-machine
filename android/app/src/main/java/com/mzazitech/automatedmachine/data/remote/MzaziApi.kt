package com.mzazitech.automatedmachine.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contract for the MZAZI backend. Kept in lock-step with docs/API.md.
 */
interface MzaziApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/v1/auth/me")
    suspend fun me(): AdminDto

    @GET("api/v1/dashboard/summary")
    suspend fun dashboardSummary(): DashboardSummaryDto

    @GET("api/v1/bundles")
    suspend fun bundles(@Query("network") network: String? = null): List<BundleDto>

    @POST("api/v1/bundles")
    suspend fun createBundle(@Body body: BundleDto): BundleDto

    @PATCH("api/v1/bundles/{id}")
    suspend fun updateBundle(@Path("id") id: String, @Body body: BundleDto): BundleDto

    @GET("api/v1/workflows")
    suspend fun workflows(@Query("network") network: String? = null): List<WorkflowDto>

    @POST("api/v1/workflows")
    suspend fun createWorkflow(@Body body: WorkflowDto): WorkflowDto

    @PATCH("api/v1/workflows/{id}")
    suspend fun updateWorkflow(@Path("id") id: String, @Body body: WorkflowDto): WorkflowDto

    @POST("api/v1/transactions")
    suspend fun createTransaction(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateTransactionRequest
    ): TransactionDto

    @GET("api/v1/transactions/{id}")
    suspend fun transaction(@Path("id") id: String): TransactionDto

    @GET("api/v1/transactions")
    suspend fun transactions(@Query("limit") limit: Int = 50): List<TransactionDto>

    @POST("api/v1/payments/test/simulate")
    suspend fun simulatePayment(@Body body: SimulatePaymentRequest): TransactionDto

    @GET("api/v1/ussd/jobs/next")
    suspend fun nextUssdJob(): Response<UssdJobDto>

    @POST("api/v1/ussd/jobs/{id}/result")
    suspend fun reportJobResult(
        @Path("id") id: String,
        @Body body: JobResultRequest
    ): UssdJobDto

    @POST("api/v1/ussd/jobs/{id}/manual-confirmation")
    suspend fun confirmManual(
        @Path("id") id: String,
        @Body body: ManualConfirmationRequest
    ): UssdJobDto
}
