package com.familyhub.display

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.familyhub.display.ui.FamilyHubRoot
import com.familyhub.display.ui.theme.FamilyHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as FamilyHubApplication).container

        setContent {
            FamilyHubTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FamilyHubRoot(container = container)
                }
            }
        }
    }
}
