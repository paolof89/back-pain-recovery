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

import android.template.data.local.database.MyModel
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [MyModelScreen].
 */
@RunWith(AndroidJUnit4::class)
class MyModelScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            MyModelScreen(FAKE_DATA, onSave = {})
        }
    }

    @Test
    fun firstItem_exists() {
        composeTestRule
            .onNodeWithText("Saved item: ${FAKE_DATA.first().name}")
            .assertExists()
    }

    @Test
    fun loadingState_isDisplayed() {
        composeTestRule.setContent {
            MyModelScreen(uiState = MyModelUiState.Loading, onSave = {})
        }

        composeTestRule.onNodeWithText("Loading...").assertExists()
    }

    @Test
    fun errorState_isDisplayed() {
        composeTestRule.setContent {
            MyModelScreen(
                uiState = MyModelUiState.Error(IllegalStateException("Boom")),
                onSave = {},
            )
        }

        composeTestRule.onNodeWithText("Boom").assertExists()
    }

    @Test
    fun saveButton_usesCurrentInputValue() {
        var savedName = ""

        composeTestRule.setContent {
            MyModelScreen(emptyList(), onSave = { savedName = it })
        }

        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Room")
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.runOnIdle {
            org.junit.Assert.assertEquals("Room", savedName)
        }
    }
}

private val FAKE_DATA = listOf(
    MyModel(uid = 1, name = "Compose"),
    MyModel(uid = 2, name = "Room"),
    MyModel(uid = 3, name = "Kotlin"),
)
