package com.example.walky.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walky.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    object Idle : LoginState
    object Loading : LoginState
    object Success : LoginState
    data class Error(val msg: String) : LoginState
}

class LoginViewModel(
    private val repo: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    /** Google 로그인 */
    fun signInGoogle(idToken: String?) {
        if (idToken.isNullOrBlank()) {
            _state.value = LoginState.Error("Google 토큰이 없습니다.")
            return
        }
        viewModelScope.launch {
            _state.value = LoginState.Loading
            runCatching {
                repo.firebaseWithGoogle(idToken)
                repo.registerUserIfNew()
            }
                .onSuccess { _state.value = LoginState.Success }
                .onFailure { _state.value = LoginState.Error(it.message ?: "Google 로그인 실패") }
        }
    }
    fun signInKakao() {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            runCatching {
                val (id, access) = repo.loginWithKakao()
                    ?: error("토큰을 받지 못했습니다.")
                repo.firebaseWithKakao(id, access)
                repo.registerUserIfNew()
            }.onSuccess {
                _state.value = LoginState.Success
            }.onFailure {
                _state.value = LoginState.Error(it.message ?: "카카오 로그인 실패")
            }
        }
    }
}
