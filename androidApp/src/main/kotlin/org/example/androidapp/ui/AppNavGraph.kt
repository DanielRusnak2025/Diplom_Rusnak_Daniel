package org.example.androidapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.example.androidapp.NewsDoveApp
import org.example.androidapp.ui.auth.LoginScreen
import org.example.androidapp.ui.auth.LoginViewModel
import org.example.androidapp.ui.auth.RegisterScreen
import org.example.androidapp.ui.auth.RegisterViewModel
import org.example.androidapp.ui.feed.FeedScreen
import org.example.androidapp.ui.feed.FeedViewModel
import org.example.androidapp.ui.onboarding.OnboardingInterestsScreen
import org.example.androidapp.ui.onboarding.OnboardingLanguagesScreen
import org.example.androidapp.ui.onboarding.OnboardingRegionsScreen
import org.example.androidapp.ui.onboarding.OnboardingViewModel
import org.example.androidapp.ui.settings.SettingsViewModel
import org.example.androidapp.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(appViewModel: AppViewModel) {
    val navTarget by appViewModel.navTarget.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(navTarget) {
        when (navTarget) {
            AppViewModel.NavTarget.Auth -> navController.navigate("auth") {
                popUpTo(0) { inclusive = true }
            }
            AppViewModel.NavTarget.Onboarding -> navController.navigate("onboarding") {
                popUpTo(0) { inclusive = true }
            }
            AppViewModel.NavTarget.Home -> navController.navigate("home") {
                popUpTo(0) { inclusive = true }
            }
            AppViewModel.NavTarget.Loading -> Unit
        }
    }

    NavHost(navController = navController, startDestination = "loading") {
        composable("loading") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        navigation(startDestination = "login", route = "auth") {
            composable("login") {
                val vm: LoginViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                        LoginViewModel(app.container.authApi, app.container.tokenRepository)
                    }
                })
                LoginScreen(
                    viewModel = vm,
                    onNavigateToRegister = { navController.navigate("register") },
                )
            }
            composable("register") {
                val vm: RegisterViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                        RegisterViewModel(app.container.authApi, app.container.tokenRepository)
                    }
                })
                RegisterScreen(
                    viewModel = vm,
                    onNavigateToLogin = { navController.popBackStack() },
                )
            }
        }

        navigation(startDestination = "onboarding/languages", route = "onboarding") {
            composable("onboarding/languages") { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("onboarding") }
                val vm: OnboardingViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = viewModelFactory {
                        initializer {
                            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                            OnboardingViewModel(app.container.metaApi, app.container.meApi)
                        }
                    },
                )
                OnboardingLanguagesScreen(
                    viewModel = vm,
                    onNext = { navController.navigate("onboarding/regions") },
                )
            }
            composable("onboarding/regions") { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("onboarding") }
                val vm: OnboardingViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = viewModelFactory {
                        initializer {
                            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                            OnboardingViewModel(app.container.metaApi, app.container.meApi)
                        }
                    },
                )
                OnboardingRegionsScreen(
                    viewModel = vm,
                    onNext = { navController.navigate("onboarding/interests") },
                )
            }
            composable("onboarding/interests") { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("onboarding") }
                val vm: OnboardingViewModel = viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = viewModelFactory {
                        initializer {
                            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                            OnboardingViewModel(app.container.metaApi, app.container.meApi)
                        }
                    },
                )
                OnboardingInterestsScreen(
                    viewModel = vm,
                    onDone = {
                        navController.navigate("home") { popUpTo(0) { inclusive = true } }
                    },
                )
            }
        }

        composable("home") {
            val feedVm: FeedViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    FeedViewModel(app.container.feedApi, app.container.authApi, app.container.tokenRepository)
                }
            })
            FeedScreen(
                viewModel = feedVm,
                onNavigateToSettings = { navController.navigate("settings") },
            )
        }

        composable("settings") {
            val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    SettingsViewModel(
                        metaApi = app.container.metaApi,
                        meApi = app.container.meApi,
                        authApi = app.container.authApi,
                        tokenRepository = app.container.tokenRepository,
                    )
                }
            })
            SettingsScreen(
                viewModel = settingsVm,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
