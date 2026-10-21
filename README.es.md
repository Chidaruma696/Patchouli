[English](README.md) · [Español](README.es.md)

<p align="center"><img src="docs/icon.png" width="96" alt=""></p>

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

<br/>

## 📖 Qué es

MX Player era el reproductor de cualquiera con una carpeta de episodios; luego llegaron los anuncios. Patchouli es ese reproductor, hecho de cero, para una sola cosa: reproducir los archivos del teléfono y recordar por dónde vas en cada serie.

| La biblioteca | El reproductor | El motor |
| --- | --- | --- |
| Todos los vídeos del teléfono, agrupados por carpeta. Una carpeta con episodios recibe su carátula, año y sinopsis de AniList; una con temporadas se vuelve una estantería. «Seguir viendo» arriba del todo. | Brillo a la izquierda, volumen a la derecha, arrastrar de lado para buscar, doble toque para saltar, mantener para 2×, pellizco para el zoom. Bloqueo, aspecto, pistas de subtítulos y audio, velocidad, cambio de decodificador. | libVLC: decodificación por hardware con caída a software, así que el MKV que nada abre aquí se ve. |

Nació para leer las carpetas que baja [Seija](https://github.com/Chidaruma696/Seija) (`Franquicia/01 - Serie/Serie - E01.mp4`), pero cualquier carpeta de vídeos funciona igual.

<br/>

## 📲 Descargar

Baja el APK de la [última versión](https://github.com/Chidaruma696/Patchouli/releases/latest) e instálalo. Android 8.0 o más nuevo. La app pide permiso para leer tus vídeos y nada más; lo único que sale del teléfono es el nombre de una carpeta cuando le pide la carátula a AniList (y eso se puede apagar).

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

La interfaz es un port del sistema de diseño de [Komi Store](https://github.com/komi-store/komi-store) con piel propia: una serif para los titulares, esquinas discretas, nada de patrón detrás de la página. Doce paletas con nombres de personajes de Touhou Project, cada una en claro y oscuro; la de Patchouli es la de serie.

<br/>

## 🔧 Cómo funciona

- **Biblioteca.** Una consulta al MediaStore da todos los vídeos con su ruta y duración; la app arma un árbol de carpetas a partir de las rutas. Las cadenas de carpetas con una sola hija se colapsan (`Movies/Seija` se lee «Seija»). Los números de episodio salen del nombre del archivo (`E01`, `- 03`, `03 Título`, `Título 03 [1080p]`).
- **Carátulas.** El nombre de cada carpeta, sin su prefijo `01 - ` ni guiones bajos, se busca una vez en AniList (sin clave). La respuesta se guarda en disco; los nombres comunes (Camera, Download…) nunca se buscan, y cualquier carpeta se puede marcar «no es una serie».
- **Progreso.** Posición y duración de cada archivo visto, en SharedPreferences. Pasado el 95 % cuenta como visto; por debajo aparece en «Seguir viendo» y se abre donde se quedó.
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

- [x] Biblioteca desde el MediaStore, árbol de carpetas, la estructura de Seija
- [x] Carátulas y sinopsis de AniList, con caché e interruptor «no es una serie»
- [x] Seguir viendo, reanudar, siguiente episodio
- [x] Gestos: brillo, volumen, búsqueda, doble toque, mantener para 2×, pellizco
- [x] Bloqueo, aspecto, pistas de subtítulos y audio, velocidad, decodificador HW/SW, rotación
- [ ] Picture in picture
- [ ] Subtítulos externos (`.srt` junto al archivo)
- [ ] Leer el ID de AniList que Seija dejará en cada carpeta en vez de buscar por nombre
- [ ] Buscar dentro de la biblioteca

<br/>

## ⚖️ Legal

Licencia Apache 2.0; ver [LICENSE](LICENSE). Los componentes de terceros y sus licencias están en [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). libVLC se usa como biblioteca dinámica sin modificar bajo la LGPL 2.1.

Patchouli reproduce archivos que ya están en tu dispositivo. No descarga, transmite ni comparte nada.

Touhou Project y sus personajes pertenecen a Team Shanghai Alice (ZUN); obra de fans no oficial hecha según sus directrices para obras derivadas, sin afiliación ni respaldo.

<br/>

## 📢 Keep Android Open

Google ha anunciado que desde 2027 todo desarrollador de Android tendrá que registrarse y verificar su identidad para que una app se pueda instalar en un dispositivo certificado, con o sin Play Store. Patchouli la hace una sola persona y no va a existir bajo esa regla. Lee qué está pasando en [keepandroidopen.org](https://keepandroidopen.org/es/).

<br/>

<p align="center">パチュリー</p>
