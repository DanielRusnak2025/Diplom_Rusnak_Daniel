package org.example.androidapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTokenRepository : TokenRepository {
    private val state = MutableStateFlow<TokenPair?>(null)

    override fun tokensFlow(): Flow<TokenPair?> = state

    override suspend fun loadOnce(): TokenPair? = state.value

    override suspend fun save(pair: TokenPair) { state.value = pair }

    override suspend fun clear() { state.value = null }
}
