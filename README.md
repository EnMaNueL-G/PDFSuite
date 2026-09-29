# 📄 OptiSuite PDF

**Lector y editor de PDF para Android — gratis, sin anuncios y sin permiso de Internet.**

Parte de la suite **[OptiSuite](https://optisuite.app)** · por Enmanuel Gil (EnMaNueL-G)

> ⚠️ **Si tienes la 1.5.0 (PDFSuite), desinstálala antes.** La 1.6.0 es una app nueva
> (`com.optisuite.pdf`) firmada con la clave de publicación de OptiSuite.

## ✨ Qué hace

- **Lector**: zoom, **búsqueda en todo el documento** (con "3 de 63" y saltos), **recuerda la página**
  en la que te quedaste, ir a una página, **modo noche real** (invierte colores), compartir e **imprimir**.
- **Editor directo** sobre la página: sustituir texto, firma dibujada a mano, imágenes, notas y
  resaltado — todo se guarda de una vez.
- **Censura real**: la página afectada se convierte en imagen con las zonas en negro; el dato oculto
  se **elimina de verdad** (no se puede copiar ni buscar). Esa página deja de tener texto seleccionable.
- **Páginas**: combinar, dividir, rotar, reordenar y eliminar.
- **Comprimir de verdad**: recomprime las imágenes grandes (p. ej. 18,9 MB → 0,8 MB en un PDF con fotos).
- **Contraseña** AES-256 y quitarla (si la conoces).
- **Formularios**: rellenar campos de texto y casillas.
- **Texto**: extraer, buscar y enviar a una app de traducción.
- **Escanear** documentos (escáner de Google Play) e **imágenes → PDF**.
- Acentos, ñ, griego y cirílico al escribir en el PDF (fuente Noto Sans incluida).

Los resultados se guardan como **archivo nuevo en Descargas/OptiSuite PDF**. Solo se sobrescribe el
original si lo eliges, y con copia de seguridad: si algo falla, el original se restaura.

### Límites (para que no haya sorpresas)

- "Editar texto" **tapa** el texto original con blanco y escribe el nuevo encima: el original sigue
  dentro del archivo. Para **eliminar** un dato usa **Censurar**.
- La firma es una **imagen de tu firma a mano**, no una firma electrónica certificada.
- Los PDF con contraseña hay que desbloquearlos primero (herramienta Contraseña).
- Word/Excel no se abren dentro de la app: "Convertir" los abre en tu app de Office para exportarlos a PDF.

## 🔒 Privacidad

- **Sin permiso de Internet**, sin anuncios, sin cuentas y **sin copia en la nube** (`allowBackup=false`).
  La app no pide ningún permiso: los PDF se abren con el selector de Android.
- El **escáner** es un servicio de Google Play (ML Kit): funciona dentro de Google Play Services, que
  puede enviar a Google estadísticas de uso anónimas según sus condiciones.
- **Traducir** envía el texto a la app que elijas (p. ej. Google Traductor), que lo procesa en sus servidores.

## 📥 Descarga

[**OptiSuite-PDF.apk**](https://github.com/EnMaNueL-G/PDFSuite/releases/latest/download/OptiSuite-PDF.apk)
(Android 8+).

## 🛠️ Tecnología

Kotlin · Jetpack Compose · Material 3 · [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)
(Apache-2.0) · `PdfRenderer` de Android · ML Kit Document Scanner.

Compilar: `gradlew.bat :app:assembleRelease` (la firma se lee de `keystore.properties`, que no está en el repositorio).
Autotest del motor (solo depuración): ver `app/src/main/java/com/enmanuelgil/pdfsuite/data/SelfTest.kt`.

## ❤️ Apoya el proyecto

- **Binance Pay ID:** `1165745950`
- **USDT (BSC · BEP-20):** `0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08`

## 📄 Licencia

Código propio bajo **MIT** (ver [LICENSE](LICENSE)). Librerías de terceros: PdfBox-Android, AndroidX,
Jetpack Compose, Coil y Kotlin Coroutines (Apache-2.0); fuente Noto Sans (SIL OFL 1.1,
`app/src/main/assets/fonts/OFL.txt`); Google ML Kit (condiciones de Google APIs).

— Web: **https://optisuite.app** · Soporte: **support@optisuite.app**
