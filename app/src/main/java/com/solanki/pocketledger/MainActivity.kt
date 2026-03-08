package com.solanki.pocketledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.solanki.pocketledger.ui.HomeScreen
import com.solanki.pocketledger.ui.PersonDetailScreen
import com.solanki.pocketledger.ui.theme.PocketLedgerTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import com.solanki.pocketledger.ui.theme.SettingsDataStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            val context = applicationContext
            val settingsDataStore = remember { SettingsDataStore(context) }

            val darkTheme by settingsDataStore
                .darkThemeFlow
                .collectAsState(initial = false)

            val scope = rememberCoroutineScope()

            PocketLedgerTheme(
                darkTheme = darkTheme,
                dynamicColor = true
            ) {

                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {

                    composable("home") {
                        HomeScreen(
                            onPersonClick = { name ->
                                navController.navigate("person/$name")
                            },
                            onToggleTheme = { isDark ->
                                scope.launch {
                                    settingsDataStore.setDarkTheme(isDark)
                                }
                            },
                            isDarkTheme = darkTheme
                        )
                    }

                    composable("person/{name}") { backStackEntry ->
                        val name = backStackEntry.arguments?.getString("name") ?: ""
                        PersonDetailScreen(
                            personName = name,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
