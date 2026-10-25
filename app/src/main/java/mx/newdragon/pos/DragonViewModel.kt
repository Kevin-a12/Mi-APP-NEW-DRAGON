package mx.newdragon.pos

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class DragonApplication : Application() {
    val repository by lazy { DragonRepository(DragonDatabase.open(this),this) }
}
class DragonViewModel(application: Application): AndroidViewModel(application) {
    val repository=(application as DragonApplication).repository
    val printer=BluetoothPrinter(application)
    private val preferences=application.getSharedPreferences("printer",0)
    private val operations=Channel<suspend () -> Unit>(Channel.UNLIMITED)
    val catalog=repository.dao.observeCatalog().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val tickets=repository.dao.observeTickets().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val printAttempts=repository.dao.observePrints().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val cashSessions=repository.dao.observeCashSessions().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val cashMovements=repository.dao.observeCashMovements().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    var draft by mutableStateOf(OrderDraft()); private set
    var ready by mutableStateOf(false); private set
    var startupError by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set
    var printing by mutableStateOf(false); private set
    var deleting by mutableStateOf(false); private set
    var restoring by mutableStateOf(false); private set
    var fileBusy by mutableStateOf(false); private set
    var pendingBackup by mutableStateOf<BackupSnapshot?>(null); private set
    private var reportForExport:IncomeReport?=null
    var screen by mutableStateOf("Pedidos")
    var settingsPage by mutableStateOf("Inicio")
    var reportPage by mutableStateOf("Ventas")
    var checkoutOpen by mutableStateOf(false)
    var orderingItem by mutableStateOf<CatalogItem?>(null)
    var editingOrderLine by mutableStateOf<OrderLine?>(null)
    var menuItem by mutableStateOf<CatalogItem?>(null)
    var creatingMenu by mutableStateOf(false)
    var menuSaving by mutableStateOf(false);private set
    var cashBusy by mutableStateOf(false);private set
    var lastRemoved by mutableStateOf<Pair<OrderLine,Int>?>(null);private set
    var resumeCandidate by mutableStateOf<TicketDetail?>(null);private set
    var lastBackupAt by mutableStateOf(preferences.getLong("lastBackupAt",0));private set
    var selectedTicket by mutableStateOf<TicketDetail?>(null); private set
    var message by mutableStateOf<String?>(null)
    var printerAddress by mutableStateOf(preferences.getString("address","") ?: ""); private set
    var printerName by mutableStateOf(preferences.getString("name","") ?: ""); private set
    var printWidth by mutableStateOf(preferences.getInt("width",576)); private set
    var printPace by mutableStateOf(PrintPace.entries.firstOrNull { it.name==preferences.getString("pace",null) } ?: PrintPace.FAST); private set
    var receiptCut by mutableStateOf(ReceiptCut.entries.firstOrNull { it.name==preferences.getString("cut",null) } ?: ReceiptCut.FULL); private set
    init {
        viewModelScope.launch {
            try { repository.ensureSeed(); draft=repository.readDraft(); ready=true }
            catch (e:Exception) { startupError="No se pudo abrir la base de datos: ${e.message}"; return@launch }
            for (operation in operations) try { operation() } catch(e:Exception) { message=e.message ?: "No se pudo completar la operación." }
        }
    }
    private fun mutate(change:(OrderDraft)->OrderDraft) {
        if (!ready || saving || restoring || deleting) return
        draft=change(draft)
        val snapshot=draft
        operations.trySend { repository.saveDraft(snapshot) }
    }
    fun add(line:OrderLine)=mutate { it.copy(lines=it.lines+line,payment=it.payment.copy(exactCash=false,transferConfirmed=false)) }
    fun editLine(line:OrderLine)=mutate {it.copy(lines=it.lines.map {old -> if(old.key==line.key) line else old},payment=it.payment.copy(exactCash=false,transferConfirmed=false))}
    fun quantity(key:String,delta:Int)=mutate { d -> d.copy(lines=d.lines.mapNotNull { l ->
        if(l.key!=key) l else (l.quantity+delta).let { q -> l.copy(quantity=q.coerceIn(1,99)) }
    },payment=d.payment.copy(exactCash=false,transferConfirmed=false)) }
    fun removeLine(key:String) {
        if(saving || restoring) return
        val index=draft.lines.indexOfFirst {it.key==key}
        if(index<0) return
        lastRemoved=draft.lines[index] to index
        mutate {it.copy(lines=it.lines.filterNot {l -> l.key==key},payment=it.payment.copy(exactCash=false,transferConfirmed=false))}
    }
    fun undoRemove() {
        val removed=lastRemoved ?: return
        mutate {d -> d.copy(lines=d.lines.toMutableList().apply {add(removed.second.coerceAtMost(size),removed.first)})}
        lastRemoved=null
    }
    fun clearDraft() {lastRemoved=null;checkoutOpen=false;mutate { OrderDraft() }}
    fun orderInfo(service:String=draft.service,reference:String=draft.reference,notes:String=draft.notes,
        deliveryAddress:String=draft.deliveryAddress,customerPhone:String=draft.customerPhone)=
        mutate { it.copy(service=service,reference=reference.take(80),notes=notes.take(300),
            deliveryAddress=if(service=="A domicilio") deliveryAddress.take(300) else "",customerPhone=customerPhone.take(40)) }
    fun paymentMethod(value:PaymentMethod)=mutate { if(it.payment.method==value) it else it.copy(payment=PaymentDraft(value)) }
    fun paymentAmounts(transfer:String=draft.payment.transferInput,cash:String=draft.payment.cashReceivedInput)=
        mutate {it.copy(payment=it.payment.copy(transferInput=transfer.take(16),cashReceivedInput=cash.take(16),exactCash=false,transferConfirmed=if(transfer!=it.payment.transferInput) false else it.payment.transferConfirmed))}
    fun exactPayment()=mutate {it.copy(payment=it.payment.copy(exactCash=!it.payment.exactCash,cashReceivedInput=""))}
    fun confirmTransfer(value:Boolean)=mutate {it.copy(payment=it.payment.copy(transferConfirmed=value))}
    fun showCheckout() {
        runCatching {DeliveryRules.validate(draft);require(draft.lines.isNotEmpty()) {"Añade un platillo al pedido."}}
            .onSuccess {checkoutOpen=true}.onFailure {message=it.message}
    }
    fun navigate(destination:String) {
        screen=destination
        if(destination=="Historial") selectedTicket=null
    }
    fun saveTicket(printAfter:Boolean=false,paid:Boolean=true) {
        if(!ready || saving || restoring || draft.lines.isEmpty()) return
        val validation=runCatching {DeliveryRules.validate(draft);if(paid) draft.payment.resolve(draft.total)}
        if(validation.isFailure) {message=validation.exceptionOrNull()?.message;return}
        saving=true
        val snapshot=draft
        val next=OrderDraft()
        operations.trySend {
            try {
                val result=repository.checkout(snapshot,next,paid)
                draft=next;lastRemoved=null;checkoutOpen=false; selectedTicket=result; screen="Historial"
                message="Ticket ${result.ticket.folio} guardado."
                if(printAfter) {
                    if(snapshot.sourceTicketId>0 && repository.dao.printAttempts().any {it.ticketId==result.ticket.id}) message="Pedido actualizado. Revisa el papel antes de reimprimir su ticket."
                    else print(result)
                }
            } finally { saving=false }
        }
    }
    fun saveProduct(product:Product,variants:List<Variant>,done:()->Unit) {
        if(restoring || menuSaving) return
        menuSaving=true
        operations.trySend {try { repository.updateProduct(product,variants); done(); message="Platillo guardado." } finally {menuSaving=false} }
    }
    fun openTicket(id:Long) { viewModelScope.launch { runCatching { repository.dao.detail(id) }
        .onSuccess { selectedTicket=it }.onFailure { message=it.message } } }
    fun closeTicket() { selectedTicket=null }
    fun voidTicket(ticket:Ticket,reason:String,refund:Boolean=false,done:()->Unit={}) {
        if(!ready || printing || deleting || restoring) return
        deleting=true
        operations.trySend {
            try {
                repository.voidTicket(ticket.id,reason,refund)
                selectedTicket=repository.dao.detail(ticket.id)
                draft=repository.readDraft()
                message="Ticket ${ticket.folio} anulado. Se conserva su historial."
                done()
            } finally { deleting=false }
        }
    }
    fun deliver(ticket:Ticket) {
        operations.trySend {repository.markDelivered(ticket.id);selectedTicket=repository.dao.detail(ticket.id);message="Pedido marcado como entregado."}
    }
    fun requestResume(detail:TicketDetail) {
        if(draft.lines.isNotEmpty() && draft.sourceTicketId!=detail.ticket.id) resumeCandidate=detail else resume(detail)
    }
    fun dismissResume() {resumeCandidate=null}
    fun resume(detail:TicketDetail) {
        if(saving || restoring) return
        resumeCandidate=null;lastRemoved=null;checkoutOpen=false
        mutate {OrderDraft(clientId=detail.ticket.clientId,service=detail.ticket.service,reference=detail.ticket.reference,notes=detail.ticket.notes,
            lines=detail.lines.map {l -> OrderLine(l.id.toString(),l.productId,l.variantId,l.name,l.category,l.description,l.variant,l.unitCents,l.quantity,l.selections,l.notes)},
            deliveryAddress=detail.ticket.deliveryAddress,customerPhone=detail.ticket.customerPhone,sourceTicketId=detail.ticket.id)}
        screen="Pedidos";selectedTicket=null
    }
    private fun cashAction(action:suspend ()->Unit) {if(cashBusy || restoring) return;cashBusy=true;operations.trySend {try {action()} finally {cashBusy=false}}}
    fun openCash(amount:Long)=cashAction {repository.openCash(amount);message="Caja abierta. Las ventas nuevas se incluirán en el corte."}
    fun cashMovement(amount:Long,concept:String,done:()->Unit)=cashAction {repository.cashMovement(amount,concept);done();message="Movimiento de caja guardado."}
    fun closeCash(amount:Long,notes:String,done:()->Unit)=cashAction {repository.closeCash(amount,notes);done();message="Corte guardado. Recuerda guardar un respaldo."}
    fun confirmPaper(ticket:Ticket) {
        operations.trySend {repository.dao.insertPrint(PrintAttempt(ticketId=ticket.id,timestamp=System.currentTimeMillis(),status="CONFIRMED",detail="El usuario confirmó que salió completo."));message="Impresión confirmada."}
    }
    fun selectPrinter(value:PairedPrinter) {
        printerAddress=value.address; printerName=value.name
        preferences.edit().putString("address",value.address).putString("name",value.name).apply()
    }
    fun width(value:Int) { printWidth=value; preferences.edit().putInt("width",value).apply() }
    fun pace(value:PrintPace) { printPace=value; preferences.edit().putString("pace",value.name).apply() }
    fun cut(value:ReceiptCut) { receiptCut=value; preferences.edit().putString("cut",value.name).apply() }
    fun print(detail:TicketDetail?=null) {
        if(printing || deleting || restoring) return
        if(printerAddress.isBlank()) {message="Ticket guardado. Selecciona una impresora en Ajustes → Impresora.";return}
        printing=true
        val address=printerAddress
        val width=printWidth
        val pace=printPace
        val cut=receiptCut
        viewModelScope.launch {
            var status="SENT"
            var result="Datos enviados. Comprueba que el ticket salió completo antes de repetir la impresión."
            try { printer.print(address,width,detail?.let(ReceiptContent::blocks) ?: ReceiptContent.test(),pace,cut) }
            catch(e:Exception) { status="ERROR"; result="No se completó el envío: ${e.message}. El ticket permanece guardado; revisa el papel antes de reintentar." }
            finally {
                if(detail!=null) runCatching { repository.dao.insertPrint(PrintAttempt(ticketId=detail.ticket.id,timestamp=System.currentTimeMillis(),status=status,detail=result)) }
                    .onFailure { result+=" No se pudo guardar el registro de impresión." }
                printing=false; message=result
            }
        }
    }
    fun backup(uri:Uri) {
        if(fileBusy || restoring) return
        fileBusy=true
        operations.trySend {
            try {
                val json=repository.exportJson()
                withContext(Dispatchers.IO) {
                    checkNotNull(getApplication<Application>().contentResolver.openOutputStream(uri,"wt")) { "No se pudo abrir el archivo." }
                        .bufferedWriter().use { it.write(json) }
                }
                lastBackupAt=System.currentTimeMillis();preferences.edit().putLong("lastBackupAt",lastBackupAt).apply()
                message="Respaldo exportado. Guarda el archivo fuera de la tableta para poder recuperarlo."
            } finally {fileBusy=false}
        }
    }
    fun readBackup(uri:Uri) {
        if(fileBusy || restoring) return
        pendingBackup=null;fileBusy=true
        viewModelScope.launch {
            try {
                val snapshot=withContext(Dispatchers.IO) {
                    val resolver=getApplication<Application>().contentResolver
                    val bytes=checkNotNull(resolver.openInputStream(uri)) {"No se pudo leer el respaldo."}.use {input ->
                        val buffer=ByteArray(16*1024)
                        val result=ByteArrayOutputStream()
                        while(true) {
                            val count=input.read(buffer)
                            if(count<0) break
                            require(result.size()+count<=BackupCodec.MAX_BYTES) {"El respaldo supera el límite de 50 MB."}
                            result.write(buffer,0,count)
                        }
                        result.toByteArray()
                    }
                    BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                }
                pendingBackup=snapshot
            } catch(e:Exception) {message="No se pudo cargar el respaldo: ${e.message}. Tus datos actuales se conservan."}
            finally {fileBusy=false}
        }
    }
    fun dismissBackup() {pendingBackup=null}
    fun restoreBackup() {
        val snapshot=pendingBackup ?: return
        if(restoring || fileBusy || printing || deleting || saving || cashBusy || menuSaving) return
        restoring=true;pendingBackup=null
        operations.trySend {
            try {
                repository.restoreBackup(snapshot)
                draft=snapshot.draft;selectedTicket=null;lastRemoved=null;orderingItem=null;editingOrderLine=null;creatingMenu=false;menuItem=null;checkoutOpen=false;screen="Ajustes";settingsPage="Respaldos"
                message="Respaldo restaurado: ${snapshot.tickets.size} tickets y ${snapshot.catalog.size} platillos."
            } finally {restoring=false}
        }
    }
    fun prepareReportExport(report:IncomeReport) {reportForExport=report}
    fun exportReport(uri:Uri) {
        val report=reportForExport ?: run {message="Vuelve a seleccionar Exportar PDF.";return}
        reportForExport=null
        if(fileBusy || restoring) return
        fileBusy=true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(getApplication<Application>().contentResolver.openOutputStream(uri,"wt")) {"No se pudo crear el PDF."}
                        .use {IncomePdf.write(report,it)}
                }
                message="Reporte PDF exportado: ${report.range.label}."
            } catch(e:Exception) {message="No se pudo exportar el reporte: ${e.message}"}
            finally {fileBusy=false}
        }
    }
}
