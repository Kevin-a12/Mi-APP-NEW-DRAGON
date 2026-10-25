package mx.newdragon.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun OrderScreen(vm: DragonViewModel, catalog: List<CatalogItem>, cash: CashSession? = null) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(vm.draft.sourceTicketId) { if (vm.draft.sourceTicketId > 0) tab = 1 }
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!vm.checkoutOpen) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Elige platillos, revisa y cobra", style = MaterialTheme.typography.bodySmall,
                color = DragonMuted, modifier = Modifier.weight(1f))
            TextButton({ vm.screen = "Reportes"; vm.reportPage = "Caja" }) {
                Text(if (cash == null) "Abrir caja" else "Caja abierta")
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth >= 700.dp && !largeText) Row(
                Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.weight(1.3f)) {
                    CatalogBrowser(catalog, vm.saving || vm.checkoutOpen) { vm.orderingItem = it; vm.editingOrderLine = null }
                }
                Box(Modifier.weight(1f)) {
                    if (vm.checkoutOpen) PaymentEditor(vm) { vm.checkoutOpen = false } else Cart(vm, catalog)
                }
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vm.checkoutOpen) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(tab == 0, { tab = 0 }, label = { Text("Platillos") })
                    FilterChip(tab == 1, { tab = 1 }, label = { Text("Pedido (${vm.draft.lines.sumOf { it.quantity }})") })
                }
                Box(Modifier.weight(1f)) {
                    if (vm.checkoutOpen) PaymentEditor(vm) { vm.checkoutOpen = false }
                    else if (tab == 0) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { CatalogBrowser(catalog, vm.saving) { vm.orderingItem = it; vm.editingOrderLine = null } }
                        PrimaryAction("Ver pedido · ${Money.show(vm.draft.total)}", vm.draft.lines.isNotEmpty()) { tab = 1 }
                    } else Cart(vm, catalog)
                }
            }
        }
    }
    vm.orderingItem?.let { item ->
        ProductOrderDialog(item, vm.editingOrderLine,
            onDismiss = { vm.orderingItem = null; vm.editingOrderLine = null },
            onAdd = { line ->
                if (vm.editingOrderLine == null) vm.add(line) else vm.editLine(line)
                vm.orderingItem = null; vm.editingOrderLine = null
            })
    }
}
@Composable fun ProductOrderDialog(item:CatalogItem,initial:OrderLine?=null,onDismiss:()->Unit,onAdd:(OrderLine)->Unit) {
    val variants=item.variants.sortedBy {it.sortOrder}
    val groups=remember(item) {optionGroups(item.product.optionsJson)}
    val baseline=remember(item,initial) {JSONObject().apply {
        val old=initial?.selections?.lines()?.filter {": " in it}?.associate {it.substringBefore(": ") to it.substringAfter(": ")} ?: emptyMap()
        groups.forEachIndexed {index,g -> repeat(g.count) {i ->
            val name=g.title+(if(g.count>1) " ${i+1}" else "")
            old[name]?.takeIf {it in g.options && it !in g.disabled}?.let {put("$index-$i",it)}
        }}
    }.toString()}
    var picks by rememberSaveable {mutableStateOf(baseline)}
    var variantId by rememberSaveable {mutableStateOf(initial?.variantId?.takeIf {id -> variants.any {it.id==id}} ?: variants.first().id)}
    var quantity by rememberSaveable {mutableIntStateOf(initial?.quantity ?: 1)}
    var notes by rememberSaveable {mutableStateOf(initial?.notes ?: "")}
    var closeConfirm by rememberSaveable {mutableStateOf(false)}
    val variant=variants.first {it.id==variantId}
    val selections=JSONObject(picks)
    val complete=groups.withIndex().all {(index,g) -> (0 until g.count).all {i -> selections.optString("$index-$i") in g.options.filterNot {it in g.disabled}}}
    val dirty=picks!=baseline || quantity!=(initial?.quantity ?: 1) || notes!=(initial?.notes ?: "") || variantId!=(initial?.variantId ?: variants.first().id)
    fun close() {if(dirty) closeConfirm=true else onDismiss()}
    AlertDialog(onDismissRequest=::close,title={Text(if(initial==null) item.product.name else "Editar ${initial.name}")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(item.product.description.ifBlank {item.product.category},style=MaterialTheme.typography.bodySmall)
            Choice("Presentación","${variant.label} · ${Money.show(variant.priceCents)}",variants.map {"${it.label} · ${Money.show(it.priceCents)}"}) {chosen -> variantId=variants.first {"${it.label} · ${Money.show(it.priceCents)}"==chosen}.id}
            groups.forEachIndexed {index,g -> repeat(g.count) {i ->
                Choice(g.title+(if(g.count>1) " ${i+1}" else ""),selections.optString("$index-$i").ifBlank {"Seleccionar"},g.options.filterNot {it in g.disabled}) {value -> picks=JSONObject(picks).put("$index-$i",value).toString()}
            }}
            if(groups.isEmpty() && !initial?.selections.isNullOrBlank()) Text(initial!!.selections,style=MaterialTheme.typography.bodySmall)
            QuantityPicker(item.product.name, quantity, onDecrease={quantity--}, onIncrease={quantity++})
            OutlinedTextField(notes,{notes=it.take(300)},label={Text("Notas del platillo")},placeholder={Text("Ej. sin cebolla")},modifier=Modifier.fillMaxWidth())
            Text("Los extras con costo se agregan como platillos.",style=MaterialTheme.typography.bodySmall)
            Text("Importe: ${Money.show(variant.priceCents*quantity)}",fontWeight=FontWeight.Bold)
            if(!complete) Text("Selecciona las opciones del platillo para continuar.",style=MaterialTheme.typography.bodySmall)
        }
    },dismissButton={TextButton(::close) {Text("Cancelar")}},confirmButton={Button({
        val options=if(groups.isEmpty()) initial?.selections ?: "" else groups.flatMapIndexed {index,g -> (0 until g.count).map {i -> g.title+(if(g.count>1) " ${i+1}" else "")+": "+selections.getString("$index-$i")}}.joinToString("\n")
        onAdd(OrderLine(key=initial?.key ?: java.util.UUID.randomUUID().toString(),productId=item.product.id,variantId=variant.id,
            name=item.product.name,category=item.product.category,description=item.product.description,variant=variant.label,
            unitCents=variant.priceCents,quantity=quantity,selections=options,notes=notes.trim()))
    },enabled=complete) {Text(if(initial==null) "Añadir al pedido" else "Guardar cambios")}})
    DiscardPrompt(closeConfirm,{closeConfirm=false},onDismiss)
}
@Composable fun Cart(vm: DragonViewModel, catalog: List<CatalogItem>) {
    var clear by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, color = Color.White) {
        BoxWithConstraints {
        // On short windows or with large text, let the whole order scroll rather than clip actions.
        val scrollActions = maxHeight < 440.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        @Composable fun cartHeader() {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (vm.draft.sourceTicketId > 0) "Editar ND-${vm.draft.sourceTicketId.toString().padStart(6, '0')}" else "Pedido actual",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)
                )
                TextButton({ clear = true }, enabled = !vm.saving && vm.draft.lines.isNotEmpty()) { Text("Vaciar") }
            }
        }
        @Composable fun cartActions() {
            HorizontalDivider(color = DragonLine)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Total MXN", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(Money.show(vm.draft.total), style = MaterialTheme.typography.headlineSmall)
            }
            PrimaryAction(if (vm.saving) "Guardando…" else "Cobrar ${Money.show(vm.draft.total)}",
                vm.draft.lines.isNotEmpty() && !vm.saving, onClick = vm::showCheckout)
            SecondaryAction("Guardar pendiente", vm.draft.lines.isNotEmpty() && !vm.saving) { pending = true }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!scrollActions) cartHeader()
            LazyColumn(Modifier.weight(1f).testTag("order-fields"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (scrollActions) item { cartHeader() }
                item { Choice("Servicio", vm.draft.service, DeliveryRules.services, !vm.saving) { vm.orderInfo(service = it) } }
                if (vm.draft.service == "A domicilio") item {
                    OutlinedTextField(vm.draft.deliveryAddress, { vm.orderInfo(deliveryAddress = it) },
                        label = { Text("Domicilio de entrega *") }, placeholder = { Text("Calle, número y colonia") },
                        enabled = !vm.saving, maxLines = 3, modifier = Modifier.fillMaxWidth())
                }
                if (vm.draft.lines.isEmpty()) item { Info("Busca un platillo y toca Añadir al pedido para comenzar.") }
                items(vm.draft.lines, key = { it.key }) { line ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(line.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(Money.show(line.total), fontWeight = FontWeight.Bold)
                        }
                        if (line.variant != "Normal") Text(line.variant, style = MaterialTheme.typography.bodySmall, color = DragonMuted)
                        if (line.selections.isNotBlank()) Text(line.selections, style = MaterialTheme.typography.bodySmall, color = DragonMuted)
                        if (line.notes.isNotBlank()) Text("Nota: ${line.notes}", style = MaterialTheme.typography.bodySmall)
                        FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically) {
                            QuantityPicker(line.name, line.quantity, !vm.saving,
                                { vm.quantity(line.key, -1) }, { vm.quantity(line.key, 1) })
                            TextButton({
                                val found = catalog.firstOrNull { it.product.id == line.productId }
                                val product = found?.product?.copy(name = line.name, description = line.description)
                                    ?: Product(line.productId, line.category, line.name, line.description)
                                val variants = (found?.variants ?: emptyList()).filterNot { it.id == line.variantId } +
                                    Variant(line.variantId, line.productId, line.variant, line.unitCents, -1)
                                vm.orderingItem = CatalogItem(product, variants); vm.editingOrderLine = line
                            }, enabled = !vm.saving, colors = ButtonDefaults.textButtonColors(contentColor = DragonInk)) { Text("Editar") }
                            TextButton({ vm.removeLine(line.key) }, enabled = !vm.saving) { Text("Eliminar") }
                        }
                        HorizontalDivider(color = DragonLine)
                    }
                }
                item {
                    OutlinedButton({ details = !details }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small, colors = ButtonDefaults.outlinedButtonColors(contentColor = DragonInk)) {
                        Text(if (details) "Ocultar datos del cliente" else "Agregar nombre, contacto o notas", Modifier.weight(1f))
                        Icon(
                            if (details) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                            null, Modifier.size(20.dp)
                        )
                    }
                    if (!details) {
                        val summary = listOf(vm.draft.reference, vm.draft.customerPhone, vm.draft.notes).filter { it.isNotBlank() }.joinToString(" · ")
                        if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall,
                            color = DragonMuted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (details) {
                    item { OutlinedTextField(vm.draft.reference, { vm.orderInfo(reference = it) },
                        label = { Text(if (vm.draft.service == "En mesa") "Mesa o nombre" else "Nombre (opcional)") },
                        singleLine = true, enabled = !vm.saving, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(vm.draft.customerPhone, { vm.orderInfo(customerPhone = it) },
                        label = { Text("Contacto (opcional)") }, singleLine = true, enabled = !vm.saving,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(vm.draft.notes, { vm.orderInfo(notes = it) },
                        label = { Text("Notas del pedido (opcional)") }, enabled = !vm.saving, maxLines = 3, modifier = Modifier.fillMaxWidth()) }
                }
                if (scrollActions) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { cartActions() }
                }
            }
            vm.lastRemoved?.let { removed ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Se quitó ${removed.first.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(vm::undoRemove) { Text("Deshacer") }
                }
            }
            if (!scrollActions) cartActions()
        }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Vaciar el pedido actual?") },
        text = { Text("Se quitarán los platillos y los datos del borrador. Si editas un pendiente, el ticket guardado se conserva.") },
        dismissButton = { TextButton({ clear = false }) { Text("Conservar") } },
        confirmButton = { TextButton({ vm.clearDraft(); clear = false }) { Text("Vaciar pedido") } })
    if (pending) AlertDialog(onDismissRequest = { pending = false }, title = { Text("Guardar sin cobrar") },
        text = { Text("El pedido quedará pendiente. No se incluirá en ingresos hasta registrar su pago. Puedes editarlo y cobrar desde Historial.") },
        dismissButton = { TextButton({ pending = false; vm.saveTicket(paid = false) }) { Text("Guardar sin imprimir") } },
        confirmButton = { Button({ pending = false; vm.saveTicket(printAfter = true, paid = false) }) { Text("Guardar e imprimir") } })
}
