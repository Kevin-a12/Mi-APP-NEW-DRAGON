package mx.newdragon.pos

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID

@Composable
fun PrinterScreen(vm:DragonViewModel) {
    val context=LocalContext.current
    var paired by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    fun refresh() {runCatching {vm.printer.paired()}.onSuccess {paired=it;if(it.isEmpty()) vm.message="No hay dispositivos vinculados. Vincula la impresora en los ajustes Bluetooth."}.onFailure {vm.message=it.message}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted -> if(granted) refresh() else vm.message="Se necesita permiso de dispositivos cercanos para imprimir."}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Impresora Bluetooth",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text("Vincula primero la impresora en los ajustes de Android. Después selecciónala aquí.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({if(vm.printer.hasPermission()) refresh() else permission.launch(Manifest.permission.BLUETOOTH_CONNECT)}) {Text("Ver dispositivos vinculados")}
            OutlinedButton({context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}) {Text("Abrir ajustes Bluetooth")}
        }
        Info("Impresora seleccionada: ${vm.printerName.ifBlank { "Ninguna" }}. Comprueba el resultado con una impresión de prueba.")
        HorizontalDivider(color=DragonLine)
        Text("Dispositivo",style=MaterialTheme.typography.titleMedium)
        paired.forEach { device ->
            OutlinedButton({vm.selectPrinter(device)}) {Text("${if(vm.printerAddress==device.address) "✓ " else ""}${device.name} · ${device.address}")}
        }
        HorizontalDivider(color=DragonLine)
        Text("Papel y envío",style=MaterialTheme.typography.titleMedium)
        Choice("Ancho de impresión","${vm.printWidth} puntos",listOf("576 puntos","512 puntos","384 puntos")) {vm.width(it.substringBefore(' ').toInt())}
        Text("Para papel de 80 mm, comienza con 576 puntos. Si el texto se recorta, prueba 512. La impresión de prueba permite comprobar el ajuste.",style=MaterialTheme.typography.bodySmall)
        Choice("Velocidad",vm.printPace.label,PrintPace.entries.map {it.label},!vm.printing) {label ->vm.pace(PrintPace.entries.first {it.label==label})}
        Text("Rápida optimizada reduce los datos enviados y conserva la imagen del ticket. Si salen partes incompletas, prueba Equilibrada o Conservadora.",style=MaterialTheme.typography.bodySmall)
        Choice("Corte automático",vm.receiptCut.label,ReceiptCut.entries.map {it.label},!vm.printing) {label ->vm.cut(ReceiptCut.entries.first {it.label==label})}
        Text("El corte se solicita al terminar el avance del papel. Su funcionamiento depende de la cortadora de la impresora.",style=MaterialTheme.typography.bodySmall)
        Button({if(vm.printer.hasPermission()) vm.print() else permission.launch(Manifest.permission.BLUETOOTH_CONNECT)},enabled=vm.printerAddress.isNotBlank()&&!vm.printing) {Text(if(vm.printing) "Enviando…" else "Imprimir prueba")}
    }
}
