package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.DeliveryRate
import com.shoptracker.pos.data.ORDER_DELIVERY
import com.shoptracker.pos.data.ORDER_PICKUP
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.util.Fmt

@Composable
fun NewOrderScreen(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val day by vm.openDay.collectAsState()
    val products by vm.products.collectAsState()
    val rates by vm.rates.collectAsState()
    val active = remember(products) { products.filter { it.active } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("New order", onBack)
        when {
            day == null -> EmptyState(
                "No active business day",
                "Tap NEW DAY on the main screen to start a business day before taking orders."
            )
            active.isEmpty() -> EmptyState(
                "No active products",
                "Add or enable products first in \"Products\"."
            )
            else -> OrderForm(vm, active, rates, onDone)
        }
    }
}

@Composable
private fun OrderForm(
    vm: MainViewModel,
    products: List<Product>,
    rates: List<DeliveryRate>,
    onDone: () -> Unit
) {
    var type by remember { mutableStateOf(ORDER_PICKUP) }
    var customer by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val qty = remember { mutableStateMapOf<Long, Int>() }
    var showRates by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val delivery = type == ORDER_DELIVERY
    val feeCentavos: Long? = if (delivery) Fmt.parsePeso(fee) else 0L
    val itemsTotal = products.sumOf { it.priceCentavos * (qty[it.id] ?: 0) }
    val totalUnits = products.sumOf { qty[it.id] ?: 0 }
    val grandTotal = itemsTotal + (feeCentavos ?: 0L)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeButton("PICKUP", type == ORDER_PICKUP, { type = ORDER_PICKUP }, Modifier.weight(1f))
                TypeButton("DELIVERY", type == ORDER_DELIVERY, { type = ORDER_DELIVERY }, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = customer,
                onValueChange = { if (it.length <= 60) customer = it },
                label = { Text("Customer name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            if (delivery) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { if (it.length <= 120) address = it },
                    label = { Text("Delivery address / area") },
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showRates = !showRates },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            rates.isEmpty() -> "No delivery fee reference yet (add in DF Rates)"
                            showRates -> "Hide delivery fee reference"
                            else -> "Choose from delivery fee reference"
                        }
                    )
                }
                if (showRates && rates.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth()) {
                        rates.forEach { r ->
                            TextButton(
                                onClick = {
                                    if (address.isBlank()) address = r.name
                                    fee = Fmt.plain(r.feeCentavos)
                                    showRates = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(r.name, modifier = Modifier.weight(1f))
                                    Text(Fmt.peso(r.feeCentavos), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                MoneyField(
                    fee, { fee = it }, "Delivery fee",
                    error = if (showErrors && feeCentavos == null) "Enter the delivery fee (0 if free)" else null
                )
            }

            Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            products.forEach { p ->
                QtyRow(p, qty[p.id] ?: 0) { n ->
                    if (n <= 0) {
                        qty.remove(p.id)
                    } else {
                        qty[p.id] = n
                    }
                    Unit
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text("Note (optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            SectionCard("Order summary") {
                LabelValue("Units", totalUnits.toString())
                LabelValue("Items total", Fmt.peso(itemsTotal))
                if (delivery) LabelValue("Delivery fee", if (feeCentavos == null) "-" else Fmt.peso(feeCentavos))
                LabelValue("TOTAL", Fmt.peso(grandTotal), bold = true)
            }
            if (showErrors && totalUnits == 0) {
                Text("Add at least one item.", color = negativeColor(), style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = {
                if (totalUnits == 0 || feeCentavos == null) showErrors = true else confirm = true
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("SAVE ORDER", fontWeight = FontWeight.Bold) }
    }

    if (confirm && feeCentavos != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Record this order?") },
            text = {
                Text(
                    (if (delivery) "Delivery" else "Pickup") +
                        (if (customer.isNotBlank()) " • ${customer.trim()}" else "") + "\n" +
                        "$totalUnits units • items ${Fmt.peso(itemsTotal)}\n" +
                        (if (delivery) "Delivery fee ${Fmt.peso(feeCentavos)}\n" else "") +
                        "Total ${Fmt.peso(grandTotal)}\n\n" +
                        "It will be counted as sales of the current business day."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (!busy) {
                            busy = true
                            confirm = false
                            val items = products.mapNotNull { p ->
                                val n = qty[p.id] ?: 0
                                if (n > 0) p.id to n else null
                            }
                            vm.createOrder(customer, type, address, feeCentavos, note, items) { ok ->
                                busy = false
                                if (ok) onDone()
                            }
                        }
                    }
                ) { Text("Save order") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TypeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(52.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QtyRow(product: Product, qty: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                Fmt.peso(product.priceCentavos) + "  •  stock ${product.stock}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StepButton("−", onClick = { onChange(qty - 1) }, enabled = qty > 0)
        OutlinedTextField(
            value = if (qty == 0) "" else qty.toString(),
            onValueChange = { v -> if (v.length <= 4 && v.all { c -> c.isDigit() }) onChange(v.toIntOrNull() ?: 0) },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(84.dp)
        )
        StepButton("+", onClick = { onChange(qty + 1) })
    }
}
