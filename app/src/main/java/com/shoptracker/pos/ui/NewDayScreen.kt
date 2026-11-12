package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.util.Fmt

/**
 * Two-step flow derived from the database state:
 *  - a business day is open  -> step 1: close it (enter final floating money and cash on hand)
 *  - no business day is open -> step 2: start the new day (previous day shown for reference)
 */
@Composable
fun NewDayScreen(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val open by vm.openDay.collectAsState()
    val current = open
    if (current != null) {
        CloseDayStep(vm, current, onBack)
    } else {
        StartDayStep(vm, onBack, onDone)
    }
}

@Composable
private fun CloseDayStep(vm: MainViewModel, day: BusinessDay, onBack: () -> Unit) {
    val flow = remember(day.id) { vm.txnsFor(day.id) }
    val txns by flow.collectAsState(initial = emptyList())
    val stats = remember(txns) { computeStats(txns) }

    var finalFloat by remember { mutableStateOf("") }
    var finalCash by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val floatC = Fmt.parsePeso(finalFloat)
    val cashC = Fmt.parsePeso(finalCash)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Close current day", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Step 1 of 2. Close ${Fmt.date(day.openedAt)} before starting a new day.",
                style = MaterialTheme.typography.bodyMedium
            )
            SectionCard("Today's sales") {
                LabelValue("Total units sold", stats.units.toString(), bold = true)
                LabelValue("Total gross sales", Fmt.peso(stats.gross), bold = true)
                if (day.targetUnits > 0) {
                    val pct = Fmt.targetPct(stats.units, day.targetUnits)
                    LabelValue("Target", "${stats.units} / ${day.targetUnits}" + (if (pct != null) " (${pct.toPlainString()}%)" else ""))
                }
                LabelValue("Opening floating money", Fmt.peso(day.openingFloatCentavos))
                LabelValue("Opening cash on hand", Fmt.peso(day.openingCashCentavos))
            }
            Text(
                "Count your money and enter the final amounts. Sales are not assumed to be cash, " +
                    "so please enter what you actually have.",
                style = MaterialTheme.typography.bodySmall
            )
            MoneyField(
                finalFloat, { finalFloat = it }, "Final floating money",
                error = if (showErrors && floatC == null) "Enter an amount (0 is fine)" else null
            )
            MoneyField(
                finalCash, { finalCash = it }, "Final cash on hand",
                error = if (showErrors && cashC == null) "Enter an amount (0 is fine)" else null
            )
        }
        Button(
            onClick = {
                if (floatC == null || cashC == null) showErrors = true else confirm = true
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("CLOSE DAY", fontWeight = FontWeight.Bold) }
    }

    if (confirm && floatC != null && cashC != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Close this business day?") },
            text = {
                Text(
                    "No more sales can be recorded for ${Fmt.shortDate(day.openedAt)} after closing.\n\n" +
                        "Final floating money: ${Fmt.peso(floatC)}\nFinal cash on hand: ${Fmt.peso(cashC)}"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        confirm = false
                        vm.closeDay(day.id, floatC, cashC) { busy = false }
                    }
                ) { Text("Close day") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StartDayStep(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val days by vm.allDays.collectAsState()
    val prev = days.firstOrNull() // newest, and it is closed because no day is open

    var openFloat by remember { mutableStateOf("") }
    var openCash by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val floatC = Fmt.parsePeso(openFloat)
    val cashC = Fmt.parsePeso(openCash)
    val targetN = if (target.isBlank()) 0 else target.toIntOrNull()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (prev == null) "Start your first day" else "Start new day", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (prev != null) {
                val flow = remember(prev.id) { vm.txnsFor(prev.id) }
                val txns by flow.collectAsState(initial = emptyList())
                val stats = remember(txns) { computeStats(txns) }
                SectionCard("Previous day • ${Fmt.shortDate(prev.openedAt)}") {
                    LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
                    LabelValue("Units sold", stats.units.toString())
                    LabelValue("Final floating money", prev.finalFloatCentavos?.let { Fmt.peso(it) } ?: "-")
                    LabelValue("Final cash on hand", prev.finalCashCentavos?.let { Fmt.peso(it) } ?: "-")
                    Text(
                        "For reference only. Enter the amounts you actually start with today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Opening financial information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            MoneyField(
                openFloat, { openFloat = it }, "Opening floating money (PHP)",
                error = if (showErrors && floatC == null) "Enter an amount (0 is fine)" else null
            )
            if (prev?.finalFloatCentavos != null) {
                TextButton(onClick = { openFloat = Fmt.plain(prev.finalFloatCentavos) }) {
                    Text("Use previous final: ${Fmt.peso(prev.finalFloatCentavos)}")
                }
            }
            MoneyField(
                openCash, { openCash = it }, "Opening cash on hand (PHP)",
                error = if (showErrors && cashC == null) "Enter an amount (0 is fine)" else null
            )
            if (prev?.finalCashCentavos != null) {
                TextButton(onClick = { openCash = Fmt.plain(prev.finalCashCentavos) }) {
                    Text("Use previous final: ${Fmt.peso(prev.finalCashCentavos)}")
                }
            }
            OutlinedTextField(
                value = target,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) target = it },
                label = { Text("Target sales (number of meals/products)") },
                singleLine = true,
                isError = showErrors && targetN == null,
                supportingText = { Text("Leave empty or 0 for no target") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = {
                if (floatC == null || cashC == null || targetN == null) showErrors = true else confirm = true
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("START NEW DAY", fontWeight = FontWeight.Bold) }
    }

    if (confirm && floatC != null && cashC != null && targetN != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Start a new business day?") },
            text = {
                Text(
                    "Opening floating money: ${Fmt.peso(floatC)}\n" +
                        "Opening cash on hand: ${Fmt.peso(cashC)}\n" +
                        "Target: " + (if (targetN > 0) "$targetN units" else "none")
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (!busy) {
                            busy = true
                            confirm = false
                            vm.startDay(floatC, cashC, targetN) { ok ->
                                busy = false
                                if (ok) onDone()
                            }
                        }
                    }
                ) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
