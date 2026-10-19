package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseHelper {
    private const val TAG = "FirebaseHelper"

    fun ensureInitialized(context: Context) {
        try {
            val appContext = context.applicationContext ?: context
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                val app = FirebaseApp.initializeApp(appContext)
                if (app == null) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:421580678905:android:bad3dc568a86568f8b6428")
                        .setApiKey("AIzaSyBCL16msxF4QY_9tGmOAPCpZqrOfGM5JjE")
                        .setProjectId("werostudios")
                        .setStorageBucket("werostudios.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(appContext, options)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing FirebaseApp", e)
        }
    }

    fun getFirestore(context: Context): FirebaseFirestore? {
        return try {
            ensureInitialized(context)
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get Firestore instance", e)
            null
        }
    }
}
