package com.shoptracker.pos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel
import com.shoptracker.pos.data.BusinessDay
import com.shoptracker.pos.data.STATUS_COMPLETED
import com.shoptracker.pos.data.computeStats
import com.shoptracker.pos.data.isOpen
import com.shoptracker.pos.util.Fmt

@Composable
fun ChartScreen(vm: MainViewModel, onBack: () -> Unit) {
    val open by vm.openDay.collectAsState()
    val days by vm.allDays.collectAsState()
    val day = open ?: days.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sales charts", onBack)
        if (day == null) {
            EmptyState("No business day yet", "Start a business day and record sales to see charts.")
        } else {
            ChartContent(vm, day)
        }
    }
}

@Composable
private fun ChartContent(vm: MainViewModel, day: BusinessDay) {
    val flow = remember(day.id) { vm.txnsFor(day.id) }
    val txns by flow.collectAsState(initial = emptyList())
    val stats = remember(txns) { computeStats(txns) }
    val points = remember(txns) {
        var cum = 0L
        txns.filter { it.status == STATUS_COMPLETED }.map {
            cum += it.totalCentavos
            it.timestamp to cum
        }
    }

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

        SectionCard("Daily sales summary") {
            LabelValue("Total revenue", Fmt.peso(stats.gross), bold = true)
            LabelValue("Total units sold", stats.units.toString(), bold = true)
            LabelValue("Average selling price", if (stats.units == 0) "-" else Fmt.peso(stats.averagePrice))
            if (day.targetUnits > 0) {
                val pct = Fmt.targetPct(stats.units, day.targetUnits)
                LabelValue(
                    "Target progress",
                    "${stats.units} / ${day.targetUnits}" + (if (pct != null) " (${pct.toPlainString()}%)" else "")
                )
                LinearProgressIndicator(
                    progress = { (stats.units.toFloat() / day.targetUnits.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LabelValue("Target progress", "No target set")
            }
        }

        if (stats.units == 0) {
            EmptyState("No sales recorded yet", "Charts will appear after the first sale.")
        } else {
            SectionCard("Sales over time (cumulative)") {
                LineChart(points, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Fmt.time(points.first().first), style = MaterialTheme.typography.labelSmall)
                    Text("Total ${Fmt.peso(points.last().second)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(Fmt.time(points.last().first), style = MaterialTheme.typography.labelSmall)
                }
            }
            SectionCard("Units sold per product") {
                HBars(
                    stats.products.map { BarRow(it.name, it.units.toLong(), it.units.toString()) },
                    MaterialTheme.colorScheme.primary
                )
            }
            SectionCard("Revenue per product") {
                HBars(
                    stats.products.sortedByDescending { it.revenue }
                        .map { BarRow(it.name, it.revenue, Fmt.peso(it.revenue)) },
                    MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun LineChart(points: List<Pair<Long, Long>>, lineColor: Color, axisColor: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val pad = 8.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        // axes
        drawLine(axisColor, Offset(pad, pad), Offset(pad, pad + h), strokeWidth = 2f)
        drawLine(axisColor, Offset(pad, pad + h), Offset(pad + w, pad + h), strokeWidth = 2f)
        if (points.isEmpty()) return@Canvas

        val minT = points.first().first
        val spanT = (points.last().first - minT).coerceAtLeast(1L)
        val maxV = points.last().second.coerceAtLeast(1L)

        fun px(t: Long): Float =
            if (points.size == 1) pad + w / 2f else pad + w * ((t - minT).toFloat() / spanT.toFloat())

        fun py(v: Long): Float = pad + h - h * (v.toFloat() / maxV.toFloat())

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = px(p.first)
            val y = py(p.second)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (points.size > 1) {
            drawPath(
                path,
                lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        if (points.size <= 60) {
            points.forEach { p -> drawCircle(lineColor, radius = 3.5.dp.toPx(), center = Offset(px(p.first), py(p.second))) }
        }
    }
}
