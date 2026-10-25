package mx.newdragon.pos

import kotlinx.coroutines.delay

enum class PrintPace(val label: String, val chunkPauseMillis: Long) {
    FAST("Rápida optimizada",20), BALANCED("Equilibrada",20), CONSERVATIVE("Conservadora",30)
}
enum class ReceiptCut(val label: String, val mode: Int?) {
    FULL("Completo",0), PARTIAL("Parcial",1), NONE("Sin corte",null)
}

/**
 * Binary ESC/POS raster stream. The transport must write and flush every chunk.
 * Small bands and paced writes allow a Bluetooth-to-serial bridge to drain.
 * Completion means data was sent; it is not a physical printer acknowledgement.
 */
object EscPosReceipt {
    private const val BAND_ROWS = 24
    private const val CHUNK_BYTES = 256

    fun rasterBand(width: Int, rows: Int, black: (Int, Int) -> Boolean): ByteArray {
        require(width in listOf(384, 512, 576))
        require(rows in 1..BAND_ROWS)
        val stride = width / 8
        val result = ByteArray(8 + stride * rows)
        byteArrayOf(0x1d, 0x76, 0x30, 0x00, stride.toByte(), 0, rows.toByte(), 0).copyInto(result)
        for (y in 0 until rows) for (x in 0 until width) {
            if (black(x, y)) {
                val offset = 8 + y * stride + x / 8
                result[offset] = (result[offset].toInt() or (0x80 shr (x % 8))).toByte()
            }
        }
        return result
    }

    /** Keep every dot and feed row; omit unused white columns and compact blank bands. */
    private fun optimizedPackets(width:Int,height:Int,black:(Int,Int)->Boolean)=sequence {
        val rows=mutableListOf<ByteArray>()
        var previousBlank=false
        fun packet():ByteArray {
            val stride=rows.maxOf {row -> row.indexOfLast {it!=0.toByte()}+1}.coerceAtLeast(1)
            return ByteArray(8+stride*rows.size).also {result ->
                byteArrayOf(29,118,48,0,stride.toByte(),0,rows.size.toByte(),0).copyInto(result)
                rows.forEachIndexed {index,row -> row.copyInto(result,8+index*stride,0,stride)}
            }
        }
        for(y in 0 until height) {
            val row=ByteArray(width/8)
            for(x in 0 until width) if(black(x,y)) row[x/8]=(row[x/8].toInt() or (128 shr (x%8))).toByte()
            val blank=row.all {it==0.toByte()}
            if(rows.isNotEmpty() && (blank!=previousBlank || rows.size==BAND_ROWS)) {yield(packet());rows.clear()}
            rows.add(row);previousBlank=blank
        }
        if(rows.isNotEmpty()) yield(packet())
    }

    suspend fun write(
        width: Int,
        height: Int,
        black: (Int, Int) -> Boolean,
        send: (ByteArray) -> Unit,
        pause: suspend (Long) -> Unit = { delay(it) },
        pace: PrintPace = PrintPace.BALANCED,
        cut: ReceiptCut = ReceiptCut.FULL
    ) {
        require(width in listOf(384, 512, 576))
        require(height in 1..60000)
        // Let the connection settle, then let ESC @ finish before sending image data.
        pause(350)
        send(byteArrayOf(0x1b, 0x40))
        pause(350)
        if(pace==PrintPace.FAST) {
            // Coalesce complete binary packets without increasing chunk size or send rate.
            val chunk=ByteArray(CHUNK_BYTES)
            var used=0
            for(packet in optimizedPackets(width,height,black)) {
                var offset=0
                while(offset<packet.size) {
                    val count=minOf(CHUNK_BYTES-used,packet.size-offset)
                    packet.copyInto(chunk,used,offset,offset+count)
                    used+=count;offset+=count
                    if(used==CHUNK_BYTES) {send(chunk.copyOf());pause(pace.chunkPauseMillis);used=0}
                }
            }
            if(used>0) {send(chunk.copyOf(used));pause(pace.chunkPauseMillis)}
        } else {
        for (top in 0 until height step BAND_ROWS) {
            val rows = minOf(BAND_ROWS, height - top)
            val packet = rasterBand(width, rows) { x, y -> black(x, top + y) }
            for (offset in packet.indices step CHUNK_BYTES) {
                send(packet.copyOfRange(offset, minOf(offset + CHUNK_BYTES, packet.size)))
                pause(pace.chunkPauseMillis)
            }
        }
        }
        // Restore normal line spacing and advance eight lines past the print head.
        // LF is used instead of relying on a clone's multi-line feed command.
        send(byteArrayOf(0x1b, 0x32) + ByteArray(8) { 0x0a })
        // One documented GS V command at the beginning of a line, after sufficient feed.
        // Do not retry or send multiple cut variants: a partial job may already be printed.
        cut.mode?.let { mode ->
            pause(300)
            send(byteArrayOf(0x1d,0x56,mode.toByte()))
        }
        // flush() only reaches the Bluetooth stack; do not close the socket immediately.
        pause(1500)
    }
}
