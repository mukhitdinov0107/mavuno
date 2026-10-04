package org.mavuno.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
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
            val active = language ?: services.languagesInOrder.first()
            MavunoTheme {
                CompositionLocalProvider(LocalPack provides services.pack(active), LocalLanguage provides active) {
                    val nav = rememberNavController()
                    val start = when {
                        language == null -> "language"
                        !services.prefs.isSetUp -> "setup"
                        else -> "home"
                    }
                    val questions = FeatureCatalog.questions.size
                    fun NavHostController.goHome() = navigate("home") { popUpTo("home") { inclusive = true } }
                    fun startCheck() { services.newSession(); nav.navigate("photos") }

                    NavHost(
                        navController = nav,
                        startDestination = start,
                        // Forward moves slide in from the right; back moves slide the other way.
                        enterTransition = { slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)) },
                        exitTransition = { fadeOut(tween(200)) },
                        popEnterTransition = { slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(320)) },
                        popExitTransition = { slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(260)) },
                    ) {
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
                                onCheck = ::startCheck,
                                onExample = { services.newExampleSession(); nav.navigate("result") },
                                onHistory = { nav.navigate("history") },
                                onSettings = { nav.navigate("settings") },
                                onOpenRecord = { id -> nav.navigate("record/$id") },
                            )
                        }
                        composable("photos") { PhotoScreen { nav.navigate("interview/0") } }
                        composable("interview/{i}") { entry ->
                            val i = entry.arguments?.getString("i")?.toIntOrNull() ?: 0
                            InterviewScreen(i) { nav.navigate(if (i + 1 < questions) "interview/${i + 1}" else "result") }
                        }
                        composable("result") {
                            ResultScreen(
                                onContinue = { nav.navigate("consent") },
                                onStartReal = { nav.goHome(); startCheck() },
                                onHome = { nav.goHome() },
                            )
                        }
                        composable("consent") { ConsentScreen { nav.goHome() } }
                        composable("history") { HistoryScreen(onOpen = { id -> nav.navigate("record/$id") }, onCheck = ::startCheck) }
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
