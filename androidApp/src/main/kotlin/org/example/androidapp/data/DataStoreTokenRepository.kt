package org.example.androidapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.example.androidapp.crypto.TokenCipher

class DataStoreTokenRepository(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : TokenRepository {

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")

    private fun Preferences.decode(): TokenPair? {
        val a = get(accessKey) ?: return null
        val r = get(refreshKey) ?: return null
        return try { TokenPair(cipher.decrypt(a), cipher.decrypt(r)) } catch (_: Exception) { null }
    }

    override fun tokensFlow(): Flow<TokenPair?> = store.data.map { it.decode() }

    override suspend fun loadOnce(): TokenPair? = store.data.first().decode()

    override suspend fun save(pair: TokenPair) {
        store.edit {
            it[accessKey] = cipher.encrypt(pair.access)
            it[refreshKey] = cipher.encrypt(pair.refresh)
        }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(accessKey)
            it.remove(refreshKey)
        }
    }
}
