[English](README.md) · [Español](README.es.md)

<p align="center"><img src="docs/patchouli.png" width="180" alt="Patchouli Knowledge, chibi, holding a book"></p>

<h1 align="center">Patchouli <sub>パチュリー</sub></h1>

<p align="center">A video player for the anime you already have on your phone.<br>Your folders become the library, the gestures are the ones you know.</p>

<p align="center">
<a href="https://github.com/Chidaruma696/Patchouli/releases/latest"><img src="https://img.shields.io/badge/📲_DOWNLOAD_APK-7C4FB0?style=for-the-badge" alt="Download APK"></a>
</p>

<p align="center">
<img src="https://img.shields.io/badge/Android-8.0+-3DDC84?logo=android&logoColor=white" alt="">
<img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" alt="">
<img src="https://img.shields.io/badge/engine-libVLC-F58220?logo=vlcmediaplayer&logoColor=white" alt="">
<img src="https://img.shields.io/badge/license-Apache_2.0-blue" alt="">
</p>

<br/>

## 📖 What it is

MX Player used to be the player for everybody with a folder of episodes; then the ads moved in. Patchouli is that player, made from scratch, for one job: play the files on your phone and keep track of where you are in each series.

| The library | The player | The engine |
| --- | --- | --- |
| Every video on the phone, grouped by folder. A folder with episodes gets its cover, year and synopsis from AniList; a folder with seasons becomes a shelf. "Continue watching" at the top. | Brightness on the left, volume on the right, drag sideways to seek, double tap to skip, hold for 2×, pinch to zoom. Lock, aspect ratio, subtitle and audio tracks, playback speed, decoder switch. | libVLC: hardware decoding with a software fallback, so the MKV that nothing else opens plays here. |

It was made to read the folders [Seija](https://github.com/Chidaruma696/Seija) downloads (`Franchise/01 - Series/Series - E01.mp4`), but any folder of videos works the same.

<br/>

## 📲 Download

Grab the APK from the [latest release](https://github.com/Chidaruma696/Patchouli/releases/latest) and install it. Android 8.0 or newer. The app asks for permission to read your videos and nothing else; the only thing that leaves the phone is the name of a folder when it asks AniList for a cover (and that can be turned off).

<br/>

## 🖐 Gestures

| Gesture | What it does |
| --- | --- |
| Tap | Show or hide the controls |
| Double tap on the left / right | Skip back / forward (10 s by default, up to 60) |
| Double tap in the middle | Play / pause |
| Drag up or down on the left half | Brightness |
| Drag up or down on the right half | Volume |
| Drag sideways | Seek, with the target time shown while you drag |
| Hold | Play at 2× until you let go |
| Pinch | Zoom |

<br/>

## 🎨 Look

The interface is a port of [Komi Store](https://github.com/komi-store/komi-store)'s design system with its own skin: a serif for the headings, modest corners, no pattern behind the page. Twelve palettes named after Touhou Project characters, each in light and dark; Patchouli's is the default.

<br/>

## 🔧 How it works

- **Library.** One query to the MediaStore gives every video with its path and duration; the app builds a tree of folders from the paths. Chains of folders with a single child collapse (`Movies/Seija` reads as "Seija"). Episode numbers come from the file names (`E01`, `- 03`, `03 Title`, `Title 03 [1080p]`).
- **Covers.** Each folder name, cleaned of its `01 - ` prefix and underscores, is searched once on AniList (no key needed). The answer is cached on disk; common folder names (Camera, Download…) are never looked up, and any folder can be marked "not a series".
- **Progress.** Position and length of every file watched, in SharedPreferences. A file is "watched" past 95 %; below that it shows in "Continue watching" and opens where it was left.
- **Player.** `PlayerActivity` holds libVLC; `PlayerScreen` is Compose over a `VLCVideoLayout`. All gestures live in one pointer-input detector so they never fight each other.

<br/>

## 🛠 Build

```
git clone https://github.com/Chidaruma696/Patchouli
cd Patchouli
./gradlew :app:assembleDebug
```

JDK 17+, Android SDK 36. Release signing reads `keystore.*` from `local.properties` (never committed) and falls back to the debug key.

<br/>

## 🗺 Roadmap

- [x] Library from the MediaStore, folder tree, Seija's layout
- [x] Covers and synopses from AniList, with a cache and a "not a series" switch
- [x] Continue watching, resume, next episode
- [x] Gestures: brightness, volume, seek, double tap, hold for 2×, pinch
- [x] Lock, aspect ratio, subtitle and audio tracks, speed, HW/SW decoder, rotation
- [ ] Picture in picture
- [ ] External subtitles (`.srt` next to the file)
- [ ] Read the AniList id Seija will leave in each folder instead of searching by name
- [ ] Search inside the library

<br/>

## ⚖️ Legal

Apache License 2.0; see [LICENSE](LICENSE). Third-party components and their licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). libVLC is used as an unmodified dynamic library under the LGPL 2.1.

The chibi Patchouli above and in the app is ["Chibi Patchouli Knownledge (Touhou)"](https://www.deviantart.com/fantastiic/art/Chibi-Patchouli-Knownledge-Touhou-305044472) by **Fantastiic**, [CC BY-NC 3.0](https://creativecommons.org/licenses/by-nc/3.0/), with the artist's watermark cropped out. It is not covered by the Apache license: credit the artist and keep it out of anything commercial.

Patchouli plays files that are already on your device. It does not download, stream or share anything.

Touhou Project and its characters belong to Team Shanghai Alice (ZUN); unofficial fan work made under their guidelines for derivative works, with no affiliation or endorsement.

<br/>

## 📢 Keep Android Open

Google has announced that from 2027 every Android developer will have to register and verify their identity before an app can be installed on a certified device, Play Store or not. Patchouli is made by one person and will not exist under that rule. Read what is going on at [keepandroidopen.org](https://keepandroidopen.org/).

<br/>

<p align="center">パチュリー</p>
