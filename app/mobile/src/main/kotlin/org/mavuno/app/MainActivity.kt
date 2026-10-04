package org.mavuno.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.mavuno.app.ui.ConsentScreen
import org.mavuno.app.ui.HistoryScreen
import org.mavuno.app.ui.HomeScreen
import org.mavuno.app.ui.InterviewScreen
import org.mavuno.app.ui.LanguageScreen
import org.mavuno.app.ui.LocalLanguage
import org.mavuno.app.ui.LocalPack
import org.mavuno.app.ui.MavunoTheme
import org.mavuno.app.ui.PhotoScreen
import org.mavuno.app.ui.RecordScreen
import org.mavuno.app.ui.ResultScreen
import org.mavuno.app.ui.SettingsScreen
import org.mavuno.app.ui.SetupScreen
import org.mavuno.fusion.FeatureCatalog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = services
        setContent {
            var language by remember { mutableStateOf(services.prefs.language) }
            val active = language ?: services.availableLanguages.firstOrNull { it == "sw" } ?: services.availableLanguages.first()
            MavunoTheme {
                CompositionLocalProvider(LocalPack provides services.pack(active), LocalLanguage provides active) {
                    val nav = rememberNavController()
                    val start = when {
                        language == null -> "language"
                        !services.prefs.isSetUp -> "setup"
                        else -> "home"
                    }
                    val questions = FeatureCatalog.questions.size
                    NavHost(navController = nav, startDestination = start) {
                        composable("language") {
                            LanguageScreen { picked ->
                                services.prefs.language = picked
                                language = picked
                                nav.navigate(if (services.prefs.isSetUp) "home" else "setup") { popUpTo("language") { inclusive = true } }
                            }
                        }
                        composable("setup") {
                            SetupScreen { nav.navigate("home") { popUpTo("setup") { inclusive = true } } }
                        }
                        composable("home") {
                            HomeScreen(
                                onCheck = { services.newSession(); nav.navigate("photos") },
                                onHistory = { nav.navigate("history") },
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("photos") { PhotoScreen { nav.navigate("interview/0") } }
                        composable("interview/{i}") { entry ->
                            val i = entry.arguments?.getString("i")?.toIntOrNull() ?: 0
                            InterviewScreen(i) { nav.navigate(if (i + 1 < questions) "interview/${i + 1}" else "result") }
                        }
                        composable("result") { ResultScreen { nav.navigate("consent") } }
                        composable("consent") {
                            ConsentScreen { nav.navigate("home") { popUpTo("home") { inclusive = true } } }
                        }
                        composable("history") { HistoryScreen { id -> nav.navigate("record/$id") } }
                        composable("record/{id}") { entry -> RecordScreen(entry.arguments?.getString("id").orEmpty()) }
                        composable("settings") {
                            SettingsScreen(
                                onLanguage = { picked -> services.prefs.language = picked; language = picked },
                                onDeleted = {
                                    language = null
                                    nav.navigate("language") { popUpTo(nav.graph.id) { inclusive = true } }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
