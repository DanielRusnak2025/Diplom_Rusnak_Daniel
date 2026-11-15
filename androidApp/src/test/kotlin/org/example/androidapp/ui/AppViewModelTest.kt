@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.androidapp.data.FakeTokenRepository
import org.example.androidapp.data.TokenPair
import org.example.androidapp.network.MeApi
import org.example.dto.RoleDto
import org.example.dto.UserProfileDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val meApi: MeApi = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun profile(languages: List<String> = emptyList()) =
        UserProfileDto(1, "user", null, RoleDto.USER, languages, emptyList(), "DAILY")

    @Test
    fun `tokens null at start navigates to Auth`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()  // starts with null

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
    }

    @Test
    fun `tokens saved with empty languages navigates to Onboarding`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(emptyList())

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Onboarding, vm.navTarget.value)
    }

    @Test
    fun `tokens saved with languages navigates to Home`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(listOf("en"))

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)
    }

    @Test
    fun `silent token rotation does not re-run onboarding check`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc1", "ref1"))
        coEvery { meApi.me() } returns profile(listOf("en"))

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()
        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)

        fakeRepo.save(TokenPair("acc2", "ref2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { meApi.me() }
        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)
    }

    @Test
    fun `tokens cleared navigates back to Auth`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.me() } returns profile(emptyList())

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()
        assertEquals(AppViewModel.NavTarget.Onboarding, vm.navTarget.value)

        fakeRepo.clear()
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
    }
}
