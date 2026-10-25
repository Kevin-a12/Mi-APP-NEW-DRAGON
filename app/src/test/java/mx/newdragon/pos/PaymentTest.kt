package mx.newdragon.pos

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate

private fun paidDraft(total:Long=53000,payment:PaymentDraft=PaymentDraft(PaymentMethod.MIXED,"265","400",transferConfirmed=true))=OrderDraft(
    lines=listOf(OrderLine(productId="p001",variantId="p001v1",name="Pedido de prueba",category="Combos",description="",
        variant="Normal",unitCents=total,quantity=1)),payment=payment)
private fun paidTicket(id:Long,total:Long,payment:PaymentDraft,day:LocalDate=LocalDate.of(2026,9,8)):Ticket {
    val p=payment.resolve(total)
    return Ticket(id,"example-$id",day.atTime(12,0).atZone(RestaurantTime.zone).toInstant().toEpochMilli(),"Para llevar","","",total,
        paymentMethod=p.method.name,transferCents=p.transferCents,cashReceivedCents=p.cashReceivedCents)
}
private fun exampleDetail(ticket:Ticket)=TicketDetail(ticket,listOf(TicketLine(1,ticket.id,"p001","p001v1",
    "Pedido de prueba","Combos","Platillo de ejemplo.","Normal",ticket.totalCents,1,"Guiso 1: Pollo en tiras\nGuiso 2: Pollo enchilado","Poco dorado")))

class PaymentMathTest {
    @Test fun referenceExamplesGiveCorrectAppliedCashAndChange() {
        val transfer=PaymentDraft(PaymentMethod.TRANSFER,transferConfirmed=true).resolve(16000)
        assertEquals(16000L,transfer.transferCents);assertEquals(0L,transfer.cashAppliedCents);assertEquals(0L,transfer.changeCents)
        val cash=PaymentDraft(cashReceivedInput="500").resolve(16500)
        assertEquals(16500L,cash.cashAppliedCents);assertEquals(50000L,cash.cashReceivedCents);assertEquals(33500L,cash.changeCents)
        val mixed=PaymentDraft(PaymentMethod.MIXED,"265","400",transferConfirmed=true).resolve(53000)
        assertEquals(26500L,mixed.cashAppliedCents);assertEquals(26500L,mixed.transferCents);assertEquals(13500L,mixed.changeCents)
        assertEquals(53000L,mixed.cashAppliedCents+mixed.transferCents)
    }
    @Test fun exactCashCentavosAndOrderChangesRemainExact() {
        assertEquals(0L,PaymentDraft(exactCash=true).resolve(16500).changeCents)
        val small=PaymentDraft(PaymentMethod.MIXED,"0,10","0,50",transferConfirmed=true).resolve(30)
        assertEquals(20L,small.cashAppliedCents);assertEquals(30L,small.changeCents)
        val mixed=PaymentDraft(PaymentMethod.MIXED,"265","400",transferConfirmed=true)
        assertEquals(13500L,mixed.resolve(53000).changeCents)
        assertEquals(3500L,mixed.resolve(63000).changeCents)
        assertTrue(runCatching {mixed.resolve(73000)}.isFailure)
    }
    @Test fun insufficientOverpaidTransferAndMalformedAmountsAreRejected() {
        val invalid=listOf(PaymentDraft(cashReceivedInput="164.99"),PaymentDraft(cashReceivedInput="-2"),
            PaymentDraft(cashReceivedInput="1.001"),PaymentDraft(cashReceivedInput="NaN"),PaymentDraft(cashReceivedInput="1e3"),
            PaymentDraft(PaymentMethod.MIXED,"","200",transferConfirmed=true),PaymentDraft(PaymentMethod.MIXED,"0","200",transferConfirmed=true),
            PaymentDraft(PaymentMethod.MIXED,"165","200",transferConfirmed=true),PaymentDraft(PaymentMethod.MIXED,"166","200",transferConfirmed=true),PaymentDraft(PaymentMethod.UNSPECIFIED))
        invalid.forEach {assertTrue(it.toString(),runCatching {it.resolve(16500)}.isFailure)}
        assertTrue(runCatching {Payments.recorded(16500,PaymentMethod.TRANSFER,16499,0)}.isFailure)
        assertTrue(runCatching {Payments.recorded(16500,PaymentMethod.UNSPECIFIED,100,0)}.isFailure)
    }
    @Test fun receiptPlacesPaymentAfterTotalBeforeThanks() {
        val cases=listOf(paidTicket(1,16000,PaymentDraft(PaymentMethod.TRANSFER,transferConfirmed=true)),
            paidTicket(2,16500,PaymentDraft(cashReceivedInput="500")),paidTicket(3,53000,PaymentDraft(PaymentMethod.MIXED,"265","400",transferConfirmed=true)))
        for(t in cases) {
            val text=ReceiptContent.blocks(exampleDetail(t)).map {it.text}
            val total=text.indexOf("TOTAL MXN: ${Money.show(t.totalCents)}")
            val payment=text.indexOf("Pago: ${t.payment.method.label}")
            assertTrue(payment>total && payment<text.indexOf("¡Gracias por tu preferencia!"))
            if(t.payment.cashAppliedCents>0) {
                assertTrue(text.contains("Cambio: ${Money.show(t.payment.changeCents)}"))
                assertTrue(text.contains("Efectivo recibido: ${Money.show(t.cashReceivedCents)}"))
            } else assertFalse(text.any {it.startsWith("Cambio:")})
            assertFalse(text.contains("Combos · Normal"))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class PaymentStorageTest {
    private fun repository():DragonRepository {
        val context=ApplicationProvider.getApplicationContext<Application>()
        return DragonRepository(Room.inMemoryDatabaseBuilder(context,DragonDatabase::class.java).build(),context)
    }
    @Test fun checkoutSnapshotAndPendingDraftSurviveBackupAndRestore():Unit=runBlocking {
        val source=repository();val target=repository()
        try {
            source.ensureSeed()
            val draft=paidDraft().copy(service="A domicilio",deliveryAddress="Calle de prueba 123",customerPhone="6680000000")
            source.saveDraft(draft)
            assertEquals(draft,source.readDraft())
            val saved=source.checkout(draft)
            val repeated=source.checkout(draft.copy(payment=PaymentDraft(PaymentMethod.TRANSFER,transferConfirmed=true)))
            assertEquals(saved.ticket,repeated.ticket)
            assertEquals(13500L,repeated.ticket.payment.changeCents)
            val pending=paidDraft(16500,PaymentDraft(cashReceivedInput="1."))
            source.saveDraft(pending)
            val snapshot=BackupCodec.decode(source.exportJson())
            assertEquals(5,JSONObject(source.exportJson()).getInt("version"))
            target.restoreBackup(snapshot)
            assertEquals(pending,target.readDraft())
            assertTrue(runCatching {target.checkout(pending)}.isFailure)
            assertEquals(1,target.dao.allTickets().size)
            assertEquals(saved.ticket,target.dao.detail(saved.ticket.id)!!.ticket)
            assertEquals("Calle de prueba 123",target.dao.detail(saved.ticket.id)!!.ticket.deliveryAddress)
            assertEquals(13500L,target.dao.detail(saved.ticket.id)!!.ticket.payment.changeCents)
        } finally {source.db.close();target.db.close()}
    }
    @Test fun legacyBackupsStayUnspecifiedAndBadPaymentFilesAreRejected():Unit=runBlocking {
        val repo=repository()
        try {
            repo.ensureSeed();repo.checkout(paidDraft())
            val raw=repo.exportJson()
            for(v in 1..3) {
                val old=JSONObject(raw).put("version",v)
                old.getJSONObject("draft").remove("payment")
                val t=old.getJSONArray("tickets").getJSONObject(0)
                listOf("paymentMethod","transferCents","cashReceivedCents").forEach(t::remove)
                val restored=BackupCodec.decode(old.toString())
                assertEquals(53000L,restored.tickets.single().ticket.payment.unspecifiedCents)
                assertEquals(0L,restored.tickets.single().ticket.payment.cashAppliedCents)
                assertEquals(PaymentDraft(),restored.draft.payment)
            }
            for((field,bad) in listOf("paymentMethod" to "BOGUS","transferCents" to 53001,"cashReceivedCents" to 1,"transferCents" to "26500")) {
                val j=JSONObject(raw);j.getJSONArray("tickets").getJSONObject(0).put(field,bad)
                assertTrue("$field = $bad",runCatching {BackupCodec.decode(j.toString())}.isFailure)
            }
            val malformed=JSONObject(raw);malformed.getJSONObject("draft").getJSONObject("payment").put("cashReceivedInput",23)
            assertTrue(runCatching {BackupCodec.decode(malformed.toString())}.isFailure)
            assertEquals(1,repo.dao.allTickets().size)
        } finally {repo.db.close()}
    }
    @Test fun realSchemaTwoMigrationPreservesDeliveryHistoryAndFolio():Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val name="migration-payments-024.db";context.deleteDatabase(name)
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(File(checkNotNull(System.getProperty("nd.schema.dir")),"mx.newdragon.pos.DragonDatabase/2.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file,null).use {old ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices") ?: JSONArray()
                for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val queries=schema.getJSONArray("setupQueries")
            for(i in 0 until queries.length()) old.execSQL(queries.getString(i))
            old.execSQL("INSERT INTO tickets VALUES(7,'old-ticket',1000,'A domicilio','Cliente','Conservar nota',12000,'Calle anterior','6680000000')")
            old.execSQL("INSERT INTO ticket_lines VALUES(11,7,'p001','p001v1','Nombre original','Combos','Descripción','Normal',12000,1,'Guiso: Pollo','Nota antigua')")
            old.execSQL("INSERT INTO drafts VALUES(1,?)",arrayOf(paidDraft().json().apply {remove("payment")}.toString()))
            old.execSQL("UPDATE sqlite_sequence SET seq=23 WHERE name='tickets'")
            old.version=2
        }
        val db=Room.databaseBuilder(context,DragonDatabase::class.java,name).addMigrations(DragonDatabase.MIGRATION_2_3,DragonDatabase.MIGRATION_3_4).build()
        val repo=DragonRepository(db,context)
        try {
            val old=repo.dao.detail(7)!!.ticket
            assertEquals(4,db.openHelper.writableDatabase.version)
            assertEquals("Calle anterior",old.deliveryAddress);assertEquals("6680000000",old.customerPhone)
            assertEquals("Conservar nota",old.notes)
            assertEquals(12000L,old.payment.unspecifiedCents);assertEquals(0L,old.payment.cashAppliedCents)
            assertEquals(PaymentDraft(),repo.readDraft().payment)
            assertEquals(24L,repo.checkout(paidDraft()).ticket.id)
        } finally {db.close();context.deleteDatabase(name)}
    }
}

class PaymentReportTest {
    @Test fun totalsExcludeChangeSeparateLegacyAndRespectDatesAndDeletion() {
        val day=LocalDate.of(2026,9,8)
        val t1=paidTicket(1,16000,PaymentDraft(PaymentMethod.TRANSFER,transferConfirmed=true),day.minusDays(1))
        val t2=paidTicket(2,16500,PaymentDraft(cashReceivedInput="500"),day)
        val t3=paidTicket(3,53000,PaymentDraft(PaymentMethod.MIXED,"265","400",transferConfirmed=true),day.plusDays(1))
        val legacy=Ticket(4,"legacy",t1.createdAt,"Para llevar","","",11000)
        val tickets=listOf(t1,t2,t3,legacy)
        val range=ReportRange(day.minusDays(1),day.plusDays(1))
        val report=IncomeReport.create(range,tickets)
        assertEquals(96500L,report.totalCents);assertEquals(43000L,report.cashCents)
        assertEquals(42500L,report.transferCents);assertEquals(11000L,report.unspecifiedCents)
        assertEquals(report.totalCents,report.cashCents+report.transferCents+report.unspecifiedCents)
        assertEquals(16500L,IncomeReport.create(ReportRange(day,day),tickets).cashCents)
        assertEquals(16500L,IncomeReport.create(range,tickets-t3).cashCents)
        assertEquals(16000L,IncomeReport.create(range,tickets-t3).transferCents)
        val dir=File(checkNotNull(System.getProperty("nd.preview.dir")));dir.mkdirs()
        File(dir,"reporte-pagos-024.pdf").outputStream().use {IncomePdf.write(report,it)}
    }
}

class FastRasterTest {
    @Test fun optimizedRasterPreservesAllPixelsAndBlankRows():Unit=runBlocking {
        for(width in listOf(384,512,576)) for(pattern in 0..3) {
            val height=137
            val expected=BooleanArray(width*height) {i ->val x=i%width;val y=i/width
                when(pattern) {0->false;1->true;2->y%31<12 && x<width/3;else->(x==width-1 || x==0 || x%17==0) && y%3==0}}
            val output=ByteArrayOutputStream()
            EscPosReceipt.write(width,height,{x,y->expected[y*width+x]},{output.write(it)},{},PrintPace.FAST)
            assertArrayEquals(expected,decodeReceipt(output.toByteArray(),width,height))
        }
    }
    @Test fun failedFastWriteStopsBeforeCutAndDoesNotReplay():Unit=runBlocking {
        val chunks=mutableListOf<ByteArray>();var calls=0
        val result=runCatching {EscPosReceipt.write(576,100,{_,_->true},{bytes->
            calls++;if(calls==3) throw IOException("stalled link");chunks.add(bytes)
        },{},PrintPace.FAST)}
        assertTrue(result.isFailure);assertEquals(3,calls)
        assertTrue(chunks.all {it.size<=256})
        assertEquals(1,chunks.count {it.contentEquals(byteArrayOf(27,64))})
        assertFalse(chunks.any {it.contentEquals(byteArrayOf(29,86,0))})
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaymentRenderingTest {
    @Test fun referenceTicketsHaveIdenticalPixelsWithLessTransmission():Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val logo=BitmapFactory.decodeResource(context.resources,R.drawable.nd_ticket_logo)
        val cases=listOf("transferencia" to paidTicket(1,16000,PaymentDraft(PaymentMethod.TRANSFER,transferConfirmed=true)),
            "efectivo" to paidTicket(2,16500,PaymentDraft(cashReceivedInput="500")),"mixto" to paidTicket(3,53000,PaymentDraft(PaymentMethod.MIXED,"265","400",transferConfirmed=true)))
        val folder=File(checkNotNull(System.getProperty("nd.preview.dir")));folder.mkdirs()
        val stats=JSONArray()
        try {
            for((name,ticket) in cases) {
                val bitmap=ReceiptRenderer.render(ReceiptContent.blocks(exampleDetail(ticket)),576,logo)
                try {
                    val expected=BooleanArray(bitmap.width*bitmap.height) {ReceiptRenderer.isBlack(bitmap.getPixel(it%bitmap.width,it/bitmap.width))}
                    var normalSize=0;var normalTime=0L;var fastSize=0;var fastTime=0L
                    var decodedFast=BooleanArray(0)
                    for(pace in listOf(PrintPace.BALANCED,PrintPace.FAST)) {
                        val out=ByteArrayOutputStream();var time=0L
                        EscPosReceipt.write(bitmap.width,bitmap.height,{x,y->expected[y*bitmap.width+x]},
                            {out.write(it)},{time+=it},pace=pace)
                        val decoded=decodeReceipt(out.toByteArray(),bitmap.width,bitmap.height)
                        assertArrayEquals(expected,decoded)
                        if(pace==PrintPace.BALANCED) {normalSize=out.size();normalTime=time}
                        else {fastSize=out.size();fastTime=time;decodedFast=decoded}
                    }
                    assertTrue(fastSize<normalSize*0.75);assertTrue(fastTime<normalTime*0.8)
                    val output=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
                    try {
                        output.setPixels(IntArray(decodedFast.size) {if(decodedFast[it]) Color.BLACK else Color.WHITE},0,bitmap.width,0,0,bitmap.width,bitmap.height)
                        File(folder,"ticket-024-$name.png").outputStream().use {output.compress(Bitmap.CompressFormat.PNG,100,it)}
                    } finally {output.recycle()}
                    stats.put(JSONObject().put("example",name).put("normalBytes",normalSize).put("fastBytes",fastSize)
                        .put("normalScheduledMs",normalTime).put("fastScheduledMs",fastTime).put("pixelsIdentical",true))
                } finally {bitmap.recycle()}
            }
            File(folder,"print-speed-024.json").writeText(stats.toString(2))
        } finally {logo.recycle()}
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=DragonApplication::class,qualifiers="w400dp-h900dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaymentInterfaceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[DragonViewModel::class.java]
    @Before fun startFresh() {
        compose.waitUntil(30_000) {vm.ready}
        resetInterfaceFixture(vm)
        compose.runOnIdle {vm.clearDraft();vm.navigate("Pedidos")}
    }
    @Test fun mixedPaymentNeedsConfirmationAndSavesExactChange() {
        compose.runOnIdle {vm.add(paidDraft().lines.single())}
        compose.onNodeWithText("Pedido (1)").performClick()
        compose.onNodeWithText("Cobrar $530.00").performClick()
        compose.onNodeWithText("Mixto",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Importe por transferencia").performTextInput("265")
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Efectivo recibido"))
        compose.onNodeWithText("Efectivo recibido").performTextInput("400")
        compose.onNodeWithText("Guardar sin imprimir").assertIsNotEnabled()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Confirmo que recibí la transferencia"))
        compose.onNodeWithText("Confirmo que recibí la transferencia").performClick()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Cambio: $135.00"))
        compose.onNodeWithText("Cambio: $135.00").assertIsDisplayed()
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-pago-mixto.png")}
        compose.runOnIdle {vm.quantity(vm.draft.lines.single().key,1)}
        compose.onNodeWithText("Guardar sin imprimir").assertIsNotEnabled()
        compose.runOnIdle {vm.quantity(vm.draft.lines.single().key,-1)}
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Confirmo que recibí la transferencia"))
        compose.onNodeWithText("Confirmo que recibí la transferencia").performClick()
        compose.onNodeWithText("Guardar sin imprimir").assertIsEnabled().performClick()
        compose.waitUntil(30_000) {vm.selectedTicket!=null && !vm.saving}
        assertEquals(13500L,vm.selectedTicket!!.ticket.payment.changeCents)
        assertEquals(PaymentDraft(),vm.draft.payment)
        compose.onNodeWithText("Reportes").performClick()
        compose.onNodeWithText("Hoy").performClick()
        compose.waitUntil(30_000) {compose.onAllNodesWithText("Importe total MXN").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("report-scroll").performScrollToNode(hasText("Efectivo: $265.00"))
        compose.onNodeWithText("Efectivo: $265.00").assertExists()
        compose.onNodeWithText("Transferencia: $265.00").assertExists()
        compose.onNodeWithText("Total: $530.00").assertExists()
    }
}
