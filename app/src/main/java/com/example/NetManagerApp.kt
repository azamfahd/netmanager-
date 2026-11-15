package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class NetManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("NetManagerApp", "Initializing Firebase in Application.onCreate")
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
            android.util.Log.d("NetManagerApp", "Firebase auto-initialization complete")
        } catch (e: Exception) {
            android.util.Log.w("NetManagerApp", "Auto-init failed, trying manual init: ${e.message}")
            try {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey("AIzaSyD2pA0c1Ec9BgwYTlkEzJTwJ2uzJPN977E")
                    .setApplicationId("1:119789279212:android:9954c81af58740818b0c4e")
                    .setProjectId("netmanager25")
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(this, options)
                android.util.Log.i("NetManagerApp", "Firebase manual initialization successful")
            } catch (manualEx: Exception) {
                android.util.Log.e("NetManagerApp", "CRITICAL: Firebase manual init failed: ${manualEx.message}")
            }
        }
    }
}
