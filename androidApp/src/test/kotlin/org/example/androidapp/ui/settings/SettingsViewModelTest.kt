@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.settings

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
import org.example.androidapp.data.TokenPair
import org.example.androidapp.data.TokenRepository
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.AuthApi
import org.example.androidapp.network.MeApi
import org.example.androidapp.network.MetaApi
import org.example.dto.CategoryDto
import org.example.dto.InterestDto
import org.example.dto.LanguageDto
import org.example.dto.RegionDto
import org.example.dto.RoleDto
import org.example.dto.UserProfileDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val metaApi: MetaApi = mockk()
    private val meApi: MeApi = mockk()
    private val authApi: AuthApi = mockk()
    private val tokenRepository: TokenRepository = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun profile(langs: List<String> = emptyList(), regions: List<String> = emptyList()) =
        UserProfileDto(1, "user", null, RoleDto.USER, langs, regions, "DAILY")

    private fun makeVm(): SettingsViewModel {
        coEvery { meApi.me() } returns profile(listOf("en"), listOf("global"))
        coEvery { metaApi.languages() } returns listOf(LanguageDto("en"), LanguageDto("ru"))
        coEvery { metaApi.regions() } returns listOf(RegionDto("global", "Global"))
        coEvery { metaApi.categories() } returns listOf(
            CategoryDto(1, "cat:politics", "Politics", emptyList()),
            CategoryDto(2, "cat:sports", "Sports", emptyList()),
        )
        coEvery { meApi.interests() } returns listOf(
            InterestDto(1, "cat:politics", "Politics", 50)
        )
        return SettingsViewModel(metaApi, meApi, authApi, tokenRepository)
    }

    @Test
    fun `loadAll populates state correctly`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNull(s.error)
        assertEquals(2, s.languages.size)
        assertEquals(setOf("en"), s.selectedLanguages)
        assertEquals(setOf("global"), s.selectedRegions)
        assertEquals(2, s.categories.size)
        assertEquals(setOf(1), s.selectedCategories)
    }

    @Test
    fun `toggleLanguage adds and removes`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        vm.toggleLanguage("ru")
        assertTrue("ru" in vm.state.value.selectedLanguages)

        vm.toggleLanguage("ru")
        assertFalse("ru" in vm.state.value.selectedLanguages)
    }

    @Test
    fun `toggleRegion adds and removes`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        vm.toggleRegion("global")
        assertFalse("global" in vm.state.value.selectedRegions)

        vm.toggleRegion("global")
        assertTrue("global" in vm.state.value.selectedRegions)
    }

    @Test
    fun `toggleCategory adds and removes`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        vm.toggleCategory(2)
        assertTrue(2 in vm.state.value.selectedCategories)

        vm.toggleCategory(2)
        assertFalse(2 in vm.state.value.selectedCategories)
    }

    @Test
    fun `saveSettings calls meApi and shows snackbar`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        coEvery { meApi.updateSettings(any(), any()) } just Runs

        vm.saveSettings()
        advanceUntilIdle()

        coVerify { meApi.updateSettings(any(), any()) }
        assertEquals("Настройки сохранены", vm.state.value.snackbar)
    }

    @Test
    fun `saveInterests calls meApi and shows snackbar`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        coEvery { meApi.setInterests(any()) } just Runs

        vm.saveInterests()
        advanceUntilIdle()

        coVerify { meApi.setInterests(any()) }
        assertEquals("Интересы сохранены", vm.state.value.snackbar)
    }

    @Test
    fun `changePassword success shows snackbar and clears fields`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        coEvery { meApi.changePassword(any(), any()) } just Runs

        vm.onCurrentPasswordChange("OldPass1!")
        vm.onNewPasswordChange("NewPass1!")
        vm.changePassword()
        advanceUntilIdle()

        assertNotNull(vm.state.value.snackbar)
        assertTrue(vm.state.value.snackbar!!.contains("Пароль"))
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("", vm.state.value.newPassword)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `changePassword INVALID_PASSWORD shows Russian error`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        coEvery { meApi.changePassword(any(), any()) } throws
            ApiException("INVALID_PASSWORD", "Wrong password", 422)

        vm.onCurrentPasswordChange("WrongPass!")
        vm.onNewPasswordChange("NewPass1!")
        vm.changePassword()
        advanceUntilIdle()

        assertEquals("Неверный текущий пароль", vm.state.value.error)
        assertNull(vm.state.value.snackbar)
    }

    @Test
    fun `changePassword REUSED_PASSWORD shows Russian error`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        coEvery { meApi.changePassword(any(), any()) } throws
            ApiException("REUSED_PASSWORD", "Reused", 422)

        vm.onCurrentPasswordChange("OldPass!")
        vm.onNewPasswordChange("OldPass!")
        vm.changePassword()
        advanceUntilIdle()

        assertEquals("Нельзя повторять один из последних 3 паролей", vm.state.value.error)
    }

    @Test
    fun `logout clears tokens`() = runTest(testDispatcher) {
        val vm = makeVm()
        advanceUntilIdle()

        coEvery { tokenRepository.loadOnce() } returns TokenPair("acc", "ref")
        coEvery { authApi.logout(any()) } just Runs
        coEvery { tokenRepository.clear() } just Runs

        vm.logout()
        advanceUntilIdle()

        coVerify { tokenRepository.clear() }
    }

    @Test
    fun `loadAll error sets error state`() = runTest(testDispatcher) {
        coEvery { meApi.me() } throws java.io.IOException("network")
        coEvery { metaApi.languages() } returns emptyList()
        coEvery { metaApi.regions() } returns emptyList()
        coEvery { metaApi.categories() } returns emptyList()
        coEvery { meApi.interests() } returns emptyList()

        val vm = SettingsViewModel(metaApi, meApi, authApi, tokenRepository)
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }
}
