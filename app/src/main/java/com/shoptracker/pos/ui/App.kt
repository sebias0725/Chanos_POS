package com.shoptracker.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.shoptracker.pos.MainViewModel
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Main : Screen
    data object Products : Screen
    data object History : Screen
    data class DayDetail(val dayId: Long) : Screen
    data object Chart : Screen
    data object Compare : Screen
    data object NewDay : Screen
}

@Composable
fun App(vm: MainViewModel) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Main) }
    val screen = stack.last()
    val snackbar = remember { SnackbarHostState() }

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    LaunchedEffect(Unit) {
        vm.events.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(msg, duration = SnackbarDuration.Short) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (screen) {
                Screen.Main -> MainScreen(
                    vm = vm,
                    onProducts = { push(Screen.Products) },
                    onHistory = { push(Screen.History) },
                    onNewDay = { push(Screen.NewDay) },
                    onChart = { push(Screen.Chart) },
                    onCompare = { push(Screen.Compare) },
                    onOpenDay = { push(Screen.DayDetail(it)) }
                )
                Screen.Products -> ProductsScreen(vm, onBack = { pop() })
                Screen.History -> HistoryScreen(vm, onBack = { pop() }, onOpenDay = { push(Screen.DayDetail(it)) })
                is Screen.DayDetail -> DayDetailScreen(
                    vm = vm,
                    dayId = screen.dayId,
                    onBack = { pop() },
                    onCloseDay = { push(Screen.NewDay) }
                )
                Screen.Chart -> ChartScreen(vm, onBack = { pop() })
                Screen.Compare -> CompareScreen(vm, onBack = { pop() })
                Screen.NewDay -> NewDayScreen(vm, onBack = { pop() }, onDone = { pop() })
            }
        }
    }
}
