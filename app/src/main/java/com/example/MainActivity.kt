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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.model.ConnectionState
import com.example.data.network.MikrotikRepository
import com.example.ui.screens.ConnectRouterScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = MikrotikRepository()
        MainViewModelFactory(repository, database)
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
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    when (connectionState) {
        is ConnectionState.Connected -> {
            MainScreen(viewModel = viewModel)
        }
        else -> {
            ConnectRouterScreen(viewModel = viewModel)
        }
    }
}
