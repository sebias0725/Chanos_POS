package com.shoptracker.pos.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import com.shoptracker.pos.data.DELIVERY_TIME_SLOTS
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesOrder
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

/** Orders of the open business day (or of the latest day when none is open). */
@Composable
fun OrdersScreen(vm: MainViewModel, onBack: () -> Unit, onNewOrder: () -> Unit) {
    val open by vm.openDay.collectAsState()
    val day = open
    val data = rememberDayData(vm, setOfNotNull(day?.id))
    var cancelling by remember { mutableStateOf<SalesOrder?>(null) }
    var editingTime by remember { mutableStateOf<SalesOrder?>(null) }
    var details by remember { mutableStateOf<SalesOrder?>(null) }
    val context = LocalContext.current
    val orders = remember(data) { data.orders.filter { it.status == STATUS_COMPLETED }.sortedWith(compareBy<SalesOrder> { deliverySortKey(it.deliveryTime) }.thenBy { it.createdAt }) }
    fun copyOrders() {
        val body = buildString {
            append("‼️ Today's Orders")
            orders.forEachIndexed { index, o ->
                val lines = data.txns.filter { it.orderId == o.id && it.status == STATUS_COMPLETED }
                append("\n\n${index + 1}. ${o.customerName.ifBlank { "Customer #${o.id}" }}${if (o.delivered) " [✅ Delivered]" else ""}")
                append("\n- ")
                append(lines.joinToString(", ") { "${it.quantity}x ${it.productName}" })
                val total = lines.sumOf { it.totalCentavos } + o.deliveryFeeCentavos
                append("\n- ${Fmt.peso(total)} (${Fmt.peso(o.deliveryFeeCentavos)} df; ${o.paymentMethod.ifBlank { "Unspecified" }}; ${if (o.paid) "Paid" else "Pending"})")
                if (o.address.isNotBlank()) append("\n- ${o.address}")
                if (o.note.isNotBlank()) append("\n- Additional Notes: ${o.note}")
                append("\n- Time: ${o.deliveryTime.ifBlank { "Pending" }}")
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Today's Orders", body))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScreenHeader(if (day != null) "Orders • ${Fmt.dayMonth(day.openedAt)}" else "Orders", onBack)
            IconButton(onClick = { copyOrders() }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy today's orders") }
        }
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
                            if (o.paymentMethod.isNotBlank()) {
                                LabelValue("Payment", o.paymentMethod)
                            }
                            if (o.type == ORDER_DELIVERY) {
                                LabelValue("Delivery time", o.deliveryTime.ifBlank { "Not recorded" })
                                if (done) {
                                    OutlinedButton(onClick = { editingTime = o }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Edit delivery time")
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (o.delivered) "Delivered" else "Not delivered")
                                Text(if (o.paid) "Paid" else "Unpaid")
                            }
                            OutlinedButton(onClick = { details = o }, modifier = Modifier.fillMaxWidth()) { Text("Order details / status") }
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

    details?.let { selected ->
        val o = orders.firstOrNull { it.id == selected.id } ?: selected
        val lines = data.txns.filter { it.orderId == o.id && it.status == STATUS_COMPLETED }
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("Order #${o.id} • ${o.customerName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lines.forEach { Text("${it.quantity} × ${it.productName} — ${Fmt.peso(it.totalCentavos)}") }
                    Text("Delivery fee: ${Fmt.peso(o.deliveryFeeCentavos)}")
                    Text("Total: ${Fmt.peso(lines.sumOf { it.totalCentavos } + o.deliveryFeeCentavos)}")
                    Text("Payment: ${o.paymentMethod.ifBlank { "Not recorded" }}")
                    Text("Address: ${o.address}")
                    Text("Notes: ${o.note}")
                    Text("Delivery time: ${o.deliveryTime.ifBlank { "Pending" }}")
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = o.paid, onCheckedChange = { vm.setOrderPaid(o.id, it) })
                        Text("Paid")
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = o.delivered, onCheckedChange = { vm.setOrderDelivered(o.id, it) })
                        Text("Delivered (uncheck to reverse)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Done") } }
        )
    }

    editingTime?.let { o ->
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = { Text("Delivery time • order #${o.id}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DELIVERY_TIME_SLOTS.forEach { slot ->
                        val current = o.deliveryTime == slot
                        TextButton(
                            onClick = {
                                if (!current) vm.updateDeliveryTime(o.id, slot)
                                editingTime = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (current) "✓ " else "") + slot,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editingTime = null }) { Text("Close") } }
        )
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

/** Pending and unrecognized delivery times are sorted after every known slot. */
private fun deliverySortKey(time: String): Int {
    val index = DELIVERY_TIME_SLOTS.indexOf(time)
    return if (index <= 0) Int.MAX_VALUE else index
}
