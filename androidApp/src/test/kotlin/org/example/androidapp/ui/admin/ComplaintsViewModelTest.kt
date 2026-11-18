@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.admin

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
import org.example.androidapp.network.AdminApi
import org.example.androidapp.network.ApiException
import org.example.dto.ComplaintItemDto
import org.example.dto.ComplaintPageDto
import org.example.dto.ReportReasonDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ComplaintsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val adminApi: AdminApi = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun complaint(reportId: Int, articleId: Int = 1) = ComplaintItemDto(
        reportId     = reportId,
        articleId    = articleId,
        articleTitle = "Article $articleId",
        userId       = 10,
        userNick     = "user",
        reasonCode   = ReportReasonDto.OTHER,
        date         = "2026-01-01",
    )

    @Test
    fun `load first page populates items`() = runTest(testDispatcher) {
        val page = ComplaintPageDto(items = listOf(complaint(1), complaint(2)), nextCursor = null)
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } returns page

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.isLoading)
        assertEquals(2, s.items.size)
        assertEquals(true, s.isEndOfList)
        assertNull(s.error)
    }

    @Test
    fun `load empty list shows empty state`() = runTest(testDispatcher) {
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } returns
            ComplaintPageDto(emptyList(), null)

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.isLoading)
        assertTrue(s.items.isEmpty())
        assertTrue(s.isEndOfList)
        assertNull(s.error)
    }

    @Test
    fun `403 on load shows Russian forbidden error`() = runTest(testDispatcher) {
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } throws
            ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.isLoading)
        assertTrue(s.error!!.contains("прав"))
        assertTrue(s.items.isEmpty())
    }

    @Test
    fun `hide article removes all complaints for that article`() = runTest(testDispatcher) {
        val item1 = complaint(1, articleId = 10)
        val item2 = complaint(2, articleId = 10)
        val item3 = complaint(3, articleId = 20)
        val page = ComplaintPageDto(items = listOf(item1, item2, item3), nextCursor = null)
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } returns page
        coEvery { adminApi.hideArticle(10, true) } just Runs

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()

        vm.requestHide(item1)
        vm.confirmHide()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.items.size)
        assertEquals(item3, s.items.first())
        assertEquals("Статья скрыта", s.snackbar)
        assertEquals(1, s.hidCount)
        coVerify(exactly = 1) { adminApi.hideArticle(10, true) }
    }

    @Test
    fun `dismiss complaint removes only that one item`() = runTest(testDispatcher) {
        val item1 = complaint(1, articleId = 10)
        val item2 = complaint(2, articleId = 10)
        val item3 = complaint(3, articleId = 20)
        val page = ComplaintPageDto(items = listOf(item1, item2, item3), nextCursor = null)
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } returns page
        coEvery { adminApi.dismissComplaint(1) } just Runs

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()

        vm.requestDismiss(item1)
        vm.executeDismiss()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(2, s.items.size)
        assertTrue(s.items.none { it.reportId == 1 })
        assertTrue(s.items.any { it.reportId == 2 })
        assertEquals("Жалоба отклонена", s.snackbar)
        coVerify(exactly = 1) { adminApi.dismissComplaint(1) }
    }

    @Test
    fun `403 on hide shows Russian snackbar and keeps item in list`() = runTest(testDispatcher) {
        val item = complaint(1, articleId = 10)
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } returns
            ComplaintPageDto(listOf(item), null)
        coEvery { adminApi.hideArticle(10, true) } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()

        vm.requestHide(item)
        vm.confirmHide()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Недостаточно прав", s.snackbar)
        assertEquals(1, s.items.size)
    }

    @Test
    fun `setQuery triggers debounced reload with query param`() = runTest(testDispatcher) {
        val page1 = ComplaintPageDto(listOf(complaint(1)), null)
        val page2 = ComplaintPageDto(listOf(complaint(5)), null)
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } returns page1
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = "news", fromDate = null, toDate = null) } returns page2

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items[0].reportId)

        vm.setQuery("news")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(5, s.items[0].reportId)
        assertEquals("news", s.query)
    }

    @Test
    fun `setDateRange triggers reload with date params`() = runTest(testDispatcher) {
        val page1 = ComplaintPageDto(listOf(complaint(1)), null)
        val page2 = ComplaintPageDto(listOf(complaint(7)), null)
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = null, toDate = null) } returns page1
        coEvery { adminApi.complaints(cursor = null, limit = any(), q = null, fromDate = "2026-01-01", toDate = "2026-06-30") } returns page2

        val vm = ComplaintsViewModel(adminApi)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items[0].reportId)

        vm.setDateRange("2026-01-01", "2026-06-30")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(7, s.items[0].reportId)
        assertEquals("2026-01-01", s.fromDate)
        assertEquals("2026-06-30", s.toDate)
    }
}
