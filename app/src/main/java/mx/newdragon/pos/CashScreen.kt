package mx.newdragon.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable fun CashScreen(vm:DragonViewModel,sessions:List<CashSession>,tickets:List<Ticket>,movements:List<CashMovement>) {
    val active=sessions.firstOrNull {it.closedAt==0L}
    var opening by rememberSaveable {mutableStateOf("")}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    var dialog by rememberSaveable {mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=12.dp)) {
        item {SectionTitle("Caja y cortes","Compara el efectivo que debe haber con el que cuentas físicamente.")}
        if(active==null) item {
            Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Abrir caja",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("Escribe el dinero inicial para dar cambio. Las ventas nuevas quedarán ligadas a esta caja.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(opening,{opening=it;error=null},label={Text("Fondo inicial MXN")},placeholder={Text("Puede ser 0")},enabled=!vm.cashBusy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.widthIn(max=480.dp).fillMaxWidth())
                    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                    Button({runCatching {Payments.parse(opening)}.onSuccess {vm.openCash(it)}.onFailure {error=it.message}},enabled=!vm.cashBusy) {Text(if(vm.cashBusy) "Abriendo…" else "Iniciar caja")}
                    Info("Los cobros anteriores a la apertura no se añaden a este corte.")
                }
            }
        } else {
            val summary=CashSummary.create(active,tickets,movements)
            item {Text("Caja #${active.id} · Abierta ${RestaurantTime.date(active.openedAt)}",fontWeight=FontWeight.Bold)}
            item {AmountCard("Efectivo esperado",summary.expectedCents,Modifier.fillMaxWidth())}
            item {FlowRow(horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Fondo inicial" to summary.openingCents,"Ventas en efectivo" to summary.salesCents,"Entradas" to summary.entriesCents,"Salidas" to summary.withdrawalsCents).forEach {(label,value) -> Column {Text(label,style=MaterialTheme.typography.bodySmall);Text(Money.show(value),fontWeight=FontWeight.Bold)}}
            }}
            item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton({dialog="Movimiento"},enabled=!vm.cashBusy) {Text("Registrar entrada o salida")}
                Button({dialog="Cerrar"},enabled=!vm.cashBusy) {Text("Contar y cerrar caja")}
            }}
            item {Info("El cambio ya está descontado de las ventas. Las transferencias no aumentan el efectivo. Una anulación conserva el dinero recibido hasta registrar su devolución.")}
            items(movements.filter {it.sessionId==active.id}.take(20),key={"m${it.id}"}) {m ->
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {Column(Modifier.padding(12.dp)) {Text(m.concept,fontWeight=FontWeight.Bold);Text("${if(m.deltaCents>0) "Entrada" else "Salida"}: ${Money.show(kotlin.math.abs(m.deltaCents))} · ${RestaurantTime.date(m.timestamp)}",style=MaterialTheme.typography.bodySmall)}}
            }
        }
        item {Text("Cortes guardados",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
        val closed=sessions.filter {it.closedAt>0}
        if(closed.isEmpty()) item {Text("Aquí aparecerán los cortes al cerrar una caja.",style=MaterialTheme.typography.bodySmall)}
        items(closed,key={"c${it.id}"}) {s ->
            Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("Caja #${s.id} · ${RestaurantTime.date(s.closedAt)}",fontWeight=FontWeight.Bold)
                Text("Esperado: ${Money.show(s.expectedCents)} · Contado: ${Money.show(s.countedCents)}")
                Text("${if(s.differenceCents==0L) "Cuadra" else if(s.differenceCents<0) "Faltante" else "Sobrante"}: ${Money.show(kotlin.math.abs(s.differenceCents))}",fontWeight=FontWeight.Bold,color=if(s.differenceCents==0L) Color(0xFF386448) else DragonRed)
                if(s.notes.isNotBlank()) Text(s.notes,style=MaterialTheme.typography.bodySmall)
                if(vm.lastBackupAt<s.closedAt) TextButton({vm.screen="Ajustes";vm.settingsPage="Respaldos"}) {Text("Guardar respaldo del cierre")}
            }}
        }
    }
    if(dialog=="Movimiento" && active!=null) CashMovementDialog(vm) {dialog=""}
    if(dialog=="Cerrar" && active!=null) CloseCashDialog(vm,CashSummary.create(active,tickets,movements).expectedCents) {dialog=""}
}
@Composable private fun CashMovementDialog(vm:DragonViewModel,dismiss:()->Unit) {
    var kind by rememberSaveable {mutableStateOf("Salida")}
    var amount by rememberSaveable {mutableStateOf("")}
    var concept by rememberSaveable {mutableStateOf("")}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    var closing by rememberSaveable {mutableStateOf(false)}
    fun close() {if(!vm.cashBusy) {if(amount.isNotBlank() || concept.isNotBlank()) closing=true else dismiss()}}
    AlertDialog(onDismissRequest=::close,title={Text("Movimiento de efectivo")},text={Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Choice("Tipo",kind,listOf("Entrada","Salida"),!vm.cashBusy) {kind=it}
        OutlinedTextField(amount,{amount=it},label={Text("Monto MXN")},enabled=!vm.cashBusy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(concept,{concept=it.take(300)},label={Text("Concepto *")},placeholder={Text("Ej. retiro para pagar un proveedor")},enabled=!vm.cashBusy,modifier=Modifier.fillMaxWidth())
        Info("Registra dinero físico que entra o sale del cajón. Este movimiento no crea una venta ni un gasto en la otra app.")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},dismissButton={TextButton(::close,enabled=!vm.cashBusy) {Text("Cancelar")}},confirmButton={Button({runCatching {
        val parsed=Payments.parse(amount);require(parsed>0) {"El monto debe ser mayor que cero."};require(concept.trim().length>=3) {"Escribe un concepto de al menos 3 caracteres."}
        vm.cashMovement(if(kind=="Entrada") parsed else -parsed,concept,dismiss)
    }.onFailure {error=it.message}},enabled=!vm.cashBusy) {Text(if(vm.cashBusy) "Guardando…" else "Guardar movimiento")}})
    DiscardPrompt(closing,{closing=false},dismiss)
}
@Composable private fun CloseCashDialog(vm:DragonViewModel,expected:Long,dismiss:()->Unit) {
    var counted by rememberSaveable {mutableStateOf("")}
    var notes by rememberSaveable {mutableStateOf("")}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    var closing by rememberSaveable {mutableStateOf(false)}
    val amount=runCatching {Payments.parse(counted)}.getOrNull()
    fun close() {if(!vm.cashBusy) {if(counted.isNotBlank() || notes.isNotBlank()) closing=true else dismiss()}}
    AlertDialog(onDismissRequest=::close,title={Text("Contar y cerrar caja")},text={Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Efectivo esperado: ${Money.show(expected)}",fontWeight=FontWeight.Bold)
        OutlinedTextField(counted,{counted=it},label={Text("Efectivo que contaste MXN")},enabled=!vm.cashBusy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(amount!=null) Text("Diferencia: ${Money.show(amount-expected)}",fontWeight=FontWeight.Bold)
        OutlinedTextField(notes,{notes=it.take(300)},label={Text("Notas del cierre (opcional)")},enabled=!vm.cashBusy,modifier=Modifier.fillMaxWidth())
        Info("El corte quedará guardado con estas cifras. Las próximas ventas necesitarán una nueva caja para incluirse en otro corte.")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},dismissButton={TextButton(::close,enabled=!vm.cashBusy) {Text("Cancelar")}},confirmButton={Button({runCatching {Payments.parse(counted)}.onSuccess {vm.closeCash(it,notes,dismiss)}.onFailure {error=it.message}},enabled=amount!=null && !vm.cashBusy) {Text(if(vm.cashBusy) "Cerrando…" else "Guardar corte")}})
    DiscardPrompt(closing,{closing=false},dismiss)
}
