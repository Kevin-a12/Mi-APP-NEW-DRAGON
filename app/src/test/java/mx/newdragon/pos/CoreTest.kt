package mx.newdragon.pos

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Robolectric activities share an app data directory across UI classes in this suite.
// Reset only the simulated fixture so each end-to-end scenario starts at folio 1.
internal fun resetInterfaceFixture(vm:DragonViewModel) = runBlocking(Dispatchers.IO) {
    vm.repository.db.clearAllTables()
    vm.repository.db.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence")
    vm.repository.ensureSeed()
}

class MoneyAndSearchTest {
    @Test fun exactMoneyAndInvalidAmounts() {
        assertEquals(11025L,Money.parse("110.25"))
        assertEquals(1050L,Money.parse("10,50"))
        listOf("-1","0","1.001","NaN","1e3","").forEach { assertTrue(runCatching {Money.parse(it)}.isFailure) }
        assertEquals(30L,Money.parse("0.10")*3)
    }
    @Test fun searchCombinesCategoryWordsAndAccents() {
        val p=Product("id","Sushi empanizado","Camarón","Con aguacate y Philadelphia")
        assertTrue(MenuSearch.matches(p,"CAMARON"))
        assertTrue(MenuSearch.matches(p,"philadelphia camaron","Sushi empanizado"))
        assertTrue(MenuSearch.matches(p,"camaron","Todas"))
        assertFalse(MenuSearch.matches(p,"camaron","Sopas"))
        assertFalse(MenuSearch.matches(p,"pollo"))
        assertTrue(MenuSearch.matches(p,""))
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class StorageTest {
    private fun repository():DragonRepository {
        val context=ApplicationProvider.getApplicationContext<Application>()
        return DragonRepository(Room.inMemoryDatabaseBuilder(context,DragonDatabase::class.java).allowMainThreadQueries().build(),context)
    }
    @Test fun seedLoadsOnceAndPreservesEdits() = runBlocking {
        val repo=repository()
        try {
            repo.ensureSeed()
            val catalog=repo.dao.allCatalog()
            assertEquals(101,catalog.size)
            assertEquals(152,catalog.sumOf {it.variants.size})
            assertEquals(15,catalog.map {it.product.category}.distinct().size)
            val first=catalog.first()
            repo.updateProduct(first.product.copy(name="Mi nombre editado",active=false),first.variants.map {it.copy(priceCents=12345)})
            repo.ensureSeed()
            val edited=repo.dao.allCatalog().first()
            assertEquals("Mi nombre editado",edited.product.name)
            assertFalse(edited.product.active)
            assertEquals(12345L,edited.variants.first().priceCents)
        } finally {repo.db.close()}
    }
    @Test fun checkoutIsAtomicIdempotentAndKeepsSnapshot() = runBlocking {
        val repo=repository()
        try {
            repo.ensureSeed()
            val item=repo.dao.allCatalog().first {it.product.name=="Arroz de pollo"}
            val v=item.variants.first {it.label=="Media orden"}
            val draft=OrderDraft(reference="Mesa 2",lines=listOf(OrderLine(productId=item.product.id,variantId=v.id,
                name=item.product.name,category=item.product.category,description="Descripción original",variant=v.label,unitCents=v.priceCents,quantity=2)),payment=PaymentDraft(exactCash=true))
            repo.saveDraft(draft)
            val saved=repo.checkout(draft)
            assertEquals(28000L,saved.ticket.totalCents)
            repo.updateProduct(item.product.copy(name="Nuevo nombre"),item.variants.map {it.copy(priceCents=99900)})
            val retry=repo.checkout(draft)
            assertEquals(saved.ticket.id,retry.ticket.id)
            assertEquals(1,repo.dao.allTickets().size)
            assertEquals("Arroz de pollo",retry.lines.single().name)
            assertEquals("Descripción original",retry.lines.single().description)
            assertEquals(14000L,retry.lines.single().unitCents)
            assertTrue(repo.readDraft().lines.isEmpty())
            val backup=org.json.JSONObject(repo.exportJson())
            assertEquals(1,backup.getJSONArray("tickets").length())
            assertEquals(101,backup.getJSONArray("catalog").length())
        } finally {repo.db.close()}
    }
    @Test fun invalidCheckoutRetainsDraftAndWritesNoTicket() = runBlocking {
        val repo=repository()
        try {
            val draft=OrderDraft(lines=listOf(OrderLine(productId="p",variantId="v",name="Platillo",category="Prueba",description="",variant="Normal",unitCents=100,quantity=0)))
            repo.saveDraft(draft)
            assertTrue(runCatching {repo.checkout(draft)}.isFailure)
            assertEquals(draft.clientId,repo.readDraft().clientId)
            assertEquals(1,repo.readDraft().lines.size)
            assertTrue(repo.dao.allTickets().isEmpty())
        } finally {repo.db.close()}
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=DragonApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InterfaceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[DragonViewModel::class.java]
    @Before fun startFresh() {
        compose.waitUntil(30_000) {vm.ready}
        resetInterfaceFixture(vm)
        compose.runOnIdle {vm.clearDraft();vm.navigate("Pedidos")}
    }
    @Test fun filterSaveAnnulAndRestoreFromBackup() {
        compose.onNodeWithText("Buscar nombre o descripción").performTextInput("camaron")
        compose.onNodeWithText("Categoría: Todas").performClick()
        compose.onNodeWithText("Sushi empanizado",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("1 platillo").assertExists()
        compose.onNodeWithText("Quitar filtros").performClick()
        compose.onNodeWithText("Arroz + 1 guiso",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Guiso: Seleccionar").performClick()
        compose.onNodeWithText("Pollo agridulce",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Añadir al pedido").performClick()
        compose.onNodeWithText("Guiso: Pollo agridulce").assertExists()
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-tablet.png")}
        compose.onNodeWithText("Cobrar $110.00").performClick()
        compose.onNodeWithText("Guardar sin imprimir").assertIsNotEnabled()
        compose.onNodeWithText("Pago exacto · $110.00").performClick()
        compose.onNodeWithText("Guardar sin imprimir").assertIsEnabled().performClick()
        compose.waitUntil(30_000) {vm.selectedTicket!=null && !vm.saving}
        val saved=vm.selectedTicket!!
        val backupFile=File(compose.activity.cacheDir,"round-trip-030.json")
        runBlocking {backupFile.writeText(vm.repository.exportJson())}
        compose.onNodeWithText("Anular ticket").performClick()
        compose.onNodeWithText("Motivo de la anulación *").performTextInput("Pedido de prueba")
        compose.onNodeWithText("Confirmar anulación").performClick()
        compose.waitUntil(30_000) {vm.selectedTicket?.ticket?.voided==true && !vm.deleting}
        runBlocking {assertEquals(saved.lines,vm.repository.dao.detail(saved.ticket.id)!!.lines);assertEquals(1,vm.repository.dao.allTickets().size)}
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Guardar o restaurar respaldo").performClick()
        compose.runOnIdle {vm.readBackup(Uri.fromFile(backupFile))}
        compose.waitUntil(30_000) {vm.pendingBackup!=null && !vm.fileBusy}
        compose.onNodeWithText("Cancelar").performClick()
        runBlocking {assertTrue(vm.repository.dao.detail(saved.ticket.id)!!.ticket.voided)}
        compose.runOnIdle {vm.readBackup(Uri.fromFile(backupFile))}
        compose.waitUntil(30_000) {vm.pendingBackup!=null && !vm.fileBusy}
        compose.onNodeWithText("Restaurar y reemplazar").performClick()
        compose.waitUntil(30_000) {!vm.restoring && vm.pendingBackup==null && runBlocking {vm.repository.dao.detail(saved.ticket.id)?.ticket?.voided==false}}
        compose.onNodeWithText("Reportes").performClick()
        compose.onNodeWithText("Hoy").performClick()
        compose.waitUntil(30_000) {compose.onAllNodesWithText("Importe total MXN").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Zona horaria: Los Mochis, Sin.").assertExists()
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-reportes.png")}
        runBlocking {assertEquals(11000L,vm.repository.dao.allTickets().single().ticket.totalCents)}
    }
}
internal fun capturePreview(activity:MainActivity,name:String,dialog:Boolean=false) {
    val root=if(dialog) checkNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView) else activity.window.decorView
    val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
    root.draw(Canvas(image))
    val dir=File(checkNotNull(System.getProperty("nd.preview.dir")));dir.mkdirs()
    File(dir,name).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    image.recycle()
}
