package org.example.androidapp

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import org.example.androidapp.crypto.AndroidKeystoreTokenCipher
import org.example.androidapp.data.DataStoreTokenRepository
import org.example.androidapp.data.TokenRepository
import org.example.androidapp.network.ApiClient
import org.example.androidapp.network.AuthApi
import org.example.androidapp.network.FeedApi
import org.example.androidapp.network.MeApi
import org.example.androidapp.network.MetaApi

private val Context.tokenDataStore by preferencesDataStore(name = "token_prefs")

class AppContainer(context: Context) {
    val tokenRepository: TokenRepository = DataStoreTokenRepository(
        context.tokenDataStore,
        AndroidKeystoreTokenCipher(),
    )
    val apiClient = ApiClient(tokenRepository, BuildConfig.BASE_URL)
    val authApi = AuthApi(BuildConfig.BASE_URL, apiClient.anonClient, apiClient.authClient,
        clearCache = { apiClient.clearBearerCache() })
    val metaApi = MetaApi(BuildConfig.BASE_URL, apiClient.authClient)
    val meApi = MeApi(BuildConfig.BASE_URL, apiClient.authClient)
    val feedApi = FeedApi(BuildConfig.BASE_URL, apiClient.authClient)
}
