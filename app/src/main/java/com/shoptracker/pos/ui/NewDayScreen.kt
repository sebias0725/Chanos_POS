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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.util.Fmt

/**
 * Two-step flow derived from the database state:
 *  - a business day is open  -> step 1: close it (review sales + expenses, enter final amounts)
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
    val data = rememberDayData(vm, setOf(day.id))
    val stats = data.stats

    var finalFloat by remember { mutableStateOf("") }
    var finalCash by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var addingExpense by remember { mutableStateOf(false) }

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
                LabelValue("Product sales", Fmt.peso(stats.productSales))
                LabelValue("Delivery fees", Fmt.peso(stats.deliveryFees))
                LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
                LabelValue("Profit from products", Fmt.peso(stats.profit))
                if (day.targetUnits > 0) {
                    val pct = Fmt.targetPct(stats.units, day.targetUnits)
                    LabelValue("Target", "${stats.units} / ${day.targetUnits}" + (if (pct != null) " (${pct.toPlainString()}%)" else ""))
                }
            }

            SectionCard("Expenses today") {
                if (data.expenses.isEmpty()) {
                    Text("No expenses recorded.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    data.expenses.forEach { e ->
                        LabelValue(e.description, Fmt.peso(e.amountCentavos))
                    }
                }
                LabelValue("Total expenses", Fmt.peso(data.totalExpenses), bold = true)
                LabelValue(
                    "Net (profit + delivery fees - expenses)",
                    Fmt.signedPeso(data.net),
                    valueColor = deltaColor(data.net),
                    bold = true
                )
                OutlinedButton(onClick = { addingExpense = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add expense")
                }
            }

            SectionCard("Opening amounts (for reference)") {
                LabelValue("Opening floating money", Fmt.peso(day.openingFloatCentavos))
                LabelValue("Opening cash on hand", Fmt.peso(day.openingCashCentavos))
            }

            Text(
                "Count your money and enter the final amounts. Sales and expenses are shown for reference only; " +
                    "they are not added to or subtracted from your cash automatically.",
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

    if (addingExpense) {
        ExpenseDialog(
            onDismiss = { addingExpense = false },
            onSave = { desc, amount ->
                vm.addExpense(desc, amount)
                addingExpense = false
            }
        )
    }

    if (confirm && floatC != null && cashC != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Close this business day?") },
            text = {
                Text(
                    "No more sales, orders or expenses can be recorded for ${Fmt.shortDate(day.openedAt)} after closing.\n\n" +
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
    val prevData = rememberDayData(vm, setOfNotNull(prev?.id))

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
                val stats = prevData.stats
                SectionCard("Previous day • ${Fmt.shortDate(prev.openedAt)}") {
                    LabelValue("Total sales", Fmt.peso(stats.gross), bold = true)
                    LabelValue("Units sold", stats.units.toString())
                    LabelValue("Expenses", Fmt.peso(prevData.totalExpenses))
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
            NumberField(
                target, { target = it }, "Target sales (number of meals/products)",
                error = if (showErrors && targetN == null) "Enter a whole number" else null,
                hint = "Leave empty or 0 for no target"
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
