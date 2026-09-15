package com.example.macro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.macro.ui.EditorScreen
import com.example.macro.ui.HomeScreen
import com.example.macro.ui.MacroViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MacroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refreshMacros()
        setContent {
            MaterialTheme {
                Surface {
                    App(viewModel)
                }
            }
        }
    }
}

@Composable
fun App(viewModel: MacroViewModel) {
    val editor by viewModel.currentEditor.collectAsState()
    var showEditor by remember { mutableStateOf(false) }

    if (editor != null && showEditor) {
        EditorScreen(
            viewModel = viewModel,
            onBack = {
                viewModel.closeMacro()
                viewModel.refreshMacros()
                showEditor = false
            }
        )
    } else {
        HomeScreen(
            viewModel = viewModel,
            onOpen = { id ->
                viewModel.openMacro(id)
                showEditor = true
            }
        )
    }
}
