package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.model.ConnectionState
import com.example.data.network.MikrotikRepository
import com.example.ui.screens.ConnectRouterScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.example.ui.screens.AuthScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        // Failsafe: Ensure Firebase is initialized if Application.onCreate was not called yet
        try {
            android.util.Log.d("MainActivity", "Checking for default FirebaseApp instance")
            com.google.firebase.FirebaseApp.getInstance()
            android.util.Log.d("MainActivity", "Default FirebaseApp already initialized")
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Default FirebaseApp NOT initialized, attempting failsafe init with options: ${e.message}")
            try {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey("AIzaSyD2pA0c1Ec9BgwYTlkEzJTwJ2uzJPN977E")
                    .setApplicationId("1:119789279212:android:9954c81af58740818b0c4e")
                    .setProjectId("netmanager25")
                    .build()
                com.google.firebase.FirebaseApp.initializeApp(applicationContext, options)
                android.util.Log.i("MainActivity", "Firebase failsafe initialization complete with options")
            } catch (initEx: Exception) {
                android.util.Log.e("MainActivity", "Firebase failsafe initialization FAILED: ${initEx.message}")
            }
        }
        val db = try {
            FirebaseFirestore.getInstance()
        } catch (dbEx: Exception) {
            android.util.Log.e("MainActivity", "CRITICAL: Could not get Firestore instance: ${dbEx.message}")
            throw dbEx
        }
        val repository = MikrotikRepository(db, database.appDao())
        MainViewModelFactory(repository, database, db, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppContent(viewModel: MainViewModel) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var showAuthScreen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (showAuthScreen) {
        AuthScreen(
            onAuthSuccess = {
                showAuthScreen = false
                currentUser = Firebase.auth.currentUser
            },
            onDismiss = { showAuthScreen = false }
        )
    } else {
        AppNavigation(
            viewModel = viewModel,
            onTriggerSignIn = { showAuthScreen = true }
        )
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    onTriggerSignIn: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    when (connectionState) {
        is ConnectionState.Connected -> {
            MainScreen(
                viewModel = viewModel,
                onTriggerSignIn = onTriggerSignIn
            )
        }
        else -> {
            ConnectRouterScreen(viewModel = viewModel)
        }
    }
}
