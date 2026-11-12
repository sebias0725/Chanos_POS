package com.shoptracker.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.util.Fmt

@Composable
fun ProductsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val products by vm.products.collectAsState()
    val lifetime by vm.lifetime.collectAsState()
    val todayTxns by vm.todayTxns.collectAsState()
    val todayStats = remember(todayTxns) { com.shoptracker.pos.data.computeStats(todayTxns) }
    val todayById = remember(todayStats) { todayStats.products.associateBy { it.productId } }

    var editing by remember { mutableStateOf<Product?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Product?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Products", onBack)
        Box(Modifier.weight(1f)) {
            if (products.isEmpty()) {
                EmptyState(
                    "No products yet",
                    "Add the items you sell. They will appear as big tap buttons on the main screen.",
                    Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(products, key = { it.id }) { p ->
                        val today = todayById[p.id]
                        val life = lifetime[p.id]
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(Fmt.peso(p.priceCentavos), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Switch(checked = p.active, onCheckedChange = { vm.updateProduct(p.copy(active = it)) })
                                }
                                Text(
                                    "Today: ${today?.units ?: 0} sold • ${Fmt.peso(today?.revenue ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "All time: ${life?.units ?: 0} sold • ${Fmt.peso(life?.revenue ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(
                                        if (p.active) "Active" else "Disabled (hidden from POS)",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .align(Alignment.CenterVertically)
                                    )
                                    IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Edit") }
                                    IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, "Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { adding = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(56.dp)
        ) { Text("ADD PRODUCT", fontWeight = FontWeight.Bold) }
    }

    if (adding) {
        ProductDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { name, price ->
                vm.addProduct(name, price)
                adding = false
            }
        )
    }
    editing?.let { p ->
        ProductDialog(
            initial = p,
            onDismiss = { editing = null },
            onSave = { name, price ->
                vm.updateProduct(p.copy(name = name.trim(), priceCentavos = price))
                editing = null
            }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            text = {
                Text(
                    "The product will be removed from the app. Past sales stay in your records. " +
                        "If you only want to hide it, use the switch to disable it instead."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProduct(p)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProductDialog(
    initial: Product?,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(if (initial != null) Fmt.plain(initial.priceCentavos) else "") }
    var showErrors by remember { mutableStateOf(false) }

    val priceCentavos = Fmt.parsePeso(price)
    val nameError = if (showErrors && name.isBlank()) "Enter a name" else null
    val priceError = if (showErrors && (priceCentavos == null || priceCentavos <= 0L)) "Enter a price above 0" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add product" else "Edit product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Product name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = if (nameError != null) ({ Text(nameError) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                MoneyField(price, { price = it }, "Selling price", error = priceError)
                if (initial != null) {
                    Text(
                        "Changing the price only affects future sales. Past sales keep their original price.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || priceCentavos == null || priceCentavos <= 0L) {
                    showErrors = true
                } else {
                    onSave(name, priceCentavos)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
