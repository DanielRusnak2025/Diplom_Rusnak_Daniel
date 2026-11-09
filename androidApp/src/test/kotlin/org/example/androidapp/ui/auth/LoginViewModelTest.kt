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

class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authApi: AuthApi = mockk()
    private val fakeRepo = FakeTokenRepository()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `login success saves tokens and clears loading`() = runTest(testDispatcher) {
        coEvery { authApi.login(any()) } returns TokenPairDto("acc", "ref", 900L)

        val vm = LoginViewModel(authApi, fakeRepo)
        vm.onLoginChange("user")
        vm.onPasswordChange("Pass1!")
        vm.login()
        advanceUntilIdle()

        assertEquals(TokenPair("acc", "ref"), fakeRepo.loadOnce())
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `login INVALID_CREDENTIALS shows Russian error`() = runTest(testDispatcher) {
        coEvery { authApi.login(any()) } throws ApiException("INVALID_CREDENTIALS", "wrong", 401)

        val vm = LoginViewModel(authApi, fakeRepo)
        vm.login()
        advanceUntilIdle()

        assertEquals("Неверный логин или пароль", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertNull(fakeRepo.loadOnce())
    }

    @Test
    fun `login TOO_MANY_ATTEMPTS shows Russian error`() = runTest(testDispatcher) {
        coEvery { authApi.login(any()) } throws ApiException("TOO_MANY_ATTEMPTS", "rate limited", 429)

        val vm = LoginViewModel(authApi, fakeRepo)
        vm.login()
        advanceUntilIdle()

        assertEquals("Слишком много попыток. Повторите позже", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `login network error shows network error message`() = runTest(testDispatcher) {
        coEvery { authApi.login(any()) } throws java.io.IOException("No connection")

        val vm = LoginViewModel(authApi, fakeRepo)
        vm.login()
        advanceUntilIdle()

        assertEquals("Ошибка сети. Проверьте подключение", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `concurrent login calls ignored while loading`() = runTest(testDispatcher) {
        coEvery { authApi.login(any()) } returns TokenPairDto("acc", "ref", 900L)

        val vm = LoginViewModel(authApi, fakeRepo)
        vm.login()
        vm.login()  // second call ignored while first is in progress
        advanceUntilIdle()

        assertEquals(TokenPair("acc", "ref"), fakeRepo.loadOnce())
    }
}
