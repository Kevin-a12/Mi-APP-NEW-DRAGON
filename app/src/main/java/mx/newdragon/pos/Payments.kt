package mx.newdragon.pos

import org.json.JSONObject
import java.math.BigDecimal

enum class PaymentMethod(val label:String) {
    CASH("Efectivo"), TRANSFER("Transferencia"), MIXED("Mixto"), UNSPECIFIED("Sin especificar");
    companion object {
        val selectable get()=entries.filter {it!=UNSPECIFIED}
        fun decode(value:String)=entries.firstOrNull {it.name==value} ?: error("Forma de pago no válida.")
    }
}

data class PaymentDraft(val method:PaymentMethod=PaymentMethod.CASH,val transferInput:String="",val cashReceivedInput:String="",val exactCash:Boolean=false,val transferConfirmed:Boolean=false) {
    fun json()=JSONObject().put("method",method.name).put("transferInput",transferInput).put("cashReceivedInput",cashReceivedInput).put("exactCash",exactCash).put("transferConfirmed",transferConfirmed)
    fun resolve(total:Long):PaymentBreakdown {
        require(method!=PaymentMethod.UNSPECIFIED) {"Selecciona una forma de pago."}
        require(total in 1..Payments.MAX_CENTS) {"Añade platillos con un total válido."}
        val transfer=when(method) {
            PaymentMethod.TRANSFER -> total
            PaymentMethod.MIXED -> {
                require(transferInput.isNotBlank()) {"Escribe el importe por transferencia."}
                Payments.parse(transferInput).also {require(it>0 && it<total) {"En un pago mixto, la transferencia debe ser mayor que cero y menor que el total."}}
            }
            else -> 0L
        }
        if(transfer>0) require(transferConfirmed) {"Confirma que recibiste la transferencia."}
        val cashDue=total-transfer
        val received=if(method==PaymentMethod.TRANSFER) 0L else if(exactCash) cashDue else {
            require(cashReceivedInput.isNotBlank()) {"Escribe el efectivo recibido o elige Pago exacto."}
            Payments.parse(cashReceivedInput)
        }
        require(received>=cashDue) {"Faltan ${Money.show(cashDue-received)} en efectivo."}
        return Payments.recorded(total,method,transfer,received)
    }
    companion object {
        fun from(j:JSONObject):PaymentDraft {
            fun text(key:String):String {
                val value=j.get(key)
                require(value is String && value.length<=16) {"Importe de pago inválido."}
                return value
            }
            for(key in listOf("exactCash","transferConfirmed")) if(j.has(key)) require(j.get(key) is Boolean) {"Confirmación de pago inválida."}
            val method=PaymentMethod.decode(j.getString("method"))
            require(method!=PaymentMethod.UNSPECIFIED) {"Forma de pago del pedido no válida."}
            // Drafts may contain incomplete input; validate amounts at checkout.
            return PaymentDraft(method,text("transferInput"),text("cashReceivedInput"),j.optBoolean("exactCash",false),j.optBoolean("transferConfirmed",false))
        }
    }
}

data class PaymentBreakdown(val method:PaymentMethod,val totalCents:Long,val cashAppliedCents:Long,
    val transferCents:Long,val cashReceivedCents:Long,val changeCents:Long,val unspecifiedCents:Long)

object Payments {
    const val MAX_CENTS=99_999_999_999_999L
    fun parse(raw:String):Long {
        val value=raw.trim().replace(',','.')
        require(Regex("[0-9]{1,12}(\\.[0-9]{1,2})?").matches(value)) {"Escribe un importe válido con hasta 2 decimales."}
        return BigDecimal(value).movePointRight(2).longValueExact().also {require(it in 0..MAX_CENTS)}
    }
    fun recorded(total:Long,method:PaymentMethod,transfer:Long,received:Long):PaymentBreakdown {
        require(total in 1..MAX_CENTS && transfer in 0..MAX_CENTS && received in 0..MAX_CENTS) {"Importes de pago fuera de rango."}
        when(method) {
            PaymentMethod.UNSPECIFIED -> require(transfer==0L && received==0L) {"El pago sin especificar no debe tener importes asignados."}
            PaymentMethod.CASH -> require(transfer==0L && received>=total) {"El efectivo no cubre el total."}
            PaymentMethod.TRANSFER -> require(transfer==total && received==0L) {"La transferencia no coincide con el total."}
            PaymentMethod.MIXED -> require(transfer>0 && transfer<total && received>=total-transfer) {"El pago mixto no cubre el total."}
        }
        val cash=if(method==PaymentMethod.CASH || method==PaymentMethod.MIXED) total-transfer else 0L
        return PaymentBreakdown(method,total,cash,transfer,received,received-cash,if(method==PaymentMethod.UNSPECIFIED) total else 0L)
    }
}
