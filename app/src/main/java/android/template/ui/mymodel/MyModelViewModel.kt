/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.template.ui.mymodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.template.data.MyModelRepository
import android.template.data.local.database.MyModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.template.ui.mymodel.MyModelUiState.Error
import android.template.ui.mymodel.MyModelUiState.Loading
import android.template.ui.mymodel.MyModelUiState.Success

@HiltViewModel
class MyModelViewModel @Inject constructor(
    private val myModelRepository: MyModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyModelUiState>(Loading)
    val uiState: StateFlow<MyModelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            myModelRepository.myModels
                .map(::Success)
                .catch { emit(Error(it)) }
                .collect { _uiState.value = it }
        }
    }

    fun addMyModel(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        viewModelScope.launch {
            try {
                myModelRepository.add(trimmedName)
            } catch (throwable: Throwable) {
                _uiState.value = Error(throwable)
            }
        }
    }
}

sealed interface MyModelUiState {
    object Loading : MyModelUiState
    data class Error(val throwable: Throwable) : MyModelUiState
    data class Success(val data: List<MyModel>) : MyModelUiState
}
