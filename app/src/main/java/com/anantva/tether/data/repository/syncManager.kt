package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.MerchantPatternDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.GoalContributionEntity
import com.anantva.tether.data.local.entity.UserProfileEntity
import com.anantva.tether.data.local.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val merchantPatternDao: MerchantPatternDao,
    private val firestoreRepository: FirestoreRepository,
    private val preferencesRepository: UserPreferencesRepository
) {

    private val syncMutex = Mutex()

    fun syncAll(uid: String): Flow<SyncResult> = flow {
        syncMutex.withLock {
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
            syncMerchantPatterns(uid)
        } catch (e: Exception) {
            Log.e(SYNC_TAG, "Category correction sync failed", e)
            emit(SyncResult.Error("Category learning sync failed: ${e.message}"))
            return@flow
        }

        Log.d(SYNC_TAG, "Full sync completed for uid=$uid")
        emit(SyncResult.Done("All data synced successfully"))
        }
    }.flowOn(Dispatchers.IO)

    // ── Transaction Sync ──

    private suspend fun syncTransactions(uid: String) {
        val localTxns = transactionDao.getAllConfirmedTransactions()
        val cloudTxns = firestoreRepository.getTransactions(uid)

        val localMap = localTxns.associateBy { it.transactionId }
        val cloudMap = cloudTxns.associateBy { it.transactionId }

        val allIds = (localMap.keys + cloudMap.keys).toSet()

        val localOnly = localMap.keys - cloudMap.keys
        Log.d(SYNC_TAG, "Transactions: ${localOnly.size} local-only, pushing to cloud")
        localOnly.forEach { id ->
            firestoreRepository.saveTransaction(uid, localMap[id]!!)
        }

        val cloudOnly = cloudMap.keys - localMap.keys
        Log.d(SYNC_TAG, "Transactions: ${cloudOnly.size} cloud-only, pulling to local")
        cloudOnly.forEach { id ->
            transactionDao.upsertTransaction(cloudMap[id]!!)
        }

        val commonIds = localMap.keys.intersect(cloudMap.keys)
        Log.d(SYNC_TAG, "Transactions: ${commonIds.size} common IDs, checking for conflicts")
        commonIds.forEach { id ->
            val local = localMap[id]!!
            val cloud = cloudMap[id]!!
            if (cloud.date > local.date) {
                Log.d(SYNC_TAG, "Transaction $id: cloud is newer, pulling to local")
                transactionDao.upsertTransaction(cloud)
            } else if (local.date > cloud.date) {
                Log.d(SYNC_TAG, "Transaction $id: local is newer, pushing to cloud")
                firestoreRepository.saveTransaction(uid, local)
            } else if (cloud.amount != local.amount || cloud.category != local.category) {
                Log.d(SYNC_TAG, "Transaction $id: same timestamp but different data, merging")
                val merged = local.copy(
                    amount = maxOf(local.amount, cloud.amount),
                    category = if (local.category != "Other" && local.category.isNotBlank()) local.category else cloud.category,
                    merchant = if (local.merchant.length >= cloud.merchant.length) local.merchant else cloud.merchant
                )
                transactionDao.upsertTransaction(merged)
                firestoreRepository.saveTransaction(uid, merged)
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

        val localOnly = localMap.keys - cloudMap.keys
        Log.d(SYNC_TAG, "Goals: ${localOnly.size} local-only, pushing to cloud")
        localOnly.forEach { id ->
            firestoreRepository.saveGoal(uid, localMap[id]!!)
        }

        val cloudOnly = cloudMap.keys - localMap.keys
        Log.d(SYNC_TAG, "Goals: ${cloudOnly.size} cloud-only, pulling to local")
        cloudOnly.forEach { id ->
            goalDao.upsertGoal(cloudMap[id]!!)
        }

        val commonIds = localMap.keys.intersect(cloudMap.keys)
        Log.d(SYNC_TAG, "Goals: ${commonIds.size} common IDs, checking for conflicts")
        commonIds.forEach { id ->
            val local = localMap[id]!!
            val cloud = cloudMap[id]!!
            if (cloud.startDate > local.startDate) {
                goalDao.upsertGoal(cloud)
            } else if (local.startDate > cloud.startDate) {
                firestoreRepository.saveGoal(uid, local)
            } else if (cloud.targetAmount != local.targetAmount || cloud.isActive != local.isActive) {
                val merged = local.copy(
                    targetAmount = maxOf(local.targetAmount, cloud.targetAmount),
                    isActive = local.isActive || cloud.isActive
                )
                goalDao.upsertGoal(merged)
                firestoreRepository.saveGoal(uid, merged)
            }
        }

        Log.d(SYNC_TAG, "Goal sync complete: ${allIds.size} total processed")

        syncGoalContributions(uid, allIds)
    }

    private suspend fun syncGoalContributions(uid: String, goalIds: Set<Int>) {
        val local = goalDao.getAllGoalContributions()
        val localByKey = local.associateBy { it.goalId to contributionMonthKey(it.timestamp) }
        val cloud = goalIds.flatMap { firestoreRepository.getGoalContributions(uid, it) }
        val cloudByKey = cloud.associateBy { it.goalId to contributionMonthKey(it.timestamp) }
        val allKeys = (localByKey.keys + cloudByKey.keys).toSet()

        allKeys.forEach { key ->
            val localContribution = localByKey[key]
            val cloudContribution = cloudByKey[key]
            when {
                localContribution == null && cloudContribution != null -> {
                    val range = contributionMonthRange(cloudContribution.timestamp)
                    goalDao.deleteGoalContributionForMonth(cloudContribution.goalId, range.first, range.second)
                    goalDao.insertGoalContribution(cloudContribution)
                }
                localContribution != null && cloudContribution == null -> {
                    firestoreRepository.saveGoalContribution(uid, localContribution)
                }
                localContribution != null && cloudContribution != null -> {
                    val winner = if (cloudContribution.timestamp > localContribution.timestamp) {
                        cloudContribution
                    } else {
                        localContribution
                    }
                    val range = contributionMonthRange(winner.timestamp)
                    goalDao.deleteGoalContributionForMonth(winner.goalId, range.first, range.second)
                    val newId = goalDao.insertGoalContribution(winner)
                    firestoreRepository.saveGoalContribution(uid, winner.copy(
                        contributionId = if (newId > 0) newId.toInt() else winner.contributionId
                    ))
                }
            }
        }
        Log.d(SYNC_TAG, "Goal contribution sync complete: ${allKeys.size} month entries")
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
            val cloudStreak = (cloudProfile["currentStreak"] as? Number)?.toInt() ?: 0
            val localStreak = localProfile.currentStreak
            if (cloudStreak > localStreak) {
                Log.d(SYNC_TAG, "Profile: cloud has higher streak ($cloudStreak > $localStreak), merging")
                val merged = UserProfileEntity.fromMap(cloudProfile).copy(
                    currentBalance = localProfile.currentBalance,
                    emergencyFundBalance = localProfile.emergencyFundBalance
                )
                userProfileDao.insertOrUpdateUser(merged)
                firestoreRepository.saveUserProfileMap(uid, merged.toMap())
            } else {
                Log.d(SYNC_TAG, "Profile: both exist, keeping local, pushing to cloud")
                firestoreRepository.saveUserProfileMap(uid, localProfile.toMap())
            }
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
        var hasChanges = false
        localMap.forEach { (key, value) ->
            if (!merged.containsKey(key)) {
                merged[key] = value
                hasChanges = true
            }
        }

        cloudMap.forEach { (key, value) ->
            if (!localMap.containsKey(key)) {
                hasChanges = true
            }
        }

        firestoreRepository.savePreferencesMap(uid, merged)
        val localOnly = merged - "isCloudStorage"
        preferencesRepository.applyMap(localOnly)

        Log.d(SYNC_TAG, "Preferences sync complete${if (hasChanges) " with changes" else ""}")
    }

    fun observeTransactionsLive(uid: String): Flow<List<TransactionEntity>> =
        firestoreRepository.observeTransactions(uid)

    fun observeGoalsLive(uid: String): Flow<List<com.anantva.tether.data.local.entity.GoalEntity>> =
        firestoreRepository.observeGoals(uid)

    fun observeGoalContributionsLive(uid: String, goalId: Int): Flow<List<GoalContributionEntity>> =
        firestoreRepository.observeGoalContributions(uid, goalId)

    fun observePreferencesLive(uid: String): Flow<Map<String, Any>?> =
        firestoreRepository.observePreferencesMap(uid)

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

    private suspend fun syncMerchantPatterns(uid: String) {
        val localPatterns = merchantPatternDao.getAllPatterns()
        val cloudPatterns = firestoreRepository.getMerchantPatterns(uid)

        val localMap = localPatterns.associateBy { it.normalizedMerchant }
        val cloudMap = cloudPatterns.associateBy { it.normalizedMerchant }
        val allKeys = (localMap.keys + cloudMap.keys).toSet()

        allKeys.forEach { key ->
            val local = localMap[key]
            val cloud = cloudMap[key]
            val winner = when {
                local == null -> cloud
                cloud == null -> local
                local.lastUsedTimestamp >= cloud.lastUsedTimestamp -> local
                else -> cloud
            } ?: return@forEach
            merchantPatternDao.upsertPattern(winner)
            firestoreRepository.saveMerchantPattern(uid, winner)
        }

        Log.d(SYNC_TAG, "Merchant pattern sync complete: ${allKeys.size} total")
    }

    private fun contributionMonthKey(timestamp: Long): String {
        val date = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return "${date.year}-${date.monthValue}"
    }

    private fun contributionMonthRange(timestamp: Long): Pair<Long, Long> {
        val zone = java.time.ZoneId.systemDefault()
        val month = java.time.YearMonth.from(
            java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        )
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }
}
