package com.pockettaco.salsa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** When a setting takes effect, shown as a small tag beside it. */
enum class Applies(val label: String) {
    NOW("Right away"),
    NEXT_GAME("Next game you start"),
    RESTART("After restarting the app"),
}

@Composable
private fun AppliesTag(applies: Applies?) {
    if (applies == null) return
    Text(applies.label, color = Color(0xFF8FD3FF), style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(Color(0xFF8FD3FF).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp))
}

/** A heading for a group of settings. */
@Composable
fun SettingHeader(title: String, about: String? = null) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        if (about != null) Text(about, color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
    }
}

/** An on/off setting with a plain explanation under it. */
@Composable
fun SettingSwitch(title: String, about: String, checked: Boolean, applies: Applies? = Applies.NOW, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = Color.White)
            Text(about, color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp))
            AppliesTag(applies)
        }
        Switch(checked, onChange)
    }
}

/** Pick one of a few options; each has its own explanation, shown for the picked one. */
@Composable
fun <T> SettingChoice(
    title: String, about: String?, options: List<Triple<T, String, String>>, selected: T,
    applies: Applies? = Applies.NOW,
    preview: (@Composable (T) -> Unit)? = null,
    onPick: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            AppliesTag(applies)
        }
        if (about != null) Text(about, color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { (value, label, _) ->
                val on = value == selected
                Column(
                    Modifier.weight(1f).clip8()
                        .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color(0xFF444444), RoundedCornerShape(10.dp))
                        .clickable { onPick(value) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    preview?.invoke(value)
                    Text((if (on) "✓ " else "") + label, color = if (on) Color.White else Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        options.firstOrNull { it.first == selected }?.let {
            Text(it.third, color = Color(0xFFCCCCCC), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Modifier.clip8() = this.then(Modifier.background(Color(0x14FFFFFF), RoundedCornerShape(10.dp)))
