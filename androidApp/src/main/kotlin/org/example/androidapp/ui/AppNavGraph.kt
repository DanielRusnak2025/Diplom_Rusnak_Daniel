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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.example.androidapp.NewsDoveApp
import org.example.androidapp.ui.admin.AdminArticleDetailScreen
import org.example.androidapp.ui.admin.AdminPanelScreen
import org.example.androidapp.ui.admin.AdminPanelViewModel
import org.example.androidapp.ui.admin.AdminUserDetailScreen
import org.example.androidapp.ui.admin.ArticleDetailViewModel
import org.example.androidapp.ui.admin.ArticlesViewModel
import org.example.androidapp.ui.admin.UserDetailViewModel
import org.example.androidapp.ui.admin.UsersViewModel
import org.example.androidapp.ui.auth.LoginScreen
import org.example.androidapp.ui.auth.LoginViewModel
import org.example.androidapp.ui.auth.RegisterScreen
import org.example.androidapp.ui.auth.RegisterViewModel
import org.example.androidapp.ui.feed.FeedScreen
import org.example.androidapp.ui.feed.FeedViewModel
import org.example.androidapp.ui.admin.ComplaintsViewModel
import org.example.androidapp.ui.onboarding.OnboardingInterestsScreen
import org.example.androidapp.ui.onboarding.OnboardingLanguagesScreen
import org.example.androidapp.ui.onboarding.OnboardingRegionsScreen
import org.example.androidapp.ui.onboarding.OnboardingViewModel
import org.example.androidapp.ui.settings.SettingsScreen
import org.example.androidapp.ui.settings.SettingsViewModel
import org.example.dto.RoleDto

@Composable
fun AppNavGraph(appViewModel: AppViewModel) {
    val navTarget by appViewModel.navTarget.collectAsStateWithLifecycle()
    val profile by appViewModel.profile.collectAsStateWithLifecycle()
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
            AppViewModel.NavTarget.AdminPanel -> navController.navigate("admin") {
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
                        appViewModel.refreshProfile()
                        val adminInStack = navController.currentBackStack.value.any {
                            it.destination.route == "admin"
                        }
                        if (adminInStack) {
                            // Admin initiated onboarding: go to home but keep admin in back stack
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        } else {
                            navController.navigate("home") { popUpTo(0) { inclusive = true } }
                        }
                    },
                )
            }
        }

        composable("home") {
            val isMod = profile?.role == RoleDto.MODERATOR || profile?.role == RoleDto.ADMIN
            val feedVm: FeedViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    FeedViewModel(app.container.feedApi, app.container.authApi, app.container.tokenRepository)
                }
            })
            val feedNeedsRefresh by it.savedStateHandle
                .getStateFlow("feedNeedsRefresh", false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(feedNeedsRefresh) {
                if (feedNeedsRefresh) {
                    feedVm.refresh()
                    it.savedStateHandle["feedNeedsRefresh"] = false
                }
            }
            FeedScreen(
                viewModel = feedVm,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPanel = if (isMod) {
                    { navController.navigate("admin") { popUpTo(0) { inclusive = true } } }
                } else null,
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

        composable("admin") { backStackEntry ->
            val role = profile?.role ?: return@composable
            val panelVm: AdminPanelViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    AdminPanelViewModel(app.container.adminApi)
                }
            })
            val complaintsVm: ComplaintsViewModel = viewModel(
                key = "admin-complaints",
                factory = viewModelFactory {
                    initializer {
                        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                        ComplaintsViewModel(app.container.adminApi)
                    }
                },
            )
            val articlesVm: ArticlesViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    ArticlesViewModel(app.container.adminApi, app.container.metaApi)
                }
            })
            val usersVm: UsersViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    UsersViewModel(app.container.adminApi)
                }
            })

            val articleHiddenId by backStackEntry.savedStateHandle
                .getStateFlow("articleHiddenId", -1)
                .collectAsStateWithLifecycle()
            val articleHiddenValue by backStackEntry.savedStateHandle
                .getStateFlow("articleHiddenValue", false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(articleHiddenId) {
                if (articleHiddenId != -1) {
                    articlesVm.updateArticle(articleHiddenId, articleHiddenValue)
                    backStackEntry.savedStateHandle["articleHiddenId"] = -1
                }
            }

            val userRoleChangedId by backStackEntry.savedStateHandle
                .getStateFlow("userRoleChangedId", -1)
                .collectAsStateWithLifecycle()
            LaunchedEffect(userRoleChangedId) {
                if (userRoleChangedId != -1) {
                    backStackEntry.savedStateHandle["userRoleChangedId"] = -1
                }
            }

            AdminPanelScreen(
                role = role,
                panelVm = panelVm,
                complaintsVm = complaintsVm,
                articlesVm = articlesVm,
                usersVm = usersVm,
                onNavigateToFeed = {
                    if (profile?.languages.isNullOrEmpty()) {
                        navController.navigate("onboarding")
                    } else {
                        navController.navigate("home")
                    }
                },
                onLogout = { appViewModel.logout() },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToArticle = { id -> navController.navigate("admin/article/$id") },
                onNavigateToUser = { id -> navController.navigate("admin/user/$id") },
            )
        }

        composable(
            route = "admin/article/{articleId}",
            arguments = listOf(navArgument("articleId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getInt("articleId") ?: return@composable
            val detailVm: ArticleDetailViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    ArticleDetailViewModel(app.container.adminApi, app.container.metaApi, articleId)
                }
            })
            AdminArticleDetailScreen(
                viewModel = detailVm,
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "admin/user/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: return@composable
            val userDetailVm: UserDetailViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsDoveApp
                    UserDetailViewModel(app.container.adminApi, app.container.metaApi, userId)
                }
            })
            AdminUserDetailScreen(
                viewModel = userDetailVm,
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
