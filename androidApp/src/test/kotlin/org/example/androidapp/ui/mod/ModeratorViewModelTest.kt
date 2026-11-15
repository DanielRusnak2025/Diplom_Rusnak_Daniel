@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.mod

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

class ModeratorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val adminApi: AdminApi = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun complaint(reportId: Int, articleId: Int = 1) = ComplaintItemDto(
        reportId   = reportId,
        articleId  = articleId,
        articleTitle = "Article $articleId",
        userId     = 10,
        userNick   = "user",
        reasonCode = ReportReasonDto.OTHER,
        date       = "2026-01-01",
    )

    @Test
    fun `load first page populates items`() = runTest(testDispatcher) {
        val page = ComplaintPageDto(items = listOf(complaint(1), complaint(2)), nextCursor = null)
        coEvery { adminApi.complaints(cursor = null, limit = any()) } returns page

        val vm = ModeratorViewModel(adminApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.isLoading)
        assertEquals(2, s.items.size)
        assertEquals(true, s.isEndOfList)
        assertNull(s.error)
    }

    @Test
    fun `load empty list shows empty state`() = runTest(testDispatcher) {
        coEvery { adminApi.complaints(cursor = null, limit = any()) } returns
            ComplaintPageDto(emptyList(), null)

        val vm = ModeratorViewModel(adminApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(false, s.isLoading)
        assertTrue(s.items.isEmpty())
        assertTrue(s.isEndOfList)
        assertNull(s.error)
    }

    @Test
    fun `403 on load shows Russian forbidden error`() = runTest(testDispatcher) {
        coEvery { adminApi.complaints(cursor = null, limit = any()) } throws
            ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = ModeratorViewModel(adminApi)
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
        coEvery { adminApi.complaints(cursor = null, limit = any()) } returns page
        coEvery { adminApi.hideArticle(10, true) } just Runs

        val vm = ModeratorViewModel(adminApi)
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
    fun `dismiss complaints removes all items for that article`() = runTest(testDispatcher) {
        val item1 = complaint(1, articleId = 10)
        val item2 = complaint(2, articleId = 10)
        val item3 = complaint(3, articleId = 20)
        val page = ComplaintPageDto(items = listOf(item1, item2, item3), nextCursor = null)
        coEvery { adminApi.complaints(cursor = null, limit = any()) } returns page
        coEvery { adminApi.dismissComplaints(10) } just Runs

        val vm = ModeratorViewModel(adminApi)
        advanceUntilIdle()

        vm.requestDismiss(item1)
        vm.executeDismiss()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.items.size)
        assertEquals(item3, s.items.first())
        assertEquals("Жалобы отклонены", s.snackbar)
        coVerify(exactly = 1) { adminApi.dismissComplaints(10) }
    }

    @Test
    fun `403 on hide shows Russian snackbar and keeps item in list`() = runTest(testDispatcher) {
        val item = complaint(1, articleId = 10)
        coEvery { adminApi.complaints(cursor = null, limit = any()) } returns
            ComplaintPageDto(listOf(item), null)
        coEvery { adminApi.hideArticle(10, true) } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = ModeratorViewModel(adminApi)
        advanceUntilIdle()

        vm.requestHide(item)
        vm.confirmHide()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Недостаточно прав", s.snackbar)
        assertEquals(1, s.items.size)
    }
}
