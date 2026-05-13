package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.local.entity.UserProfileEntity
import com.anantva.tether.data.local.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_TAG = "TetherSync"

sealed class SyncResult {
    object Idle : SyncResult()
    data class Syncing(val message: String = "Starting sync...") : SyncResult()
    data class Progress(val message: String, val synced: Int, val total: Int) : SyncResult()
    data class Done(val message: String = "Sync complete") : SyncResult()
    data class Error(val message: String) : SyncResult()
}

@Singleton
class SyncManager @Inject constructor(
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val userProfileDao: UserProfileDao,
    private val categoryCorrectionDao: CategoryCorrectionDao,
    private val firestoreRepository: FirestoreRepository,
    private val preferencesRepository: UserPreferencesRepository
) {

    /**
     * Full bidirectional reconciliation between Room and Firestore.
     * Emits SyncResult states for UI progress indication.
     */
    fun syncAll(uid: String): Flow<SyncResult> = flow {
        if (uid.isEmpty()) {
            emit(SyncResult.Error("User not signed in"))
            return@flow
        }

        emit(SyncResult.Syncing("Syncing transactions..."))
        try {
            syncTransactions(uid)
        } catch (e: Exception) {
            Log.e(SYNC_TAG, "Transaction sync failed", e)
            emit(SyncResult.Error("Transaction sync failed: ${e.message}"))
            return@flow
        }

        emit(SyncResult.Syncing("Syncing goals..."))
        try {
            syncGoals(uid)
        } catch (e: Exception) {
            Log.e(SYNC_TAG, "Goal sync failed", e)
            emit(SyncResult.Error("Goal sync failed: ${e.message}"))
            return@flow
        }

        emit(SyncResult.Syncing("Syncing profile..."))
        try {
            syncUserProfile(uid)
        } catch (e: Exception) {
            Log.e(SYNC_TAG, "Profile sync failed", e)
            emit(SyncResult.Error("Profile sync failed: ${e.message}"))
            return@flow
        }

        emit(SyncResult.Syncing("Syncing preferences..."))
        try {
            syncPreferences(uid)
        } catch (e: Exception) {
            Log.e(SYNC_TAG, "Preferences sync failed", e)
            emit(SyncResult.Error("Preferences sync failed: ${e.message}"))
            return@flow
        }

        emit(SyncResult.Syncing("Syncing category learning..."))
        try {
            syncCategoryCorrections(uid)
        } catch (e: Exception) {
            Log.e(SYNC_TAG, "Category correction sync failed", e)
            emit(SyncResult.Error("Category learning sync failed: ${e.message}"))
            return@flow
        }

        Log.d(SYNC_TAG, "Full sync completed for uid=$uid")
        emit(SyncResult.Done("All data synced successfully"))
    }.flowOn(Dispatchers.IO)

    // ── Transaction Sync ──

    private suspend fun syncTransactions(uid: String) {
        val localTxns = transactionDao.getAllConfirmedTransactions()
        val cloudTxns = firestoreRepository.getTransactions(uid)

        val localMap = localTxns.associateBy { it.transactionId }
        val cloudMap = cloudTxns.associateBy { it.transactionId }

        val allIds = (localMap.keys + cloudMap.keys).toSet()

        // Push local-only to cloud
        val localOnly = localMap.keys - cloudMap.keys
        Log.d(SYNC_TAG, "Transactions: ${localOnly.size} local-only, pushing to cloud")
        localOnly.forEach { id ->
            firestoreRepository.saveTransaction(uid, localMap[id]!!)
        }

        // Pull cloud-only to local
        val cloudOnly = cloudMap.keys - localMap.keys
        Log.d(SYNC_TAG, "Transactions: ${cloudOnly.size} cloud-only, pulling to local")
        cloudOnly.forEach { id ->
            transactionDao.upsertTransaction(cloudMap[id]!!)
        }

        // Conflict resolution: same ID exists in both, use more recent date
        val commonIds = localMap.keys.intersect(cloudMap.keys)
        Log.d(SYNC_TAG, "Transactions: ${commonIds.size} common IDs, checking for conflicts")
        commonIds.forEach { id ->
            val local = localMap[id]!!
            val cloud = cloudMap[id]!!
            if (cloud.date > local.date) {
                Log.d(SYNC_TAG, "Transaction $id: cloud is newer, pulling to local")
                transactionDao.upsertTransaction(cloud)
            } else {
                Log.d(SYNC_TAG, "Transaction $id: local is newer or equal, pushing to cloud")
                firestoreRepository.saveTransaction(uid, local)
            }
        }

        Log.d(SYNC_TAG, "Transaction sync complete: ${allIds.size} total processed")
    }

    // ── Goal Sync ──

    private suspend fun syncGoals(uid: String) {
        val localGoals = goalDao.getAllGoals()
        val cloudGoals = firestoreRepository.getGoals(uid)

        val localMap = localGoals.associateBy { it.goalId }
        val cloudMap = cloudGoals.associateBy { it.goalId }

        val allIds = (localMap.keys + cloudMap.keys).toSet()

        // Push local-only to cloud
        val localOnly = localMap.keys - cloudMap.keys
        Log.d(SYNC_TAG, "Goals: ${localOnly.size} local-only, pushing to cloud")
        localOnly.forEach { id ->
            firestoreRepository.saveGoal(uid, localMap[id]!!)
        }

        // Pull cloud-only to local
        val cloudOnly = cloudMap.keys - localMap.keys
        Log.d(SYNC_TAG, "Goals: ${cloudOnly.size} cloud-only, pulling to local")
        cloudOnly.forEach { id ->
            goalDao.upsertGoal(cloudMap[id]!!)
        }

        // Conflict resolution: use more recent startDate
        val commonIds = localMap.keys.intersect(cloudMap.keys)
        Log.d(SYNC_TAG, "Goals: ${commonIds.size} common IDs, checking for conflicts")
        commonIds.forEach { id ->
            val local = localMap[id]!!
            val cloud = cloudMap[id]!!
            if (cloud.startDate > local.startDate) {
                goalDao.upsertGoal(cloud)
            } else {
                firestoreRepository.saveGoal(uid, local)
            }
        }

        Log.d(SYNC_TAG, "Goal sync complete: ${allIds.size} total processed")
    }

    // ── User Profile Sync ──

    private suspend fun syncUserProfile(uid: String) {
        val localProfile = userProfileDao.getUserProfile(uid).first()
        val cloudProfile = firestoreRepository.getUserProfileMap(uid)

        if (localProfile == null && cloudProfile == null) {
            Log.d(SYNC_TAG, "No profile data in either local or cloud")
            return
        }

        if (localProfile != null && cloudProfile == null) {
            Log.d(SYNC_TAG, "Profile: local exists, cloud empty, pushing to cloud")
            firestoreRepository.saveUserProfileMap(uid, localProfile.toMap())
        } else if (localProfile == null && cloudProfile != null) {
            Log.d(SYNC_TAG, "Profile: cloud exists, local empty, pulling to local")
            val profile = UserProfileEntity.fromMap(cloudProfile)
            userProfileDao.insertOrUpdateUser(profile)
        } else if (localProfile != null && cloudProfile != null) {
            Log.d(SYNC_TAG, "Profile: both exist, keeping local, pushing to cloud")
            firestoreRepository.saveUserProfileMap(uid, localProfile.toMap())
        }
    }

    // ── Preferences Sync ──

    private suspend fun syncPreferences(uid: String) {
        val localMap = preferencesRepository.toMap()
        val cloudMap = firestoreRepository.getPreferencesMap(uid)

        if (cloudMap == null) {
            Log.d(SYNC_TAG, "Preferences: local exists, cloud empty, pushing to cloud")
            firestoreRepository.savePreferencesMap(uid, localMap)
            return
        }

        val merged = cloudMap.toMutableMap()
        localMap.forEach { (key, value) ->
            if (!merged.containsKey(key)) {
                merged[key] = value
            }
        }

        firestoreRepository.savePreferencesMap(uid, merged)
        preferencesRepository.applyMap(merged)

        Log.d(SYNC_TAG, "Preferences sync complete")
    }

    private suspend fun syncCategoryCorrections(uid: String) {
        val localCorrections = categoryCorrectionDao.getAllCorrections()
        val cloudCorrections = firestoreRepository.getCategoryCorrections(uid)

        val localMap = localCorrections.associateBy { it.merchantKey }
        val cloudMap = cloudCorrections.associateBy { it.merchantKey }

        val localOnly = localMap.keys - cloudMap.keys
        localOnly.forEach { key ->
            firestoreRepository.saveCategoryCorrection(uid, localMap.getValue(key))
        }

        val cloudOnly = cloudMap.keys - localMap.keys
        cloudOnly.forEach { key ->
            categoryCorrectionDao.insertCorrection(cloudMap.getValue(key))
        }

        val commonKeys = localMap.keys.intersect(cloudMap.keys)
        commonKeys.forEach { key ->
            val local = localMap.getValue(key)
            val cloud = cloudMap.getValue(key)
            if (local.category != cloud.category) {
                firestoreRepository.saveCategoryCorrection(uid, local)
            }
        }

        Log.d(SYNC_TAG, "Category correction sync complete")
    }
}
