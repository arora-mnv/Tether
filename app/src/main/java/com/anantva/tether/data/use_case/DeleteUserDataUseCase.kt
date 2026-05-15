package com.anantva.tether.data.use_case

import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.behavior.BehaviorLearningEngine
import com.anantva.tether.behavior.FinancialPersonalityEngine
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.MerchantPatternDao
import com.anantva.tether.data.parser.MerchantLearningEngine
import com.anantva.tether.data.repository.FirestoreRepository
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteUserDataUseCase @Inject constructor(
    private val tetherRepository: TetherRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val userRepository: UserRepository,
    private val firestoreRepository: FirestoreRepository,
    private val merchantLearningEngine: MerchantLearningEngine,
    private val merchantPatternDao: MerchantPatternDao,
    private val categoryCorrectionDao: CategoryCorrectionDao,
    private val behaviorEngine: BehaviorLearningEngine,
    private val personalityEngine: FinancialPersonalityEngine,
    private val authManager: FirebaseAuthManager
) {
    suspend operator fun invoke() {
        tetherRepository.clearAllData()

        merchantPatternDao.deleteAll()
        categoryCorrectionDao.deleteAll()

        merchantLearningEngine.reset()
        behaviorEngine.reset()
        personalityEngine.reset()

        preferencesRepository.resetAll()
        userRepository.clearUser()

        val uid = authManager.getCurrentUserId()
        if (uid != null) {
            firestoreRepository.deleteAllUserData(uid)
        }
    }
}
