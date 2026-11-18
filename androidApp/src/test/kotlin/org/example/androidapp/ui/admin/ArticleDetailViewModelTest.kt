@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.admin

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.androidapp.network.AdminApi
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.MetaApi
import org.example.dto.AdminArticleDetailDto
import org.example.dto.AdminUserRefDto
import org.example.dto.TagRefDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArticleDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val adminApi: AdminApi = mockk()
    private val metaApi: MetaApi = mockk()

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { metaApi.languages() } returns emptyList()
        coEvery { metaApi.regions() } returns emptyList()
        coEvery { metaApi.languageNames(any()) } returns emptyMap()
    }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun detail(hidden: Boolean = false) = AdminArticleDetailDto(
        id = 42,
        title = "Test Article",
        link = "https://example.com/42",
        publisher = "Publisher",
        language = "en",
        region = "US",
        publicationDate = "2026-01-01",
        hidden = hidden,
        classified = true,
        tags = emptyList<TagRefDto>(),
        likeCount = 5,
        dislikeCount = 1,
        complaintCount = 2,
        complainants = emptyList<AdminUserRefDto>(),
    )

    @Test
    fun `loadDetail success populates article`() = runTest(testDispatcher) {
        coEvery { adminApi.getArticleDetail(42) } returns detail()

        val vm = ArticleDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        val s = vm.state.value
        assertNotNull(s.article)
        assertEquals("Test Article", s.article!!.title)
        assertNull(s.error)
    }

    @Test
    fun `loadDetail 403 shows forbidden error`() = runTest(testDispatcher) {
        coEvery { adminApi.getArticleDetail(42) } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = ArticleDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.article)
        assertNotNull(s.error)
        assertTrue(s.error!!.contains("denied"))
    }

    @Test
    fun `loadDetail network error shows generic error`() = runTest(testDispatcher) {
        coEvery { adminApi.getArticleDetail(42) } throws RuntimeException("timeout")

        val vm = ArticleDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `toggleHidden hides visible article and sets hiddenChanged`() = runTest(testDispatcher) {
        coEvery { adminApi.getArticleDetail(42) } returns detail(hidden = false)
        coJustRun { adminApi.hideArticle(42, true) }

        val vm = ArticleDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        vm.toggleHidden()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(true, s.article?.hidden)
        assertEquals(true, s.hiddenChanged)
        assertNotNull(s.snackbar)
    }

    @Test
    fun `toggleHidden unhides hidden article and sets hiddenChanged`() = runTest(testDispatcher) {
        coEvery { adminApi.getArticleDetail(42) } returns detail(hidden = true)
        coJustRun { adminApi.hideArticle(42, false) }

        val vm = ArticleDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        vm.toggleHidden()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.article?.hidden)
        assertEquals(false, s.hiddenChanged)
    }

    @Test
    fun `toggleHidden 403 shows forbidden error in snackbar`() = runTest(testDispatcher) {
        coEvery { adminApi.getArticleDetail(42) } returns detail(hidden = false)
        coEvery { adminApi.hideArticle(42, true) } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = ArticleDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        vm.toggleHidden()
        advanceUntilIdle()

        val s = vm.state.value
        // Article hidden state unchanged
        assertEquals(false, s.article?.hidden)
        assertNull(s.hiddenChanged)
        assertNotNull(s.snackbar)
        assertTrue(s.snackbar!!.contains("denied"))
    }

    @Test
    fun `snackbarShown clears snackbar`() = runTest(testDispatcher) {
        coEvery { adminApi.getArticleDetail(42) } returns detail()
        coJustRun { adminApi.hideArticle(42, true) }

        val vm = ArticleDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()
        vm.toggleHidden()
        advanceUntilIdle()

        assertNotNull(vm.state.value.snackbar)

        vm.snackbarShown()

        assertNull(vm.state.value.snackbar)
    }
}
