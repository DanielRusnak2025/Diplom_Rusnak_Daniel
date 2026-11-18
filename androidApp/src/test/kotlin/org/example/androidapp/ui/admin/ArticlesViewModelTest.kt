@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.admin

import io.mockk.coEvery
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
import org.example.dto.AdminArticleDto
import org.example.dto.AdminArticlePageDto
import org.example.dto.RegionDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArticlesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val adminApi: AdminApi = mockk()
    private val metaApi: MetaApi = mockk()

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { metaApi.languageNames(any()) } returns emptyMap()
        coEvery { metaApi.regions() } returns emptyList()
    }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun article(id: Int, hidden: Boolean = false) = AdminArticleDto(
        id = id,
        title = "Article $id",
        link = "https://example.com/$id",
        publisher = "Publisher",
        language = "en",
        region = "US",
        publicationDate = "2026-01-01",
        hidden = hidden,
        classified = true,
    )

    private fun mockSearch(
        q: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        hidden: Boolean? = null,
        cursor: Int? = null,
        result: AdminArticlePageDto,
    ) {
        coEvery {
            adminApi.searchArticles(q = q, fromDate = fromDate, toDate = toDate, hidden = hidden, cursor = cursor, limit = any())
        } returns result
    }

    @Test
    fun `initial load populates items`() = runTest(testDispatcher) {
        mockSearch(result = AdminArticlePageDto(listOf(article(1), article(2)), null))

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(2, s.items.size)
        assertNull(s.error)
        assertTrue(s.isEndOfList)
    }

    @Test
    fun `initial load with empty result shows empty state`() = runTest(testDispatcher) {
        mockSearch(result = AdminArticlePageDto(emptyList(), null))

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.items.isEmpty())
        assertTrue(s.isEndOfList)
        assertNull(s.error)
    }

    @Test
    fun `initial load 403 shows forbidden error`() = runTest(testDispatcher) {
        coEvery {
            adminApi.searchArticles(q = any(), fromDate = any(), toDate = any(), hidden = any(), cursor = any(), limit = any())
        } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.items.isEmpty())
        assertNotNull(s.error)
        assertTrue(s.error!!.contains("denied"))
    }

    @Test
    fun `initial load network error shows generic error`() = runTest(testDispatcher) {
        coEvery {
            adminApi.searchArticles(q = any(), fromDate = any(), toDate = any(), hidden = any(), cursor = any(), limit = any())
        } throws RuntimeException("timeout")

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()

        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun `loadMore appends items`() = runTest(testDispatcher) {
        mockSearch(cursor = null, result = AdminArticlePageDto(listOf(article(10), article(9)), "9"))
        mockSearch(cursor = 9, result = AdminArticlePageDto(listOf(article(8)), null))

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(3, vm.state.value.items.size)
        assertTrue(vm.state.value.isEndOfList)
    }

    @Test
    fun `setHiddenFilter reloads with correct param`() = runTest(testDispatcher) {
        mockSearch(hidden = null, result = AdminArticlePageDto(listOf(article(1)), null))
        mockSearch(hidden = true, result = AdminArticlePageDto(listOf(article(2, hidden = true)), null))

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items[0].id)

        vm.setHiddenFilter(ArticleHiddenFilter.HIDDEN)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.items.size)
        assertEquals(2, s.items[0].id)
        assertEquals(ArticleHiddenFilter.HIDDEN, s.hiddenFilter)
    }

    @Test
    fun `setQuery triggers debounced reload`() = runTest(testDispatcher) {
        mockSearch(q = null, result = AdminArticlePageDto(listOf(article(1)), null))
        mockSearch(q = "news", result = AdminArticlePageDto(listOf(article(5)), null))

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.setQuery("news")
        advanceUntilIdle()

        assertEquals(5, vm.state.value.items[0].id)
        assertEquals("news", vm.state.value.query)
    }

    @Test
    fun `updateArticle changes hidden state in list`() = runTest(testDispatcher) {
        mockSearch(result = AdminArticlePageDto(listOf(article(1, hidden = false), article(2)), null))

        val vm = ArticlesViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.updateArticle(1, true)

        assertEquals(true, vm.state.value.items.find { it.id == 1 }?.hidden)
    }
}
