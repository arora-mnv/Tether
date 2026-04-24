import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.dao.userProfileDao
import com.anantva.tether.data.repository.TetherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val streakDays: Int = 0,
    // Add other UI state properties here if needed
)

class DashboardViewModel(
    private val tetherRepository: TetherRepository,
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> get() = _uiState

    // New shared flow for streak break event
    private val _streakBrokenEvent = MutableSharedFlow<Unit>()
    val streakBrokenEvent = _streakBrokenEvent.asSharedFlow()

    init {
        checkAndUpdateStreak()
    }

    fun checkAndUpdateStreak() {
        viewModelScope.launch {
            // Get current date in epoch days
            val todayEpochDay = LocalDate.now().toEpochDay()

            // Fetch user profile to get last streak check date and current streak
            val userProfile = tetherRepository.getUserProfile("user_id").firstOrNull()
            if (userProfile == null) return@launch

            val lastStreakCheckDate = userProfile.lastStreakCheckDate
            val previousStreak = userProfile.currentStreak

            // If it's the first run or already checked today, return
            if (lastStreakCheckDate == 0L) {
                // First run
                tetherRepository.updateBalances("user_id", userProfile.currentBalance, userProfile.emergencyFundBalance)
                tetherRepository.updateStreakAndCheckDate(0, todayEpochDay)
                _uiState.value = DashboardUiState(streakDays = 0)
                return@launch
            } else if (lastStreakCheckDate == todayEpochDay) {
                // Already checked today
                _uiState.value = DashboardUiState(streakDays = previousStreak)
                return@launch
            }

            // Get daily limit and spent today
            val dailyLimitResult = tetherRepository.calculateDailyLimitUseCase()
            val spentToday = tetherRepository.getExpenseSpentValue(todayEpochDay, todayEpochDay)

            // Calculate new streak
            val newStreak = if (spentToday <= dailyLimitResult.dailyLimit) {
                previousStreak + 1
            } else {
                0
            }

            // Emit streak broken event if necessary
            if (previousStreak > 0 && newStreak == 0) {
                _streakBrokenEvent.emit(Unit)
            }

            // Save new streak and update last check date
            tetherRepository.updateBalances("user_id", userProfile.currentBalance, userProfile.emergencyFundBalance)
            tetherRepository.updateStreakAndCheckDate(newStreak, todayEpochDay)

            _uiState.value = DashboardUiState(streakDays = newStreak)
        }
    }
}
