package mx.newdragon.pos

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 34, 36], application = DragonApplication::class, qualifiers = "w400dp-h900dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrderFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val vm get() = ViewModelProvider(compose.activity)[DragonViewModel::class.java]
    private var height by mutableStateOf(720.dp)

    @Before fun showPhoneOrder() {
        compose.waitUntil(30_000) { vm.ready }
        resetInterfaceFixture(vm)
        compose.runOnIdle {
            vm.clearDraft()
            vm.add(OrderLine(productId = "sample", variantId = "normal", name = "Arroz",
                category = "Combos", description = "", variant = "Normal", unitCents = 11000, quantity = 1))
        }
        compose.setContent {
            DragonTheme {
                Box(Modifier.requiredSize(width = 400.dp, height = height)) { Cart(vm, emptyList()) }
            }
        }
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Agregar nombre, contacto o notas"))
        compose.onNodeWithText("Agregar nombre, contacto o notas").performClick()
    }

    @Test fun nameKeepsFocusAndCursorWhenHeightChanges() = checkEditingAcrossResize("Nombre (opcional)") { it.reference }

    @Test fun contactKeepsFocusAndCursorWhenHeightChanges() = checkEditingAcrossResize("Contacto (opcional)") { it.customerPhone }

    @Test fun notesKeepFocusAndCursorWhenHeightChanges() = checkEditingAcrossResize("Notas del pedido (opcional)") { it.notes }

    @Test fun deliveryAddressKeepsFocusAndCursorWhenHeightChanges() {
        compose.runOnIdle { vm.orderInfo(service = "A domicilio") }
        checkEditingAcrossResize("Domicilio de entrega *") { it.deliveryAddress }
    }

    private fun checkEditingAcrossResize(label: String, value: (OrderDraft) -> String) {
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText(label))
        val field = compose.onNodeWithText(label)
        field.performClick().performTextInput("antes")
        field.performTextInputSelection(TextRange(2))
        field.assertIsFocused()

        // Simulate IME opening/closing and its intermediate animation frames. Cross
        // Cart's 440dp breakpoint without recreating the activity or tapping again.
        for (nextHeight in listOf(520, 440, 439, 360)) {
            compose.runOnIdle { height = nextHeight.dp }
            field.assertIsFocused()
        }
        field.performTextInput("X")
        compose.runOnIdle { assertEquals("anXtes", value(vm.draft)) }
        for (nextHeight in listOf(439, 440, 520, 720)) {
            compose.runOnIdle { height = nextHeight.dp }
            field.assertIsFocused()
        }
        field.performTextInput("Y")
        compose.runOnIdle { assertEquals("anXYtes", value(vm.draft)) }
        compose.onNodeWithText("Cobrar $110.00").assertIsDisplayed()
        compose.onNodeWithTag("order-fields").performScrollToNode(hasText("Ocultar datos del cliente"))
        compose.onNodeWithText("Ocultar datos del cliente").performClick()
        compose.onNodeWithText("Agregar nombre, contacto o notas").assertExists()
    }
}
