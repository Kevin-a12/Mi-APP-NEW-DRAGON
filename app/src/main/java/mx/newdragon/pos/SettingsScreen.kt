package mx.newdragon.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun SettingsScreen(vm:DragonViewModel,catalog:List<CatalogItem>,tickets:List<Ticket>,sessions:List<CashSession>) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(vm.settingsPage!="Inicio") TextButton({vm.settingsPage="Inicio"}) {Text("Volver a Ajustes")}
        Box(Modifier.weight(1f)) {
            when(vm.settingsPage) {
                "Mi menú" -> MenuScreen(vm,catalog)
                "Impresora" -> PrinterScreen(vm)
                "Respaldos" -> BackupsScreen(vm)
                else -> Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Ajustes","Configuración del negocio y cuidado de tus datos.")
                    listOf("Editar menú" to "Mi menú","Configurar impresora" to "Impresora","Guardar o restaurar respaldo" to "Respaldos").forEach {(label,page) ->
                        Card(onClick={vm.settingsPage=page},colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(label,style=MaterialTheme.typography.titleMedium)
                                Text(when(page) {"Mi menú"->"Precios, presentaciones, categorías y guisos disponibles.";"Impresora"->"Selecciona tu equipo y comprueba cómo sale el ticket.";else->"Crea una copia completa o recupera la información."},style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null,tint=DragonMuted)
                            }
                        }
                    }
                    Text("New Dragon · Versión ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall,color=DragonMuted)
                    val changed=maxOf(tickets.maxOfOrNull {maxOf(it.createdAt,it.paidAt,it.voidedAt,it.deliveredAt)} ?: 0,sessions.maxOfOrNull {it.closedAt} ?: 0)
                    Info(if(vm.lastBackupAt==0L) "Todavía no has guardado un respaldo en este equipo." else "Último respaldo: ${RestaurantTime.date(vm.lastBackupAt)}")
                    if(changed>vm.lastBackupAt) Info("Hay datos posteriores a tu última copia. Guarda un respaldo al terminar el día.",true)
                }
            }
        }
    }
}
@Composable fun HelpDialog(vm:DragonViewModel,dismiss:()->Unit) {
    val title=if(vm.screen=="Ajustes" && vm.settingsPage!="Inicio") vm.settingsPage else vm.screen
    val items=when(title) {
        "Pedidos" -> listOf("Añadir platillos" to "Busca un nombre o elige una categoría. Selecciona presentación, guisos y cantidad.","Corregir" to "Editar cambia guisos, notas o presentación. Eliminar permite Deshacer.","Cobrar" to "Indica el efectivo o toca Pago exacto. Confirma manualmente las transferencias recibidas. Guarda con o sin impresión.","Pendientes" to "Guardar pendiente conserva el pedido sin contarlo como ingreso. Ábrelo desde Historial para editar y cobrar.")
        "Historial" -> listOf("Buscar" to "Escribe folio, nombre, teléfono o dirección; combina los filtros de fecha de creación, estado, pago y servicio.","Imprimir" to "Datos enviados indica que terminó el envío. Revisa el papel y confirma que salió completo.","Anular" to "Conserva folio, motivo y registro de impresión. Si devolviste efectivo, puedes registrarlo en la caja abierta.","Entregar" to "Un pedido con pago registrado puede marcarse como Entregado.")
        "Reportes" -> listOf("Ventas" to "Solo suma pedidos con pago registrado y vigentes. Los pendientes y anulados se muestran por separado. Los pagos pendientes cuentan en la fecha en que se cobran.","Abrir caja" to "Escribe el fondo inicial antes de cobrar. Las ventas nuevas se asignan a esa caja.","Entradas y salidas" to "Registra dinero agregado, retiros y devoluciones. Una transferencia no aumenta el efectivo del cajón.","Cerrar caja" to "Cuenta el efectivo físico, revisa la diferencia y guarda el corte. Los cortes cerrados se conservan.")
        "Mi menú" -> listOf("Editar menú" to "Los cambios aplican a nuevas selecciones. Los tickets guardados conservan sus precios.","Presentaciones" to "Puedes agregar o quitar tamaños. Debe quedar al menos uno y sus nombres deben ser diferentes.","Guisos y opciones" to "Escribe una opción por línea y desmarca lo que no esté disponible. Puedes agregar grupos y elegir cuántas opciones necesita el cliente.")
        "Impresora" -> listOf("Vincular" to "Vincula la impresora en Android, permite dispositivos cercanos y selecciónala aquí.","Prueba" to "Imprimir prueba no registra una venta. Comprueba ancho, acentos, papel y corte.","Reimprimir" to "Antes de repetir un envío, revisa el papel para evitar copias duplicadas o parciales.")
        "Respaldos" -> listOf("Guardar copia" to "Incluye menú, tickets, anulaciones, pedido pendiente, pagos y cajas. Guarda el JSON fuera de la tableta.","Recuperar" to "Cargar permite revisar el archivo antes de confirmar. Restaurar reemplaza los datos actuales; no los mezcla.")
        else -> listOf("Editar menú" to "Administra lo que vendes y sus precios.","Impresora" to "Configura Bluetooth y realiza una prueba.","Respaldos" to "Guarda copias frecuentes fuera de la tableta.")
    }
    AlertDialog(onDismissRequest=dismiss,title={Text("Ayuda · $title")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items.forEach {(name,detail) -> Column {Text(name,fontWeight=FontWeight.Bold);Text(detail,style=MaterialTheme.typography.bodyMedium)}}
            Info("Funciona sin internet. Importes en pesos mexicanos. Fechas de Los Mochis. Las transferencias se confirman manualmente.")
        }
    },confirmButton={TextButton(dismiss) {Text("Entendido")}})
}
