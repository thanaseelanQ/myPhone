package com.drosocode.myphone.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.widget.Toast
import android.os.Bundle

object CallUtils {
    fun makeCall(context: Context, number: String) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.parse("tel:${Uri.encode(number)}")
        
        if (telecomManager.defaultDialerPackage == context.packageName) {
            try {
                // If we are default dialer, place the call directly.
                // This ensures the system uses our InCallService immediately.
                val extras = Bundle()
                telecomManager.placeCall(uri, extras)
            } catch (e: SecurityException) {
                // Fallback if permission is somehow lost
                triggerSystemDialer(context, number)
            } catch (e: Exception) {
                triggerSystemDialer(context, number)
            }
        } else {
            // If not default, we MUST use ACTION_CALL or ACTION_DIAL
            triggerSystemDialer(context, number)
            Toast.makeText(context, "Please set this app as default phone handler", Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerSystemDialer(context: Context, number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
