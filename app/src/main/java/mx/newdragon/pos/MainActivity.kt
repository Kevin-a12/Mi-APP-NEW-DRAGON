package mx.newdragon.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NewDragonInterface() }
    }
}

private val destinations = listOf("Pedidos", "Historial", "Reportes", "Ajustes")
private val destinationIcons = listOf(
    Icons.Default.ShoppingCart, Icons.AutoMirrored.Filled.List,
    Icons.Default.DateRange, Icons.Default.Settings
)

@Composable fun NewDragonInterface(vm: DragonViewModel = viewModel()) {
    DragonTheme {
        val catalog by vm.catalog.collectAsStateWithLifecycle()
        val tickets by vm.tickets.collectAsStateWithLifecycle()
        val attempts by vm.printAttempts.collectAsStateWithLifecycle()
        val sessions by vm.cashSessions.collectAsStateWithLifecycle()
        val movements by vm.cashMovements.collectAsStateWithLifecycle()
        var help by rememberSaveable { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.message) {
            vm.message?.let { message ->
                try { snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long) }
                finally { if (vm.message == message) vm.message = null }
            }
        }
        BackHandler(enabled = vm.screen != "Pedidos" || vm.checkoutOpen) {
            when {
                vm.checkoutOpen -> vm.checkoutOpen = false
                vm.screen == "Historial" && vm.selectedTicket != null -> vm.closeTicket()
                vm.screen == "Ajustes" && vm.settingsPage != "Inicio" -> vm.settingsPage = "Inicio"
                else -> vm.navigate("Pedidos")
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val focusedPayment = vm.screen == "Pedidos" && vm.checkoutOpen
            val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Scaffold(
                containerColor = DragonCream,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!wide && !focusedPayment && !keyboardVisible) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                        destinations.forEachIndexed { index, page ->
                            NavigationBarItem(
                                selected = vm.screen == page, onClick = { vm.navigate(page) }, enabled = !vm.restoring,
                                icon = { Icon(destinationIcons[index], null) }, label = { Text(page) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DragonRed, selectedTextColor = DragonRed,
                                    indicatorColor = DragonRose, unselectedIconColor = DragonMuted,
                                    unselectedTextColor = DragonMuted
                                )
                            )
                        }
                    }
                }
            ) { insets ->
                Row(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                    if (wide) {
                        NavigationRail(
                            modifier = Modifier.width(112.dp).fillMaxHeight().testTag("main-navigation-rail")
                                .verticalScroll(rememberScrollState()),
                            containerColor = Color.White,
                            header = {
                                Image(painterResource(R.drawable.nd_logo), "New Dragon", Modifier.padding(8.dp).size(88.dp))
                                Spacer(Modifier.height(16.dp))
                            }
                        ) {
                            destinations.forEachIndexed { index, page ->
                                NavigationRailItem(
                                    selected = vm.screen == page, onClick = { vm.navigate(page) }, enabled = !vm.restoring,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    icon = { Icon(destinationIcons[index], null) }, label = { Text(page) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = DragonRed, selectedTextColor = DragonRed,
                                        indicatorColor = DragonRose, unselectedIconColor = DragonMuted,
                                        unselectedTextColor = DragonMuted
                                    )
                                )
                            }
                        }
                        VerticalDivider(color = DragonLine)
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (wide) 20.dp else 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (wide || !focusedPayment) Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!wide) {
                                Image(painterResource(R.drawable.nd_logo), "New Dragon", Modifier.size(40.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (wide) when (vm.screen) {
                                    "Pedidos" -> "Pedidos y cobro"
                                    "Historial" -> "Tus pedidos"
                                    "Reportes" -> "Resumen del negocio"
                                    else -> "Configuración del negocio"
                                } else "NEW DRAGON",
                                style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton({ help = true }, colors = ButtonDefaults.textButtonColors(contentColor = DragonInk)) {
                                Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Ayuda")
                            }
                        }
                        if (vm.startupError != null) Info(vm.startupError!!, true)
                        else if (!vm.ready || vm.restoring) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text(if (vm.restoring) "Restaurando respaldo…" else "Abriendo tus datos…")
                        }
                        else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            val maxContent = when (vm.screen) {
                                "Ajustes" -> 800.dp
                                "Reportes" -> 1040.dp
                                "Historial" -> 1000.dp
                                else -> 1600.dp
                            }
                            Box(Modifier.widthIn(max = maxContent).fillMaxSize()) {
                                when (vm.screen) {
                                    "Pedidos" -> OrderScreen(vm, catalog, sessions.firstOrNull { it.closedAt == 0L })
                                    "Historial" -> TicketsScreen(vm, tickets, attempts, sessions)
                                    "Reportes" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(vm.reportPage == "Ventas", { vm.reportPage = "Ventas" }, label = { Text("Ventas") })
                                            FilterChip(vm.reportPage == "Caja", { vm.reportPage = "Caja" }, label = { Text("Caja y cortes") })
                                        }
                                        Box(Modifier.weight(1f)) {
                                            if (vm.reportPage == "Caja") CashScreen(vm, sessions, tickets, movements)
                                            else ReportsScreen(vm, tickets)
                                        }
                                    }
                                    "Ajustes" -> SettingsScreen(vm, catalog, tickets, sessions)
                                }
                            }
                        }
                    }
                }
            }
            // Keep transient notices away from payment actions and do not move the form.
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 560.dp))
        }
        if (help) HelpDialog(vm) { help = false }
        vm.resumeCandidate?.let { detail ->
            AlertDialog(onDismissRequest = vm::dismissResume, title = { Text("Hay un pedido en curso") },
                text = { Text("Abrir ${detail.ticket.folio} reemplazará el borrador actual. Puedes conservarlo y guardarlo primero.") },
                dismissButton = { TextButton(vm::dismissResume) { Text("Conservar pedido actual") } },
                confirmButton = { TextButton({ vm.resume(detail) }) { Text("Reemplazar borrador") } })
        }
    }
}
