package com.shoptracker.pos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onOpenDay: (Long) -> Unit) {
    val days by vm.allDays.collectAsState()
    val totals by vm.dayTotals.collectAsState()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Business day history", onBack)
        if (days.isEmpty()) {
            EmptyState("No business days yet", "Your daily records will be listed here after you start your first day.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(days, key = { it.id }) { d ->
                    val tot = totals[d.id]
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDay(d.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.date(d.openedAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (d.isOpen) "Open" else "Closed ${Fmt.time(d.closedAt ?: d.openedAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (d.isOpen) positiveColor() else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.peso(tot?.revenue ?: 0L), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${tot?.units ?: 0} units", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayDetailScreen(
    vm: MainViewModel,
    dayId: Long,
    onBack: () -> Unit,
    onCloseDay: () -> Unit
) {
    val days by vm.allDays.collectAsState()
    val day = days.firstOrNull { it.id == dayId }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Day summary", onBack)
        if (day == null) {
            EmptyState("Day not found", "This business day is no longer available.")
        } else {
            DayDetailContent(vm, day, onCloseDay)
        }
    }
}

@Composable
private fun DayDetailContent(vm: MainViewModel, day: BusinessDay, onCloseDay: () -> Unit) {
    val flow = remember(day.id) { vm.txnsFor(day.id) }
    val txns by flow.collectAsState(initial = emptyList())
    val stats = remember(txns) { computeStats(txns) }
    var editFinals by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            Fmt.date(day.openedAt) + if (day.isOpen) " • open" else " • closed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        SectionCard("Sales") {
            LabelValue("Total units sold", stats.units.toString(), bold = true)
            LabelValue("Total gross sales", Fmt.peso(stats.gross), bold = true)
            val pct = Fmt.targetPct(stats.units, day.targetUnits)
            LabelValue("Target sales", if (day.targetUnits > 0) "${day.targetUnits} units" else "No target")
            if (pct != null) LabelValue("Target progress", "${pct.toPlainString()}%")
        }

        SectionCard("Money (physical cash is tracked separately from sales)") {
            LabelValue("Opening floating money", Fmt.peso(day.openingFloatCentavos))
            LabelValue("Opening cash on hand", Fmt.peso(day.openingCashCentavos))
            LabelValue("Final floating money", day.finalFloatCentavos?.let { Fmt.peso(it) } ?: "Not entered yet")
            LabelValue("Final cash on hand", day.finalCashCentavos?.let { Fmt.peso(it) } ?: "Not entered yet")
        }

        if (day.isOpen) {
            Button(onClick = onCloseDay, modifier = Modifier.fillMaxWidth()) { Text("Close this day…") }
        } else {
            OutlinedButton(onClick = { editFinals = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Edit final amounts")
            }
        }

        SectionCard("Products sold") {
            if (stats.products.isEmpty()) {
                Text("No sales recorded for this day.", style = MaterialTheme.typography.bodyMedium)
            } else {
                stats.products.forEach { p ->
                    LabelValue("${p.name} × ${p.units}", Fmt.peso(p.revenue))
                }
            }
        }

        SectionCard("Transactions (${txns.size})") {
            if (txns.isEmpty()) {
                Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                txns.asReversed().forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    val done = t.status == STATUS_COMPLETED
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (done) TextDecoration.None else TextDecoration.LineThrough
                            )
                            Text(
                                "#${t.id} • ${Fmt.time(t.timestamp)}" + if (done) "" else " • UNDONE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            Fmt.peso(t.totalCentavos),
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (done) TextDecoration.None else TextDecoration.LineThrough
                        )
                    }
                }
            }
        }
    }

    if (editFinals) {
        EditFinalsDialog(
            day = day,
            onDismiss = { editFinals = false },
            onSave = { f, c ->
                vm.updateFinals(day.id, f, c)
                editFinals = false
            }
        )
    }
}

@Composable
private fun EditFinalsDialog(day: BusinessDay, onDismiss: () -> Unit, onSave: (Long, Long) -> Unit) {
    var f by remember { mutableStateOf(day.finalFloatCentavos?.let { Fmt.plain(it) } ?: "") }
    var c by remember { mutableStateOf(day.finalCashCentavos?.let { Fmt.plain(it) } ?: "") }
    var showErrors by remember { mutableStateOf(false) }
    val fc = Fmt.parsePeso(f)
    val cc = Fmt.parsePeso(c)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final amounts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(f, { f = it }, "Final floating money", error = if (showErrors && fc == null) "Enter an amount" else null)
                MoneyField(c, { c = it }, "Final cash on hand", error = if (showErrors && cc == null) "Enter an amount" else null)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (fc == null || cc == null) showErrors = true else onSave(fc, cc)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
