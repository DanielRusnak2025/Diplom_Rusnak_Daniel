@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.auth

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
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.AuthApi
import org.example.dto.TokenPairDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authApi: AuthApi = mockk()
    private val fakeRepo = FakeTokenRepository()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `register success saves tokens`() = runTest(testDispatcher) {
        coEvery { authApi.register(any()) } returns TokenPairDto("acc", "ref", 900L)

        val vm = RegisterViewModel(authApi, fakeRepo)
        vm.onLoginChange("newuser")
        vm.onPasswordChange("Pass1!")
        vm.register()
        advanceUntilIdle()

        assertEquals(TokenPair("acc", "ref"), fakeRepo.loadOnce())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `register NICK_TAKEN shows Russian error`() = runTest(testDispatcher) {
        coEvery { authApi.register(any()) } throws ApiException("NICK_TAKEN", "nick taken", 409)

        val vm = RegisterViewModel(authApi, fakeRepo)
        vm.onLoginChange("taken")
        vm.onPasswordChange("Pass1!")
        vm.register()
        advanceUntilIdle()

        assertEquals("Этот логин уже занят", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertNull(fakeRepo.loadOnce())
    }

    @Test
    fun `register INVALID_INPUT shows Russian error`() = runTest(testDispatcher) {
        coEvery { authApi.register(any()) } throws ApiException("INVALID_INPUT", "bad input", 422)

        val vm = RegisterViewModel(authApi, fakeRepo)
        vm.register()
        advanceUntilIdle()

        assertEquals("Проверьте введённые данные", vm.state.value.error)
    }

    @Test
    fun `register network error shows network error message`() = runTest(testDispatcher) {
        coEvery { authApi.register(any()) } throws java.io.IOException("No connection")

        val vm = RegisterViewModel(authApi, fakeRepo)
        vm.register()
        advanceUntilIdle()

        assertEquals("Ошибка сети. Проверьте подключение", vm.state.value.error)
    }
}
