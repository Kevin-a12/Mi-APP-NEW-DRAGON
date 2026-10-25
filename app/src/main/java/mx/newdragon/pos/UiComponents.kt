package mx.newdragon.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Choice(label: String, value: String, options: List<String>, enabled: Boolean = true, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true }, enabled = enabled,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DragonInk),
            border = BorderStroke(1.dp, DragonLine),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("$label: $value", modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChoose(option); expanded = false })
            }
        }
    }
}

@Composable
fun PrimaryAction(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SecondaryAction(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DragonInk), border = BorderStroke(1.dp, DragonLine),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(text) }
}

@Composable
fun QuantityPicker(name: String, quantity: Int, enabled: Boolean = true, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedIconButton(onDecrease, enabled = enabled && quantity > 1,
            shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, DragonLine),
            modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_remove), "Reducir cantidad de $name", Modifier.size(18.dp))
        }
        Text(quantity.toString(), modifier = Modifier.widthIn(min = 24.dp).semantics {
            contentDescription = "$quantity unidades de $name"
            liveRegion = LiveRegionMode.Polite
        }, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
        OutlinedIconButton(onIncrease, enabled = enabled && quantity < 99,
            shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, DragonLine),
            modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, "Aumentar cantidad de $name", Modifier.size(18.dp))
        }
    }
}

@Composable fun Info(text: String, warning: Boolean = false) {
    Surface(color = if (warning) Color(0xFFFFF3DC) else Color(0xFFF0F2EE), shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (warning) Icons.Default.Warning else Icons.Default.Info, null,
                Modifier.size(18.dp), tint = if (warning) DragonGold else DragonMuted)
            Text(text, style = MaterialTheme.typography.bodySmall, color = DragonInk)
        }
    }
}

@Composable fun SectionTitle(title: String, detail: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = DragonMuted)
    }
}

@Composable fun DiscardPrompt(show: Boolean, keep: () -> Unit, discard: () -> Unit) {
    if (show) AlertDialog(onDismissRequest = keep, title = { Text("Tienes cambios sin guardar") },
        text = { Text("Puedes seguir editando o descartar estos cambios.") },
        dismissButton = { TextButton(keep, colors = ButtonDefaults.textButtonColors(contentColor = DragonInk)) { Text("Seguir editando") } },
        confirmButton = { TextButton(discard) { Text("Descartar cambios") } })
}

@Composable fun AmountCard(label: String, amount: Long, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = DragonMuted)
            Text(Money.show(amount), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
