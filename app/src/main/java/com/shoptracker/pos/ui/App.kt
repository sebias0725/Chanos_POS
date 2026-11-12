package com.shoptracker.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Main : Screen
    data object Products : Screen
    data object Orders : Screen
    data object NewOrder : Screen
    data object Expenses : Screen
    data object Wastes : Screen
    data object Export : Screen
    data object Rates : Screen
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
    val context = LocalContext.current
    val themePrefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var sidebar by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        LocalThemePreference.dark = themePrefs.getBoolean("dark_mode", false)
    }

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
            Row(Modifier.fillMaxSize()) {
                if (sidebar) {
                    Surface(Modifier.width(190.dp).fillMaxHeight(), tonalElevation = 5.dp) {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Button(onClick = { sidebar = false }) { Text("‹ Hide") }
                            listOf(
                                "Dashboard" to Screen.Main, "Products" to Screen.Products,
                                "Orders" to Screen.Orders, "Expenses" to Screen.Expenses,
                                "Wastes" to Screen.Wastes,
                                "Delivery Rates" to Screen.Rates, "History" to Screen.History,
                                "Chart" to Screen.Chart, "Compare" to Screen.Compare,
                                "Export" to Screen.Export
                            ).forEach { (label, destination) ->
                                Button(onClick = { stack.clear(); stack.add(Screen.Main); if (destination != Screen.Main) push(destination); sidebar = false }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                            }
                            Row {
                                Text("Dark Mode")
                                Switch(checked = LocalThemePreference.dark, onCheckedChange = {
                                    LocalThemePreference.dark = it
                                    themePrefs.edit().putBoolean("dark_mode", it).apply()
                                })
                            }
                        }
                    }
                } else {
                    Button(onClick = { sidebar = true }, modifier = Modifier.width(42.dp)) { Text("›") }
                }
                Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.Main -> MainScreen(
                    vm = vm,
                    onProducts = { push(Screen.Products) },
                    onOrders = { push(Screen.Orders) },
                    onExpenses = { push(Screen.Expenses) },
                    onRates = { push(Screen.Rates) },
                    onHistory = { push(Screen.History) },
                    onNewOrder = { push(Screen.NewOrder) },
                    onNewDay = { push(Screen.NewDay) },
                    onChart = { push(Screen.Chart) },
                    onCompare = { push(Screen.Compare) },
                    onOpenDay = { push(Screen.DayDetail(it)) }
                )
                Screen.Products -> ProductsScreen(vm, onBack = { pop() })
                Screen.Orders -> OrdersScreen(vm, onBack = { pop() }, onNewOrder = { push(Screen.NewOrder) })
                Screen.NewOrder -> NewOrderScreen(vm, onBack = { pop() }, onDone = { pop() })
                Screen.Expenses -> ExpensesScreen(vm, onBack = { pop() })
                Screen.Wastes -> WastesScreen(vm, onBack = { pop() })
                Screen.Export -> ExportScreen(vm, onBack = { pop() })
                Screen.Rates -> RatesScreen(vm, onBack = { pop() })
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
    }
}
