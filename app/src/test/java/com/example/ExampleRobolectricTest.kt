package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BatchGenerationConfig
import com.example.data.model.CodeCharType
import com.example.data.network.MikrotikRepository
import com.example.data.network.NetworkUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NetManager", appName)
    }

    @Test
    fun `verify batch voucher generation produces unique cards`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setProjectId("demo-project")
                .setApplicationId("1:1234567890:android:1234567890")
                .setApiKey("fake-api-key")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val database = com.example.data.local.AppDatabase.getInstance(context)
        val repository = MikrotikRepository(db)
        val config = BatchGenerationConfig(
            count = 15,
            profile = "TestProfile",
            prefix = "VIP-",
            codeLength = 6,
            characterSet = CodeCharType.NUMBERS_ONLY,
            isSingleCode = true,
            timeLimit = "1d",
            dataLimitMB = 1000L,
            price = "500 ر.ي",
            batchComment = "Unit Test Batch"
        )
        val result = repository.generateBatch(config, database.appDao())
        assertTrue(result.isSuccess)
        val generated = result.getOrNull()?.successfulCards.orEmpty()
        assertEquals(15, generated.size)
        // Check prefix and length
        generated.forEach { card ->
            assertTrue(card.name.startsWith("VIP-"))
            assertEquals(10, card.name.length) // "VIP-" (4) + 6 digits = 10
            assertEquals("", card.password) // Single code mode with empty password
        }
    }

    @Test
    fun `verify bytes formatting`() {
        assertEquals("0 B", NetworkUtils.formatBytes(0))
        assertEquals("500 KB", NetworkUtils.formatBytes(500 * 1024L))
        assertEquals("100.0 MB", NetworkUtils.formatBytes(100 * 1024 * 1024L))
        assertEquals("2.50 GB", NetworkUtils.formatBytes((2.5 * 1024 * 1024 * 1024L).toLong()))
    }
}
