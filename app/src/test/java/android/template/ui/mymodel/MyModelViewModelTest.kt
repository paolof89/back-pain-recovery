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


import android.template.MainDispatcherRule
import android.template.data.MyModelRepository
import android.template.data.local.database.MyModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@OptIn(ExperimentalCoroutinesApi::class) // TODO: Remove when stable
class MyModelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_initiallyLoading() = runTest {
        val viewModel = MyModelViewModel(LoadingMyModelRepository())
        assertEquals(viewModel.uiState.first(), MyModelUiState.Loading)
    }

    @Test
    fun uiState_onItemSaved_isDisplayed() = runTest {
        val viewModel = MyModelViewModel(FakeMyModelRepository())

        viewModel.addMyModel("Compose")

        assertEquals(
            MyModelUiState.Success(listOf(MyModel(name = "Compose"))),
            viewModel.uiState.first { it is MyModelUiState.Success }
        )
    }
}

private class LoadingMyModelRepository : MyModelRepository {
    override val myModels: Flow<List<MyModel>> = emptyFlow()

    override suspend fun add(name: String) = Unit
}

private class FakeMyModelRepository : MyModelRepository {

    private val data = MutableStateFlow<List<MyModel>>(emptyList())

    override val myModels: Flow<List<MyModel>> = data

    override suspend fun add(name: String) {
        data.value = listOf(MyModel(name = name)) + data.value
    }
}
