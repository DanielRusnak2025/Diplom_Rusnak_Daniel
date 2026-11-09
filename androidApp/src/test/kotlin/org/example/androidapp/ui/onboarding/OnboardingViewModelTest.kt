@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.onboarding

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.androidapp.network.MeApi
import org.example.androidapp.network.MetaApi
import org.example.dto.CategoryDto
import org.example.dto.LanguageDto
import org.example.dto.RegionDto
import org.example.dto.SubtopicDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val metaApi: MetaApi = mockk()
    private val meApi: MeApi = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `init loads languages`() = runTest(testDispatcher) {
        val langs = listOf(LanguageDto("en"), LanguageDto("ru"))
        coEvery { metaApi.languages() } returns langs

        val vm = OnboardingViewModel(metaApi, meApi)
        advanceUntilIdle()

        assertEquals(langs, vm.state.value.languages)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `toggleLanguage adds and removes`() = runTest(testDispatcher) {
        coEvery { metaApi.languages() } returns listOf(LanguageDto("en"), LanguageDto("ru"))

        val vm = OnboardingViewModel(metaApi, meApi)
        advanceUntilIdle()

        vm.toggleLanguage("en")
        assertTrue("en" in vm.state.value.selectedLanguages)

        vm.toggleLanguage("en")
        assertFalse("en" in vm.state.value.selectedLanguages)
    }

    @Test
    fun `loadRegions populates regions`() = runTest(testDispatcher) {
        coEvery { metaApi.languages() } returns emptyList()
        val regions = listOf(RegionDto("north_america", "North America"), RegionDto("global", "Global"))
        coEvery { metaApi.regions() } returns regions

        val vm = OnboardingViewModel(metaApi, meApi)
        advanceUntilIdle()

        vm.loadRegions()
        advanceUntilIdle()

        assertEquals(regions, vm.state.value.regions)
    }

    @Test
    fun `complete calls settings and interests and sets isDone`() = runTest(testDispatcher) {
        val langs = listOf(LanguageDto("en"))
        coEvery { metaApi.languages() } returns langs
        coEvery { meApi.updateSettings(any(), any()) } just Runs
        coEvery { meApi.setInterests(any()) } just Runs

        val vm = OnboardingViewModel(metaApi, meApi)
        advanceUntilIdle()

        vm.toggleLanguage("en")
        vm.complete()
        advanceUntilIdle()

        assertTrue(vm.state.value.isDone)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `complete API error shows error message`() = runTest(testDispatcher) {
        coEvery { metaApi.languages() } returns emptyList()
        coEvery { meApi.updateSettings(any(), any()) } throws java.io.IOException("network")
        coEvery { meApi.setInterests(any()) } just Runs

        val vm = OnboardingViewModel(metaApi, meApi)
        advanceUntilIdle()

        vm.complete()
        advanceUntilIdle()

        assertFalse(vm.state.value.isDone)
        assertEquals("Ошибка сохранения настроек", vm.state.value.error)
    }
}
