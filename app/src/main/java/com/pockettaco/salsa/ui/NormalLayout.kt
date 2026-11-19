package com.pockettaco.salsa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pockettaco.salsa.*

/** The everyday home screen when no controller is attached: a simple app grid. */
@Composable
fun NormalLayout(activity: MainActivity, onOpenSettings: () -> Unit) {
    val apps = remember { loadApps(activity) }
    DisposableEffect(Unit) {
        activity.onBackRequest = { false }
        onDispose { activity.onBackRequest = null }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Home", style = MaterialTheme.typography.headlineSmall,
                color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize()) {
            items(apps, key = { it.pkg }) { app ->
                Column(
                    Modifier.padding(8.dp).clickable { openApp(activity, app.pkg) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val bmp = remember(app.pkg) { app.icon.toBitmap(144, 144).asImageBitmap() }
                    Image(bmp, contentDescription = app.label, modifier = Modifier.size(56.dp))
                    Text(app.label, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
