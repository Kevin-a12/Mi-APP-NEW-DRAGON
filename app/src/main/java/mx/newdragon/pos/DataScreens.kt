package mx.newdragon.pos

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun BackupsScreen(vm:DragonViewModel) {
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri -> uri?.let(vm::backup)}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> uri?.let(vm::readBackup)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Respaldos",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text("Lleva tus datos a otra instalación de New Dragon y continúa desde tu último respaldo.")
        Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Guardar un respaldo",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("Incluye el menú editado, precios, tickets, registros de impresión, pedido actual, anulaciones, cajas y numeración de folios.")
                Button({export.launch("New-Dragon-respaldo-${System.currentTimeMillis()}.json")},enabled=!vm.fileBusy) {Text("Exportar respaldo")}
                Text("Guarda el archivo en una memoria externa, otra computadora o una carpeta en la nube. Una copia que solo está en esta tableta no protege frente a su pérdida.",style=MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Continuar desde un respaldo",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("Selecciona el archivo JSON que exportaste. Podrás revisar su fecha y contenido antes de confirmar.")
                OutlinedButton({open.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!vm.fileBusy) {Text("Cargar respaldo")}
                Text("Restaurar reemplaza el menú, los tickets y el pedido actual de esta instalación. No combina dos bases de datos.",style=MaterialTheme.typography.bodySmall)
            }
        }
        if(vm.fileBusy) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {CircularProgressIndicator(Modifier.size(24.dp));Text("Procesando archivo…")}
        Text(if(vm.lastBackupAt==0L) "Último respaldo: todavía no has guardado uno en este equipo." else "Último respaldo: ${RestaurantTime.date(vm.lastBackupAt)}",fontWeight=FontWeight.Bold)
        Text("Los respaldos son manuales: los cambios posteriores a la exportación requieren un respaldo nuevo. En otra tableta deberás volver a vincular la impresora.",style=MaterialTheme.typography.bodySmall)
    }
    vm.pendingBackup?.let { snapshot ->
        AlertDialog(onDismissRequest=vm::dismissBackup,title={Text("¿Restaurar este respaldo?")},text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Creado: ${ReceiptContent.date(snapshot.exportedAt)}")
                Text("${snapshot.catalog.size} platillos\n${snapshot.tickets.size} tickets\n${snapshot.draft.lines.size} líneas en el pedido actual\n${snapshot.cashSessions.size} cajas / cortes")
                Text("Reemplazará los datos actuales de esta instalación. Exporta primero un respaldo si necesitas conservarlos.",fontWeight=FontWeight.Bold)
            }
        },dismissButton={TextButton(vm::dismissBackup) {Text("Cancelar")}},confirmButton={
            Button(vm::restoreBackup,enabled=!vm.printing&&!vm.saving&&!vm.deleting&&!vm.fileBusy&&!vm.cashBusy&&!vm.menuSaving) {Text("Restaurar y reemplazar")}
        })
    }
}

@Composable
private fun ReportDateButton(label:String,date:LocalDate,onChoose:(LocalDate)->Unit) {
    val context=LocalContext.current
    OutlinedButton({DatePickerDialog(context,{_,year,month,day -> onChoose(LocalDate.of(year,month+1,day))},date.year,date.monthValue-1,date.dayOfMonth).show()}) {
        Text("$label: ${date.format(ReportDateFormat)}")
    }
}
@Composable
fun ReportsScreen(vm:DragonViewModel,tickets:List<Ticket>) {
    var fromEpoch by rememberSaveable {mutableLongStateOf(RestaurantTime.today().withDayOfMonth(1).toEpochDay())}
    var throughEpoch by rememberSaveable {mutableLongStateOf(RestaurantTime.today().toEpochDay())}
    val from=LocalDate.ofEpochDay(fromEpoch)
    val through=LocalDate.ofEpochDay(throughEpoch)
    val zone=RestaurantTime.zone
    val range=remember(from,through,zone) {if(from<=through) ReportRange(from,through,zone) else null}
    val computed by produceState<IncomeReport?>(null,range,tickets) {
        value=null
        if(range!=null) value=withContext(Dispatchers.Default) {IncomeReport.create(range,tickets)}
    }
    val report=computed?.takeIf {it.range==range}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {uri -> uri?.let(vm::exportReport)}
    LazyColumn(Modifier.fillMaxSize().testTag("report-scroll"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Reporte de ingresos",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(RestaurantTime.label,style=MaterialTheme.typography.bodySmall)
            Text("Ventas con pago registrado. Pendientes y anulados se muestran por separado. No descuenta costos ni gastos.",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ReportDateButton("Desde",from) {fromEpoch=it.toEpochDay()}
                ReportDateButton("Hasta",through) {throughEpoch=it.toEpochDay()}
                TextButton({val today=RestaurantTime.today();fromEpoch=today.toEpochDay();throughEpoch=fromEpoch}) {Text("Hoy")}
                TextButton({val today=RestaurantTime.today();fromEpoch=today.withDayOfMonth(1).toEpochDay();throughEpoch=today.toEpochDay()}) {Text("Este mes")}
            }
        }
        if(range==null) item {Text("La fecha inicial debe ser anterior o igual a la final.",color=MaterialTheme.colorScheme.error)}
        else if(report==null) item {CircularProgressIndicator(Modifier.size(24.dp))}
        else {
            item {
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("Importe total MXN",style=MaterialTheme.typography.bodyMedium,color=DragonMuted)
                            Text(Money.show(report.totalCents),style=MaterialTheme.typography.headlineLarge)
                        }
                        @Composable fun summary(label:String,value:String) {
                            Column(Modifier.widthIn(min=130.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(label,style=MaterialTheme.typography.bodySmall,color=DragonMuted)
                                Text(value,style=MaterialTheme.typography.titleLarge)
                            }
                        }
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            summary("Efectivo",Money.show(report.cashCents))
                            summary("Transferencia",Money.show(report.transferCents))
                            if(report.unspecifiedCents>0) summary("Sin especificar",Money.show(report.unspecifiedCents))
                        }
                        HorizontalDivider(color=DragonLine)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            summary("Tickets",report.ticketCount.toString())
                            summary("Promedio por ticket",Money.show(report.averageCents))
                            summary("Tickets anulados",report.voidedCount.toString())
                            summary("Importe de pedidos anulados",Money.show(report.voidedCents))
                        }
                    }
                }
            }
            item {
                Button({vm.prepareReportExport(report);export.launch("New-Dragon-ingresos-${from}-${through}.pdf")},enabled=!vm.fileBusy) {Text("Exportar reporte PDF")}
                Text("Efectivo: importe aplicado al pedido; excluye el cambio devuelto.",style=MaterialTheme.typography.bodySmall)
                if(report.unspecifiedCents>0) Text("Sin especificar: tickets anteriores sin forma de pago registrada.",style=MaterialTheme.typography.bodySmall)
                Text("Incluye ambos días completos. Los pagos pendientes se suman en la fecha de cobro. El importe anulado no confirma una devolución.",style=MaterialTheme.typography.bodySmall)
            }
            val pending=tickets.filter {it.status==OrderStates.PENDING && !it.voided && it.createdAt>=range!!.startMillis && it.createdAt<range.endExclusiveMillis}
            item {Info("Pendientes del periodo: ${pending.size} · ${Money.show(pending.sumOf {it.totalCents})}. No se suman a ingresos. Abre Historial para cobrarlos.")}
            if(report.days.isEmpty()) item {Text("No hay ventas con pago registrado en este periodo.")}
            items(report.days,key={it.date.toEpochDay()}) {day ->
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${day.date.format(ReportDateFormat)} · ${day.ticketCount} tickets",fontWeight=FontWeight.Bold)
                        Text("Total: ${Money.show(day.totalCents)}",fontWeight=FontWeight.Bold)
                        Text("Efectivo: ${Money.show(day.cashCents)}")
                        Text("Transferencia: ${Money.show(day.transferCents)}")
                        if(day.unspecifiedCents>0) Text("Sin especificar: ${Money.show(day.unspecifiedCents)}")
                    }
                }
            }
        }
    }
}
