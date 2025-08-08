// app/src/main/java/com/example/walky/ui/screen/profile/ProfileViewModel.kt
package com.example.walky.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walky.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserProfile? = null,
    val dogs: List<DogProfile> = emptyList(),
    val weekly: WeeklyStats? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showEditDialog: Boolean = false,
    val showAddDogDialog: Boolean = false
)

class ProfileViewModel(
    private val repo: UserRepository = UserRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        observeAll()
    }

    private fun observeAll() {
        viewModelScope.launch {
            combine(
                repo.listenUser(),
                repo.listenDogs(),
                repo.listenWeeklyStats()
            ) { user, dogs, weekly ->
                ProfileUiState(
                    user = user,
                    dogs = dogs,
                    weekly = weekly,
                    isLoading = false
                )
            }.catch { e ->
                _ui.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state -> _ui.value = state }
        }
    }

    fun openEdit() = _ui.update { it.copy(showEditDialog = true) }
    fun closeEdit() = _ui.update { it.copy(showEditDialog = false) }
    fun openAddDog() = _ui.update { it.copy(showAddDogDialog = true) }
    fun closeAddDog() = _ui.update { it.copy(showAddDogDialog = false) }

    // ProfileViewModel.kt
    fun saveProfile(name: String, location: String, email: String?, photoUrl: String?) =
        viewModelScope.launch {
            runCatching {
                repo.updateUserAndAuth(
                    displayName = name,
                    locationText = location,
                    photoUrl = photoUrl,
                    newEmail = email
                )
            }.onFailure { e ->
                _ui.update { it.copy(error = e.message ?: "프로필 수정 실패") }
            }
            closeEdit()
        }


    fun addDog(name: String, breed: String, age: Int, neutered: Boolean) = viewModelScope.launch {
        runCatching { repo.addDog(name, breed, age, neutered) }
            .onFailure { _ui.update { it.copy(error = it.error ?: "강아지 추가 실패") } }
            .also { closeAddDog() }
    }

    fun deleteDog(dogId: String) = viewModelScope.launch {
        runCatching { repo.deleteDog(dogId) }
            .onFailure { _ui.update { it.copy(error = it.error ?: "삭제 실패") } }
    }

    fun signOut() = repo.signOut()
}
