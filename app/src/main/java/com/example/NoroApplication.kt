package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class NoroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initFirebase()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app == null) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:421580678905:android:bad3dc568a86568f8b6428")
                        .setApiKey("AIzaSyBCL16msxF4QY_9tGmOAPCpZqrOfGM5JjE")
                        .setProjectId("werostudios")
                        .setStorageBucket("werostudios.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
            }
        } catch (e: Throwable) {
            Log.e("NoroApplication", "Failed to initialize FirebaseApp", e)
        }
    }
}
