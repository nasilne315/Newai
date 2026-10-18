package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiKeyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiKeyFailoverTest {

    @Test
    fun testThreeKeyFailoverCycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ApiKeyManager(context)

        // Save 3 test keys
        manager.saveKeys("AIzaSyKeyOneSample12345", "AIzaSyKeyTwoSample12345", "AIzaSyKeyThreeSample12345")
        val keys = manager.getValidKeysWithIndices()
        assertEquals(3, keys.size)

        // Initially active is key 1 (index 0)
        val initialKey = manager.getActiveKey()
        assertNotNull(initialKey)
        assertEquals(0, initialKey?.first)

        // Key 1 fails -> switch to Key 2 (index 1)
        val next1 = manager.switchToNextKey(0, "Quota exceeded 429")
        assertNotNull(next1)
        assertEquals(1, next1?.first)

        // Key 2 fails -> switch to Key 3 (index 2)
        val next2 = manager.switchToNextKey(1, "Quota exceeded 429")
        assertNotNull(next2)
        assertEquals(2, next2?.first)

        // Key 3 fails -> cycles back to Key 1 (index 0)
        val next3 = manager.switchToNextKey(2, "Quota exceeded 429")
        assertNotNull(next3)
        assertEquals(0, next3?.first)
    }

    @Test
    fun testModelSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ApiKeyManager(context)
        manager.setSelectedModel("gemini-1.5-flash")
        assertEquals("gemini-1.5-flash", manager.status.value.selectedModel)
    }
}
