# New Dragon · Android 0.3.2

Aplicación nativa para registrar pedidos, cobrar e imprimir tickets. Kotlin, Jetpack Compose y Room; Android 12 o posterior. Interfaz en español, importes en MXN y fechas del negocio en Los Mochis, Sinaloa.

## Descargar el APK de prueba

[Descargar New Dragon 0.3.2 para Android](https://github.com/Kevin-a12/Mi-APP-NEW-DRAGON/raw/refs/heads/apk-0.3.2/New-Dragon-0.3.2.apk).

Si el navegador no inicia la descarga, abre la [página del APK en GitHub](https://github.com/Kevin-a12/Mi-APP-NEW-DRAGON/blob/apk-0.3.2/New-Dragon-0.3.2.apk) y utiliza **Download raw file**. GitHub puede solicitar que inicies sesión si el repositorio es privado.

El APK se conserva en la rama de distribución `apk-0.3.2`; el proyecto editable está en `main`. La versión anterior permanece en `apk-0.3.1`.

## Corrección del teclado · 0.3.2

Nombre, contacto, notas y domicilio conservan el foco y la posición del cursor cuando cambia la altura disponible al abrir o cerrar el teclado. Los elementos del pedido ahora tienen claves estables: insertar la cabecera y las acciones en la lista de una ventana pequeña ya no recrea los campos de texto. La solución usa la identidad de elementos de Jetpack Compose y se aplica a Android 12 o posterior sin condiciones por marca o teclado.

La reproducción, las pruebas y sus límites están en [docs/keyboard-focus-0.3.2.md](docs/keyboard-focus-0.3.2.md).

## Instalar la actualización

1. Guarda un respaldo desde **Respaldos** en la aplicación actual. Esta opción se encuentra en **Ajustes → Guardar o restaurar respaldo**.
2. Abre **New-Dragon-0.3.2.apk** en el mismo dispositivo y elige **Actualizar**.
3. Entra en **Ajustes** y comprueba **Versión 0.3.2**.
4. En **Ajustes → Configurar impresora**, revisa el equipo e imprime una prueba.

Conserva la aplicación instalada: desinstalar puede borrar su información. Esta versión mantiene el paquete `mx.newdragon.pos` y el certificado de desarrollo de 0.2.4; usa versionCode 10. Las migraciones conservan menú editado, tickets, pagos, borrador, folios y registros de impresión. Si Android muestra un error al actualizar, conserva los datos y revisa ese mensaje.

## Diseño Operación ágil · 0.3.1

- Navegación lateral en pantallas amplias y navegación inferior en teléfono; se conservan Pedidos, Historial, Reportes y Ajustes.
- Catálogo compacto de dos columnas, con búsqueda, categorías y precios alineados; con letra grande se usa una columna.
- Pedido con cantidad, edición y eliminación diferenciadas. Los datos opcionales del cliente se despliegan y se pueden volver a ocultar; el domicilio sigue siendo obligatorio para entrega.
- Total y cobro accesibles en la distribución habitual. En ventanas pequeñas o con texto ampliado, el pedido completo se desplaza para evitar recortar acciones.
- Cobro centrado en total, método, recibido y cambio. La confirmación de transferencia sigue siendo manual y el ticket se guarda antes de imprimir.
- Un tema completo de colores, tipografía, bordes y diálogos. Las confirmaciones aparecen arriba sin desplazar los formularios ni tapar el botón de guardar.
- Ticket con una acción principal según su estado; impresión secundaria y anulación identificada. Reportes distingue ingresos y formas de pago de métricas secundarias.
- Ajustes, caja, edición de menú, impresora y respaldos utilizan el mismo estilo. No hay cambios de esquema de base de datos ni de formato de respaldo.

Las capturas de esta actualización están en [previews/0.3.1](previews/0.3.1). La comparación con la dirección visual seleccionada y los límites de la comprobación están en [design-qa.md](design-qa.md).

## Funciones conservadas

| Área | Comportamiento |
| --- | --- |
| Navegación | Cuatro secciones: Pedidos, Historial, Reportes y Ajustes; Ayuda explica la pantalla actual. |
| Cobro | Campo de efectivo recibido o botón Pago exacto. Un campo vacío no registra un pago. Transferencia y Mixto requieren confirmación manual del depósito. |
| Entrega | A domicilio exige dirección. Al cambiar a Para llevar o En mesa se limpia la dirección de entrega. |
| Pedido | Editar presentación, guisos, cantidad y notas. Eliminar ofrece Deshacer; restar una unidad no elimina la última. |
| Guardar | Guardar e imprimir o Guardar sin imprimir. El ticket queda guardado antes del envío. Nuevo pedido permite seguir atendiendo. |
| Pendientes | Guardar sin cobrar; abrir desde Historial con Editar y cobrar. Conserva el folio al registrar su pago. Después se puede marcar Entregado. |
| Anulaciones | Motivo obligatorio, fecha y conservación del folio, líneas e impresiones. Se separan de las ventas vigentes. |
| Historial | Búsqueda por folio, cliente, teléfono o dirección y filtros de periodo, estado, pago y servicio. |
| Impresión | Último resultado visible, confirmación manual del papel y aviso antes de reimprimir el mismo folio. |
| Menú | Categorías existentes o nuevas, quitar presentaciones manteniendo al menos una y editar grupos de guisos y su disponibilidad. |
| Formularios | Se avisa antes de descartar cambios de un platillo, menú, movimiento, corte o anulación. Los campos de los diálogos conservan su contenido al girar el dispositivo. |
| Caja | Apertura con fondo inicial, entradas y salidas, efectivo esperado, contado, diferencia y cortes históricos. |
| Respaldos | Fecha de la última copia guardada correctamente y recordatorios después de ventas o cierres. |

## Uso diario

Consulta [GUIA_RAPIDA.md](GUIA_RAPIDA.md) para los pasos de cada sección.

Los cobros son capturados manualmente. La app no recibe dinero ni consulta bancos. Las transferencias necesitan comprobación en la aplicación bancaria antes de confirmar. Si cambian los platillos o el importe transferido, debe revisarse nuevamente la confirmación.

Los pendientes no cuentan como ingresos hasta registrar su pago. Reportes asigna el ingreso al día del cobro; Historial filtra por el día de creación del pedido. Los tickets antiguos conservan sus importes y métodos de pago; si un respaldo antiguo carecía de método, se muestra Sin especificar.

Una anulación elimina el ticket de las ventas vigentes, pero no supone una devolución física. Si realmente devolviste efectivo, la casilla de anulación registra una salida en la caja abierta y evita duplicarla. Las devoluciones por transferencia se realizan y comprueban en el banco.

Abre la caja antes de cobrar si deseas incluir las ventas nuevas en el corte. Efectivo esperado = fondo inicial + efectivo aplicado a las ventas + entradas − salidas. El cambio y las transferencias no se agregan al cajón. Un corte cerrado conserva sus cifras aunque se anule después una venta; una devolución posterior se registra en la caja actualmente abierta. Las ventas realizadas sin caja siguen en Reportes, pero no se asignan retroactivamente a un corte. Los movimientos de caja no crean gastos en la aplicación independiente de gastos.

## Menú, datos y tickets

Se mantienen los 101 platillos, 152 presentaciones y 15 categorías originales. La carga inicial se realiza una sola vez. La búsqueda ignora acentos y mayúsculas y admite varias palabras. Los tickets guardan copias de nombres, guisos y precios: los cambios del menú no alteran ventas anteriores. Un pendiente retomado conserva los precios de sus líneas; cambiar una presentación utiliza su precio correspondiente.

La base local usa el esquema Room **4**, con migraciones 1→2→3→4 sin borrado destructivo. El respaldo JSON usa el formato **5**, incluye cajas y anulaciones y acepta formatos 1–5. Restaurar reemplaza la información tras mostrar un resumen y pedir confirmación; el archivo se valida completo antes de modificar la base. La numeración de folios no se reduce al restaurar una copia anterior. Un respaldo nuevo debe abrirse en 0.3.0 o posterior.

Guardar el borrador es automático. Conviene guardar copias fuera del dispositivo al terminar el día y después de editar el menú. Los permisos y ajustes de la impresora son del dispositivo y se revisan al instalar en otro equipo.

## Impresora Bluetooth

Vincula el equipo desde Android, permite Dispositivos cercanos y selecciónalo en Ajustes. Se conserva Bluetooth Classic SPP, raster ESC/POS, logo, acentos, ancho y modo Rápida optimizada. Prueba el ancho de 576 puntos para papel de 80 mm; hay alternativas de ancho y velocidades Equilibrada/Conservadora.

Datos enviados significa que concluyó el envío; no confirma que el papel salió completo. Revisa el ticket físico antes de Confirmar que salió completo o de Reimprimir. Se mantiene el mismo folio y no se repiten automáticamente trabajos parciales. Si cobras un pendiente que ya tenía intentos de impresión, se conserva la advertencia para revisar el papel antes de volver a enviarlo.

## Compilar y validar

Requisitos: JDK 21, Android SDK 36 y Build Tools 35.0.0. Usa el Gradle Wrapper incluido (8.13), crea `local.properties` con la ruta de tu SDK y ejecuta:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El código Android mantiene el destino JVM 17. Las pruebas seleccionan un runtime Java 21 mediante Gradle Toolchains porque Robolectric lo necesita para Android 16/API 36. También se puede compilar con JDK 17 si hay una instalación Java 21 detectable para ejecutar las pruebas.

Para ejecutar solo la regresión del teclado:

```bash
./gradlew :app:testDebugUnitTest --tests 'mx.newdragon.pos.OrderFocusTest'
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. La firma de desarrollo incluida permite actualizar la versión anterior. El proyecto no incluye caches, SDK ni rutas locales de la máquina de compilación.

Las pruebas cubren cálculos exactos, pagos inválidos, migraciones reales, conservación de folios y menú, edición de pendientes, anulaciones, devoluciones, cortes, restauración, búsquedas, transmisión y pantallas de teléfono y tablet. `VALIDACION.json` contiene los resultados de esta entrega. Las imágenes en `previews/` usan datos ficticios de prueba; una instalación nueva comienza sin ventas.

La regresión del foco simula Android 12, 14 y 16 (API 31, 34 y 36); el resto de las pruebas Android usa API 34. La impresora Bluetooth, su cortadora y el teclado real del dispositivo del negocio requieren comprobación física.
