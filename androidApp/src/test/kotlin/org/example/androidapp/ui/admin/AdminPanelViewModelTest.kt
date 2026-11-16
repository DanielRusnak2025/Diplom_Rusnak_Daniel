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
import org.example.dto.AuditLogItemDto
import org.example.dto.AuditPageDto
import org.example.dto.StatsDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminPanelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val adminApi: AdminApi = mockk()

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun stats() = StatsDto(
        totalArticles = 100L,
        classifiedArticles = 80L,
        pendingQueue = 20L,
        totalUsers = 50L,
        openComplaints = 3L,
    )

    private fun auditItem(id: Long) = AuditLogItemDto(
        id = id,
        actorId = 1,
        actorNick = "admin",
        action = "ARTICLE_HIDDEN",
        targetType = "article",
        targetId = id.toString(),
        details = null,
        createdAt = "2026-01-01T12:00:00",
        targetNick = null,
    )

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Test
    fun `loadStats success populates stats`() = runTest(testDispatcher) {
        coEvery { adminApi.stats() } returns stats()

        val vm = AdminPanelViewModel(adminApi)
        vm.loadStats()
        advanceUntilIdle()

        val s = vm.stats.value
        assertNotNull(s.stats)
        assertEquals(100L, s.stats!!.totalArticles)
        assertEquals(false, s.isLoading)
        assertNull(s.error)
    }

    @Test
    fun `loadStats does not reload when stats already loaded`() = runTest(testDispatcher) {
        coEvery { adminApi.stats() } returns stats()

        val vm = AdminPanelViewModel(adminApi)
        vm.loadStats()
        advanceUntilIdle()

        vm.loadStats()
        advanceUntilIdle()

        // stats() was called exactly once
        io.mockk.coVerify(exactly = 1) { adminApi.stats() }
    }

    @Test
    fun `loadStats 403 shows forbidden error`() = runTest(testDispatcher) {
        coEvery { adminApi.stats() } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = AdminPanelViewModel(adminApi)
        vm.loadStats()
        advanceUntilIdle()

        val s = vm.stats.value
        assertNull(s.stats)
        assertEquals(false, s.isLoading)
        assertTrue(s.error!!.contains("прав"))
    }

    @Test
    fun `loadStats network error shows generic error`() = runTest(testDispatcher) {
        coEvery { adminApi.stats() } throws RuntimeException("timeout")

        val vm = AdminPanelViewModel(adminApi)
        vm.loadStats()
        advanceUntilIdle()

        val s = vm.stats.value
        assertNull(s.stats)
        assertTrue(s.error != null)
    }

    // ─── Audit ────────────────────────────────────────────────────────────────

    @Test
    fun `loadAudit success populates items`() = runTest(testDispatcher) {
        val page = AuditPageDto(items = listOf(auditItem(1), auditItem(2)), nextCursor = null)
        coEvery { adminApi.audit(cursor = null, limit = any(), actions = any()) } returns page

        val vm = AdminPanelViewModel(adminApi)
        vm.loadAudit()
        advanceUntilIdle()

        val s = vm.audit.value
        assertEquals(2, s.items.size)
        assertEquals(false, s.isLoading)
        assertEquals(true, s.isEndOfList)
        assertNull(s.error)
    }

    @Test
    fun `loadAudit empty list shows empty state`() = runTest(testDispatcher) {
        coEvery { adminApi.audit(cursor = null, limit = any(), actions = any()) } returns AuditPageDto(emptyList(), null)

        val vm = AdminPanelViewModel(adminApi)
        vm.loadAudit()
        advanceUntilIdle()

        val s = vm.audit.value
        assertTrue(s.items.isEmpty())
        assertEquals(false, s.isLoading)
        assertEquals(true, s.isEndOfList)
        assertNull(s.error)
    }

    @Test
    fun `loadAudit 403 shows forbidden error`() = runTest(testDispatcher) {
        coEvery { adminApi.audit(cursor = null, limit = any(), actions = any()) } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = AdminPanelViewModel(adminApi)
        vm.loadAudit()
        advanceUntilIdle()

        val s = vm.audit.value
        assertTrue(s.items.isEmpty())
        assertEquals(false, s.isLoading)
        assertTrue(s.error!!.contains("прав"))
    }

    @Test
    fun `loadAudit network error shows error message`() = runTest(testDispatcher) {
        coEvery { adminApi.audit(cursor = null, limit = any(), actions = any()) } throws RuntimeException("timeout")

        val vm = AdminPanelViewModel(adminApi)
        vm.loadAudit()
        advanceUntilIdle()

        val s = vm.audit.value
        assertTrue(s.items.isEmpty())
        assertTrue(s.error != null)
    }

    @Test
    fun `loadMoreAudit appends items`() = runTest(testDispatcher) {
        val page1 = AuditPageDto(items = listOf(auditItem(10), auditItem(9)), nextCursor = "9")
        val page2 = AuditPageDto(items = listOf(auditItem(8)), nextCursor = null)
        coEvery { adminApi.audit(cursor = null, limit = any(), actions = any()) } returns page1
        coEvery { adminApi.audit(cursor = "9", limit = any(), actions = any()) } returns page2

        val vm = AdminPanelViewModel(adminApi)
        vm.loadAudit()
        advanceUntilIdle()

        assertEquals(2, vm.audit.value.items.size)

        vm.loadMoreAudit()
        advanceUntilIdle()

        val s = vm.audit.value
        assertEquals(3, s.items.size)
        assertEquals(true, s.isEndOfList)
    }

    @Test
    fun `loadAudit does not reload when items already present`() = runTest(testDispatcher) {
        val page = AuditPageDto(items = listOf(auditItem(1)), nextCursor = null)
        coEvery { adminApi.audit(cursor = null, limit = any(), actions = any()) } returns page

        val vm = AdminPanelViewModel(adminApi)
        vm.loadAudit()
        advanceUntilIdle()

        vm.loadAudit()
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { adminApi.audit(cursor = null, limit = any(), actions = any()) }
    }

    @Test
    fun `refreshAudit reloads from scratch`() = runTest(testDispatcher) {
        val page1 = AuditPageDto(items = listOf(auditItem(10)), nextCursor = null)
        val page2 = AuditPageDto(items = listOf(auditItem(99)), nextCursor = null)
        coEvery { adminApi.audit(cursor = null, limit = any(), actions = any()) } returnsMany listOf(page1, page2)

        val vm = AdminPanelViewModel(adminApi)
        vm.loadAudit()
        advanceUntilIdle()
        assertEquals(10L, vm.audit.value.items.first().id)

        vm.refreshAudit()
        advanceUntilIdle()

        assertEquals(99L, vm.audit.value.items.first().id)
    }
}
