package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesOrder
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

/** Orders of the open business day (or of the latest day when none is open). */
@Composable
fun OrdersScreen(vm: MainViewModel, onBack: () -> Unit, onNewOrder: () -> Unit) {
    val open by vm.openDay.collectAsState()
    val days by vm.allDays.collectAsState()
    val day = open ?: days.firstOrNull()
    val data = rememberDayData(vm, setOfNotNull(day?.id))
    var cancelling by remember { mutableStateOf<SalesOrder?>(null) }
    val orders = remember(data) { data.orders.asReversed() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (day != null) "Orders • ${Fmt.dayMonth(day.openedAt)}" else "Orders", onBack)
        if (orders.isEmpty()) {
            EmptyState(
                "No orders yet",
                "Bulk orders, pickups and deliveries you record with NEW ORDER will be listed here.",
                Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.id }) { o ->
                    val lines = data.txns.filter { it.orderId == o.id }
                    val itemsTotal = lines.sumOf { it.totalCentavos }
                    val done = o.status == STATUS_COMPLETED
                    val strike = if (done) TextDecoration.None else TextDecoration.LineThrough
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "#${o.id} • " + (if (o.type == ORDER_DELIVERY) "DELIVERY" else "PICKUP"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (done) Fmt.time(o.createdAt) else "CANCELLED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else negativeColor()
                                )
                            }
                            if (o.customerName.isNotBlank()) {
                                Text(o.customerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (o.address.isNotBlank()) {
                                Text(o.address, style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider()
                            lines.forEach { l ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "${l.quantity} × ${l.productName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = strike,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(Fmt.peso(l.totalCentavos), style = MaterialTheme.typography.bodyMedium, textDecoration = strike)
                                }
                            }
                            if (o.type == ORDER_DELIVERY) {
                                LabelValue("Delivery fee", Fmt.peso(o.deliveryFeeCentavos))
                            }
                            LabelValue("Total", Fmt.peso(itemsTotal + o.deliveryFeeCentavos), bold = true)
                            if (o.note.isNotBlank()) {
                                Text("Note: ${o.note}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (done && day != null && day.isOpen) {
                                OutlinedButton(onClick = { cancelling = o }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancel order")
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onNewOrder,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("NEW ORDER", fontWeight = FontWeight.Bold) }
    }

    cancelling?.let { o ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel order #${o.id}?") },
            text = {
                Text("Its items and delivery fee will be removed from today's sales and the stock will be put back. The order stays in your records as CANCELLED.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.cancelOrder(o.id)
                    cancelling = null
                }) { Text("Cancel order") }
            },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep") } }
        )
    }
}
