# PDF Reader

Lector de PDF para Android. Sin anuncios, sin trackers, sin permisos innecesarios.

## Características v1.0

- **Lector** — Visualización rápida con zoom y navegación por páginas
- **Herramientas** — Interfaz lista para v1.1: combinar, dividir, comprimir, rotar
- **Recientes** — Historial local de archivos abiertos
- **Favoritos** — Marca tus PDFs más importantes
- **Modo oscuro** — Diseño oscuro para lectura cómoda
- **Apertura directa** — Abre PDFs desde el explorador de archivos

## Descarga

[Releases →](https://github.com/EnMaNueL-G/PDFReader/releases)

## Tecnología

- Kotlin + Jetpack Compose + Material Design 3
- `PdfRenderer` nativo de Android (sin dependencias externas para visualización)
- DataStore para persistencia de recientes y favoritos
- Navigation Compose para navegación entre pantallas

## Permisos

- `READ_EXTERNAL_STORAGE` (Android ≤12) — para leer PDFs del almacenamiento
- `READ_MEDIA_IMAGES` (Android 13+) — mínimo requerido

## Licencia

MIT — código abierto, auditable, gratuito.
