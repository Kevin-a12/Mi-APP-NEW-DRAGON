package mx.newdragon.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=DragonApplication::class, qualifiers="w400dp-h900dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OperationInterfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[DragonViewModel::class.java]

    @Before fun prepareOrder() {
        compose.waitUntil(30_000) { vm.ready }
        resetInterfaceFixture(vm)
        compose.runOnIdle {
            vm.clearDraft()
            vm.navigate("Pedidos")
            vm.message = null
            vm.add(OrderLine(productId="sample-1", variantId="normal-1", name="Arroz + 1 guiso",
                category="Combos", description="", variant="Normal", unitCents=11000, quantity=1,
                selections="Guiso: Pollo agridulce"))
            vm.add(OrderLine(productId="sample-2", variantId="normal-2", name="Arroz + 2 guisos",
                category="Combos", description="", variant="Normal", unitCents=12000, quantity=1,
                selections="Guisos: Pollo enchilado y Res con brócoli"))
        }
    }

    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi")
    fun tabletShowsNavigationCatalogAndEditableOrderTogether() {
        compose.onNodeWithTag("main-navigation-rail").assertIsDisplayed()
        compose.onNodeWithText("Buscar nombre o descripción").assertIsDisplayed()
        compose.onNodeWithText("Cobrar $230.00").assertIsDisplayed()
        val minus = compose.onNodeWithContentDescription("Reducir cantidad de Arroz + 1 guiso")
        minus.assertIsNotEnabled()
        val bounds = minus.fetchSemanticsNode().boundsInRoot
        assertTrue("Quantity target must be at least 48dp", bounds.width >= 48 && bounds.height >= 48)
        compose.runOnIdle { capturePreview(compose.activity, "operation-tablet-order.png") }
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Editar menú").assertIsDisplayed()
        compose.onNodeWithText("Pedidos").performClick()
        compose.onNodeWithText("Cobrar $230.00").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, vm.draft.lines.size) }
    }

    @Test fun phoneShowsBothOrderLinesAndOptionalFieldsCanReallyCollapse() {
        compose.onNodeWithTag("main-navigation-rail").assertDoesNotExist()
        compose.onNodeWithText("Pedido (2)").performClick()
        compose.onNodeWithContentDescription("Reducir cantidad de Arroz + 1 guiso").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reducir cantidad de Arroz + 2 guisos").assertIsDisplayed()
        compose.onNodeWithText("Cobrar $230.00").assertIsDisplayed()
        compose.onNodeWithText("Nombre (opcional)").assertDoesNotExist()
        compose.runOnIdle { capturePreview(compose.activity, "operation-phone-order.png") }
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Agregar nombre, contacto o notas"))
        compose.onNodeWithText("Agregar nombre, contacto o notas").performClick()
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Nombre (opcional)"))
        compose.onNodeWithText("Nombre (opcional)").performTextInput("Cliente de prueba")
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Ocultar datos del cliente"))
        compose.onNodeWithText("Ocultar datos del cliente").performClick()
        compose.onNodeWithText("Nombre (opcional)").assertDoesNotExist()
        compose.runOnIdle { assertEquals("Cliente de prueba", vm.draft.reference) }
    }

    @Test fun mixedPaymentKeepsMethodVisibleAndStoresCorrectChange() {
        compose.onNodeWithText("Pedido (2)").performClick()
        completeMixedPayment()
        compose.onNodeWithText("Mixto").assertIsDisplayed()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Cambio: $70.00"))
        compose.onNodeWithText("Cambio: $70.00").assertIsDisplayed()
        compose.onNodeWithText("Mixto").assertIsDisplayed()
        compose.runOnIdle { capturePreview(compose.activity, "operation-phone-payment.png") }
        compose.onNodeWithText("Guardar sin imprimir").performClick()
        compose.waitUntil(30_000) { !vm.saving && vm.selectedTicket != null }
        compose.runOnIdle {
            val ticket = vm.selectedTicket!!.ticket
            assertEquals(23000L, ticket.totalCents)
            assertEquals(10000L, ticket.payment.transferCents)
            assertEquals(7000L, ticket.payment.changeCents)
        }
    }

    @Test @Config(qualifiers="w1280dp-h800dp-land-mdpi")
    fun tabletPaymentKeepsTheOrderAndCatalogInContext() {
        completeMixedPayment()
        compose.onNodeWithTag("main-navigation-rail").assertIsDisplayed()
        compose.onNodeWithText("Buscar nombre o descripción").assertIsDisplayed()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Cambio: $70.00"))
        compose.runOnIdle { capturePreview(compose.activity, "operation-tablet-payment.png") }
    }

    @Test @Config(qualifiers="w320dp-h640dp-port-mdpi")
    fun smallWindowCanReachPaymentFieldsAndFinalActions() {
        compose.onNodeWithText("Pedido (2)").performClick()
        completeMixedPayment()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Cambio: $70.00"))
        compose.onNodeWithText("Cambio: $70.00").assertIsDisplayed()
        compose.onNodeWithText("Guardar sin imprimir").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Guardar e imprimir").assertIsDisplayed()
        compose.runOnIdle { capturePreview(compose.activity, "operation-small-payment.png") }
    }

    @Test @Config(qualifiers="w320dp-h640dp-port-mdpi")
    fun largeTextCanScrollToOrderAndPaymentActionsWithoutClipping() {
        compose.runOnIdle {
            val resources = compose.activity.resources
            val configuration = android.content.res.Configuration(resources.configuration)
            configuration.fontScale = 2f
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { vm.ready }
        assertEquals(2f, compose.activity.resources.configuration.fontScale, 0.01f)
        compose.onNodeWithText("Pedido (2)").performClick()
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Cobrar $230.00"))
        compose.onNodeWithText("Cobrar $230.00").assertIsDisplayed()
        compose.runOnIdle { capturePreview(compose.activity, "operation-large-text-order.png") }
        compose.onNodeWithText("Cobrar $230.00").performClick()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Pago exacto · $230.00"))
        compose.onNodeWithText("Pago exacto · $230.00").performClick()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Cambio: $0.00"))
        compose.onNodeWithText("Cambio: $0.00").assertIsDisplayed()
        compose.onNodeWithText("Guardar sin imprimir").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { capturePreview(compose.activity, "operation-large-text-payment.png") }
    }

    private fun completeMixedPayment() {
        if (compose.onAllNodesWithText("Cobrar $230.00").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Cobrar $230.00"))
        }
        compose.onNodeWithText("Cobrar $230.00").performClick()
        compose.onNodeWithTag("payment-fields").let {
            // On shorter windows the summary belongs to the scrollable content.
            if (compose.onAllNodesWithText("Mixto").fetchSemanticsNodes().isEmpty()) {
                it.performScrollToNode(hasText("Mixto"))
            }
        }
        compose.onNodeWithText("Mixto").performClick()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Importe por transferencia"))
        compose.onNodeWithText("Importe por transferencia").performTextInput("100")
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Efectivo recibido"))
        compose.onNodeWithText("Efectivo recibido").performTextInput("200")
        compose.onNodeWithText("Guardar sin imprimir").assertIsNotEnabled()
        compose.onNodeWithTag("payment-fields").performScrollToNode(hasText("Confirmo que recibí la transferencia"))
        compose.onNodeWithText("Confirmo que recibí la transferencia").performClick()
        compose.onNodeWithText("Guardar sin imprimir").assertIsEnabled()
    }
}
