package org.example.androidapp.data

import kotlinx.coroutines.flow.Flow

interface TokenRepository {
    fun tokensFlow(): Flow<TokenPair?>
    suspend fun loadOnce(): TokenPair?
    suspend fun save(pair: TokenPair)
    suspend fun clear()
}
