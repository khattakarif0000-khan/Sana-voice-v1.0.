package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.SanaScreen
import com.example.ui.theme.SanaBlack
import com.example.ui.theme.SanaTheme
import com.example.viewmodel.SanaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SanaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SanaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SanaBlack
                ) {
                    SanaScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateDiagnostics()
    }
}
