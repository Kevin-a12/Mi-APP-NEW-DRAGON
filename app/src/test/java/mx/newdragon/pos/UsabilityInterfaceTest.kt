package mx.newdragon.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=DragonApplication::class,qualifiers="w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UsabilityInterfaceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(compose.activity)[DragonViewModel::class.java]
    @Before fun startFresh() {
        compose.waitUntil(30_000) {vm.ready}
        resetInterfaceFixture(vm)
        compose.runOnIdle {vm.clearDraft();vm.navigate("Pedidos");vm.settingsPage="Inicio";vm.reportPage="Ventas"}
    }
    @Test fun editGuisoAndNotesThenRemoveAndUndoPreservesTheLine() {
        compose.onNodeWithText("Arroz + 1 guiso",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Guiso: Seleccionar").performClick()
        compose.onNodeWithText("Pollo agridulce",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Añadir al pedido").performClick()
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Editar"))
        compose.onNodeWithContentDescription("Reducir cantidad de Arroz + 1 guiso").assertIsNotEnabled()
        compose.onNodeWithText("Editar").performClick()
        compose.onNode(hasText("Guiso: Pollo agridulce") and hasClickAction()).performClick()
        compose.onNodeWithText("Pollo enchilado",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Notas del platillo").performScrollTo().performTextInput("Sin cebolla")
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-dialogo-editar.png",dialog=true)}
        compose.onNodeWithText("Guardar cambios").performClick()
        compose.runOnIdle {
            assertEquals("Guiso: Pollo enchilado",vm.draft.lines.single().selections)
            assertEquals("Sin cebolla",vm.draft.lines.single().notes)
        }
        val edited=vm.draft.lines.single()
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Eliminar"))
        compose.onNodeWithText("Eliminar").performClick()
        compose.runOnIdle {assertTrue(vm.draft.lines.isEmpty())}
        compose.onNodeWithText("Deshacer").performClick()
        compose.runOnIdle {assertEquals(edited,vm.draft.lines.single());capturePreview(compose.activity,"new-dragon-030-edicion.png")}
    }
    @Test fun menuCanRemovePresentationAndProtectUnsavedEdits() {
        val repository=vm.repository
        val item=runBlocking(Dispatchers.IO) {repository.dao.allCatalog().first {it.variants.size==2}}
        compose.runOnIdle {vm.navigate("Ajustes");vm.settingsPage="Mi menú";vm.menuItem=item}
        compose.onNodeWithText("Nombre *").performTextReplacement("Platillo actualizado")
        compose.onAllNodesWithText("Quitar presentación").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Tienes cambios sin guardar").assertExists()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.onNodeWithText("Guardar cambios").performClick()
        compose.waitUntil(30_000) {!vm.menuSaving && vm.menuItem==null}
        val saved=runBlocking(Dispatchers.IO) {repository.dao.allCatalog().first {it.product.id==item.product.id}}
        assertEquals("Platillo actualizado",saved.product.name)
        assertEquals(1,saved.variants.size)
        compose.runOnIdle {vm.menuItem=saved}
        compose.onNodeWithText("Nombre *").performTextReplacement("Cambio a descartar")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Cambio a descartar").assertExists()
        compose.onAllNodesWithText("Quitar presentación").onFirst().performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-menu.png",dialog=true)}
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Descartar cambios").performClick()
        compose.runOnIdle {assertNull(vm.menuItem)}
        runBlocking(Dispatchers.IO) {assertEquals(saved,repository.dao.allCatalog().first {it.product.id==item.product.id})}
    }
    @Test fun openingSaleWithdrawalAndPhysicalCountProduceTheExpectedCut() {
        compose.onNodeWithText("Abrir caja").performClick()
        compose.onNodeWithText("Fondo inicial MXN").performTextInput("100")
        compose.onNodeWithText("Iniciar caja").performClick()
        compose.waitUntil(30_000) {!vm.cashBusy && vm.cashSessions.value.any {it.closedAt==0L}}
        compose.onNodeWithText("Pedidos").performClick()
        compose.runOnIdle {vm.add(OrderLine(productId="p",variantId="v",name="Venta de prueba",category="Combos",description="",variant="Normal",unitCents=16500,quantity=1))}
        compose.onNodeWithText("Cobrar $165.00").performClick()
        compose.onNodeWithText("Efectivo recibido").performTextInput("200")
        compose.onNodeWithText("Guardar sin imprimir").performClick()
        compose.waitUntil(30_000) {vm.selectedTicket!=null && !vm.saving}
        assertEquals(3500L,vm.selectedTicket!!.ticket.payment.changeCents)
        compose.onNodeWithText("Reportes").performClick()
        compose.onNode(hasText("Caja y cortes") and hasClickAction()).performClick()
        compose.onNodeWithText("Registrar entrada o salida").performClick()
        compose.onNodeWithText("Monto MXN").performTextInput("20")
        compose.onNodeWithText("Concepto *").performTextInput("Compra de hielo")
        compose.onNodeWithText("Guardar movimiento").performClick()
        compose.waitUntil(30_000) {!vm.cashBusy && vm.cashMovements.value.any {it.concept=="Compra de hielo"}}
        compose.onNodeWithText("Contar y cerrar caja").performClick()
        compose.onNodeWithText("Efectivo que contaste MXN").performTextInput("240")
        compose.onNodeWithText("Diferencia: -$5.00").assertExists()
        compose.onNodeWithText("Guardar corte").performClick()
        compose.waitUntil(30_000) {!vm.cashBusy && vm.cashSessions.value.any {it.closedAt>0L}}
        val cut=vm.cashSessions.value.single()
        assertEquals(24500L,cut.expectedCents)
        assertEquals(24000L,cut.countedCents)
        assertEquals(-500L,cut.differenceCents)
        compose.runOnIdle {capturePreview(compose.activity,"new-dragon-030-caja.png")}
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed();assertEquals("Pedidos",vm.screen)}
    }
}
