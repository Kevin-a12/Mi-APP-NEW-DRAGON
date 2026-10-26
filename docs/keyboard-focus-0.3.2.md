# Corrección del foco al abrir el teclado · 0.3.2

## Causa y solución

En 0.3.1, `Cart` cambia su distribución cuando su altura disponible baja de 440 dp: la cabecera y las acciones pasan a la `LazyColumn`. Los campos opcionales utilizaban claves implícitas basadas en la posición. Insertar la cabecera cambia esas posiciones, por lo que Compose sustituye los editores, pierde el foco y reinicia la selección. El teclado puede entonces cerrarse.

`OrderScreen.kt` asigna claves explícitas y únicas a la cabecera, servicio, domicilio, estado vacío, líneas, control de datos del cliente, nombre, contacto, notas y acciones. Las claves de líneas usan un prefijo para no colisionar con las del formulario. La estructura adaptable y el desplazamiento siguen disponibles; el editor conserva su identidad al cambiar de posición.

La corrección usa APIs de Compose comunes a todos los dispositivos admitidos (minSdk 31, Android 12). No consulta fabricante, modelo ni teclado, ni fuerza repetidamente el foco o la apertura del teclado. Conserva el manejo de insets y `adjustResize`.

## Reproducción automatizada

`OrderFocusTest` muestra el carrito de teléfono a 400 dp de ancho, con un platillo y los datos opcionales abiertos. Para cada campo (nombre, contacto, notas y domicilio):

1. Escribe `antes` y coloca el cursor en la posición 2.
2. Cambia la altura 720 → 520 → 440 → 439 → 360 dp, comprobando el foco en cada paso, sin recrear la actividad ni volver a tocar el campo.
3. Inserta `X` y comprueba `anXtes` en el borrador.
4. Amplía la altura 360 → 439 → 440 → 520 → 720 dp y comprueba el foco en cada paso.
5. Inserta `Y` y comprueba `anXYtes`, la acción de cobro visible y el control de datos opcionales.

Los cuatro casos se ejecutan en API 31, 34 y 36: 12 ejecuciones de regresión.

Antes de cambiar `OrderScreen.kt`, las 12 ejecuciones fallaron en la aserción de foco al reducir la altura: `Focused = false`; el contenido seguía siendo `antes`, pero la selección se había reiniciado a `TextRange(0, 0)`. La base de producción era el commit `b0ea1051164e421ab8ee50fcafbebd75819e5c0e` de 0.3.1. Se añadieron únicamente las pruebas y el runtime Java 21 necesario para ejecutarlas antes de reproducir el fallo.

Con las claves estables, las mismas 12 ejecuciones aprobaron, sin errores ni omisiones, conservando foco, selección y texto al reducir y ampliar la altura.

## Compilación y actualización

Versión 0.3.2, versionCode 10, paquete `mx.newdragon.pos`, minSdk 31 y targetSdk 36. Se utiliza el mismo certificado de desarrollo que 0.3.1 para permitir actualizar conservando los datos. No hay cambios en la base de datos ni en el formato de respaldo.

Comandos reproducibles (JDK 21, SDK 36 y Build Tools 35.0.0):

```bash
./gradlew :app:testDebugUnitTest --tests 'mx.newdragon.pos.OrderFocusTest'
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

La validación completa aprobó: 72 pruebas, 0 fallos, 0 errores y 0 omitidas; APK compilado; Android Lint sin errores (11 advertencias y 2 sugerencias). Los resultados y la huella SHA-256 del APK se registran en `VALIDACION.json`. `apksigner verify` confirmó el APK nuevo y la coincidencia de su certificado con el APK 0.3.1.

## Alcance de la comprobación

Robolectric y Compose prueban la pérdida de foco y la continuidad del cursor mediante cambios reales de las restricciones de altura del componente. No ejecutan un teclado de fabricante ni sustituyen una prueba en hardware. API 31, 34 y 36 representan Android 12, 14 y 16; no se han probado físicamente todas las marcas ni todas las versiones intermedias.

Comprobación en un teléfono: instalar el APK como actualización; abrir Pedidos → Pedido → Agregar nombre, contacto o notas; escribir en cada campo, mover el cursor e insertar texto; cerrar y reabrir el teclado; repetir con domicilio, letra grande y un pedido largo. El foco debe permanecer mientras el teclado cambia la altura, y Cobrar/Guardar pendiente deben seguir accesibles mediante desplazamiento.
