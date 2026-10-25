package mx.newdragon.pos

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/** Decode the documented ESC/POS wire format independently of the encoder. */
internal fun decodeReceipt(data: ByteArray, width: Int, height: Int): BooleanArray {
    fun u(index: Int) = data[index].toInt() and 255
    assertArrayEquals(byteArrayOf(27,64),data.copyOfRange(0,2))
    val pixels=BooleanArray(width*height)
    var offset=2
    var top=0
    while(top<height) {
        assertArrayEquals(byteArrayOf(29,118,48,0),data.copyOfRange(offset,offset+4))
        val stride=u(offset+4)+256*u(offset+5)
        val rows=u(offset+6)+256*u(offset+7)
        assertTrue(stride in 1..width/8)
        assertTrue(rows>0 && top+rows<=height)
        offset+=8
        for(y in 0 until rows) for(x in 0 until stride*8) {
            pixels[(top+y)*width+x]=(u(offset+y*stride+x/8) and (128 shr (x%8)))!=0
        }
        offset+=stride*rows
        top+=rows
    }
    // No image bytes spill into text; spacing, eight feeds and one full cut remain.
    assertArrayEquals(byteArrayOf(27,50)+ByteArray(8) {10}+byteArrayOf(29,86,0),data.copyOfRange(offset,data.size))
    return pixels
}

class EscPosTransmissionTest {
    @Test fun roundTripPreservesAllPixelsAcrossBandsAndWidths()=runBlocking {
        for(width in listOf(384,512,576)) {
            val height=53
            fun black(x:Int,y:Int)=x==0 || x==width-1 || y==0 || y==height-1 || (x+3*y)%17==0
            val bytes=ByteArrayOutputStream()
            EscPosReceipt.write(width,height,::black,{bytes.write(it)},{})
            val expected=BooleanArray(width*height) {black(it%width,it/width)}
            assertArrayEquals(expected,decodeReceipt(bytes.toByteArray(),width,height))
        }
    }

    @Test fun initializationPacingAndFinalDrainOccurInOrder()=runBlocking {
        val writes=mutableListOf<ByteArray>()
        val events=mutableListOf<String>()
        EscPosReceipt.write(576,53,{_,_->false},
            {writes.add(it);events.add("write")},
            {events.add("wait:"+it)})
        assertEquals(listOf("wait:350","write","wait:350"),events.take(3))
        assertTrue(writes.all {it.size<=256})
        assertEquals("write",events[events.lastIndex-1])
        assertEquals("wait:1500",events.last())
        assertArrayEquals(byteArrayOf(29,86,0),writes.last())
        assertArrayEquals(byteArrayOf(27,50)+ByteArray(8) {10},writes[writes.lastIndex-1])
        assertEquals(listOf("write","wait:300","write","wait:1500"),events.takeLast(4))
        for(i in 3 until events.size-4 step 2) {
            assertEquals("write",events[i])
            assertEquals("wait:20",events[i+1])
        }
    }

    @Test fun longReceiptKeepsItsLastRowsBeyondFortySeconds()=runBlocking {
        val width=384
        val height=10000
        val bytes=ByteArrayOutputStream()
        var elapsed=0L
        EscPosReceipt.write(width,height,{x,y->x==0 || y==height-1},{bytes.write(it)},{elapsed+=it})
        assertTrue(elapsed>40000)
        val decoded=decodeReceipt(bytes.toByteArray(),width,height)
        assertTrue((0 until width).all {decoded[(height-1)*width+it]})
    }

    @Test fun failedWriteStopsWithoutReplayOrFinishingFeed()=runBlocking {
        val writes=mutableListOf<ByteArray>()
        val result=runCatching {
            EscPosReceipt.write(576,100,{_,_->true},{
                writes.add(it)
                if(writes.size==3) throw IOException("Disconnected")
            },{})
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(3,writes.size)
        assertFalse(writes.last().contentEquals(byteArrayOf(27,50)+ByteArray(8) {10}))
    }

    @Test fun speedProfilesKeepIdenticalBytesAndCutOptionsAreExplicit()=runBlocking {
        val streams=mutableListOf<ByteArray>()
        val times=mutableListOf<Long>()
        for(pace in listOf(PrintPace.BALANCED,PrintPace.CONSERVATIVE)) {
            val out=ByteArrayOutputStream();var time=0L
            EscPosReceipt.write(576,480,{x,y->(x+y)%7==0},{out.write(it)},{time+=it},pace=pace)
            streams.add(out.toByteArray());times.add(time)
        }
        assertArrayEquals(streams[0],streams[1])
        assertTrue(times[0]<times[1])
        for(cut in ReceiptCut.entries) {
            val writes=mutableListOf<ByteArray>()
            EscPosReceipt.write(576,24,{_,_->false},{writes.add(it)},{},cut=cut)
            val cuts=writes.filter {it.size==3 && it[0]==29.toByte() && it[1]==86.toByte()}
            assertEquals(if(cut==ReceiptCut.NONE) 0 else 1,cuts.size)
            if(cut!=ReceiptCut.NONE) assertEquals(cut.mode!!.toByte(),cuts.single()[2])
        }
    }

    @Test fun invalidDimensionsSendNoBytes()=runBlocking {
        for((width,height) in listOf(0 to 10,577 to 10,576 to 0,576 to 60001)) {
            var sends=0
            assertTrue(runCatching {EscPosReceipt.write(width,height,{_,_->false},{sends++},{})}.isFailure)
            assertEquals(0,sends)
        }
    }
}

class RestaurantTimeTest {
    @Test fun losMochisDatesIgnoreTheDeviceZoneAtMidnight() {
        val original=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val before=Instant.parse("2026-09-07T06:59:59.999Z")
            val midnight=Instant.parse("2026-09-07T07:00:00Z")
            assertEquals("06/09/2026 23:59",ReceiptContent.date(before.toEpochMilli()))
            assertEquals("07/09/2026 00:00",ReceiptContent.date(midnight.toEpochMilli()))
            assertEquals(LocalDate.of(2026,9,6),RestaurantTime.today(Clock.fixed(before,ZoneOffset.UTC)))
            assertEquals(LocalDate.of(2026,9,7),RestaurantTime.today(Clock.fixed(midnight,ZoneOffset.UTC)))
            assertEquals("Zona horaria: Los Mochis, Sin.",RestaurantTime.labelFor(RestaurantTime.zone))
        } finally {TimeZone.setDefault(original)}
    }

    @Test fun reportBoundariesUseSinaloaInSummerAndWinter() {
        for(date in listOf(LocalDate.of(2026,1,15),LocalDate.of(2026,9,6))) {
            val range=ReportRange(date,date)
            assertEquals(date.toString()+"T07:00:00Z",Instant.ofEpochMilli(range.startMillis).toString())
            fun row(id:Long,time:Long)=Ticket(id,"c"+id,time,"En mesa","","",100)
            val report=IncomeReport.create(range,listOf(
                row(1,range.startMillis-1),row(2,range.startMillis),
                row(3,range.endExclusiveMillis-1),row(4,range.endExclusiveMillis)))
            assertEquals(2,report.ticketCount)
            assertEquals(200L,report.totalCents)
            assertEquals(date,report.days.single().date)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptRenderingTest {
    @Test fun receiptFooterAndRenderedTransmissionRemainComplete(): Unit = runBlocking {
        val ticket=Ticket(5,"print-example",Instant.parse("2026-09-07T06:49:00Z").toEpochMilli(),"Para llevar","Cliente de prueba","",47000,
            "Calle de ejemplo #123, Col. Centro","668-000-0000")
        val detail=TicketDetail(ticket,listOf(
            TicketLine(1,5,"p001","p001v1","Arroz + 1 guiso","Combos","Arroz con guisos a elegir.","Normal",11000,1,"Guiso: Pollo enchilado",""),
            TicketLine(2,5,"p003","p003v1","Arroz + 3 guisos","Combos","Arroz con guisos a elegir. Plato 9×9.","Normal",18000,2,"Guiso 1: Pollo en tiras\nGuiso 2: Pollo enchilado\nGuiso 3: Pollo enchilado","")
        ))
        val blocks=ReceiptContent.blocks(detail)
        assertEquals("¡Gracias por tu preferencia!",blocks.last().text)
        assertEquals("Contactanos al: 668-822-7717",ReceiptContent.test()[2].text)
        val context=ApplicationProvider.getApplicationContext<Application>()
        val logo=BitmapFactory.decodeResource(context.resources,R.drawable.nd_ticket_logo)
        val bitmap=try {ReceiptRenderer.render(blocks,576,logo)} finally {logo.recycle()}
        assertTrue(ReceiptRenderer.isBlack(Color.RED))
        assertFalse(ReceiptRenderer.isBlack(Color.WHITE))
        assertTrue((12 until 220).sumOf {y -> (176 until 400).count {x -> ReceiptRenderer.isBlack(bitmap.getPixel(x,y))}} > 12000)
        try {
            val bytes=ByteArrayOutputStream()
            EscPosReceipt.write(bitmap.width,bitmap.height,{x,y->ReceiptRenderer.isBlack(bitmap.getPixel(x,y))},{bytes.write(it)},{})
            val decoded=decodeReceipt(bytes.toByteArray(),bitmap.width,bitmap.height)
            for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
                assertEquals(ReceiptRenderer.isBlack(bitmap.getPixel(x,y)),decoded[y*bitmap.width+x])
            }
            val output=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
            try {
                output.setPixels(IntArray(decoded.size) {if(decoded[it]) Color.BLACK else Color.WHITE},0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val dir=File(checkNotNull(System.getProperty("nd.preview.dir")));dir.mkdirs()
                File(dir,"ticket-023-transmision.png").outputStream().use {output.compress(Bitmap.CompressFormat.PNG,100,it)}
            } finally {output.recycle()}
        } finally {bitmap.recycle()}
    }
}
