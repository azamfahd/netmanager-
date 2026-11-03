package com.example

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreRuleTest {

    private lateinit var db: FirebaseFirestore

    @Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setProjectId("demo-project")
                .setApplicationId("1:1234567890:android:1234567890")
                .setApiKey("fake-api-key")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
        
        // Initialize Firestore with emulator settings
        db = FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance())
        val settings = FirebaseFirestoreSettings.Builder()
            .setHost("127.0.0.1:8085")
            .setSslEnabled(false)
            .setPersistenceEnabled(false)
            .build()
        db.firestoreSettings = settings
        
        // Use Auth emulator if needed
        Firebase.auth.useEmulator("127.0.0.1", 9099)
    }

    private fun isEmulatorAvailable(port: Int): Boolean {
        return try {
            java.net.Socket("127.0.0.1", port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun `test writing a router document`() = runBlocking {
        org.junit.Assume.assumeTrue(
            "Firebase Auth & Firestore emulators are not running locally on ports 8085/9099",
            isEmulatorAvailable(8085) && isEmulatorAvailable(9099)
        )
        // Mock sign in (Robolectric Auth Emulator bypass)
        // In Robolectric, we can't easily use CredentialManager, 
        // so we use email/pass against the emulator for rules testing
        try {
            Firebase.auth.createUserWithEmailAndPassword("test@example.com", "password123").await()
        } catch (e: Exception) {
            Firebase.auth.signInWithEmailAndPassword("test@example.com", "password123").await()
        }
        
        val uid = Firebase.auth.currentUser?.uid
        assertNotNull("User ID should not be null", uid)

        val routerData = mapOf(
            "id" to "r1",
            "name" to "Test Router",
            "ipAddress" to "192.168.88.1"
        )

        db.collection("users").document(uid!!).collection("routers").document("r1")
            .set(routerData)
            .await()

        val doc = db.collection("users").document(uid).collection("routers").document("r1")
            .get()
            .await()

        assertTrue("Document should exist", doc.exists())
        assertTrue("Name should match", doc.getString("name") == "Test Router")
    }
}
