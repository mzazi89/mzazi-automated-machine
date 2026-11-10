package com.mzazitech.automatedmachine.data.ussd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.mzazitech.automatedmachine.domain.model.UssdFailureCategory
import com.mzazitech.automatedmachine.domain.ussd.SimSlotInfo
import com.mzazitech.automatedmachine.domain.ussd.UssdExecutor
import com.mzazitech.automatedmachine.domain.ussd.UssdOutcome
import com.mzazitech.automatedmachine.domain.ussd.UssdRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * USSD transport built on the public Android telephony API.
 *
 * Scope and honest limits:
 *  - [TelephonyManager.sendUssdRequest] is a public API from Android 8.0 (API 26).
 *  - Android does not expose a way to type into a live USSD session. A flow that needs
 *    menu input therefore ends in an operator-confirmed manual step; the engine reports
 *    that explicitly instead of pretending it succeeded.
 *  - [Manifest.permission.CALL_PHONE] is required, and the carrier must permit USSD from
 *    the selected subscription. Neither restriction is bypassed here.
 */
class AndroidTelephonyUssdExecutor(
    private val context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper())
) : UssdExecutor {

    override fun isSimulation(): Boolean = false

    override suspend fun listSims(): List<SimSlotInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) return@withContext emptyList()

        val manager = context.getSystemService(SubscriptionManager::class.java)
            ?: return@withContext emptyList()

        val active: List<SubscriptionInfo> = runCatching {
            manager.activeSubscriptionInfoList
        }.getOrNull().orEmpty()

        if (active.isEmpty()) return@withContext emptyList()

        val defaultSubId = runCatching {
            SubscriptionManager.getDefaultVoiceSubscriptionId()
        }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)

        active.mapIndexed { position, info ->
            SimSlotInfo(
                slotIndex = slotOf(info, position),
                subscriptionId = info.subscriptionId,
                carrierName = runCatching { info.carrierName?.toString() }.getOrNull(),
                displayName = runCatching { info.displayName?.toString() }.getOrNull(),
                isDefaultVoice = info.subscriptionId == defaultSubId
            )
        }.sortedBy { it.slotIndex }
    }

    override suspend fun send(request: UssdRequest): UssdOutcome {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            return UssdOutcome.Failure(
                message = "Phone permission is required to send USSD. Grant it in Settings.",
                category = UssdFailureCategory.PERMISSION_DENIED
            )
        }

        val telephony = telephonyFor(request)
            ?: return UssdOutcome.Failure(
                message = "The selected SIM is not available.",
                category = UssdFailureCategory.SIM_UNAVAILABLE
            )

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var timeoutJob: Job? = null

                val callback = object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        response: CharSequence?
                    ) {
                        timeoutJob?.cancel()
                        if (continuation.isActive) {
                            continuation.resume(
                                UssdOutcome.Response(response?.toString().orEmpty())
                            )
                        }
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        failureCode: Int
                    ) {
                        timeoutJob?.cancel()
                        if (continuation.isActive) {
                            continuation.resume(failureFor(failureCode))
                        }
                    }
                }

                try {
                    telephony.sendUssdRequest(request.ussd, callback, handler)
                } catch (security: SecurityException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            UssdOutcome.Failure(
                                "The system blocked this USSD request.",
                                UssdFailureCategory.PERMISSION_DENIED
                            )
                        )
                    }
                    return@suspendCancellableCoroutine
                } catch (unsupported: UnsupportedOperationException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            UssdOutcome.Failure(
                                "This device does not support programmatic USSD.",
                                UssdFailureCategory.INTERACTIVE_UNSUPPORTED
                            )
                        )
                    }
                    return@suspendCancellableCoroutine
                } catch (illegal: IllegalArgumentException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            UssdOutcome.Failure(
                                "The USSD request was rejected as invalid.",
                                UssdFailureCategory.INVALID_REQUEST
                            )
                        )
                    }
                    return@suspendCancellableCoroutine
                }

                timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(request.timeoutMs)
                    if (continuation.isActive) {
                        continuation.resume(
                            UssdOutcome.Failure(
                                "No USSD response within ${request.timeoutMs / 1000} seconds.",
                                UssdFailureCategory.TIMEOUT
                            )
                        )
                    }
                }

                continuation.invokeOnCancellation {
                    timeoutJob?.cancel()
                }
            }
        }
    }

    private fun failureFor(code: Int): UssdOutcome = when (code) {
        USSD_ERROR_SERVICE_UNAVAILABLE -> UssdOutcome.Failure(
            message = "USSD service is unavailable right now. Carrier network may be busy.",
            category = UssdFailureCategory.NETWORK_ERROR,
            // The request never reached the application layer, so a retry cannot double-charge.
            retryable = true
        )
        USSD_RETURN_FAILURE -> UssdOutcome.Failure(
            message = "The carrier rejected the USSD request.",
            category = UssdFailureCategory.CARRIER_REJECTED
        )
        else -> UssdOutcome.Failure(
            message = "USSD failed with code $code.",
            category = UssdFailureCategory.UNKNOWN
        )
    }

    private fun telephonyFor(request: UssdRequest): TelephonyManager? {
        val base = context.getSystemService(TelephonyManager::class.java) ?: return null
        val subId = request.subscriptionId ?: return base
        return runCatching { base.createForSubscriptionId(subId) }.getOrNull() ?: base
    }

    private companion object {
        const val USSD_RETURN_FAILURE = -1
        const val USSD_ERROR_SERVICE_UNAVAILABLE = -2
    }

    private fun slotOf(info: SubscriptionInfo, fallback: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { info.simSlotIndex }.getOrDefault(fallback)
        } else {
            fallback
        }

    /**
     * Failure codes delivered by TelephonyManager.UssdResponseCallback.
     * They are documented constants but not part of the public SDK surface on every
     * API level, so they are declared here with their platform values.
     */
    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
