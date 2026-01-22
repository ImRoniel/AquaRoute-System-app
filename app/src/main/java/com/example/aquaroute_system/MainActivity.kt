package com.example.aquaroute_system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aquaroute_system.View.SplashScreen
import com.example.aquaroute_system.ui.theme.AquaRouteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AquaRouteTheme {
                AquaRouteApp()
            }
        }
    }
}

@Composable
fun AquaRouteApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(navController = navController)
        }
        composable("main") {
            // TODO: Add your main screen composable here
            Text("Main Screen")
        }
    }
}