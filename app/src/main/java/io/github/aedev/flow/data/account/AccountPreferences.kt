package io.github.aedev.flow.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.KeystoreSecretBox
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.accountDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "account_preferences")

class AccountPreferences(
    context: Context,
) {
    private val context = context.applicationContext

    private object Keys {
        val PROFILES_JSON = stringPreferencesKey("account_profiles_json")
        val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    val profiles: Flow<List<AccountProfile>> =
        context.accountDataStore.data.map { prefs ->
            val rawJson = prefs[Keys.PROFILES_JSON] ?: "[]"
            runCatching {
                val list = json.decodeFromString<List<AccountProfile>>(rawJson)
                list.map { profile ->
                    profile.copy(cookie = KeystoreSecretBox.open(profile.cookie))
                }
            }.getOrDefault(emptyList())
        }

    val activeAccountId: Flow<String?> =
        context.accountDataStore.data.map { prefs ->
            prefs[Keys.ACTIVE_ACCOUNT_ID]
        }

    suspend fun saveProfiles(
        profiles: List<AccountProfile>,
        activeId: String?,
    ) {
        context.accountDataStore.edit { prefs ->
            val encryptedList =
                profiles.map { profile ->
                    profile.copy(cookie = KeystoreSecretBox.seal(profile.cookie))
                }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(encryptedList)
            if (activeId != null) {
                prefs[Keys.ACTIVE_ACCOUNT_ID] = activeId
            } else {
                prefs.remove(Keys.ACTIVE_ACCOUNT_ID)
            }
        }
    }
}
