package mx.newdragon.pos

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun printLabel(attempt:PrintAttempt?)=when(attempt?.status) {"ERROR"->"Error de envío";"SENT"->"Datos enviados";"CONFIRMED"->"Impresión confirmada";else->"Sin enviar"}
@Composable fun TicketBadge(ticket:Ticket) {
    val color=when {ticket.voided->DragonRed;ticket.status==OrderStates.PENDING->Color(0xFF976111);else->Color(0xFF386448)}
    Surface(color=color.copy(alpha=0.1f),shape=MaterialTheme.shapes.small) {Text(OrderStates.label(ticket),Modifier.padding(horizontal=9.dp,vertical=4.dp),color=color,style=MaterialTheme.typography.labelMedium)}
}
@Composable fun TicketsScreen(vm:DragonViewModel,tickets:List<Ticket>,attempts:List<PrintAttempt>,sessions:List<CashSession>) {
    val detail=vm.selectedTicket
    var query by rememberSaveable {mutableStateOf("")}
    var period by rememberSaveable {mutableStateOf("Hoy")}
    var state by rememberSaveable {mutableStateOf("Todos")}
    var payment by rememberSaveable {mutableStateOf("Todos")}
    var service by rememberSaveable {mutableStateOf("Todos")}
    var more by rememberSaveable {mutableStateOf(false)}
    var reprint by rememberSaveable {mutableStateOf(false)}
    var voidOpen by rememberSaveable {mutableStateOf(false)}
    var printWhenPermitted by rememberSaveable {mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        if(granted && printWhenPermitted) vm.selectedTicket?.let {vm.print(it)} else if(!granted) vm.message="Permite dispositivos cercanos para imprimir."
        printWhenPermitted=false
    }
    fun printNow() {if(vm.printer.hasPermission()) detail?.let {vm.print(it)} else {printWhenPermitted=true;permission.launch(Manifest.permission.BLUETOOTH_CONNECT)}}
    if(detail!=null) {
        val t=detail.ticket
        val prints=attempts.filter {it.ticketId==t.id}.sortedByDescending {it.id}
        val latest=prints.firstOrNull()
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(vm::closeTicket,enabled=!vm.deleting,modifier=Modifier.weight(1f),
                    colors=ButtonDefaults.textButtonColors(contentColor=DragonInk)) {Text("Volver al historial")}
                if(!t.voided && t.status==OrderStates.PENDING)
                    Button({vm.requestResume(detail)},enabled=!vm.saving,modifier=Modifier.weight(1f),
                        shape=MaterialTheme.shapes.medium) {Text("Editar y cobrar")}
                else Button({vm.navigate("Pedidos")},modifier=Modifier.weight(1f),shape=MaterialTheme.shapes.medium) {
                    Text(if(vm.draft.lines.isEmpty()) "Nuevo pedido" else "Ir al pedido actual")
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(t.folio,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                Text(Money.show(t.totalCents),style=MaterialTheme.typography.headlineSmall)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                OutlinedButton({if(prints.isEmpty()) printNow() else reprint=true},
                    enabled=!vm.printing && !vm.deleting && !vm.cashBusy,shape=MaterialTheme.shapes.small,
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=DragonInk)) {
                    Text(if(vm.printing) "Enviando…" else if(prints.isEmpty()) "Imprimir ticket" else "Reimprimir ticket")
                }
                if(!t.voided && t.status==OrderStates.PAID)
                    OutlinedButton({vm.deliver(t)},colors=ButtonDefaults.outlinedButtonColors(contentColor=DragonInk)) {Text("Marcar entregado")}
                if(!t.voided) TextButton({voidOpen=true},enabled=!vm.printing && !vm.deleting && !vm.cashBusy) {Text("Anular ticket",color=DragonRed)}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {TicketBadge(t);Text(printLabel(latest),style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=5.dp))}
            if(vm.printerAddress.isBlank() || latest?.status=="ERROR") TextButton({vm.screen="Ajustes";vm.settingsPage="Impresora"}) {Text("Revisar impresora")}
            if(latest?.status=="SENT") TextButton({vm.confirmPaper(t)},enabled=!vm.printing) {Text("Confirmar que salió completo")}
            if(t.voided) Info("Anulado: ${t.voidReason}. El folio y los registros de impresión se conservan.",true)
            LazyColumn(Modifier.weight(1f).testTag("ticket-preview"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(latest!=null) item {Info("${RestaurantTime.date(latest.timestamp)} · ${latest.detail}")}
                item {
                    Column(Modifier.fillMaxWidth().widthIn(max=650.dp).background(Color.White).padding(18.dp)) {
                        Box(Modifier.fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center) {Image(painterResource(R.drawable.nd_ticket_logo),"Logo del ticket",Modifier.size(76.dp))}
                        ReceiptContent.blocks(detail).forEach {block -> Text(block.text,fontWeight=if(block.bold) FontWeight.Bold else FontWeight.Normal,
                            modifier=Modifier.fillMaxWidth().padding(vertical=3.dp),textAlign=if(block.centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)}
                    }
                }
                if(prints.size>1) item {
                    Text("Últimos envíos",fontWeight=FontWeight.Bold)
                    prints.take(5).forEach {p -> Text("${RestaurantTime.date(p.timestamp)} · ${printLabel(p)}",style=MaterialTheme.typography.bodySmall)}
                }
            }
        }
        if(reprint) AlertDialog(onDismissRequest={reprint=false},title={Text("¿Reimprimir ${t.folio}?")},text={Text("Ya se intentó imprimir este ticket. Revisa el papel antes de repetir para evitar copias parciales o duplicadas. Se conserva el mismo folio.")},dismissButton={TextButton({reprint=false}) {Text("Cancelar")}},confirmButton={Button({reprint=false;printNow()}) {Text("Reimprimir")}})
        if(voidOpen) VoidTicketDialog(vm,t,sessions.any {it.closedAt==0L}) {voidOpen=false}
    } else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        SectionTitle("Historial de pedidos","Busca, cobra pendientes, reimprime o consulta anulaciones.")
        OutlinedTextField(query,{query=it},label={Text("Buscar folio, cliente o teléfono")},singleLine=true,modifier=Modifier.fillMaxWidth(),trailingIcon={if(query.isNotBlank()) TextButton({query=""}) {Text("Limpiar")}})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Choice("Periodo",period,listOf("Hoy","Esta semana","Este mes","Todo")) {period=it}
            Choice("Estado",state,listOf("Todos","Pendiente de pago","Pago registrado","Entregado","Anulados")) {state=it}
            TextButton({more=!more}) {Text(if(more) "Menos filtros" else "Más filtros")}
        }
        if(more) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Choice("Pago",payment,listOf("Todos")+PaymentMethod.entries.map {it.label}) {payment=it}
            Choice("Servicio",service,listOf("Todos")+DeliveryRules.services) {service=it}
        }
        val filter=TicketFilter(query,period,state,payment,service)
        val filtered=remember(tickets,filter) {tickets.filter(filter::matches)}
        Row {
            Text("${filtered.size} pedidos · Ventas vigentes: ${Money.show(filtered.filter {!it.voided && it.status!=OrderStates.PENDING}.sumOf {it.totalCents})}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
            if(query.isNotBlank() || period!="Todo" || state!="Todos" || payment!="Todos" || service!="Todos") TextButton({query="";period="Todo";state="Todos";payment="Todos";service="Todos"}) {Text("Quitar filtros")}
        }
        if(filtered.isEmpty()) Info(if(tickets.isEmpty()) "Aquí aparecerán los pedidos que guardes." else "No hay coincidencias. Cambia el periodo o quita los filtros.")
        val byTicket=remember(attempts) {attempts.groupBy {it.ticketId}.mapValues {it.value.maxBy {p -> p.id}}}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(filtered,key={it.id}) {t -> Card(onClick={vm.openTicket(t.id)},colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row {Text(t.folio,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(Money.show(t.totalCents),fontWeight=FontWeight.Bold)}
                    Text("${RestaurantTime.date(t.createdAt)} · ${t.service}${if(t.reference.isBlank()) "" else " · ${t.reference}"}",style=MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        TicketBadge(t);Text(printLabel(byTicket[t.id]),style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=6.dp))
                        if(t.status!=OrderStates.PENDING) Text(t.payment.method.label,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=6.dp))
                    }
                }
            }}
        }
    }
}
@Composable private fun VoidTicketDialog(vm:DragonViewModel,ticket:Ticket,cashOpen:Boolean,dismiss:()->Unit) {
    var reason by rememberSaveable {mutableStateOf("")}
    var refund by rememberSaveable {mutableStateOf(false)}
    var closeConfirm by rememberSaveable {mutableStateOf(false)}
    fun close() {if(!vm.deleting) {if(reason.isNotBlank() || refund) closeConfirm=true else dismiss()}}
    val cash=ticket.payment.cashAppliedCents
    AlertDialog(onDismissRequest=::close,title={Text("Anular ${ticket.folio}")},text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Total: ${Money.show(ticket.totalCents)}. Se conservará el historial y dejará de sumarse a las ventas vigentes.")
            OutlinedTextField(reason,{reason=it.take(300)},label={Text("Motivo de la anulación *")},enabled=!vm.deleting,modifier=Modifier.fillMaxWidth())
            if(cash>0) {
                if(cashOpen) Row(Modifier.fillMaxWidth().toggleable(value=refund,enabled=!vm.deleting,role=Role.Checkbox,onValueChange={refund=it}),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Checkbox(refund,null,enabled=!vm.deleting);Text("Ya devolví ${Money.show(cash)} del efectivo de la caja abierta",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}
                else Info("Si devolviste efectivo, abre una caja y registra la salida. Anular por sí solo no retira dinero del cajón.",true)
            }
            if(ticket.payment.transferCents>0) Info("Una devolución por transferencia debe realizarse y comprobarse en el banco.")
        }
    },dismissButton={TextButton(::close,enabled=!vm.deleting) {Text("Cancelar")}},confirmButton={Button({vm.voidTicket(ticket,reason,refund,dismiss)},enabled=reason.trim().length>=3 && !vm.deleting && !vm.cashBusy) {Text(if(vm.deleting) "Anulando…" else "Confirmar anulación")}})
    DiscardPrompt(closeConfirm,{closeConfirm=false},dismiss)
}
