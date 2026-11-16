@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.androidapp.data.FakeTokenRepository
import org.example.androidapp.data.TokenPair
import org.example.androidapp.network.AuthApi
import org.example.androidapp.network.MeApi
import org.example.dto.RoleDto
import org.example.dto.UserProfileDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val meApi: MeApi = mockk()
    private val authApi: AuthApi = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun profile(languages: List<String> = emptyList()) =
        UserProfileDto(1, "user", null, RoleDto.USER, languages, emptyList(), "DAILY")

    private fun staffProfile(role: RoleDto, languages: List<String> = listOf("ru")) =
        UserProfileDto(1, "staff", null, role, languages, emptyList(), "DAILY")

    private fun makeVm(repo: FakeTokenRepository) = AppViewModel(repo, meApi, authApi)

    @Test
    fun `tokens null at start navigates to Auth`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
    }

    @Test
    fun `tokens saved with empty languages navigates to Onboarding`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(emptyList())

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Onboarding, vm.navTarget.value)
    }

    @Test
    fun `tokens saved with languages navigates to Home`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(listOf("en"))

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)
    }

    @Test
    fun `old user with no language preferences in me() is sent to Onboarding`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(emptyList())

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Onboarding, vm.navTarget.value)
    }

    @Test
    fun `me() network error falls back to Home without crash`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } throws RuntimeException("network error")

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)
    }

    @Test
    fun `silent token rotation does not re-run onboarding check`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc1", "ref1"))
        coEvery { meApi.me() } returns profile(listOf("en"))

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()
        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)

        fakeRepo.save(TokenPair("acc2", "ref2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { meApi.me() }
        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)
    }

    @Test
    fun `ADMIN with languages goes to AdminPanel`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns staffProfile(RoleDto.ADMIN, listOf("ru"))

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.AdminPanel, vm.navTarget.value)
    }

    @Test
    fun `ADMIN with no languages goes to AdminPanel not Onboarding`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns staffProfile(RoleDto.ADMIN, emptyList())

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.AdminPanel, vm.navTarget.value)
    }

    @Test
    fun `MODERATOR with languages goes to AdminPanel`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns staffProfile(RoleDto.MODERATOR, listOf("en"))

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.AdminPanel, vm.navTarget.value)
    }

    @Test
    fun `tokens cleared navigates back to Auth`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(emptyList())

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()
        assertEquals(AppViewModel.NavTarget.Onboarding, vm.navTarget.value)

        fakeRepo.clear()
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
    }

    @Test
    fun `logout clears tokens and navigates to Auth`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(listOf("en"))
        coEvery { authApi.logout(any()) } just Runs

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()
        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)

        vm.logout()
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
        assertNull(fakeRepo.loadOnce())
    }

    @Test
    fun `logout still clears tokens when authApi throws`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(listOf("en"))
        coEvery { authApi.logout(any()) } throws RuntimeException("network")

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()

        vm.logout()
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
        assertNull(fakeRepo.loadOnce())
    }

    @Test
    fun `refreshProfile updates profile flow`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returnsMany listOf(
            profile(emptyList()),
            profile(listOf("en", "ru")),
        )

        val vm = makeVm(fakeRepo)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.profile.value?.languages)

        vm.refreshProfile()
        advanceUntilIdle()

        assertEquals(listOf("en", "ru"), vm.profile.value?.languages)
    }
}
