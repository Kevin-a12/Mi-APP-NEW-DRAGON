package mx.newdragon.pos

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private fun deliveryDraft()=OrderDraft(service="A domicilio",payment=PaymentDraft(exactCash=true),reference="Cliente de prueba",deliveryAddress="Calle de ejemplo #123, Col. Centro",
    customerPhone="668-000-0000",lines=listOf(OrderLine(productId="p001",variantId="p001v1",name="Arroz + 2 guisos",
        category="Combos",description="Arroz con guisos a elegir.",variant="Normal",unitCents=12000,quantity=1,
        selections="Guiso 1: Pollo en tiras\nGuiso 2: Pollo enchilado",notes="Poco dorado")))

class DeliveryReceiptTest {
    @Test fun headerAndDeliveryPrecedeItemsWithoutCategoryAndPreserveChoices() {
        val draft=deliveryDraft();val line=draft.lines.single()
        val ticket=Ticket(19,"example",0,"A domicilio",draft.reference,"",12000,draft.deliveryAddress,draft.customerPhone)
        val detail=TicketDetail(ticket,listOf(TicketLine(1,19,line.productId,line.variantId,line.name,line.category,line.description,
            line.variant,line.unitCents,line.quantity,line.selections,line.notes)))
        val texts=ReceiptContent.blocks(detail).map {it.text}
        assertEquals(listOf("Fracc. Nuevo Horizonte","Ruperto L. Paliza #3519 · Los Mochis, Sin.",
            "Contactanos al: 668-822-7717","Recibo: ND-000019"),texts.take(4))
        assertEquals(1,texts.count {it=="Contactanos al: 668-822-7717"})
        val address=texts.indexOf("A domicilio: ${draft.deliveryAddress}")
        val name=texts.indexOf("Entregar a: ${draft.reference}")
        val contact=texts.indexOf("Contacto: ${draft.customerPhone}")
        val item=texts.indexOf("1 × Arroz + 2 guisos")
        assertTrue(address>4 && address<name && name<contact && contact<item)
        assertTrue(texts.contains(line.selections));assertTrue(texts.contains("Nota: Poco dorado"))
        assertFalse(texts.any {"Combos" in it || "Normal" in it})
        val empty=ReceiptContent.blocks(detail.copy(ticket=ticket.copy(service="Para llevar",reference="",deliveryAddress="",customerPhone="")))
        assertTrue(empty.any {it.text=="Para llevar"})
        assertFalse(empty.any {it.text.startsWith("Contacto:") || it.text.startsWith("Entregar a:") || it.text.startsWith("A domicilio:")})
        val sized=ReceiptContent.blocks(detail.copy(lines=listOf(detail.lines.single().copy(variant="Media orden"))))
        assertTrue(sized.any {it.text=="1 × Arroz + 2 guisos (Media orden)"})
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class DeliveryStorageTest {
    private fun repo():DragonRepository {
        val context=ApplicationProvider.getApplicationContext<Application>()
        return DragonRepository(Room.inMemoryDatabaseBuilder(context,DragonDatabase::class.java).allowMainThreadQueries().build(),context)
    }
    @Test fun newFieldsSurviveDraftCheckoutBackupRestoreAndLegacyVersions(): Unit=runBlocking {
        val source=repo();val target=repo()
        try {
            source.ensureSeed()
            val draft=deliveryDraft();source.saveDraft(draft)
            assertEquals(draft,source.readDraft())
            val saved=source.checkout(draft)
            assertEquals(draft.deliveryAddress,saved.ticket.deliveryAddress)
            assertEquals(draft.customerPhone,saved.ticket.customerPhone)
            assertEquals("Para llevar",source.readDraft().service)
            assertEquals("",source.readDraft().deliveryAddress)
            assertEquals("",source.readDraft().customerPhone)
            val pending=deliveryDraft();source.saveDraft(pending)
            val raw=source.exportJson()
            assertEquals(5,JSONObject(raw).getInt("version"))
            target.restoreBackup(BackupCodec.decode(raw))
            assertEquals(saved,target.dao.allTickets().single())
            assertEquals(pending,target.readDraft())
            for(version in 1..2) {
                val old=JSONObject(raw).put("version",version)
                if(version==1) old.remove("lastTicketId")
                val oldTicket=old.getJSONArray("tickets").getJSONObject(0)
                oldTicket.remove("deliveryAddress");oldTicket.remove("customerPhone");oldTicket.put("notes","Observación antigua")
                val oldDraft=old.getJSONObject("draft")
                oldDraft.remove("deliveryAddress");oldDraft.remove("customerPhone");oldDraft.put("notes","Pedido antiguo")
                val loaded=BackupCodec.decode(old.toString())
                assertEquals("",loaded.tickets.single().ticket.deliveryAddress)
                assertEquals("",loaded.tickets.single().ticket.customerPhone)
                assertEquals("Observación antigua",loaded.tickets.single().ticket.notes)
                assertEquals("Pedido antiguo",loaded.draft.notes)
                assertEquals("",loaded.draft.deliveryAddress)
                assertEquals("",loaded.draft.customerPhone)
                assertEquals("Pedido antiguo",OrderDraft.from(oldDraft.toString()).notes)
            }
            for(key in listOf("deliveryAddress","customerPhone")) {
                for(bad in listOf<Any>(23,JSONObject.NULL,"x".repeat(301))) {
                    val invalid=JSONObject(raw);invalid.getJSONArray("tickets").getJSONObject(0).put(key,bad)
                    assertTrue(runCatching {BackupCodec.decode(invalid.toString())}.isFailure)
                    val invalidDraft=JSONObject(raw);invalidDraft.getJSONObject("draft").put(key,bad)
                    assertTrue(runCatching {BackupCodec.decode(invalidDraft.toString())}.isFailure)
                }
            }
        } finally {source.db.close();target.db.close()}
    }

    @Test fun upgradingRealVersionOneDatabasePreservesAllTablesAndFolio(): Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val name="migration-023.db";context.deleteDatabase(name)
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(File(checkNotNull(System.getProperty("nd.schema.dir")),"mx.newdragon.pos.DragonDatabase/1.json").readText()).getJSONObject("database")
        val oldDraft=deliveryDraft().json().apply {remove("deliveryAddress");remove("customerPhone");put("notes","Conservar observaciones");put("service","En mesa")}
        SQLiteDatabase.openOrCreateDatabase(file,null).use {old ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices") ?: org.json.JSONArray()
                for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val queries=schema.getJSONArray("setupQueries")
            for(i in 0 until queries.length()) old.execSQL(queries.getString(i))
            old.execSQL("INSERT INTO products VALUES('p001','Combos','Nombre editado','Descripción','[]',1,0)")
            old.execSQL("INSERT INTO variants VALUES('p001v1','p001','Normal',12345,0)")
            old.execSQL("INSERT INTO tickets VALUES(7,'old-ticket',1000,'En mesa','Mesa 4','Sin cebolla',12000)")
            old.execSQL("INSERT INTO ticket_lines VALUES(11,7,'p001','p001v1','Nombre original','Combos','Descripción original','Normal',12000,1,'Guiso: Pollo','Nota antigua')")
            old.execSQL("INSERT INTO print_attempts VALUES(2,7,1000,'SENT','Envío anterior')")
            old.execSQL("INSERT INTO metadata VALUES('menu_seed_v1','1')")
            old.execSQL("INSERT INTO drafts VALUES(1,?)",arrayOf(oldDraft.toString()))
            old.execSQL("UPDATE sqlite_sequence SET seq=23 WHERE name='tickets'")
            old.version=1
        }
        val db=Room.databaseBuilder(context,DragonDatabase::class.java,name).addMigrations(DragonDatabase.MIGRATION_1_2,DragonDatabase.MIGRATION_2_3,DragonDatabase.MIGRATION_3_4).build()
        val repo=DragonRepository(db,context)
        try {
            val old=checkNotNull(repo.dao.detail(7)) // Opening invokes Room's schema validation after migration.
            assertEquals(4,db.openHelper.writableDatabase.version)
            assertEquals("Sin cebolla",old.ticket.notes)
            assertEquals("",old.ticket.deliveryAddress);assertEquals("",old.ticket.customerPhone)
            assertEquals("Nombre original",old.lines.single().name)
            assertEquals("Nota antigua",old.lines.single().notes)
            assertEquals("Envío anterior",repo.dao.printAttempts().single().detail)
            repo.ensureSeed()
            assertEquals(1,repo.dao.allCatalog().size)
            assertEquals("Nombre editado",repo.dao.allCatalog().single().product.name)
            assertEquals(12345L,repo.dao.allCatalog().single().variants.single().priceCents)
            assertEquals("Conservar observaciones",repo.readDraft().notes)
            assertEquals("En mesa",repo.readDraft().service)
            val next=repo.checkout(deliveryDraft())
            assertEquals(24L,next.ticket.id)
            assertEquals("668-000-0000",next.ticket.customerPhone)
            assertEquals("Para llevar",repo.readDraft().service)
            assertEquals(2,BackupCodec.decode(repo.exportJson()).tickets.size)
        } finally {db.close();context.deleteDatabase(name)}
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=DragonApplication::class,qualifiers="w400dp-h900dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeliveryInterfaceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[DragonViewModel::class.java]
    @Before fun startFresh() {
        compose.waitUntil(30_000) {vm.ready}
        resetInterfaceFixture(vm)
        compose.runOnIdle {vm.clearDraft();vm.navigate("Pedidos")}
    }
    @Test fun narrowScreenValidatesDeliveryAndKeepsPendingThenRegistersPayment() {
        compose.runOnIdle {vm.add(deliveryDraft().lines.single())}
        compose.onNodeWithText("Pedido (1)").performClick()
        compose.onNodeWithText("Servicio: Para llevar").performClick()
        compose.onNodeWithText("A domicilio",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Domicilio de entrega *").performTextInput("Calle de ejemplo #123, Col. Centro")
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Agregar nombre, contacto o notas"))
        compose.onNodeWithText("Agregar nombre, contacto o notas").performClick()
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Nombre (opcional)"))
        compose.onNodeWithText("Nombre (opcional)").performTextInput("Cliente de prueba")
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Contacto (opcional)"))
        compose.onNodeWithText("Contacto (opcional)").performTextInput("668-000-0000")
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-pedido-telefono.png")}
        compose.onNodeWithText("Guardar pendiente").performClick()
        compose.onNodeWithText("Guardar sin imprimir").performClick()
        compose.waitUntil(30_000) {vm.selectedTicket!=null && !vm.saving}
        val pending=vm.selectedTicket!!.ticket
        assertEquals(OrderStates.PENDING,pending.status)
        assertEquals("Calle de ejemplo #123, Col. Centro",pending.deliveryAddress)
        compose.onNodeWithText("Editar y cobrar").performClick()
        compose.onNodeWithText("Cobrar $120.00").performClick()
        compose.onNodeWithText("Pago exacto · $120.00").performClick()
        compose.onNodeWithText("Guardar sin imprimir").performClick()
        try {
            compose.waitUntil(30_000) {vm.selectedTicket?.ticket?.status==OrderStates.PAID && !vm.saving}
        } catch(e:Throwable) {
            throw AssertionError("Payment did not complete: message=${vm.message}; draft=${vm.draft}; selected=${vm.selectedTicket}; screen=${vm.screen}",e)
        }
        assertEquals(pending.id,vm.selectedTicket!!.ticket.id)
        compose.onNodeWithText("Marcar entregado").performClick()
        compose.waitUntil(30_000) {vm.selectedTicket?.ticket?.status==OrderStates.DELIVERED}
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-ticket-telefono.png")}
        compose.onNodeWithText("Nuevo pedido").performClick()
        compose.onNodeWithText("Pedido (0)").performClick()
        compose.onNodeWithText("Servicio: Para llevar").assertExists()
        compose.onNodeWithText("Domicilio de entrega *").assertDoesNotExist()
    }
}
