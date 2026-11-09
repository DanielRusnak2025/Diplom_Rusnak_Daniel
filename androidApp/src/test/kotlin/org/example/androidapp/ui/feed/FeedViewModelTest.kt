@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.ui.feed

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.androidapp.data.FakeTokenRepository
import org.example.androidapp.data.TokenPair
import org.example.androidapp.network.ApiException
import org.example.androidapp.network.AuthApi
import org.example.androidapp.network.FeedApi
import org.example.dto.FeedArticleDto
import org.example.dto.FeedPageDto
import org.example.dto.ReportReasonDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val feedApi: FeedApi = mockk()
    private val authApi: AuthApi = mockk(relaxed = true)
    private val fakeRepo = FakeTokenRepository()

    private fun article(id: Int, rating: Int? = null) = FeedArticleDto(
        id = id,
        title = "Title $id",
        link = "https://example.com/$id",
        publisher = "Publisher",
        publicationDate = "2026-01-01",
        language = "en",
        region = "global",
        mainCategory = null,
        subtopic = null,
        myRating = rating,
    )

    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loadFirstPage populates items and stores nextCursor`() = runTest(testDispatcher) {
        val page = FeedPageDto(items = listOf(article(1), article(2)), nextCursor = "cursor1")
        coEvery { feedApi.feed(cursor = null, limit = any()) } returns page

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.items.size)
        assertFalse(vm.state.value.isEndOfList)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `loadMore appends page and deduplicates`() = runTest(testDispatcher) {
        val page1 = FeedPageDto(items = listOf(article(1), article(2)), nextCursor = "c1")
        val page2 = FeedPageDto(items = listOf(article(2), article(3)), nextCursor = null)
        coEvery { feedApi.feed(cursor = null, limit = any()) } returns page1
        coEvery { feedApi.feed(cursor = "c1", limit = any()) } returns page2

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(3, vm.state.value.items.size)
        assertTrue(vm.state.value.isEndOfList)
    }

    @Test
    fun `nextCursor null stops loadMore`() = runTest(testDispatcher) {
        coEvery { feedApi.feed(cursor = null, limit = any()) } returns
            FeedPageDto(items = listOf(article(1)), nextCursor = null)

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        assertTrue(vm.state.value.isEndOfList)
        vm.loadMore()
        advanceUntilIdle()

        // feed should NOT be called a second time
        coVerify(exactly = 1) { feedApi.feed(any(), any()) }
    }

    @Test
    fun `refresh resets list to first page`() = runTest(testDispatcher) {
        val page1 = FeedPageDto(items = listOf(article(1)), nextCursor = "c1")
        val page2 = FeedPageDto(items = listOf(article(2)), nextCursor = null)
        coEvery { feedApi.feed(cursor = null, limit = any()) } returnsMany listOf(page1, page2)

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items[0].id)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.items.size)
        assertEquals(2, vm.state.value.items[0].id)
    }

    @Test
    fun `like sets myRating to 1 optimistically`() = runTest(testDispatcher) {
        coEvery { feedApi.feed(any(), any()) } returns FeedPageDto(listOf(article(1, null)), null)
        coEvery { feedApi.rate(1, 1) } returns Unit

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        vm.like(vm.state.value.items[0])
        // optimistic: check immediately before API call completes
        assertEquals(1, vm.state.value.items[0].myRating)

        advanceUntilIdle()
        assertEquals(1, vm.state.value.items[0].myRating)
    }

    @Test
    fun `undo like calls deleteRate and clears rating`() = runTest(testDispatcher) {
        coEvery { feedApi.feed(any(), any()) } returns FeedPageDto(listOf(article(1, 1)), null)
        coEvery { feedApi.deleteRate(1) } returns Unit

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        vm.like(vm.state.value.items[0])
        advanceUntilIdle()

        assertNull(vm.state.value.items[0].myRating)
        coVerify { feedApi.deleteRate(1) }
    }

    @Test
    fun `dislike removes item from list`() = runTest(testDispatcher) {
        coEvery { feedApi.feed(any(), any()) } returns
            FeedPageDto(listOf(article(1), article(2)), null)
        coEvery { feedApi.rate(1, -1) } returns Unit

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)

        vm.dislike(vm.state.value.items[0])
        advanceUntilIdle()

        assertEquals(1, vm.state.value.items.size)
        assertEquals(2, vm.state.value.items[0].id)
    }

    @Test
    fun `complaint removes item from list`() = runTest(testDispatcher) {
        coEvery { feedApi.feed(any(), any()) } returns
            FeedPageDto(listOf(article(1), article(2)), null)
        coEvery { feedApi.complain(1, any()) } returns Unit

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        val target = vm.state.value.items[0]
        vm.submitComplaint(target, ReportReasonDto.NOT_AN_ARTICLE)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.items.size)
        assertEquals(2, vm.state.value.items[0].id)
    }

    @Test
    fun `rollback on dislike error reinserts item`() = runTest(testDispatcher) {
        coEvery { feedApi.feed(any(), any()) } returns
            FeedPageDto(listOf(article(1), article(2)), null)
        coEvery { feedApi.rate(1, -1) } throws RuntimeException("network error")

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        vm.dislike(vm.state.value.items[0])
        advanceUntilIdle()

        // item should be reinserted
        assertEquals(2, vm.state.value.items.size)
        assertNotNull(vm.state.value.snackbar)
    }

    @Test
    fun `rollback on complaint error reinserts item and shows snackbar`() = runTest(testDispatcher) {
        coEvery { feedApi.feed(any(), any()) } returns
            FeedPageDto(listOf(article(1)), null)
        coEvery { feedApi.complain(1, any()) } throws ApiException("COMPLAINT_CANNOT_BE_REMOVED", "", 409)

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        vm.submitComplaint(vm.state.value.items[0], ReportReasonDto.OTHER)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.items.size)
        assertEquals("Жалоба не может быть удалена", vm.state.value.snackbar)
    }

    @Test
    fun `logout clears tokens`() = runTest(testDispatcher) {
        fakeRepo.save(TokenPair("acc", "ref"))
        coEvery { feedApi.feed(any(), any()) } returns FeedPageDto(emptyList(), null)

        val vm = FeedViewModel(feedApi, authApi, fakeRepo)
        advanceUntilIdle()

        vm.logout()
        advanceUntilIdle()

        assertNull(fakeRepo.loadOnce())
    }
}
