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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.template.ui.theme.MyApplicationTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MyModelScreen(
    modifier: Modifier = Modifier,
    viewModel: MyModelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MyModelScreen(
        uiState = uiState,
        onSave = viewModel::addMyModel,
        modifier = modifier,
    )
}

@Composable
internal fun MyModelScreen(
    uiState: MyModelUiState,
    onSave: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val state = uiState) {
        is MyModelUiState.Success -> MyModelScreen(
            items = state.data,
            onSave = onSave,
            modifier = modifier,
        )

        MyModelUiState.Loading -> Text("Loading...", modifier = modifier)
        is MyModelUiState.Error -> Text(
            text = state.throwable.message ?: "Unable to load saved items.",
            modifier = modifier,
        )
    }
}

@Composable
internal fun MyModelScreen(
    items: List<MyModel>,
    onSave: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nameMyModel by remember { mutableStateOf("Compose") }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    modifier = Modifier.weight(1f),
                    value = nameMyModel,
                    onValueChange = { nameMyModel = it },
                    label = { Text("Item name") },
                )

                Button(
                    modifier = Modifier.width(96.dp),
                    enabled = nameMyModel.isNotBlank(),
                    onClick = { onSave(nameMyModel.trim()) }
                ) {
                    Text("Save")
                }
            }
        }
        items(items, key = { it.uid }) {
            Text("Saved item: ${it.name}")
        }
    }
}

// Previews

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    MyApplicationTheme {
        MyModelScreen(sampleItems(), onSave = {})
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun PortraitPreview() {
    MyApplicationTheme {
        MyModelScreen(sampleItems(), onSave = {})
    }
}

private fun sampleItems() = listOf(
    MyModel(uid = 1, name = "Compose"),
    MyModel(uid = 2, name = "Room"),
    MyModel(uid = 3, name = "Kotlin"),
)
