package com.shoptracker.pos.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shoptracker.pos.MainViewModel

/** Persistent preferences for operational defaults and reports. */
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var lowStock by remember { mutableStateOf(prefs.getInt("low_stock_threshold", 5).toString()) }
    var heading by remember { mutableStateOf(prefs.getString("report_heading", "‼️Today's orders") ?: "‼️Today's orders") }
    var includeNotes by remember { mutableStateOf(prefs.getBoolean("report_notes", true)) }
    var includeCancelled by remember { mutableStateOf(prefs.getBoolean("report_cancelled", true)) }
    var includePayment by remember { mutableStateOf(prefs.getBoolean("report_payment", true)) }
    var includeAddress by remember { mutableStateOf(prefs.getBoolean("report_address", true)) }
    var dark by remember { mutableStateOf(LocalThemePreference.dark) }
    val confirm by vm.confirmTaps.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings & Customization", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Dark mode", Modifier.weight(1f)); Switch(dark, { dark = it; LocalThemePreference.dark = it; prefs.edit().putBoolean("dark_mode", it).apply() })
            }
            Text("Inventory alerts", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(lowStock, { lowStock = it.filter(Char::isDigit).take(4) }, label = { Text("Low stock alert threshold (units)") }, modifier = Modifier.fillMaxWidth())
            Text("Order reports", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(heading, { heading = it }, label = { Text("Today's Messenger report heading") }, modifier = Modifier.fillMaxWidth())

            Toggle("Include customer notes", includeNotes, { includeNotes = it })
            Toggle("Include cancelled orders", includeCancelled, { includeCancelled = it })
            Toggle("Include payment details", includePayment, { includePayment = it })
            Toggle("Include delivery address", includeAddress, { includeAddress = it })
            Text("Sales", style = MaterialTheme.typography.titleMedium)
            Toggle("Confirm product taps", confirm, { vm.setConfirmTaps(it) })
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            Text("Tap your profile picture in the Dashboard sidebar to change your business name and logo.")
            Text("Data safety", style = MaterialTheme.typography.titleMedium)
            Text("Order and day deletions require confirmation and cannot be undone. Back up important records before deleting a day.")
        }
        Button(onClick = {
            prefs.edit().putInt("low_stock_threshold", lowStock.toIntOrNull()?.coerceIn(0, 9999) ?: 5)
                .putString("report_heading", heading).putBoolean("report_notes", includeNotes)
                .putBoolean("report_cancelled", includeCancelled).putBoolean("report_payment", includePayment)
                .putBoolean("report_address", includeAddress).apply()
            onBack()
        }, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Save settings") }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
 Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) }
}
