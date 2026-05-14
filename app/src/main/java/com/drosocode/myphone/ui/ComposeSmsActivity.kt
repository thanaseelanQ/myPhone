package com.drosocode.myphone.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.drosocode.myphone.MainActivity

class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentAction = intent.action
        val intentData = intent.data

        var address = ""
        if (intentAction == Intent.ACTION_SENDTO || intentAction == Intent.ACTION_SEND) {
            if (intentData != null && (intentData.scheme == "smsto" || intentData.scheme == "sms")) {
                address = intentData.schemeSpecificPart
            }
        }

        // Redirect to MainActivity which will handle navigation to MessageScreen
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (address.isNotEmpty()) {
                putExtra("OPEN_SMS_THREAD", address)
            }
        }
        startActivity(mainIntent)
        finish()
    }
}
