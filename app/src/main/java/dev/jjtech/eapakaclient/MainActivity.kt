package dev.jjtech.eapakaclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.jjtech.eapakaclient.server.APIServer
import dev.jjtech.eapakaclient.server.StatusDisplay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StatusDisplay()
        }
        APIServer(8080).start()
    }
}