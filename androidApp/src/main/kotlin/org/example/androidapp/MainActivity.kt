package org.example.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.example.androidapp.ui.AppNavGraph
import org.example.androidapp.ui.AppViewModel
import org.example.androidapp.ui.theme.NewsDoveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NewsDoveApp
        val factory = viewModelFactory {
            initializer {
                this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                AppViewModel(app.container.tokenRepository, app.container.meApi)
            }
        }
        val appViewModel = ViewModelProvider(this, factory)[AppViewModel::class.java]

        setContent {
            NewsDoveTheme {
                AppNavGraph(appViewModel = appViewModel)
            }
        }
    }
}
