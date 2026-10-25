package mx.newdragon.pos

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ReceiptBlock(val text: String, val bold: Boolean = false, val centered: Boolean = false, val compact: Boolean = false)
object ReceiptContent {
    fun date(time: Long): String = RestaurantTime.date(time)
    private fun header() = listOf(
        ReceiptBlock("Fracc. Nuevo Horizonte",centered=true,compact=true),
        ReceiptBlock("Ruperto L. Paliza #3519 · Los Mochis, Sin.",centered=true,compact=true),
        ReceiptBlock("Contactanos al: 668-822-7717",centered=true))
    fun blocks(detail: TicketDetail): List<ReceiptBlock> = buildList {
        val t = detail.ticket
        addAll(header())
        add(ReceiptBlock("Recibo: ${t.folio}",true))
        if(t.voided) {add(ReceiptBlock("TICKET ANULADO",true,true));add(ReceiptBlock("Motivo: ${t.voidReason}"))}
        else if(t.status==OrderStates.PENDING) add(ReceiptBlock("PENDIENTE DE PAGO",true,true))
        add(ReceiptBlock("Fecha: ${date(t.createdAt)}"))
        add(ReceiptBlock("────────────────────────"))
        if(t.service=="A domicilio" && t.deliveryAddress.isNotBlank()) add(ReceiptBlock("A domicilio: ${t.deliveryAddress.trim()}"))
        else add(ReceiptBlock(t.service))
        if(t.reference.isNotBlank()) {
            val label=if(t.service=="En mesa" && t.deliveryAddress.isBlank()) "Mesa o nombre" else "Entregar a"
            add(ReceiptBlock("$label: ${t.reference.trim()}"))
        }
        if(t.customerPhone.isNotBlank()) add(ReceiptBlock("Contacto: ${t.customerPhone.trim()}"))
        if(t.notes.isNotBlank()) add(ReceiptBlock("Observaciones: ${t.notes}"))
        add(ReceiptBlock("────────────────────────"))
        detail.lines.sortedBy { it.id }.forEach { l ->
            // Retain meaningful sizes, but omit category and the generic Normal label.
            val presentation=l.variant.trim().takeUnless { it.isBlank() || it.equals("Normal",ignoreCase=true) }
            add(ReceiptBlock("${l.quantity} × ${l.name}${presentation?.let { " ($it)" } ?: ""}",true))
            if (l.description.isNotBlank()) add(ReceiptBlock(l.description))
            if (l.selections.isNotBlank()) add(ReceiptBlock(l.selections))
            if (l.notes.isNotBlank()) add(ReceiptBlock("Nota: ${l.notes}"))
            add(ReceiptBlock("${Money.show(l.unitCents)} c/u    ${Money.show(l.total)}"))
            add(ReceiptBlock(" "))
        }
        add(ReceiptBlock("────────────────────────"))
        add(ReceiptBlock("TOTAL MXN: ${Money.show(t.totalCents)}",true))
        val payment=t.payment
        add(ReceiptBlock(if(t.status==OrderStates.PENDING) "Pago pendiente" else "Pago: ${payment.method.label}"))
        if(payment.transferCents>0) add(ReceiptBlock("Transferencia: ${Money.show(payment.transferCents)}"))
        if(payment.method==PaymentMethod.MIXED) add(ReceiptBlock("Efectivo aplicado: ${Money.show(payment.cashAppliedCents)}"))
        if(payment.cashAppliedCents>0) {
            add(ReceiptBlock("Efectivo recibido: ${Money.show(payment.cashReceivedCents)}"))
            add(ReceiptBlock("Cambio: ${Money.show(payment.changeCents)}",true))
        }
        add(ReceiptBlock("¡Gracias por tu preferencia!",centered=true))
    }
    fun test() = header() + listOf(
        ReceiptBlock("PRUEBA DE IMPRESIÓN",true,true),
        ReceiptBlock("Versión ${BuildConfig.VERSION_NAME}",centered=true),
        ReceiptBlock("Acentos: á é í ó ú ñ Ñ"),
        ReceiptBlock("2 × Platillo de prueba"),ReceiptBlock("TOTAL MXN: $0.00",true),
        ReceiptBlock("Sin venta registrada.",centered=true),
        ReceiptBlock("¡Gracias por tu preferencia!",centered=true))
}
data class PairedPrinter(val address: String, val name: String)
class BluetoothPrinter(private val context: Context) {
    private val mutex = Mutex()
    fun hasPermission() = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission")
    fun paired(): List<PairedPrinter> {
        require(hasPermission()) { "Permite el acceso a dispositivos cercanos." }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: error("Esta tableta no tiene Bluetooth.")
        require(adapter.isEnabled) { "Activa Bluetooth en los ajustes de la tableta." }
        return adapter.bondedDevices.map { PairedPrinter(it.address,it.name ?: "Dispositivo Bluetooth") }.sortedBy { it.name }
    }
    @SuppressLint("MissingPermission")
    suspend fun print(address: String, width: Int, blocks: List<ReceiptBlock>,
        pace: PrintPace = PrintPace.FAST, cut: ReceiptCut = ReceiptCut.FULL) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(hasPermission()) { "Permite el acceso a dispositivos cercanos." }
            require(address.isNotBlank()) { "Selecciona una impresora." }
            require(width in listOf(384,512,576))
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: error("Bluetooth no disponible.")
            require(adapter.isEnabled) { "Activa Bluetooth." }
            val logo = checkNotNull(BitmapFactory.decodeResource(context.resources,R.drawable.nd_ticket_logo)) { "No se pudo cargar el logo del ticket." }
            val bitmap = try { ReceiptRenderer.render(blocks,width,logo) } finally { logo.recycle() }
            try {
                val device = adapter.getRemoteDevice(address)
                val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                val timer = Executors.newSingleThreadScheduledExecutor()
                // Separate connect/stalled-write deadlines: long tickets must not be cut off
                // by the old 40-second deadline for the entire job.
                fun guarded(action: () -> Unit) {
                    val deadline = timer.schedule({ runCatching { socket.close() } },25,TimeUnit.SECONDS)
                    try { action() } finally { deadline.cancel(false) }
                }
                try {
                    guarded { socket.connect() }
                    val output = socket.outputStream
                    val pixels=IntArray(width*24)
                    var stripTop=-1
                    EscPosReceipt.write(width, bitmap.height,
                        black = { x, y ->
                            val top=y/24*24
                            if(top!=stripTop) {
                                bitmap.getPixels(pixels,0,width,0,top,width,minOf(24,bitmap.height-top));stripTop=top
                            }
                            ReceiptRenderer.isBlack(pixels[(y-stripTop)*width+x])
                        },
                        send = { bytes -> guarded { output.write(bytes); output.flush() } },pace=pace,cut=cut)
                } finally { timer.shutdownNow(); runCatching { socket.close() } }
            } finally { bitmap.recycle() }
        }
    }
}

object ReceiptRenderer {
    // Luminance is essential: testing only the red channel would erase the red logo.
    fun isBlack(color: Int): Boolean = 299*Color.red(color)+587*Color.green(color)+114*Color.blue(color)<128000
    fun render(blocks: List<ReceiptBlock>, width: Int, logo: Bitmap? = null): Bitmap {
        require(width in listOf(384,512,576))
        val layouts = blocks.map { block ->
            val paint = TextPaint().apply {
                color=Color.BLACK; textSize=if(block.compact) 20f else if(block.bold) 28f else 23f
                typeface=Typeface.create(Typeface.MONOSPACE,if(block.bold) Typeface.BOLD else Typeface.NORMAL)
                isAntiAlias=false
            }
            StaticLayout.Builder.obtain(block.text,0,block.text.length,paint,width-24)
                .setAlignment(if(block.centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(3f,1f).build()
        }
        val logoWidth=224.coerceAtMost(width-24)
        val logoHeight=logo?.let { logoWidth*it.height/it.width } ?: 0
        val logoSpace=if(logo!=null) logoHeight+16 else 0
        val height=layouts.sumOf { it.height+8 }+24+logoSpace
        require(height<=60000) { "El ticket es demasiado largo; divide el pedido." }
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
            if(logo!=null) {
                val left=(width-logoWidth)/2
                canvas.drawBitmap(logo,null,Rect(left,12,left+logoWidth,12+logoHeight),Paint(Paint.FILTER_BITMAP_FLAG))
            }
            canvas.translate(12f,(12+logoSpace).toFloat())
            layouts.forEach { it.draw(canvas); canvas.translate(0f,(it.height+8).toFloat()) }
        }
    }
}
