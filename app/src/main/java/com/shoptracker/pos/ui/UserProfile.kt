package com.shoptracker.pos.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.io.File

object UserProfile {
    var name by mutableStateOf("")
    var palette by mutableStateOf(0xFF00796B.toInt())
    var photoVersion by mutableIntStateOf(0)
    fun photoFile(context: Context) = File(context.filesDir, "user_profile_photo")
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        name = prefs.getString("user_name", "") ?: ""
        palette = prefs.getInt("user_palette", 0xFF00796B.toInt())
        LocalThemePreference.dark = prefs.getBoolean("dark_mode", false)
    }
    fun save(context: Context, newName: String, source: Uri?) {
        if (source != null) {
            context.contentResolver.openInputStream(source)?.use { input ->
                photoFile(context).outputStream().use { input.copyTo(it) }
            }
            palette = extractPalette(photoFile(context))
            photoVersion++
        }
        name = newName.trim()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("user_name", name).putInt("user_palette", palette).apply()
    }
    private fun extractPalette(file: File): Int {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return 0xFF00796B.toInt()
        val bins = HashMap<Int, Int>()
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val c = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(c) < 180) continue
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(c, hsv)
            if (hsv[1] < 0.22f || hsv[2] < 0.18f || hsv[2] > 0.94f) continue
            val r = (AndroidColor.red(c) / 32) * 32 + 16
            val g = (AndroidColor.green(c) / 32) * 32 + 16
            val b = (AndroidColor.blue(c) / 32) * 32 + 16
            val key = AndroidColor.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
            bins[key] = (bins[key] ?: 0) + 1
        }
        bitmap.recycle()
        return bins.maxByOrNull { it.value }?.key ?: 0xFF00796B.toInt()
    }
}

@Composable
fun UserPhoto(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = UserProfile.photoVersion
    val image = remember(version) {
        val file = UserProfile.photoFile(context)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
    }
    if (image != null) Image(bitmap = image, contentDescription = "User photo", modifier = modifier, contentScale = ContentScale.Crop)
}

@Composable
fun CreateUserScreen(onCreated: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Create User", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { picker.launch("image/*") }) { Text(if (photo == null) "Choose User Photo" else "Change Photo") }
        photo?.let { uri ->
            val preview = remember(uri) { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
            if (preview != null) Image(preview, "Selected photo", Modifier.size(110.dp), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("User Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = { UserProfile.save(context, name, photo); onCreated() }, enabled = name.isNotBlank() && photo != null, modifier = Modifier.fillMaxWidth()) {
            Text("Create User")
        }
    }
}
