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
import org.example.dto.AdminUserDetailDto
import org.example.dto.AdminUserRatingsDto
import org.example.dto.RoleDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val adminApi: AdminApi = mockk()
    private val metaApi: MetaApi = mockk()

    @BeforeEach fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { metaApi.languages() } returns emptyList()
        coEvery { metaApi.regions() } returns emptyList()
    }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun detailDto(role: RoleDto = RoleDto.USER) = AdminUserDetailDto(
        id = 42,
        nick = "testuser",
        email = "test@example.com",
        registrationDate = "2026-01-01",
        role = role,
        languages = listOf("en"),
        regions = listOf("western_europe"),
        interests = emptyList(),
        blocks = emptyList(),
        ratings = AdminUserRatingsDto(likeCount = 5, dislikeCount = 2, complaintCount = 1),
    )

    @Test
    fun `load success populates user`() = runTest(testDispatcher) {
        coEvery { adminApi.getUserDetail(42) } returns detailDto()

        val vm = UserDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        val s = vm.state.value
        assertNotNull(s.user)
        assertEquals("testuser", s.user!!.nick)
        assertNull(s.error)
    }

    @Test
    fun `load 403 shows forbidden error`() = runTest(testDispatcher) {
        coEvery { adminApi.getUserDetail(42) } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = UserDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.user)
        assertNotNull(s.error)
        assertTrue(s.error!!.contains("прав"))
    }

    @Test
    fun `load network error shows generic error`() = runTest(testDispatcher) {
        coEvery { adminApi.getUserDetail(42) } throws RuntimeException("timeout")

        val vm = UserDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `setRole success updates role and sets snackbar and roleChanged`() = runTest(testDispatcher) {
        coEvery { adminApi.getUserDetail(42) } returns detailDto(RoleDto.USER)
        coJustRun { adminApi.setUserRole(42, RoleDto.MODERATOR) }

        val vm = UserDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        vm.setRole(RoleDto.MODERATOR)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(RoleDto.MODERATOR, s.user?.role)
        assertEquals("Роль изменена", s.snackbar)
        assertTrue(s.roleChanged)
    }

    @Test
    fun `setRole 409 shows own role error snackbar`() = runTest(testDispatcher) {
        coEvery { adminApi.getUserDetail(42) } returns detailDto(RoleDto.ADMIN)
        coEvery { adminApi.setUserRole(42, RoleDto.USER) } throws ApiException("SELF_ROLE_CHANGE", "Self change", 409)

        val vm = UserDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        vm.setRole(RoleDto.USER)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(RoleDto.ADMIN, s.user?.role)
        assertNotNull(s.snackbar)
        assertTrue(s.snackbar!!.contains("собственную роль"))
    }

    @Test
    fun `setRole 403 shows forbidden snackbar`() = runTest(testDispatcher) {
        coEvery { adminApi.getUserDetail(42) } returns detailDto(RoleDto.USER)
        coEvery { adminApi.setUserRole(42, RoleDto.MODERATOR) } throws ApiException("FORBIDDEN", "Forbidden", 403)

        val vm = UserDetailViewModel(adminApi, metaApi, 42)
        advanceUntilIdle()

        vm.setRole(RoleDto.MODERATOR)
        advanceUntilIdle()

        val s = vm.state.value
        assertNotNull(s.snackbar)
        assertTrue(s.snackbar!!.contains("прав"))
    }
}
