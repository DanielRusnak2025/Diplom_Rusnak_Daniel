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
import org.example.dto.AdminUserDto
import org.example.dto.AdminUserPageDto
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

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
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

        val vm = UsersViewModel(adminApi)
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

        val vm = UsersViewModel(adminApi)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.items.isEmpty())
        assertNotNull(s.error)
        assertTrue(s.error!!.contains("прав"))
    }

    @Test
    fun `initial load network error shows generic error`() = runTest(testDispatcher) {
        coEvery {
            adminApi.getUsers(q = any(), fromDate = any(), toDate = any(), cursor = any(), limit = any())
        } throws RuntimeException("timeout")

        val vm = UsersViewModel(adminApi)
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `setQuery triggers debounced reload`() = runTest(testDispatcher) {
        mockGetUsers(q = null, result = AdminUserPageDto(listOf(user(1)), null))
        mockGetUsers(q = "alice", result = AdminUserPageDto(listOf(user(5)), null))

        val vm = UsersViewModel(adminApi)
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

        val vm = UsersViewModel(adminApi)
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

        val vm = UsersViewModel(adminApi)
        advanceUntilIdle()

        vm.updateUserRole(1, RoleDto.MODERATOR)

        assertEquals(RoleDto.MODERATOR, vm.state.value.items.find { it.id == 1 }?.role)
    }
}
