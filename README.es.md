[English](README.md) · [Español](README.es.md)

<p align="center"><img src="docs/patchouli.png" width="180" alt="Patchouli Knowledge, chibi, con un libro"></p>

<h1 align="center">Patchouli <sub>パチュリー</sub></h1>

<p align="center">Un reproductor de vídeo para el anime que ya tienes en el teléfono.<br>Tus carpetas son la biblioteca; los gestos, los de siempre.</p>

<p align="center">
<a href="https://github.com/Chidaruma696/Patchouli/releases/latest"><img src="https://img.shields.io/badge/📲_DESCARGAR_APK-7C4FB0?style=for-the-badge" alt="Descargar APK"></a>
</p>

<p align="center">
<img src="https://img.shields.io/badge/Android-8.0+-3DDC84?logo=android&logoColor=white" alt="">
<img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" alt="">
<img src="https://img.shields.io/badge/motor-libVLC-F58220?logo=vlcmediaplayer&logoColor=white" alt="">
<img src="https://img.shields.io/badge/licencia-Apache_2.0-blue" alt="">
</p>

<p align="center">
<img src="docs/library.png" width="240" alt="La biblioteca: carátulas sobre papel crema"> &nbsp; <img src="docs/settings.png" width="240" alt="Ajustes: paletas y reproducción">
</p>
<p align="center"><img src="docs/player.png" width="500" alt="El reproductor: tiras de papel sobre el vídeo, sellos por botones"></p>

<br/>

## 📖 Qué es

MX Player era el reproductor de cualquiera con una carpeta de episodios; luego llegaron los anuncios. Patchouli es ese reproductor, hecho de cero, para una sola cosa: reproducir los archivos del teléfono y recordar por dónde vas en cada serie.

| La biblioteca | El reproductor | El motor |
| --- | --- | --- |
| Todos los vídeos del teléfono, agrupados por carpeta, en lista o en cuadrícula. Una carpeta con episodios recibe su carátula, año, sinopsis y nombres de episodio de los catálogos; una con temporadas se vuelve una estantería. Un play flotante reanuda el último vídeo. Visto a mano, ocultar lo visto, ordenar, borrar lo ya visto. | Brillo a la izquierda, volumen a la derecha, arrastrar de lado para buscar con vista previa, doble toque para saltar, mantener para 2×, pellizco para el zoom. Saltar el opening y el ending. Pistas recordadas por serie, retrasos, temporizador, volumen extra, capítulos, fotogramas, la tele por DLNA. | libVLC: decodificación por hardware con caída a software, así que el MKV que nada abre aquí se ve. Botones de auriculares y Bluetooth, audio focus, ajuste del refresco, nivelar el volumen. |

Una carpeta organizada como `Franquicia/01 - Serie/Serie - E01.mp4` se lee como un árbol (la franquicia, luego sus series en orden, luego los episodios), pero cualquier carpeta de vídeos funciona igual.

<br/>

## 📲 Descargar

Baja el APK de la [última versión](https://github.com/Chidaruma696/Patchouli/releases/latest) e instálalo. Android 8.0 o más nuevo. La app pide permiso para leer tus vídeos y nada más. Lo que sale del teléfono: el nombre de una carpeta, para buscar su carátula (AniList, si no Jikan, si no Kitsu); un id de serie y un número de episodio, para los nombres de los episodios (Jikan) y para saber dónde están el opening y el ending (AniSkip); todo eso se apaga con un interruptor en Ajustes. Tu lista de AniList solo se escribe si conectas una cuenta, y solo con los episodios que terminas.

<br/>

## 🖐 Gestos

| Gesto | Qué hace |
| --- | --- |
| Toque | Mostrar u ocultar los controles |
| Doble toque a la izquierda / derecha | Saltar atrás / adelante (10 s por defecto, hasta 60) |
| Doble toque en el centro | Reproducir / pausa |
| Arrastrar arriba o abajo en la mitad izquierda | Brillo |
| Arrastrar arriba o abajo en la mitad derecha | Volumen |
| Arrastrar de lado | Buscar, con el tiempo de destino a la vista mientras arrastras |
| Mantener | Reproducir a 2× hasta soltar |
| Pellizco | Zoom |

<br/>

## 🎨 Estética

La interfaz está dibujada como una página de manga: papel crema con cuadrícula, paneles cuadrados con una línea de tinta de 3 dp y una sombra dura que se hunde al pulsar, tramas de puntos en las esquinas, mayúsculas en Anton para los titulares, sellos por botones. El dibujo viene del sistema de diseño de [Komi Store](https://github.com/kurikomi-labs/komi-store) (Apache 2.0), traído al kit de Patchouli. Papel de día y papel de noche; diez paletas con nombres de personajes de Touhou Project ponen el color de acento, la de Patchouli de serie.

<br/>

## 🔧 Cómo funciona

- **Biblioteca.** Una consulta al MediaStore da todos los vídeos con su ruta y duración; la app arma un árbol de carpetas a partir de las rutas. Las cadenas de carpetas con una sola hija se colapsan (`Movies/Anime` se lee «Anime»). Los números de episodio salen del nombre del archivo (`E01`, `- 03`, `03 Título`, `Título 03 [1080p]`).
- **Carátulas.** El nombre de cada carpeta, sin su prefijo `01 - ` ni guiones bajos, se busca una vez en AniList (sin clave). La respuesta se guarda en disco; los nombres comunes (Camera, Download…) nunca se buscan, y cualquier carpeta se puede marcar «no es una serie».
- **Progreso.** Posición y duración de cada archivo visto, en SharedPreferences. Pasado el 95 % cuenta como visto; por debajo, el play flotante lo reanuda donde se quedó. Todo lo recordado se puede guardar en un JSON y restaurar en otro teléfono.
- **Openings y endings.** A AniSkip se le pregunta una vez por episodio, por id de MyAnimeList y número, y contesta para el corte del episodio cuya duración coincide con el archivo; los capítulos propios del archivo llamados «OP» o «ED» hacen de respaldo cuando no tiene nada.
- **Reproductor.** `PlayerActivity` tiene a libVLC; `PlayerScreen` es Compose sobre un `VLCVideoLayout`. Todos los gestos viven en un solo detector de entrada para que nunca se peleen.

<br/>

## 🛠 Compilar

```
git clone https://github.com/Chidaruma696/Patchouli
cd Patchouli
./gradlew :app:assembleDebug
```

JDK 17+, SDK de Android 36. La firma de release lee `keystore.*` de `local.properties` (nunca se sube) y cae a la clave de debug.

<br/>

## 🗺 Hoja de ruta

- [x] Biblioteca desde el MediaStore, árbol de carpetas, la estructura por franquicias
- [x] Carátulas y sinopsis de AniList, con caché e interruptor «no es una serie»
- [x] Seguir viendo, reanudar, siguiente episodio
- [x] Gestos: brillo, volumen, búsqueda, doble toque, mantener para 2×, pellizco
- [x] Bloqueo, aspecto, pistas de subtítulos y audio, velocidad, decodificador HW/SW, rotación
- [x] Imagen en imagen: sales del reproductor y sigue en una ventana flotante
- [x] Subtítulos externos: los `.srt`/`.ass`/`.vtt` junto al archivo se cargan solos, o cargas uno de donde sea
- [x] Buscar dentro de la biblioteca
- [x] Mandar a la tele por DLNA: el teléfono sirve el archivo, la tele lo reproduce donde ibas, el teléfono queda de control con barra de avance
- [x] Biblioteca en lista o en cuadrícula de 2, 3 o 4, también dentro de las carpetas; un play flotante que reanuda el último vídeo
- [x] Ordenar una carpeta: numera las temporadas, nombra los episodios, con vista previa
- [x] El aspecto de manga, a la manera de Komi Store
- [x] Leer un ID de AniList dejado en la carpeta (`anilist.json`, con `anilist` y/o `mal`) en vez de buscar por nombre
- [x] Nombres de episodio, visto a mano, ordenar y ocultar lo visto, pistas recordadas por serie, ficha de siguiente episodio
- [x] Saltar openings y endings: AniSkip, o los capítulos del archivo
- [x] Temporizador, retraso de subtítulos y audio, tamaño y estilo de subtítulos, volumen extra, capítulos, fotogramas, vista previa al buscar
- [x] Botones de auriculares y Bluetooth, audio focus, ajuste del refresco, nivelar el volumen
- [x] Copia de seguridad y restaurar, borrar episodios vistos, atajo «seguir viendo» en el icono
- [x] Progreso a AniList (necesita registrar el id de cliente de la app)
- [ ] Un NAS como fuente: navegar una carpeta compartida por SMB y reproducir desde ahí, con las mismas estanterías y marcas que en el teléfono
- [ ] Chromecast (el Cast SDK de Google; fuera por ahora para no depender de los servicios de Play)

<br/>

## ⚖️ Legal

Licencia Apache 2.0; ver [LICENSE](LICENSE). Los componentes de terceros y sus licencias están en [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). libVLC se usa como biblioteca dinámica sin modificar bajo la LGPL 2.1.

La Patchouli chibi de arriba y de la app es ["Chibi Patchouli Knownledge (Touhou)"](https://www.deviantart.com/fantastiic/art/Chibi-Patchouli-Knownledge-Touhou-305044472) de **Fantastiic**, [CC BY-NC 3.0](https://creativecommons.org/licenses/by-nc/3.0/), con la marca de agua del artista recortada. No la cubre la licencia Apache: dale crédito al artista y déjala fuera de cualquier uso comercial.

Patchouli reproduce archivos que ya están en tu dispositivo. No descarga, transmite ni comparte nada.

Touhou Project y sus personajes pertenecen a Team Shanghai Alice (ZUN); obra de fans no oficial hecha según sus directrices para obras derivadas, sin afiliación ni respaldo.

<br/>

## 📢 Keep Android Open

Google ha anunciado que desde 2027 todo desarrollador de Android tendrá que registrarse y verificar su identidad para que una app se pueda instalar en un dispositivo certificado, con o sin Play Store. Patchouli la hace una sola persona y no va a existir bajo esa regla. Lee qué está pasando en [keepandroidopen.org](https://keepandroidopen.org/es/).

<br/>

<p align="center">パチュリー</p>
