package mx.newdragon.pos

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ReportDateFormat:DateTimeFormatter=DateTimeFormatter.ofPattern("dd/MM/yyyy")
data class ReportRange(val from:LocalDate,val through:LocalDate,val zone:ZoneId=RestaurantTime.zone) {
    init {require(!from.isAfter(through)) {"La fecha inicial debe ser anterior o igual a la final."}}
    val startMillis:Long get()=from.atStartOfDay(zone).toInstant().toEpochMilli()
    val endExclusiveMillis:Long get()=through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val label:String get()="${from.format(ReportDateFormat)} al ${through.format(ReportDateFormat)}"
}
data class DailyIncome(val date:LocalDate,val ticketCount:Int,val totalCents:Long,
    val cashCents:Long=0,val transferCents:Long=0,val unspecifiedCents:Long=totalCents-cashCents-transferCents) {
    init {require(cashCents>=0 && transferCents>=0 && unspecifiedCents>=0 && cashCents+transferCents+unspecifiedCents==totalCents)}
}
data class IncomeReport(val range:ReportRange,val days:List<DailyIncome>,val generatedAt:Long=System.currentTimeMillis(),val voidedCount:Int=0,val voidedCents:Long=0) {
    val ticketCount:Int get()=days.sumOf {it.ticketCount}
    val totalCents:Long get()=days.sumOf {it.totalCents}
    val cashCents:Long get()=days.sumOf {it.cashCents}
    val transferCents:Long get()=days.sumOf {it.transferCents}
    val unspecifiedCents:Long get()=days.sumOf {it.unspecifiedCents}
    val averageCents:Long get()=if(ticketCount==0) 0 else BigDecimal.valueOf(totalCents).divide(BigDecimal.valueOf(ticketCount.toLong()),0,RoundingMode.HALF_UP).longValueExact()
    companion object {
        fun create(range:ReportRange,tickets:List<Ticket>):IncomeReport {
            val first=range.startMillis
            val end=range.endExclusiveMillis
            val selected=tickets.filter {it.revenueTime>=first && it.revenueTime<end}
            val days=selected.filter {!it.voided && it.status!=OrderStates.PENDING}
                .groupBy {Instant.ofEpochMilli(it.revenueTime).atZone(range.zone).toLocalDate()}
                .toSortedMap().map {(date,rows)->
                    val payments=rows.map {it.payment}
                    DailyIncome(date,rows.size,rows.sumOf {it.totalCents},payments.sumOf {it.cashAppliedCents},
                        payments.sumOf {it.transferCents},payments.sumOf {it.unspecifiedCents})
                }
            return IncomeReport(range,days,voidedCount=selected.count {it.voided},voidedCents=selected.filter {it.voided}.sumOf {it.totalCents})
        }
    }
}

/** Offline PDF writer using standard PDF fonts and WinAnsi text. */
object IncomePdf {
    fun write(report:IncomeReport,output:OutputStream) {
        val document=PortablePdf()
        val groups=report.days.chunked(18).ifEmpty {listOf(emptyList())}
        groups.forEachIndexed {index,days ->
            val page=document.newPage()
            fun text(value:String,x:Float,y:Float,size:Float=11f,bold:Boolean=false,color:Int=0x242421,right:Boolean=false) {
                page.text(value,x,y,size,bold,color,right)
            }
            fun rect(left:Float,top:Float,right:Float,bottom:Float,color:Int) {page.rect(left,top,right,bottom,color)}
            fun amount(cents:Long,x:Float,y:Float,width:Float,size:Float=9f,bold:Boolean=false,right:Boolean=true) {
                val value=Money.show(cents)
                text(value,x,y,minOf(size,width/(value.length*0.6f)),bold,right=right)
            }
            rect(0f,0f,595f,8f,0xDA1414)
            text("NEW DRAGON",36f,48f,20f,true)
            text("REPORTE DE INGRESOS",36f,77f,15f,true,0xB41212)
            text("Periodo: ${report.range.label}",36f,101f,12f)
            text(RestaurantTime.labelFor(report.range.zone),36f,121f,9f)
            rect(36f,137f,559f,198f,0xFDF5E7)
            text("IMPORTE TOTAL MXN",48f,156f,9f,true)
            amount(report.totalCents,48f,183f,195f,19f,true,false)
            text("TICKETS",270f,156f,9f,true)
            text(report.ticketCount.toString(),270f,183f,19f,true)
            text("PROMEDIO POR TICKET",385f,156f,9f,true)
            amount(report.averageCents,385f,183f,160f,17f,true,false)
            rect(36f,207f,559f,269f,0xF9F6F1)
            text("EFECTIVO",48f,228f,9f,true)
            amount(report.cashCents,48f,254f,154f,17f,true,false)
            text("TRANSFERENCIA",223f,228f,9f,true)
            amount(report.transferCents,223f,254f,154f,17f,true,false)
            text("SIN ESPECIFICAR",397f,228f,9f,true)
            amount(report.unspecifiedCents,397f,254f,150f,17f,true,false)
            text("Efectivo: importe aplicado al pedido; excluye el cambio devuelto.",36f,290f,9f)
            text("Sin especificar: tickets anteriores sin forma de pago registrada.",36f,306f,9f)
            text("Pagos capturados manualmente. No descuenta costos ni gastos.",36f,322f,9f)
            text("Anulados: ${report.voidedCount} tickets · ${Money.show(report.voidedCents)} (importe de pedidos)",36f,339f,9f)
            text("DETALLE POR DÍA",36f,355f,10f,true)
            rect(36f,364f,559f,389f,0x242421)
            text("Fecha",44f,381f,8f,true,0xFFFFFF)
            text("Tickets",154f,381f,8f,true,0xFFFFFF,true)
            text("Efectivo",250f,381f,8f,true,0xFFFFFF,true)
            text("Transferencia",350f,381f,8f,true,0xFFFFFF,true)
            text("Sin especificar",451f,381f,8f,true,0xFFFFFF,true)
            text("Total MXN",551f,381f,8f,true,0xFFFFFF,true)
            var y=389f
            if(days.isEmpty()) text("No hay tickets guardados en el periodo seleccionado.",44f,y+28f,10f)
            days.forEachIndexed {row,d ->
                if(row%2==0) rect(36f,y,559f,y+19f,0xF9F6F1)
                text(d.date.format(ReportDateFormat),44f,y+13f,8f)
                text(d.ticketCount.toString(),154f,y+13f,8f,right=true)
                amount(d.cashCents,250f,y+13f,88f)
                amount(d.transferCents,350f,y+13f,92f)
                amount(d.unspecifiedCents,451f,y+13f,93f)
                amount(d.totalCents,551f,y+13f,92f)
                y+=19f
            }
            text("Pendientes y anulados excluidos de ingresos. Importes en MXN.",36f,758f,8f)
            rect(36f,772f,559f,773f,0xDCD4C9)
            val generated=Instant.ofEpochMilli(report.generatedAt).atZone(report.range.zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            text("Generado: $generated",36f,794f,9f)
            text("Página ${index+1} de ${groups.size}",559f,794f,9f,right=true)
        }
        document.writeTo(output)
    }
}

private fun pdfNumber(value:Float):String=String.format(Locale.ROOT,"%.3f",value)
private fun pdfColor(value:Int):String=listOf((value shr 16) and 255,(value shr 8) and 255,value and 255)
    .joinToString(" ") {pdfNumber(it/255f)}
private class PortablePage {
    val commands=StringBuilder()
    fun rect(left:Float,top:Float,right:Float,bottom:Float,color:Int) {
        commands.append("q ${pdfColor(color)} rg ${pdfNumber(left)} ${pdfNumber(842f-bottom)} ${pdfNumber(right-left)} ${pdfNumber(bottom-top)} re f Q\n")
    }
    fun text(value:String,x:Float,y:Float,size:Float,bold:Boolean,color:Int,right:Boolean) {
        // Numeric columns use Courier's exact fixed width for consistent right alignment.
        val font=if(right) {if(bold) "F4" else "F3"} else {if(bold) "F2" else "F1"}
        val position=if(right) x-value.length*size*0.6f else x
        val hex=value.toByteArray(charset("windows-1252")).joinToString("") {String.format(Locale.ROOT,"%02X",it.toInt() and 255)}
        commands.append("BT /$font ${pdfNumber(size)} Tf ${pdfColor(color)} rg 1 0 0 1 ${pdfNumber(position)} ${pdfNumber(842f-y)} Tm <$hex> Tj ET\n")
    }
}
private class PortablePdf {
    private val pages=mutableListOf<PortablePage>()
    fun newPage():PortablePage=PortablePage().also {pages.add(it)}
    fun writeTo(output:OutputStream) {
        val objects=mutableListOf<ByteArray>()
        fun add(value:String):Int {objects.add(value.toByteArray(Charsets.US_ASCII));return objects.size}
        add("<< /Type /Catalog /Pages 2 0 R >>")
        add("")
        listOf("Courier","Courier-Bold","Courier","Courier-Bold").forEach {add("<< /Type /Font /Subtype /Type1 /BaseFont /$it /Encoding /WinAnsiEncoding >>")}
        val pageIds=pages.map {page ->
            val stream=page.commands.toString()
            val content=add("<< /Length ${stream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${stream}endstream")
            add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R >> >> /Contents $content 0 R >>")
        }
        objects[1]="<< /Type /Pages /Count ${pageIds.size} /Kids [${pageIds.joinToString(" ") {"$it 0 R"}}] >>".toByteArray(Charsets.US_ASCII)
        val bytes=ByteArrayOutputStream()
        fun emit(value:String) {bytes.write(value.toByteArray(Charsets.US_ASCII))}
        emit("%PDF-1.4\n")
        val offsets=mutableListOf<Int>()
        objects.forEachIndexed {index,body ->
            offsets.add(bytes.size());emit("${index+1} 0 obj\n");bytes.write(body);emit("\nendobj\n")
        }
        val xref=bytes.size()
        emit("xref\n0 ${objects.size+1}\n0000000000 65535 f \n")
        offsets.forEach {emit(String.format(Locale.ROOT,"%010d 00000 n \n",it))}
        emit("trailer\n<< /Size ${objects.size+1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        bytes.writeTo(output)
    }
}
