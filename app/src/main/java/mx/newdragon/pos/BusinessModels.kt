package mx.newdragon.pos

import androidx.room.*
import java.time.Instant

object OrderStates {
    const val PENDING="PENDING"
    const val PAID="PAID"
    const val DELIVERED="DELIVERED"
    val all=setOf(PENDING,PAID,DELIVERED)
    fun label(ticket:Ticket)=if(ticket.voided) "Anulado" else when(ticket.status) {
        PENDING -> "Pendiente de pago"
        DELIVERED -> "Entregado"
        else -> "Pago registrado"
    }
}
object DeliveryRules {
    val services=listOf("Para llevar","A domicilio","En mesa")
    fun validate(draft:OrderDraft) {
        require(draft.service in services) {"Elige un tipo de servicio válido."}
        require(draft.service!="A domicilio" || draft.deliveryAddress.trim().isNotBlank()) {"Escribe el domicilio de entrega."}
        require(draft.deliveryAddress.length<=300 && draft.customerPhone.length<=40 && draft.reference.length<=80) {"Revisa los datos de entrega."}
    }
}
@Entity(tableName="cash_sessions")
data class CashSession(@PrimaryKey(autoGenerate=true) val id:Long=0,val openedAt:Long,
    val openingCents:Long,val closedAt:Long=0,val countedCents:Long=0,
    val expectedCents:Long=0,val differenceCents:Long=0,val notes:String="")
@Entity(tableName="cash_movements",foreignKeys=[ForeignKey(entity=CashSession::class,parentColumns=["id"],childColumns=["sessionId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index("sessionId"),Index(value=["refundTicketId"],unique=true)])
data class CashMovement(@PrimaryKey(autoGenerate=true) val id:Long=0,val sessionId:Long,val timestamp:Long,
    val deltaCents:Long,val concept:String,val refundTicketId:Long?=null)
data class CashSummary(val openingCents:Long,val salesCents:Long,val entriesCents:Long,val withdrawalsCents:Long) {
    val expectedCents get()=openingCents+salesCents+entriesCents-withdrawalsCents
    companion object {
        fun create(session:CashSession,tickets:List<Ticket>,movements:List<CashMovement>):CashSummary {
            val relevant=movements.filter {it.sessionId==session.id}
            // Cash received stays in the drawer until a withdrawal/refund is recorded.
            val cash=tickets.filter {it.cashSessionId==session.id && it.status!=OrderStates.PENDING}.sumOf {it.payment.cashAppliedCents}
            return CashSummary(session.openingCents,cash,relevant.filter {it.deltaCents>0}.sumOf {it.deltaCents},
                relevant.filter {it.deltaCents<0}.sumOf {-it.deltaCents})
        }
    }
}
data class TicketFilter(val query:String="",val period:String="Hoy",val state:String="Todos",val payment:String="Todos",val service:String="Todos") {
    fun matches(ticket:Ticket):Boolean {
        val today=RestaurantTime.today()
        val day=Instant.ofEpochMilli(ticket.createdAt).atZone(RestaurantTime.zone).toLocalDate()
        val from=when(period) {"Hoy"->today;"Esta semana"->today.minusDays((today.dayOfWeek.value-1).toLong());"Este mes"->today.withDayOfMonth(1);else->null}
        if(from!=null && (day<from || day>today)) return false
        if(state=="Anulados" && !ticket.voided) return false
        if(state!="Todos" && state!="Anulados" && (ticket.voided || OrderStates.label(ticket)!=state)) return false
        if(payment!="Todos" && ticket.payment.method.label!=payment) return false
        if(service!="Todos" && ticket.service!=service) return false
        val haystack=MenuSearch.normalize("${ticket.folio} ${ticket.reference} ${ticket.customerPhone} ${ticket.deliveryAddress}")
        return MenuSearch.normalize(query).split(Regex("\\s+")).all {it in haystack}
    }
}
fun optionsJson(groups:List<OptionGroup>)=org.json.JSONArray().apply {groups.forEach {g ->
    put(org.json.JSONObject().put("title",g.title).put("count",g.count).put("options",org.json.JSONArray(g.options))
        .put("disabled",org.json.JSONArray(g.disabled.toList())))
} }.toString()
