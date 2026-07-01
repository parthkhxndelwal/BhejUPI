package com.example.offlineupi

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.romellfudi.ussdlibrary.USSDApi
import com.romellfudi.ussdlibrary.USSDController

/**
 * Wrapper around VoIpUSSD library that handles USSD sessions in the background
 * via accessibility service + overlay, replacing the system USSD dialog.
 *
 * The overlay (SplashLoadingService) covers the system dialog so the user
 * never sees it — they only interact with the app's own UI.
 */
class USSDManager private constructor(private val appContext: Context) {

    private val ussdApi: USSDApi = USSDController

    companion object {
        @Volatile
        private var instance: USSDManager? = null

        fun getInstance(context: Context): USSDManager {
            return instance ?: synchronized(this) {
                instance ?: USSDManager(context.applicationContext)
            }
        }
    }

    /**
     * Check accessibility service is enabled — if not, open settings.
     */
    fun requireAccessibilityAccess(activity: Activity) {
        ussdApi.verifyAccessibilityAccess(activity)
    }

    /**
     * Check overlay permission — if not granted, open settings.
     */
    fun requireOverlayPermission(activity: Activity) {
        ussdApi.verifyOverLay(activity)
    }

    /**
     * Dial a USSD code and handle the session with the overlay hiding system dialogs.
     *
     * @param ussdCode  The full USSD code (e.g. "*99*1*1*9876543210*500*1#")
     * @param callback  Callback for USSD responses
     */
    fun callUSSD(
        ussdCode: String,
        callback: USSDCallback
    ) {
        val keywordMap = buildKeywordMap()

        ussdApi.callUSSDOverlayInvoke(
            appContext,
            ussdCode,
            keywordMap,
            object : USSDController.CallbackInvoke {
                override fun responseInvoke(message: String) {
                    callback.onUssdResponse(message) { reply ->
                        sendToSession(reply, callback)
                    }
                }

                override fun over(message: String) {
                    callback.onUssdComplete(message)
                }
            }
        )
    }

    /**
     * Send a reply back to the active USSD session and chain for further steps.
     */
    private fun sendToSession(
        data: String,
        callback: USSDCallback
    ) {
        ussdApi.send(data) { responseMessage ->
            callback.onUssdResponse(responseMessage) { reply ->
                sendToSession(reply, callback)
            }
        }
    }

    /**
     * Build keyword map for detecting USSD states.
     * KEY_LOGIN = processing/loading messages
     * KEY_ERROR = failure messages
     */
    private fun buildKeywordMap(): HashMap<String, List<String>> {
        return hashMapOf(
            "KEY_LOGIN" to listOf(
                "espere", "waiting", "loading", "esperando",
                "please wait", "processing", "connecting",
                "please hold", "one moment"
            ),
            "KEY_ERROR" to listOf(
                "problema", "problem", "error", "null",
                "failed", "invalid", "sorry", "rejected",
                "declined", "not registered", "incorrect", "expired",
                "unable", "cancelled", "canceled", "timeout"
            )
        )
    }

    fun dispose() {
        instance = null
    }

    interface USSDCallback {
        /**
         * Called when the USSD responds.
         * @param message The USSD response text
         * @param reply   Function to send a reply back. null if session is ending.
         */
        fun onUssdResponse(message: String, reply: ((String) -> Unit)?)

        /**
         * Called when the USSD session completes.
         */
        fun onUssdComplete(finalMessage: String)

        /**
         * Called on error.
         */
        fun onError(error: String)
    }
}
