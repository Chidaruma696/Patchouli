# Decisiones

Notas de por qué Patchouli es como es. Lo más nuevo arriba.

## 2026-10-21 · Nace: un reproductor local con la biblioteca de Seija

Quería algo como MX Player para ver lo que baja Seija, sin anuncios y con los gestos de siempre (brillo a la izquierda, volumen a la derecha, buscar de lado). Dos piezas:

- **Motor: libVLC**, la misma dependencia que Hatate (`org.videolan.android:libvlc-all`). Decodifica por hardware con caída a software y trae subtítulos y pistas de audio hechos. Descartado mpv: no hay artefacto en Maven y había que compilarlo. Descartado Media3/ExoPlayer: sin FFmpeg se queda corto con MKV raros.
- **Biblioteca: el MediaStore, no un recorrido de carpetas.** Android ya tiene indexados los vídeos; una consulta da todos con su ruta y su duración, y la app los agrupa como un árbol. Las cadenas de carpetas con una sola hija se colapsan (`Movies/Seija` se lee «Seija»), así que la carpeta de Seija (`{franquicia}/{NN - serie}/{serie} - E01.mp4`) aparece tal cual: franquicia → series numeradas → episodios.
- **Carátulas: AniList**, con el nombre de la carpeta limpio (`01 - Fate_stay night` → `Fate stay night`). Una búsqueda por carpeta, cacheada en disco; los nombres que tiene cualquier teléfono (Camera, Download…) nunca se preguntan, y cualquier carpeta se puede marcar «no es una serie».
- **Progreso por archivo** en SharedPreferences: «seguir viendo» en la biblioteca, seguir donde se quedó y pasar al siguiente episodio al terminar.
- **Gestos en un solo detector** (`PlayerGestures.kt`) en vez de combinar `detectTapGestures` + `detectDragGestures` + `transformable`, porque se pelean entre sí: toque, doble toque por tercios, mantener para 2×, arrastre vertical por mitad, horizontal para buscar y pellizco para zoom.

**La mascota es CC BY-NC.** No hay dibujos de Patchouli con licencia libre del todo (en Commons solo hay fotos de cosplay); la chibi de Fantastiic en DeviantArt es CC BY-NC 3.0, que basta para una app que no se vende. Va con su crédito en README, avisos y Acerca de, separada de la Apache del código.

**Buscar carátulas en la web es un navegador, no una API.** El endpoint interno de imágenes de DuckDuckGo (`i.js`) responde con un desafío anti-bot a cualquier cliente que no sea su web, así que se abre la búsqueda en un WebView y un script captura el toque sobre la imagen; la miniatura del proxy de DuckDuckGo lleva la URL original en `?u=`, que es la que se baja.

Pendiente: picture in picture, que Seija deje un `seija.json` con el ID de AniList en cada carpeta para no buscar por nombre, y subtítulos externos (.srt al lado del vídeo; libVLC los carga con `addSlave`).
