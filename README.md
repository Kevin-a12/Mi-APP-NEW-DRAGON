# New Dragon 0.3.2 · APK de prueba

[Descargar APK para Android](https://github.com/Kevin-a12/Mi-APP-NEW-DRAGON/raw/refs/heads/apk-0.3.2/New-Dragon-0.3.2.apk)

Si no inicia la descarga, abre New-Dragon-0.3.2.apk en esta rama y pulsa **Download raw file**.

Corrige el cierre del teclado por pérdida de foco al escribir nombre, contacto, notas o domicilio cuando cambia la altura disponible. La solución conserva la identidad de los campos y se aplica a Android 12 o posterior sin condiciones por marca ni teclado.

## Instalar la actualización

1. Guarda un respaldo desde Ajustes en la aplicación instalada.
2. Descarga el APK en el teléfono o tablet con Android 12 o posterior.
3. Abre el archivo y elige Actualizar. Conserva la app instalada para mantener sus datos.
4. Revisa en Ajustes la versión 0.3.2 y prueba los campos con el teclado abierto.

Paquete: `mx.newdragon.pos`. Versión: `0.3.2`. versionCode: `10`. APK de desarrollo firmado con el mismo certificado que 0.3.1, verificado con apksigner. La versión anterior permanece en la rama `apk-0.3.1`.

72 pruebas automatizadas aprobadas; Android Lint sin errores (11 advertencias y 2 sugerencias). Incluye 12 ejecuciones de regresión de foco y cursor en Android 12, 14 y 16 (API 31, 34 y 36): las 12 fallaban antes y aprueban con la corrección. Se simulan cambios de altura con Robolectric; la comprobación de teclados reales, dispositivos e impresora sigue requiriendo hardware.

Código exacto: [7fb9a05](https://github.com/Kevin-a12/Mi-APP-NEW-DRAGON/commit/7fb9a056f5d00b2f7776f98acf70141853a33f72). El proyecto editable está en [main](https://github.com/Kevin-a12/Mi-APP-NEW-DRAGON/tree/main); la causa, reproducción y comandos están en [el informe de la corrección](https://github.com/Kevin-a12/Mi-APP-NEW-DRAGON/blob/7fb9a056f5d00b2f7776f98acf70141853a33f72/docs/keyboard-focus-0.3.2.md).

`VALIDACION.json` registra los resultados, la versión y el certificado; `SHA256SUMS` permite comprobar la integridad del APK.
