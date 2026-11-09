@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui

import io.mockk.coEvery
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
import org.example.dto.InterestDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val meApi: MeApi = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `tokens null at start navigates to Auth`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()  // starts with null
        coEvery { meApi.interests() } returns emptyList()

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
    }

    @Test
    fun `tokens saved with no interests navigates to Onboarding`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.interests() } returns emptyList()

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Onboarding, vm.navTarget.value)
    }

    @Test
    fun `tokens saved with interests navigates to Home`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.interests() } returns listOf(InterestDto(1, "tech", "Tech", 60))

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Home, vm.navTarget.value)
    }

    @Test
    fun `tokens cleared with no logoutEvents collector still navigates to Auth`() = runTest(testDispatcher) {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { meApi.interests() } returns emptyList()

        val vm = AppViewModel(fakeRepo, meApi)
        advanceUntilIdle()
        assertEquals(AppViewModel.NavTarget.Onboarding, vm.navTarget.value)

        // No collector subscribed to logoutEvents — navigation must still go to Auth via state
        fakeRepo.clear()
        advanceUntilIdle()

        assertEquals(AppViewModel.NavTarget.Auth, vm.navTarget.value)
    }
}
