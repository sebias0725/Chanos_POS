package com.shoptracker.pos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.DayStats
import com.shoptracker.pos.data.ExportScope
import com.shoptracker.pos.data.Product
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.SalesTransaction
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.util.Fmt

@Composable
fun MainScreen(
    vm: MainViewModel,
    onProducts: () -> Unit,
    onHistory: () -> Unit,
    onNewDay: () -> Unit,
    onChart: () -> Unit,
    onCompare: () -> Unit,
    onOpenDay: (Long) -> Unit
) {
    val products by vm.products.collectAsState()
    val day by vm.openDay.collectAsState()
    val txns by vm.todayTxns.collectAsState()
    val allDays by vm.allDays.collectAsState()

    val stats = remember(txns) { computeStats(txns) }
    val active = remember(products) { products.filter { it.active } }
    val lastSale = remember(txns) { txns.lastOrNull { it.status == STATUS_COMPLETED } }
    val haptic = LocalHapticFeedback.current

    var showExport by remember { mutableStateOf(false) }
    var pendingScope by remember { mutableStateOf<ExportScope?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri ->
        val scope = pendingScope
        pendingScope = null
        if (uri != null && scope != null) vm.export(uri, scope)
    }

    Column(Modifier.fillMaxSize()) {
        TopSummary(
            day = day,
            stats = stats,
            lastSale = lastSale,
            onProducts = onProducts,
            onHistory = onHistory,
            onOpenSummary = { day?.let { onOpenDay(it.id) } }
        )

        Box(Modifier.weight(1f)) {
            when {
                products.isEmpty() -> EmptyState(
                    "No products yet",
                    "Tap \"Products\" at the top to add the items you sell.",
                    Modifier.align(Alignment.Center)
                )
                active.isEmpty() -> EmptyState(
                    "No active products",
                    "All your products are disabled. Enable one in \"Products\".",
                    Modifier.align(Alignment.Center)
                )
                else -> {
                    val unitsById = remember(stats) { stats.products.associate { it.productId to it } }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(active, key = { it.id }) { p ->
                            val s = unitsById[p.id]
                            ProductCard(
                                product = p,
                                units = s?.units ?: 0,
                                revenue = s?.revenue ?: 0L,
                                enabled = day != null,
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.sell(p)
                                }
                            )
                        }
                    }
                }
            }
        }

        BottomBar(
            canUndo = day != null && stats.units > 0,
            highlightNewDay = day == null,
            onUndo = { vm.undo() },
            onNewDay = onNewDay,
            onChart = onChart,
            onCompare = onCompare,
            onExport = { showExport = true }
        )
    }

    if (showExport) {
        ExportDialog(
            days = allDays,
            onDismiss = { showExport = false },
            onConfirm = { scope, fileName ->
                showExport = false
                pendingScope = scope
                launcher.launch(fileName)
            }
        )
    }
}

@Composable
private fun TopSummary(
    day: BusinessDay?,
    stats: DayStats,
    lastSale: SalesTransaction?,
    onProducts: () -> Unit,
    onHistory: () -> Unit,
    onOpenSummary: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(enabled = day != null) { onOpenSummary() }
                ) {
                    Text(
                        if (day != null) Fmt.date(day.openedAt) else "No active business day",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusChip(day)
                }
                TextButton(onClick = onProducts) { Text("Products") }
                TextButton(onClick = onHistory) { Text("History") }
            }

            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Total sales", style = MaterialTheme.typography.labelMedium)
                    Text(
                        Fmt.peso(stats.gross),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Units sold", style = MaterialTheme.typography.labelMedium)
                    Text(
                        stats.units.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (day != null && day.targetUnits > 0) {
                Spacer(Modifier.height(8.dp))
                val progress = (stats.units.toFloat() / day.targetUnits.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                val pct = Fmt.targetPct(stats.units, day.targetUnits)
                Text(
                    "Target: ${stats.units} / ${day.targetUnits} units" + (if (pct != null) " (${pct.toPlainString()}%)" else ""),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (day != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (lastSale == null) "No sales recorded yet today"
                    else "Last sale: ${lastSale.productName} • ${Fmt.time(lastSale.timestamp)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap NEW DAY to begin recording sales.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusChip(day: BusinessDay?) {
    val open = day != null
    Surface(
        shape = RoundedCornerShape(50),
        color = if (open) positiveColor().copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
    ) {
        Text(
            if (open && day != null) "● OPEN since ${Fmt.time(day.openedAt)}" else "○ NO ACTIVE DAY",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (open) positiveColor() else negativeColor()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(
    product: Product,
    units: Int,
    revenue: Long,
    enabled: Boolean,
    onTap: () -> Unit
) {
    Card(
        onClick = onTap,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(Fmt.peso(product.priceCentavos), style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        units.toString(),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 40.sp
                    )
                    Text("sold today", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    Fmt.peso(revenue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    canUndo: Boolean,
    highlightNewDay: Boolean,
    onUndo: () -> Unit,
    onNewDay: () -> Unit,
    onChart: () -> Unit,
    onCompare: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                Text("UNDO", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onNewDay,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
            ) {
                Text(
                    "NEW DAY",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pad = PaddingValues(horizontal = 4.dp)
            OutlinedButton(onClick = onChart, modifier = Modifier.weight(1f).height(48.dp), contentPadding = pad) {
                Text("CHART", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(onClick = onCompare, modifier = Modifier.weight(1f).height(48.dp), contentPadding = pad) {
                Text("COMPARE", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).height(48.dp), contentPadding = pad) {
                Text("EXPORT", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
