package mx.newdragon.pos

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ExplicitPaymentTest {
    @Test fun blankCashAndUnconfirmedTransferCannotBecomeSales() {
        assertTrue(runCatching {PaymentDraft().resolve(16500)}.isFailure)
        assertTrue(runCatching {PaymentDraft(PaymentMethod.TRANSFER).resolve(16500)}.isFailure)
        assertTrue(runCatching {PaymentDraft(PaymentMethod.MIXED,"100","100").resolve(16500)}.isFailure)
        assertEquals(0L,PaymentDraft(exactCash=true).resolve(16500).changeCents)
        assertEquals(3500L,PaymentDraft(cashReceivedInput="200").resolve(16500).changeCents)
        assertEquals(16500L,PaymentDraft(PaymentMethod.TRANSFER,transferConfirmed=true).resolve(16500).transferCents)
    }
    @Test fun savedDraftKeepsExplicitConfirmationAndLegacyDraftRequiresReview() {
        val payment=PaymentDraft(PaymentMethod.MIXED,"100","",exactCash=true,transferConfirmed=true)
        assertEquals(payment,PaymentDraft.from(payment.json()))
        val old=payment.json().apply {remove("exactCash");remove("transferConfirmed")}
        assertTrue(runCatching {PaymentDraft.from(old).resolve(16500)}.isFailure)
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class BusinessStorageTest {
    private fun repo():DragonRepository {
        val context=ApplicationProvider.getApplicationContext<Application>()
        return DragonRepository(Room.inMemoryDatabaseBuilder(context,DragonDatabase::class.java).build(),context)
    }
    private fun order(payment:PaymentDraft=PaymentDraft(exactCash=true))=OrderDraft(reference="Cliente Camarón",customerPhone="6681234567",
        lines=listOf(OrderLine(productId="p001",variantId="p001v1",name="Platillo",category="Combos",description="",variant="Normal",unitCents=16500,quantity=1)),payment=payment)
    @Test fun deliveryValidationRejectsMissingAddressAndNeverReinterpretsService()=runBlocking {
        val r=repo()
        try {
            val invalid=order().copy(service="A domicilio")
            r.saveDraft(invalid)
            assertTrue(runCatching {r.checkout(invalid)}.isFailure)
            assertTrue(r.dao.allTickets().isEmpty())
            assertEquals(invalid,r.readDraft())
            val delivery=r.checkout(invalid.copy(deliveryAddress="Calle 123"))
            assertEquals("A domicilio",delivery.ticket.service)
            assertTrue(ReceiptContent.blocks(delivery).any {it.text=="A domicilio: Calle 123"})
            val table=r.checkout(order().copy(service="En mesa",reference="Mesa 2",deliveryAddress="Dirección anterior"))
            assertEquals("",table.ticket.deliveryAddress)
            assertTrue(ReceiptContent.blocks(table).any {it.text=="En mesa"})
            assertFalse(ReceiptContent.blocks(table).any {it.text.startsWith("A domicilio:")})
        } finally {r.db.close()}
    }
    @Test fun pendingCanBeEditedAndPaidWithoutAnotherFolioOrPrematureIncome()=runBlocking {
        val r=repo()
        try {
            val draft=order(PaymentDraft())
            val pending=r.checkout(draft,paid=false)
            val day=RestaurantTime.today();val range=ReportRange(day,day)
            assertEquals(OrderStates.PENDING,pending.ticket.status)
            assertEquals(0L,IncomeReport.create(range,r.dao.allTickets().map {it.ticket}).totalCents)
            val edited=draft.copy(sourceTicketId=pending.ticket.id,lines=draft.lines.map {it.copy(quantity=2,notes="Sin cebolla")},payment=PaymentDraft(exactCash=true))
            r.saveDraft(edited)
            assertEquals(pending.ticket.id,BackupCodec.decode(r.exportJson()).draft.sourceTicketId)
            val paid=r.checkout(edited)
            assertEquals(pending.ticket.id,paid.ticket.id)
            assertEquals(1,r.dao.allTickets().size)
            assertEquals(33000L,IncomeReport.create(range,r.dao.allTickets().map {it.ticket}).totalCents)
            assertEquals("Sin cebolla",paid.lines.single().notes)
            assertTrue(runCatching {r.checkout(edited)}.isFailure)
            r.markDelivered(paid.ticket.id)
            assertEquals(OrderStates.DELIVERED,r.dao.detail(paid.ticket.id)!!.ticket.status)
            assertEquals(33000L,IncomeReport.create(range,r.dao.allTickets().map {it.ticket}).totalCents)
        } finally {r.db.close()}
    }
    @Test fun annullingPendingWhileEditingKeepsBackupRestorable()=runBlocking {
        val r=repo()
        try {
            val draft=order();val pending=r.checkout(draft,paid=false)
            r.saveDraft(draft.copy(sourceTicketId=pending.ticket.id))
            r.voidTicket(pending.ticket.id,"Cliente canceló")
            assertTrue(r.readDraft().lines.isEmpty())
            assertEquals(1,BackupCodec.decode(r.exportJson()).tickets.size)
            assertTrue(runCatching {r.markDelivered(pending.ticket.id)}.isFailure)
        } finally {r.db.close()}
    }
    @Test fun cashCutExcludesChangeAndTransfersAndCapturesWithdrawals()=runBlocking {
        val r=repo()
        try {
            r.openCash(10000)
            assertTrue(runCatching {r.openCash(0)}.isFailure)
            r.checkout(order(PaymentDraft(cashReceivedInput="500")))
            r.checkout(order(PaymentDraft(PaymentMethod.TRANSFER,transferConfirmed=true)))
            r.cashMovement(-2000,"Compra de hielo")
            r.cashMovement(500,"Fondo adicional")
            val session=r.dao.activeCashSession()!!
            assertEquals(25000L,CashSummary.create(session,r.dao.allTickets().map {it.ticket},r.dao.cashMovements()).expectedCents)
            val cut=r.closeCash(24500,"Faltaron cinco pesos")
            assertEquals(25000L,cut.expectedCents);assertEquals(-500L,cut.differenceCents)
            assertNull(r.dao.activeCashSession())
            assertTrue(runCatching {r.closeCash(24500)}.isFailure)
            val outside=r.checkout(order())
            assertEquals(0L,outside.ticket.cashSessionId)
            assertEquals(cut,r.dao.cashSessions().single())
        } finally {r.db.close()}
    }
    @Test fun annulmentPreservesPaperHistoryAndRefundIsRecordedOnce()=runBlocking {
        val r=repo()
        try {
            r.openCash(10000)
            val ticket=r.checkout(order(PaymentDraft(cashReceivedInput="200")))
            r.dao.insertPrint(PrintAttempt(ticketId=ticket.ticket.id,timestamp=1,status="SENT",detail="Envío"))
            assertTrue(runCatching {r.voidTicket(ticket.ticket.id,"")}.isFailure)
            r.voidTicket(ticket.ticket.id,"Pedido equivocado",refundCash=true)
            val void=r.dao.detail(ticket.ticket.id)!!
            assertEquals(ticket.lines,void.lines);assertTrue(void.ticket.voided)
            assertEquals(1,r.dao.printAttempts().size)
            assertEquals(1,r.dao.cashMovements().size)
            assertEquals(-16500L,r.dao.cashMovements().single().deltaCents)
            assertTrue(runCatching {r.voidTicket(ticket.ticket.id,"Otra devolución",refundCash=true)}.isFailure)
            val session=r.dao.activeCashSession()!!
            assertEquals(10000L,CashSummary.create(session,r.dao.allTickets().map {it.ticket},r.dao.cashMovements()).expectedCents)
            val report=IncomeReport.create(ReportRange(RestaurantTime.today(),RestaurantTime.today()),r.dao.allTickets().map {it.ticket})
            assertEquals(0L,report.totalCents);assertEquals(1,report.voidedCount);assertEquals(16500L,report.voidedCents)
        } finally {r.db.close()}
    }
    @Test fun closedCutIsImmutableWhenHistoricalSaleIsAnnulled()=runBlocking {
        val r=repo()
        try {
            r.openCash(0);val paid=r.checkout(order());val cut=r.closeCash(16500)
            r.voidTicket(paid.ticket.id,"Cancelación posterior")
            assertEquals(cut,r.dao.cashSessions().single())
            assertTrue(r.dao.detail(paid.ticket.id)!!.ticket.voided)
        } finally {r.db.close()}
    }
    @Test fun restorePreservesAuditCashRefundAndPendingEditingAndRejectsOrphans()=runBlocking {
        val source=repo();val target=repo()
        try {
            source.ensureSeed();source.openCash(5000)
            val paid=source.checkout(order());source.voidTicket(paid.ticket.id,"Prueba de devolución",true)
            val pendingDraft=order(PaymentDraft());val pending=source.checkout(pendingDraft,paid=false)
            source.saveDraft(pendingDraft.copy(sourceTicketId=pending.ticket.id))
            val raw=source.exportJson();val snapshot=BackupCodec.decode(raw)
            target.restoreBackup(snapshot)
            assertEquals(source.dao.allTickets(),target.dao.allTickets())
            assertEquals(source.dao.cashSessions(),target.dao.cashSessions())
            assertEquals(source.dao.cashMovements(),target.dao.cashMovements())
            assertEquals(source.readDraft(),target.readDraft())
            val malformed=JSONObject(raw);malformed.getJSONArray("cashSessions").remove(0)
            assertTrue(runCatching {BackupCodec.decode(malformed.toString())}.isFailure)
            val duplicate=JSONObject(raw);duplicate.getJSONArray("cashMovements").put(duplicate.getJSONArray("cashMovements").getJSONObject(0))
            assertTrue(runCatching {BackupCodec.decode(duplicate.toString())}.isFailure)
        } finally {source.db.close();target.db.close()}
    }
    @Test fun removingVariantAndHidingGuisoDoesNotChangeSavedTicket()=runBlocking {
        val r=repo()
        try {
            r.ensureSeed();val item=r.dao.allCatalog().first {it.variants.size>1}
            val v=item.variants.last()
            val frozen=r.checkout(order().copy(lines=listOf(OrderLine(productId=item.product.id,variantId=v.id,name=item.product.name,category=item.product.category,description=item.product.description,variant=v.label,unitCents=v.priceCents,quantity=1))))
            val group=OptionGroup("Guiso",1,listOf("Pollo","Res"),setOf("Pollo"))
            r.updateProduct(item.product.copy(optionsJson=optionsJson(listOf(group))),item.variants.dropLast(1))
            assertEquals(item.variants.size-1,r.dao.allCatalog().first {it.product.id==item.product.id}.variants.size)
            assertEquals(frozen,r.dao.detail(frozen.ticket.id))
            assertEquals(group,optionGroups(r.dao.allCatalog().first {it.product.id==item.product.id}.product.optionsJson).single())
        } finally {r.db.close()}
    }
    @Test fun historyFiltersCombineAccentInsensitiveSearchStateDateAndPayment() {
        val now=System.currentTimeMillis()
        val pending=Ticket(1,"p",now,"A domicilio","Cliente Camarón","",16500,"Calle 1","668123",status=OrderStates.PENDING)
        val paid=Ticket(2,"c",now,"Para llevar","Ana","",16500,paymentMethod="CASH",cashReceivedCents=20000)
        assertTrue(TicketFilter(query="camaron 668",state="Pendiente de pago").matches(pending))
        assertFalse(TicketFilter(payment="Efectivo").matches(pending))
        assertTrue(TicketFilter(payment="Efectivo",service="Para llevar").matches(paid))
        assertFalse(TicketFilter(state="Anulados").matches(paid))
        assertTrue(TicketFilter(state="Anulados").matches(paid.copy(voided=true)))
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class VersionFourMigrationTest {
    @Test fun versionThreeUpgradesWithoutLosingEditedMenuPaymentsDraftOrSequence()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val name="migration-030.db";context.deleteDatabase(name)
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(File(checkNotNull(System.getProperty("nd.schema.dir")),"mx.newdragon.pos.DragonDatabase/3.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file,null).use {old ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {val e=entities.getJSONObject(i);old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indexes=e.optJSONArray("indices") ?: org.json.JSONArray();for(k in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO products VALUES('p','Combos','Nombre editado','Descripción','[]',1,0)")
            old.execSQL("INSERT INTO variants VALUES('v','p','Normal',12345,0)")
            old.execSQL("INSERT INTO tickets VALUES(7,'legacy',1000,'Para llevar','Cliente','',16500,'Calle antigua','668000','CASH',0,50000)")
            old.execSQL("INSERT INTO ticket_lines VALUES(11,7,'p','v','Original','Combos','','Normal',16500,1,'','')")
            old.execSQL("INSERT INTO print_attempts VALUES(2,7,1000,'SENT','Envío anterior')")
            old.execSQL("INSERT INTO metadata VALUES('menu_seed_v1','1')")
            val draft=orderLegacyJson()
            old.execSQL("INSERT INTO drafts VALUES(1,?)",arrayOf(draft.toString()))
            old.execSQL("UPDATE sqlite_sequence SET seq=23 WHERE name='tickets'");old.version=3
        }
        val db=Room.databaseBuilder(context,DragonDatabase::class.java,name).addMigrations(DragonDatabase.MIGRATION_3_4).build()
        val r=DragonRepository(db,context)
        try {
            val t=r.dao.detail(7)!!.ticket
            assertEquals(4,db.openHelper.writableDatabase.version)
            assertEquals(33500L,t.payment.changeCents);assertEquals(1000L,t.paidAt)
            assertEquals("A domicilio",t.service);assertEquals("Calle antigua",t.deliveryAddress)
            assertFalse(t.voided);assertEquals(0L,t.cashSessionId)
            r.ensureSeed();assertEquals("Nombre editado",r.dao.allCatalog().single().product.name)
            assertEquals(12345L,r.dao.allCatalog().single().variants.single().priceCents)
            assertEquals("Envío anterior",r.dao.printAttempts().single().detail)
            assertEquals("A domicilio",r.readDraft().service)
            val next=r.checkout(OrderDraft(lines=listOf(OrderLine(productId="p",variantId="v",name="Prueba",category="Combos",description="",variant="Normal",unitCents=100,quantity=1)),payment=PaymentDraft(exactCash=true)))
            assertEquals(24L,next.ticket.id)
            assertEquals(2,BackupCodec.decode(r.exportJson()).tickets.size)
        } finally {db.close();context.deleteDatabase(name)}
    }
    private fun orderLegacyJson()=JSONObject().put("clientId","draft-legacy").put("service","Para llevar").put("reference","Pendiente").put("notes","")
        .put("deliveryAddress","Calle del borrador").put("customerPhone","668000").put("lines",org.json.JSONArray())
        .put("payment",JSONObject().put("method","CASH").put("transferInput","").put("cashReceivedInput",""))
}
