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

package android.template.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import android.template.data.local.database.MyModel
import android.template.data.local.database.MyModelDao

/**
 * Unit tests for [DefaultMyModelRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class) // TODO: Remove when stable
class DefaultMyModelRepositoryTest {

    @Test
    fun myModels_newItemSaved_itemIsReturned() = runTest {
        val repository = DefaultMyModelRepository(FakeMyModelDao())

        repository.add("Repository")

        assertEquals(listOf(MyModel(uid = 1, name = "Repository")), repository.myModels.first())
    }

}

private class FakeMyModelDao : MyModelDao {

    private val data = MutableStateFlow<List<MyModel>>(emptyList())
    private var nextId = 1

    override fun getMyModels(): Flow<List<MyModel>> = data

    override suspend fun insertMyModel(item: MyModel): Long {
        val inserted = item.copy(uid = nextId++)
        data.value = listOf(inserted) + data.value
        return inserted.uid.toLong()
    }
}
