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
import org.example.dto.AdminUserDto
import org.example.dto.AdminUserPageDto
import org.example.dto.RegionDto
import org.example.dto.RoleDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UsersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val adminApi: AdminApi = mockk()
    private val metaApi: MetaApi = mockk()

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { metaApi.languageNames(any()) } returns emptyMap()
        coEvery { metaApi.regions() } returns emptyList()
    }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun user(id: Int, role: RoleDto = RoleDto.USER) = AdminUserDto(
        id = id,
        nick = "user$id",
        email = "user$id@example.com",
        role = role,
        registrationDate = "2026-01-01",
    )

    private fun mockGetUsers(
        q: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        cursor: Int? = null,
        result: AdminUserPageDto,
    ) {
        coEvery {
            adminApi.getUsers(q = q, fromDate = fromDate, toDate = toDate, cursor = cursor, limit = any())
        } returns result
    }

    @Test
    fun `initial load populates items`() = runTest(testDispatcher) {
        mockGetUsers(result = AdminUserPageDto(listOf(user(1), user(2)), null))

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(2, s.items.size)
        assertNull(s.error)
        assertTrue(s.isEndOfList)
    }

    @Test
    fun `initial load 403 shows forbidden error`() = runTest(testDispatcher) {
        coEvery {
            adminApi.getUsers(q = any(), fromDate = any(), toDate = any(), cursor = any(), limit = any())
        } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.items.isEmpty())
        assertNotNull(s.error)
        assertTrue(s.error!!.contains("denied"))
    }

    @Test
    fun `initial load network error shows generic error`() = runTest(testDispatcher) {
        coEvery {
            adminApi.getUsers(q = any(), fromDate = any(), toDate = any(), cursor = any(), limit = any())
        } throws RuntimeException("timeout")

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `setQuery triggers debounced reload`() = runTest(testDispatcher) {
        mockGetUsers(q = null, result = AdminUserPageDto(listOf(user(1)), null))
        mockGetUsers(q = "alice", result = AdminUserPageDto(listOf(user(5)), null))

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.setQuery("alice")
        advanceUntilIdle()

        assertEquals(5, vm.state.value.items[0].id)
        assertEquals("alice", vm.state.value.query)
    }

    @Test
    fun `loadMore appends items`() = runTest(testDispatcher) {
        mockGetUsers(cursor = null, result = AdminUserPageDto(listOf(user(10), user(9)), "9"))
        mockGetUsers(cursor = 9, result = AdminUserPageDto(listOf(user(8)), null))

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(3, vm.state.value.items.size)
        assertTrue(vm.state.value.isEndOfList)
    }

    @Test
    fun `updateUserRole updates role in list`() = runTest(testDispatcher) {
        mockGetUsers(result = AdminUserPageDto(listOf(user(1, RoleDto.USER), user(2)), null))

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.updateUserRole(1, RoleDto.MODERATOR)

        assertEquals(RoleDto.MODERATOR, vm.state.value.items.find { it.id == 1 }?.role)
    }

    @Test
    fun `applyFilters - role filter sent to api`() = runTest(testDispatcher) {
        mockGetUsers(result = AdminUserPageDto(emptyList(), null))
        coEvery {
            adminApi.getUsers(q = null, fromDate = null, toDate = null,
                role = "MODERATOR", language = null, region = null, hasComplaints = null,
                cursor = null, limit = any())
        } returns AdminUserPageDto(listOf(user(1, RoleDto.MODERATOR)), null)

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.applyFilters(RoleDto.MODERATOR, null, null, null)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.items.size)
        assertEquals(RoleDto.MODERATOR, vm.state.value.roleFilter)
    }

    @Test
    fun `applyFilters - language and region filter sent to api`() = runTest(testDispatcher) {
        mockGetUsers(result = AdminUserPageDto(emptyList(), null))
        coEvery {
            adminApi.getUsers(q = null, fromDate = null, toDate = null,
                role = null, language = "en", region = "US", hasComplaints = null,
                cursor = null, limit = any())
        } returns AdminUserPageDto(listOf(user(2), user(3)), null)

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.applyFilters(null, "en", "US", null)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.items.size)
        assertEquals("en", vm.state.value.languageFilter)
        assertEquals("US", vm.state.value.regionFilter)
    }

    @Test
    fun `applyFilters - hasComplaints=true sent to api`() = runTest(testDispatcher) {
        mockGetUsers(result = AdminUserPageDto(emptyList(), null))
        coEvery {
            adminApi.getUsers(q = null, fromDate = null, toDate = null,
                role = null, language = null, region = null, hasComplaints = true,
                cursor = null, limit = any())
        } returns AdminUserPageDto(listOf(user(5)), null)

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.applyFilters(null, null, null, true)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.items.size)
        assertEquals(true, vm.state.value.hasComplaintsFilter)
    }

    @Test
    fun `resetFilters clears all filters and reloads`() = runTest(testDispatcher) {
        mockGetUsers(result = AdminUserPageDto(listOf(user(1)), null))

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.resetFilters()
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.roleFilter)
        assertNull(s.languageFilter)
        assertNull(s.regionFilter)
        assertNull(s.hasComplaintsFilter)
        assertEquals(0, s.activeFilterCount)
    }

    @Test
    fun `filters combine with query - all params sent`() = runTest(testDispatcher) {
        mockGetUsers(result = AdminUserPageDto(emptyList(), null))
        coEvery {
            adminApi.getUsers(q = "bob", fromDate = null, toDate = null,
                role = "ADMIN", language = null, region = null, hasComplaints = null,
                cursor = null, limit = any())
        } returns AdminUserPageDto(listOf(user(1, RoleDto.ADMIN)), null)

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.applyFilters(RoleDto.ADMIN, null, null, null)
        vm.setQuery("bob")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun `paging keeps filters on loadMore`() = runTest(testDispatcher) {
        coEvery {
            adminApi.getUsers(q = null, fromDate = null, toDate = null,
                role = "USER", language = null, region = null, hasComplaints = null,
                cursor = null, limit = any())
        } returns AdminUserPageDto(listOf(user(10), user(9)), "9")
        coEvery {
            adminApi.getUsers(q = null, fromDate = null, toDate = null,
                role = "USER", language = null, region = null, hasComplaints = null,
                cursor = 9, limit = any())
        } returns AdminUserPageDto(listOf(user(8)), null)

        val vm = UsersViewModel(adminApi, metaApi)
        advanceUntilIdle()

        vm.applyFilters(RoleDto.USER, null, null, null)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(3, vm.state.value.items.size)
        assertEquals(RoleDto.USER, vm.state.value.roleFilter)
    }
}
