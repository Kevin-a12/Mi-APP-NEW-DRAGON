package mx.newdragon.pos

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class BackupStorageTest {
    private fun repo():DragonRepository {
        val context=ApplicationProvider.getApplicationContext<Application>()
        return DragonRepository(Room.inMemoryDatabaseBuilder(context,DragonDatabase::class.java).allowMainThreadQueries().build(),context)
    }
    private fun draft()=OrderDraft(lines=listOf(OrderLine(productId="p001",variantId="p001v1",name="Prueba",category="Combos",
        description="Descripción guardada",variant="Normal",unitCents=11000,quantity=2,notes="Sin cebolla")),payment=PaymentDraft(exactCash=true))

    @Test fun annulOnlyTargetPreserveAuditAndRollbackOnFailure()=runBlocking {
        val r=repo()
        try {
            r.ensureSeed()
            val menu=r.dao.allCatalog()
            val retained=r.checkout(draft())
            val removed=r.checkout(draft())
            val pending=draft();r.saveDraft(pending)
            r.dao.insertPrint(PrintAttempt(ticketId=retained.ticket.id,timestamp=1,status="SENT",detail="Prueba"))
            r.dao.insertPrint(PrintAttempt(ticketId=removed.ticket.id,timestamp=2,status="SENT",detail="Prueba"))
            r.voidTicket(removed.ticket.id,"Error de captura")
            assertTrue(r.dao.detail(removed.ticket.id)!!.ticket.voided)
            assertEquals(2,r.dao.allTickets().size)
            assertEquals(listOf(retained.ticket.id,removed.ticket.id),r.dao.printAttempts().map {it.ticketId})
            assertEquals(pending,r.readDraft());assertEquals(menu,r.dao.allCatalog())
            r.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ticket_lines WHERE ticketId = ?",arrayOf(removed.ticket.id)).use {assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
            assertTrue(runCatching {r.voidTicket(removed.ticket.id,"Error de captura")}.isFailure)
            val next=r.checkout(draft());assertTrue(next.ticket.id>removed.ticket.id)
            r.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_ticket_delete BEFORE UPDATE ON tickets BEGIN SELECT RAISE(ABORT, 'Forced failure'); END")
            assertTrue(runCatching {r.voidTicket(retained.ticket.id,"Error de captura")}.isFailure)
            assertEquals(retained,r.dao.detail(retained.ticket.id))
            assertEquals(listOf(retained.ticket.id,removed.ticket.id),r.dao.printAttempts().map {it.ticketId})
        } finally {r.db.close()}
    }
    @Test fun backupRoundTripPreservesEditsHistoryDraftAndNextFolio()=runBlocking {
        val source=repo();val target=repo()
        try {
            source.ensureSeed()
            val first=source.dao.allCatalog().first()
            source.updateProduct(first.product.copy(name="Nombre editado",active=false),first.variants.map {it.copy(priceCents=12345)})
            val retained=source.checkout(draft())
            val removed=source.checkout(draft());source.voidTicket(removed.ticket.id,"Error de captura")
            source.dao.insertPrint(PrintAttempt(ticketId=retained.ticket.id,timestamp=1,status="SENT",detail="Prueba"))
            val pending=draft().copy(reference="Mesa pendiente");source.saveDraft(pending)
            val snapshot=BackupCodec.decode(source.exportJson())
            target.ensureSeed();target.restoreBackup(snapshot);target.ensureSeed()
            assertEquals(source.dao.allCatalog(),target.dao.allCatalog())
            assertEquals(source.dao.allTickets(),target.dao.allTickets())
            assertEquals(source.dao.printAttempts(),target.dao.printAttempts())
            assertEquals(pending,target.readDraft())
            target.restoreBackup(snapshot)
            assertEquals(2,target.dao.allTickets().size)
            val next=target.checkout(target.readDraft());assertTrue(next.ticket.id>removed.ticket.id)
        } finally {source.db.close();target.db.close()}
    }
    @Test fun annulledBackupPreservesFolioSequence()=runBlocking {
        val source=repo();val target=repo()
        try {
            source.ensureSeed()
            val saved=source.checkout(draft());source.voidTicket(saved.ticket.id,"Error de captura")
            val snapshot=BackupCodec.decode(source.exportJson())
            assertTrue(snapshot.tickets.single().ticket.voided)
            target.restoreBackup(snapshot)
            assertTrue(target.checkout(draft()).ticket.id>saved.ticket.id)
        } finally {source.db.close();target.db.close()}
    }
    @Test fun legacyBackupsLoadAndMalformedDataIsRejected()=runBlocking {
        val r=repo()
        try {
            r.ensureSeed();val saved=r.checkout(draft())
            val raw=r.exportJson()
            val legacy=JSONObject(raw).put("version",1);legacy.remove("lastTicketId")
            assertEquals(saved.ticket.id,BackupCodec.decode(legacy.toString()).lastTicketId)
            val wrongTotal=JSONObject(raw)
            wrongTotal.getJSONArray("tickets").getJSONObject(0).put("totalCents",1)
            val duplicate=JSONObject(raw)
            duplicate.getJSONArray("tickets").put(duplicate.getJSONArray("tickets").getJSONObject(0))
            val invalid=listOf(wrongTotal.toString(),duplicate.toString(),JSONObject(raw).put("version",999).toString(),raw.take(30))
            invalid.forEach {assertTrue(runCatching {BackupCodec.decode(it)}.isFailure)}
            assertEquals(saved,r.dao.detail(saved.ticket.id))
        } finally {r.db.close()}
    }
    @Test fun failedRestoreKeepsPreviousDatabaseIntact()=runBlocking {
        val source=repo();val target=repo()
        try {
            source.ensureSeed();source.checkout(draft())
            val snapshot=BackupCodec.decode(source.exportJson())
            target.ensureSeed()
            val p=target.dao.allCatalog().first()
            target.updateProduct(p.product.copy(name="Conservar este nombre"),p.variants)
            val old=target.checkout(draft().copy(reference="Ticket anterior"))
            val pending=draft();target.saveDraft(pending)
            target.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_ticket_insert BEFORE INSERT ON tickets BEGIN SELECT RAISE(ABORT, 'Forced failure'); END")
            assertTrue(runCatching {target.restoreBackup(snapshot)}.isFailure)
            assertEquals(listOf(old),target.dao.allTickets())
            assertEquals(pending,target.readDraft())
            assertEquals("Conservar este nombre",target.dao.allCatalog().first().product.name)
        } finally {source.db.close();target.db.close()}
    }
}

class IncomeRangeTest {
    @Test fun inclusiveDatesRespectLocalMidnightAndDaylightSaving() {
        val zone=ZoneId.of("America/New_York")
        val range=ReportRange(LocalDate.of(2026,3,8),LocalDate.of(2026,3,8),zone)
        assertEquals(23*60*60*1000L,range.endExclusiveMillis-range.startMillis)
        fun ticket(id:Long,time:Long,amount:Long)=Ticket(id,"c$id",time,"En mesa","","",amount)
        val rows=listOf(ticket(1,range.startMillis-1,900),ticket(2,range.startMillis,101),ticket(3,range.endExclusiveMillis-1,202),ticket(4,range.endExclusiveMillis,900))
        val report=IncomeReport.create(range,rows)
        assertEquals(2,report.ticketCount);assertEquals(303L,report.totalCents);assertEquals(152L,report.averageCents)
        assertEquals(range.from,report.days.single().date)
    }
    @Test fun emptyAndInvalidPeriodsAreHandled() {
        val date=LocalDate.of(2026,9,6)
        val report=IncomeReport.create(ReportRange(date,date),emptyList())
        assertEquals(0L,report.totalCents);assertEquals(0L,report.averageCents);assertEquals(0,report.ticketCount)
        assertTrue(runCatching {ReportRange(date,date.minusDays(1))}.isFailure)
    }
}

class IncomePdfTest {
    @Test fun reportExportsReadablePaginatedPdf() {
        val from=LocalDate.of(2026,1,1)
        val days=(0 until 45).map {DailyIncome(from.plusDays(it.toLong()),10+it,(100_000L+it*12345))}
        val report=IncomeReport(ReportRange(from,from.plusDays(44)),days)
        val dir=File(checkNotNull(System.getProperty("nd.preview.dir")));dir.mkdirs()
        val pdf=File(dir,"ejemplo-reporte-ingresos.pdf")
        pdf.outputStream().use {IncomePdf.write(report,it)}
        assertEquals("%PDF",pdf.inputStream().use {String(it.readNBytes(4))})
        assertTrue(pdf.length()>1000)
        // The produced PDF is additionally opened and rendered with Poppler during delivery validation.
    }
}
