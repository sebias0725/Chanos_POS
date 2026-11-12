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
import com.shoptracker.pos.data.DayStats
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.data.finalFloatFrom
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onOpenDay: (Long) -> Unit) {
    val days by vm.allDays.collectAsState()
    val txns by vm.allTxns.collectAsState()
    val orders by vm.allOrders.collectAsState()

    val totals: Map<Long, DayStats> = remember(days, txns, orders) {
        val tx = txns.groupBy { it.businessDayId }
        val od = orders.groupBy { it.businessDayId }
        days.associate { d -> d.id to computeStats(tx[d.id] ?: emptyList(), od[d.id] ?: emptyList()) }
    }

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
                                Text(Fmt.peso(tot?.gross ?: 0L), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    val data = rememberDayData(vm, setOf(day.id))
    val stats = data.stats
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
            LabelValue("Product sales", Fmt.peso(stats.productSales))
            LabelValue("Delivery fees (${stats.orderCount} order" + (if (stats.orderCount == 1) ")" else "s)"), Fmt.peso(stats.deliveryFees))
            LabelValue("Total gross sales", Fmt.peso(stats.gross), bold = true)
            LabelValue("Profit from products", Fmt.peso(stats.profit))
            val pct = Fmt.targetPct(stats.units, day.targetUnits)
            LabelValue("Target sales", if (day.targetUnits > 0) "${day.targetUnits} units" else "No target")
            if (pct != null) LabelValue("Target progress", "${pct.toPlainString()}%")
        }

        SectionCard("Expenses") {
            if (data.expenses.isEmpty()) {
                Text("No expenses recorded.", style = MaterialTheme.typography.bodyMedium)
            } else {
                data.expenses.forEach { e ->
                    LabelValue("${e.description} (${Fmt.time(e.createdAt)})", Fmt.peso(e.amountCentavos))
                }
            }
            LabelValue("Total expenses", Fmt.peso(data.totalExpenses), bold = true)
            LabelValue(
                "Net (profit + delivery fees - expenses)",
                Fmt.signedPeso(data.net),
                valueColor = deltaColor(data.net),
                bold = true
            )
        }

        SectionCard("Money (physical cash is tracked separately from sales)") {
            LabelValue("Opening floating money", Fmt.peso(day.openingFloatCentavos))
            LabelValue("Opening cash on hand", Fmt.peso(day.openingCashCentavos))
            LabelValue("Final floating money", day.finalFloatCentavos?.let { Fmt.peso(it) } ?: "Not entered yet")
            LabelValue("Final cash on hand", day.finalCashCentavos?.let { Fmt.peso(it) } ?: "Not entered yet")
            day.remitCentavos?.let { LabelValue("Remit", Fmt.peso(it)) }
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

        SectionCard("Transactions (${data.txns.size})") {
            if (data.txns.isEmpty()) {
                Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                data.txns.asReversed().forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    val done = t.status == STATUS_COMPLETED
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (t.quantity > 1) "${t.quantity} × " else "") + t.productName,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (done) TextDecoration.None else TextDecoration.LineThrough
                            )
                            Text(
                                "#${t.id} • ${Fmt.time(t.timestamp)}" +
                                    (if (t.orderId != null) " • order #${t.orderId}" else "") +
                                    (if (done) "" else " • UNDONE"),
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
            onSave = { f, c, r ->
                vm.updateFinals(day.id, f, c, r)
                editFinals = false
            }
        )
    }
}

@Composable
private fun EditFinalsDialog(day: BusinessDay, onDismiss: () -> Unit, onSave: (Long, Long, Long?) -> Unit) {
    var f by remember { mutableStateOf(day.finalFloatCentavos?.let { Fmt.plain(it) } ?: "") }
    var c by remember { mutableStateOf(day.finalCashCentavos?.let { Fmt.plain(it) } ?: "") }
    var r by remember { mutableStateOf(day.remitCentavos?.let { Fmt.plain(it) } ?: "") }
    var showErrors by remember { mutableStateOf(false) }
    // Days closed with a remit keep floating = cash - remit; older days keep the manual entry.
    val hasRemit = day.remitCentavos != null
    val rc = Fmt.parsePeso(r)
    val cc = Fmt.parsePeso(c)
    val fc = if (hasRemit) { if (cc != null && rc != null) finalFloatFrom(cc, rc) else null } else Fmt.parsePeso(f)
    val remitTooHigh = hasRemit && fc != null && fc < 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Final amounts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasRemit) {
                    MoneyField(c, { c = it }, "Final cash on hand", error = if (showErrors && cc == null) "Enter an amount" else null)
                    MoneyField(
                        r, { r = it }, "Remit",
                        error = if (showErrors && rc == null) "Enter an amount" else if (remitTooHigh) "Remit cannot be more than cash on hand" else null
                    )
                    LabelValue("Final floating money", if (fc != null && !remitTooHigh) Fmt.peso(fc) else "-", bold = true)
                } else {
                    MoneyField(f, { f = it }, "Final floating money", error = if (showErrors && fc == null) "Enter an amount" else null)
                    MoneyField(c, { c = it }, "Final cash on hand", error = if (showErrors && cc == null) "Enter an amount" else null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (fc == null || cc == null || remitTooHigh || (hasRemit && rc == null)) showErrors = true
                else onSave(fc, cc, if (hasRemit) rc else null)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
