package mx.newdragon.pos

import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val exportedAt: Long,
    val lastTicketId: Long,
    val catalog: List<CatalogItem>,
    val tickets: List<TicketDetail>,
    val draft: OrderDraft,
    val printAttempts: List<PrintAttempt>,
    val cashSessions:List<CashSession> = emptyList(),val cashMovements:List<CashMovement> = emptyList()
)

/** Validate the entire document before offering to replace any local data. */
object BackupCodec {
    const val MAX_BYTES = 50 * 1024 * 1024
    private fun JSONObject.integer(key: String): Long {
        val value=get(key)
        require(value is Int || value is Long) { "Número inválido en $key." }
        return (value as Number).toLong()
    }
    private fun JSONObject.text(key: String, max: Int=1000, required: Boolean=false): String {
        val value=get(key)
        require(value is String && value.length<=max && (!required || value.isNotBlank())) { "Texto inválido en $key." }
        return value
    }
    private fun JSONArray.objects(max: Int=200_000): List<JSONObject> {
        require(length()<=max) { "El respaldo supera la cantidad de registros admitida." }
        return (0 until length()).map(::getJSONObject)
    }
    private fun price(value:Long):Long = value.also {require(it in 1..99_999_999) {"Precio fuera de rango."}}
    private fun quantity(value:Long):Int = value.also {require(it in 1..99) {"Cantidad fuera de rango."}}.toInt()
    private fun positive(value:Long):Long = value.also {require(it in 1 until Long.MAX_VALUE) {"Identificador inválido."}}
    private fun time(value:Long):Long = value.also {require(it in 0..253_402_300_799_999) {"Fecha inválida."}}
    private fun <T> unique(values:List<T>,name:String) { require(values.distinct().size==values.size) {"Identificadores duplicados en $name."} }
    private fun line(j:JSONObject):OrderLine = OrderLine(
        key=j.text("key",200,true), productId=j.text("productId",200,true),variantId=j.text("variantId",200,true),
        name=j.text("name",100,true),category=j.text("category",60,true),description=j.text("description",600),
        variant=j.text("variant",60,true),unitCents=price(j.integer("unitCents")),quantity=quantity(j.integer("quantity")),
        selections=j.text("selections",10_000),notes=j.text("notes",300))
    fun decode(raw:String):BackupSnapshot {
        val j=JSONObject(raw)
        require(j.getString("format")=="new-dragon-backup") {"El archivo no es un respaldo de New Dragon."}
        val version=j.integer("version")
        require(version in 1..5) {"Esta versión del respaldo no es compatible. Actualiza la aplicación."}
        require(j.getString("currency")=="MXN") {"El respaldo debe usar pesos mexicanos (MXN)."}
        val catalog=j.getJSONArray("catalog").objects(10_000).map {p ->
            val id=p.text("id",200,true)
            val options=p.getJSONArray("options")
            options.objects(20).forEach {o ->
                o.text("title",100,true)
                require(o.integer("count") in 1..20) {"Número de opciones inválido."}
                val values=o.getJSONArray("options")
                require(values.length() in 1..100) {"Opciones del platillo inválidas."}
                for(i in 0 until values.length()) require(values.get(i) is String && values.getString(i).length in 1..200)
                val disabled=o.optJSONArray("disabled")
                if(disabled!=null) {
                    require(disabled.length()<=values.length()) {"Disponibilidad de opciones inválida."}
                    val names=(0 until values.length()).map(values::getString)
                    for(i in 0 until disabled.length()) require(disabled.get(i) is String && disabled.getString(i) in names) {"Opción oculta inválida."}
                }
            }
            require(p.get("active") is Boolean) {"Disponibilidad inválida."}
            val order=p.integer("sortOrder").also {require(it in Int.MIN_VALUE..Int.MAX_VALUE)}.toInt()
            val product=Product(id,p.text("category",60,true),p.text("name",100,true),p.text("description",600),options.toString(),p.getBoolean("active"),order)
            val variants=p.getJSONArray("variants").objects(100).map {v ->
                val sort=v.integer("sortOrder").also {require(it in Int.MIN_VALUE..Int.MAX_VALUE)}.toInt()
                Variant(v.text("id",200,true),id,v.text("label",60,true),price(v.integer("priceCents")),sort)
            }
            require(variants.isNotEmpty()) {"Cada platillo necesita una presentación."}
            CatalogItem(product,variants)
        }
        unique(catalog.map {it.product.id},"platillos")
        unique(catalog.flatMap {it.variants}.map {it.id},"presentaciones")
        val tickets=j.getJSONArray("tickets").objects().map {t ->
            val ticket=Ticket(positive(t.integer("id")),t.text("clientId",200,true),time(t.integer("createdAt")),
                if(version<5 && version>=3 && t.text("deliveryAddress",300).isNotBlank()) "A domicilio" else t.text("service",60,true),t.text("reference",80),t.text("notes",300),t.integer("totalCents"),
                if(version>=3) t.text("deliveryAddress",300) else "",
                if(version>=3) t.text("customerPhone",40) else "",
                if(version>=4) t.text("paymentMethod",30,true) else "UNSPECIFIED",
                if(version>=4) t.integer("transferCents") else 0L,
                if(version>=4) t.integer("cashReceivedCents") else 0L,
                status=if(version>=5) t.text("status",30,true) else OrderStates.PAID,
                paidAt=if(version>=5) time(t.integer("paidAt")) else time(t.integer("createdAt")),
                deliveredAt=if(version>=5) time(t.integer("deliveredAt")) else 0,
                voided=if(version>=5) {require(t.get("voided") is Boolean);t.getBoolean("voided")} else false,
                voidedAt=if(version>=5) time(t.integer("voidedAt")) else 0,
                voidReason=if(version>=5) t.text("voidReason",300) else "",
                cashSessionId=if(version>=5) t.integer("cashSessionId").also {require(it>=0)} else 0)
            require(ticket.status in OrderStates.all) {"Estado de pedido inválido."}
            if(ticket.status==OrderStates.PENDING) require(ticket.paymentMethod=="UNSPECIFIED" && ticket.paidAt==0L && ticket.deliveredAt==0L && ticket.cashSessionId==0L) {"Pago de pedido pendiente inválido."}
            if(ticket.voided) require(ticket.voidReason.trim().length>=3 && ticket.voidedAt>0) {"Anulación sin motivo o fecha."}
            else require(ticket.voidedAt==0L && ticket.voidReason.isEmpty()) {"Anulación inválida."}
            if(ticket.status==OrderStates.DELIVERED) require(ticket.deliveredAt>0) {"Entrega sin fecha."}
            ticket.payment // Validate all payment invariants before replacing local data.
            val lines=t.getJSONArray("lines").objects(10_000).map {l ->
                val value=line(l)
                val id=positive(value.key.toLongOrNull() ?: error("Identificador de línea inválido."))
                TicketLine(id,ticket.id,value.productId,value.variantId,value.name,value.category,value.description,
                    value.variant,value.unitCents,value.quantity,value.selections,value.notes)
            }
            require(lines.isNotEmpty() && lines.sumOf {it.total}==ticket.totalCents) {"El total del ticket ${ticket.folio} no coincide con sus platillos."}
            TicketDetail(ticket,lines)
        }
        unique(tickets.map {it.ticket.id},"tickets")
        unique(tickets.map {it.ticket.clientId},"pedidos")
        unique(tickets.flatMap {it.lines}.map {it.id},"líneas de tickets")
        val d=j.getJSONObject("draft")
        val draftLines=d.getJSONArray("lines").objects(10_000).map(::line)
        unique(draftLines.map {it.key},"pedido actual")
        val draft=OrderDraft(d.text("clientId",200,true),if(version<5 && version>=3 && d.text("deliveryAddress",300).isNotBlank()) "A domicilio" else d.text("service",60,true),d.text("reference",80),d.text("notes",300),draftLines,
            if(version>=3) d.text("deliveryAddress",300) else "",
            if(version>=3) d.text("customerPhone",40) else "",
            if(version>=4) PaymentDraft.from(d.getJSONObject("payment")) else PaymentDraft(),
            if(version>=5) d.integer("sourceTicketId").also {require(it>=0)} else 0)
        val draftTicket=tickets.firstOrNull {it.ticket.clientId==draft.clientId}?.ticket
        if(draft.sourceTicketId>0) require(draftTicket?.id==draft.sourceTicketId && draftTicket.status==OrderStates.PENDING && !draftTicket.voided) {"El pedido en edición no coincide con un pendiente."}
        else require(draftTicket==null) {"El pedido actual ya está guardado como ticket."}
        val ticketIds=tickets.map {it.ticket.id}.toSet()
        val attempts=j.getJSONArray("printAttempts").objects(500_000).map {p ->
            val attempt=PrintAttempt(positive(p.integer("id")),positive(p.integer("ticketId")),time(p.integer("timestamp")),p.text("status",30,true),p.text("detail",10_000))
            require(attempt.ticketId in ticketIds) {"Hay registros de impresión sin ticket."}
            attempt
        }
        unique(attempts.map {it.id},"impresiones")
        val maxId=tickets.maxOfOrNull {it.ticket.id} ?: 0
        val sequence=if(version>=2) j.integer("lastTicketId") else maxId
        require(sequence in maxId until Long.MAX_VALUE) {"Numeración de folios inválida."}
        val sessions=if(version>=5) j.getJSONArray("cashSessions").objects(100_000).map {v ->
            val session=CashSession(positive(v.integer("id")),time(v.integer("openedAt")),v.integer("openingCents"),time(v.integer("closedAt")),
                v.integer("countedCents"),v.integer("expectedCents"),v.integer("differenceCents"),v.text("notes",300))
            require(session.openingCents in 0..Payments.MAX_CENTS && session.countedCents in 0..Payments.MAX_CENTS) {"Importe de caja inválido."}
            if(session.closedAt>0) require(session.closedAt>=session.openedAt && session.countedCents-session.expectedCents==session.differenceCents) {"Corte de caja inválido."}
            else require(session.countedCents==0L && session.expectedCents==0L && session.differenceCents==0L) {"Caja abierta con corte inválido."}
            session
        } else emptyList()
        unique(sessions.map {it.id},"cajas")
        require(sessions.count {it.closedAt==0L}<=1) {"Hay más de una caja abierta."}
        val sessionIds=sessions.map {it.id}.toSet()
        require(tickets.all {it.ticket.cashSessionId==0L || it.ticket.cashSessionId in sessionIds}) {"Venta vinculada a una caja inexistente."}
        val movements=if(version>=5) j.getJSONArray("cashMovements").objects(200_000).map {v ->
            val refund=if(v.isNull("refundTicketId")) null else positive(v.integer("refundTicketId"))
            CashMovement(positive(v.integer("id")),positive(v.integer("sessionId")),time(v.integer("timestamp")),v.integer("deltaCents"),v.text("concept",300,true),refund).also {m ->
                require(m.sessionId in sessionIds && m.deltaCents!=0L && m.deltaCents in -Payments.MAX_CENTS..Payments.MAX_CENTS) {"Movimiento de caja inválido."}
                if(refund!=null) {
                    val ticket=tickets.firstOrNull {it.ticket.id==refund}?.ticket
                    require(ticket!=null && ticket.voided && ticket.payment.cashAppliedCents>0 && m.deltaCents==-ticket.payment.cashAppliedCents) {"Devolución inválida."}
                }
            }
        } else emptyList()
        unique(movements.map {it.id},"movimientos de caja")
        unique(movements.mapNotNull {it.refundTicketId},"devoluciones")
        return BackupSnapshot(time(j.integer("exportedAt")),sequence,catalog,tickets,draft,attempts,sessions,movements)
    }
}
