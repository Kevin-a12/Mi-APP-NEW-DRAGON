package mx.newdragon.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable fun PaymentEditor(vm: DragonViewModel, onClose: () -> Unit) {
    val draft = vm.draft.payment
    val total = vm.draft.total
    val result = runCatching { draft.resolve(total) }
    val breakdown = result.getOrNull()
    val transfer = when (draft.method) {
        PaymentMethod.TRANSFER -> total
        PaymentMethod.MIXED -> runCatching { Payments.parse(draft.transferInput) }.getOrDefault(0)
        else -> 0
    }
    val cashDue = (total - transfer).coerceAtLeast(0)
    val hasInput = draft.cashReceivedInput.isNotBlank() || draft.transferInput.isNotBlank() || draft.exactCash || draft.transferConfirmed
    Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, color = Color.White) {
        BoxWithConstraints {
        val scrollSummary = maxHeight < 560.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Cobrar pedido", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClose, enabled = !vm.saving, colors = ButtonDefaults.textButtonColors(contentColor = DragonInk)) { Text("Volver") }
            }
            if (!scrollSummary) PaymentSummary(vm)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("payment-fields"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (scrollSummary) PaymentSummary(vm)
                if (draft.method == PaymentMethod.MIXED) OutlinedTextField(
                    draft.transferInput, { vm.paymentAmounts(transfer = it) }, label = { Text("Importe por transferencia") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = !vm.saving, modifier = Modifier.fillMaxWidth()
                )
                if (transfer > 0) {
                    Text("Transferencia: ${Money.show(transfer)}", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = draft.transferConfirmed, enabled = !vm.saving, role = Role.Checkbox, onValueChange = vm::confirmTransfer
                        ), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(draft.transferConfirmed, null, enabled = !vm.saving)
                        Text("Confirmo que recibí la transferencia", Modifier.weight(1f))
                    }
                    Text("Compruébala en tu banco. La app no verifica depósitos.", style = MaterialTheme.typography.bodySmall, color = DragonMuted)
                    HorizontalDivider(color = DragonLine)
                }
                if (draft.method != PaymentMethod.TRANSFER) {
                    if (draft.method == PaymentMethod.MIXED) Text("Por pagar en efectivo: ${Money.show(cashDue)}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        draft.cashReceivedInput, { vm.paymentAmounts(cash = it) }, label = { Text("Efectivo recibido") },
                        placeholder = { Text("Ej. 500") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, enabled = !vm.saving && !draft.exactCash, modifier = Modifier.fillMaxWidth()
                    )
                    FilterChip(draft.exactCash, vm::exactPayment, enabled = !vm.saving, label = { Text("Pago exacto · ${Money.show(cashDue)}") })
                    if (draft.exactCash) Text("El cliente entregó exactamente ${Money.show(cashDue)}.", style = MaterialTheme.typography.bodySmall)
                    if (breakdown != null) {
                        Text("Recibido: ${Money.show(breakdown.cashReceivedCents)}", color = DragonMuted)
                        Surface(color = DragonGreenLight, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                            Text("Cambio: ${Money.show(breakdown.changeCents)}", Modifier.padding(14.dp),
                                style = MaterialTheme.typography.headlineSmall, color = DragonGreen)
                        }
                    }
                }
                if (result.isFailure && hasInput) Info(result.exceptionOrNull()?.message ?: "Revisa los importes.", true)
                else if (result.isFailure && draft.method != PaymentMethod.TRANSFER)
                    Text(if (draft.method == PaymentMethod.MIXED) "Completa los importes y confirma la transferencia para continuar."
                        else "Escribe el efectivo recibido o elige Pago exacto.", style = MaterialTheme.typography.bodySmall, color = DragonMuted)
                Text("El ticket se guarda antes de imprimir. Si falla el envío, puedes reimprimirlo desde Historial.",
                    style = MaterialTheme.typography.bodySmall, color = DragonMuted)
            }
            PrimaryAction(if (vm.saving) "Guardando…" else "Guardar e imprimir", result.isSuccess && !vm.saving) { vm.saveTicket(printAfter = true) }
            SecondaryAction("Guardar sin imprimir", result.isSuccess && !vm.saving) { vm.saveTicket() }
        }
    }
    }
}


@Composable private fun PaymentSummary(vm: DragonViewModel) {
    Text("Total: ${Money.show(vm.draft.total)}", style = MaterialTheme.typography.headlineMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaymentMethod.selectable.forEach { method ->
            FilterChip(vm.draft.payment.method == method, { vm.paymentMethod(method) },
                enabled = !vm.saving, label = { Text(method.label) })
        }
    }
    HorizontalDivider(color = DragonLine)
}
