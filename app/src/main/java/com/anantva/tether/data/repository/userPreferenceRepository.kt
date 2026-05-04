package com.anantva.tether.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val HAS_COMPLETED_SETUP    = booleanPreferencesKey("has_completed_setup")
        val CURRENT_BALANCE        = stringPreferencesKey("current_balance")
        val SAVINGS_GOAL           = stringPreferencesKey("savings_goal")
        val MONTHLY_COMMITMENT     = stringPreferencesKey("monthly_commitment") // ✅ replaces TARGET_DATE
        val HAS_SAVED_COMMITMENT   = booleanPreferencesKey("has_saved_commitment")
        val IS_CLOUD_STORAGE       = booleanPreferencesKey("is_cloud_storage")
        val NOTIFICATIONS_ENABLED  = booleanPreferencesKey("notifications_enabled")
        val USER_NAME              = stringPreferencesKey("user_name")
        val USER_EMAIL             = stringPreferencesKey("user_email")
        val USER_PHONE             = stringPreferencesKey("user_phone")
        val SELECTED_AVATAR        = stringPreferencesKey("selected_avatar")
        val STREAK_DAYS            = intPreferencesKey("streak_days")
        val LAST_STREAK_CHECK      = longPreferencesKey("last_streak_check_date")
    }

    val hasCompletedOnboarding: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false }

    val hasCompletedSetup: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.HAS_COMPLETED_SETUP] ?: false }

    val currentBalance: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.CURRENT_BALANCE] ?: "" }

    val savingsGoal: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.SAVINGS_GOAL] ?: "" }

    // ✅ Monthly amount user commits to save — stored as string like balance
    val monthlyCommitment: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.MONTHLY_COMMITMENT] ?: "" }

    val hasSavedCommitment: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.HAS_SAVED_COMMITMENT] ?: false }

    val isCloudStorage: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.IS_CLOUD_STORAGE] ?: false }

    val notificationsEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true }

    val userName: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.USER_NAME] ?: "" }

    val userEmail: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.USER_EMAIL] ?: "" }

    val userPhone: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.USER_PHONE] ?: "" }

    val selectedAvatar: Flow<String> =
        dataStore.data.map { it[PreferencesKeys.SELECTED_AVATAR] ?: "chill_cat" }

    val streakDays: Flow<Int> =
        dataStore.data.map { it[PreferencesKeys.STREAK_DAYS] ?: 0 }

    val lastStreakCheckDate: Flow<Long> =
        dataStore.data.map { it[PreferencesKeys.LAST_STREAK_CHECK] ?: 0L }

    suspend fun saveSetupDetails(
        balance:           String,
        goal:              String,
        monthlyCommitment: String,
        hasSavedCommitment: Boolean,
        isCloud:           Boolean,
        userName:          String
    ) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CURRENT_BALANCE]    = balance
            prefs[PreferencesKeys.SAVINGS_GOAL]       = goal
            prefs[PreferencesKeys.MONTHLY_COMMITMENT] = monthlyCommitment
            prefs[PreferencesKeys.HAS_SAVED_COMMITMENT] = hasSavedCommitment
            prefs[PreferencesKeys.IS_CLOUD_STORAGE]   = isCloud
            prefs[PreferencesKeys.HAS_COMPLETED_SETUP] = true
            prefs[PreferencesKeys.USER_NAME]          = userName
        }
    }

    suspend fun updateStreakAndCheckDate(newStreak: Int, epochDay: Long) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.STREAK_DAYS]       = newStreak
            prefs[PreferencesKeys.LAST_STREAK_CHECK] = epochDay
        }
    }

    suspend fun updateMonthlyCommitment(value: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.MONTHLY_COMMITMENT] = value
        }
    }

    suspend fun updateSavingsGoal(value: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SAVINGS_GOAL] = value
        }
    }

    suspend fun setHasSavedCommitment(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_SAVED_COMMITMENT] = value
        }
    }

    suspend fun setCloudStorageEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_CLOUD_STORAGE] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateUserProfile(name: String, email: String, phone: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.USER_NAME] = name
            prefs[PreferencesKeys.USER_EMAIL] = email
            prefs[PreferencesKeys.USER_PHONE] = phone
        }
    }

    suspend fun setSelectedAvatar(avatarId: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SELECTED_AVATAR] = avatarId
        }
    }

    suspend fun setHasCompletedOnboarding(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = value
        }
    }

    suspend fun resetAll() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
