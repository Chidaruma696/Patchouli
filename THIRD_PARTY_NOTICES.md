# Third-party notices

Patchouli uses the following third-party software. Each keeps its own license and copyright.

## Player

| Component | Use in Patchouli | License |
| --- | --- | --- |
| [libVLC](https://code.videolan.org/videolan/vlc-android) (VideoLAN), `org.videolan.android:libvlc-all` | Playback engine, used unmodified | LGPL 2.1 |

Under the LGPL 2.1, libVLC is used as a dynamic library without modifications. Its source code is available at the link above and anyone can replace the library with a compatible version.

## Catalogue

| Component | Use in Patchouli | License / terms |
| --- | --- | --- |
| [AniList](https://anilist.co) GraphQL API | Covers, titles and summaries for the folders, looked up by name | [AniList terms](https://anilist.co/terms) |

## Art

| Component | Use in Patchouli | License |
| --- | --- | --- |
| ["Chibi Patchouli Knownledge (Touhou)"](https://www.deviantart.com/fantastiic/art/Chibi-Patchouli-Knownledge-Touhou-305044472) by Fantastiic | The mascot in the README and in the app's empty screens (`docs/patchouli.png`, `app/src/main/res/drawable-nodpi/patchouli.png`); the artist's watermark in the corner was cropped out | [CC BY-NC 3.0](https://creativecommons.org/licenses/by-nc/3.0/) |

This image is **not** under the Apache license of the rest of the project: it may be shared and adapted with credit to the artist, for non-commercial purposes only.

## Design

| Component | Use in Patchouli | License |
| --- | --- | --- |
| [Komi Store](https://github.com/kurikomi-labs/komi-store) (kurikomi-labs) | The manga look of the interface: the drawing code of the kit (`app/src/main/kotlin/com/patchouli/app/ui/kit/`, hard shadows, stamps, tone sheets, metrics and typography) is ported from its design system | Apache 2.0 |
| [Anton](https://fonts.google.com/specimen/Anton) (Vernon Adams) | Headlines and stamps | SIL Open Font License 1.1 |
| [Noto Sans](https://fonts.google.com/noto) (Google) | Body text and labels | SIL Open Font License 1.1 |
| [JetBrains Mono](https://www.jetbrains.com/lp/mono/) (JetBrains) | Times and monospaced text | SIL Open Font License 1.1 |
| [Material Design Icons](https://fonts.google.com/icons) (Google) | Icons | Apache 2.0 |

The colour palettes are Patchouli's own. Their names are characters of Touhou Project, property of Team Shanghai Alice; there is no affiliation or endorsement.

## Libraries

| Component | License |
| --- | --- |
| [OkHttp](https://github.com/square/okhttp) | Apache 2.0 |
| [Coil](https://github.com/coil-kt/coil) (with `coil-video` for the thumbnails) | Apache 2.0 |
| [Jetpack Compose and AndroidX](https://developer.android.com/jetpack) | Apache 2.0 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Apache 2.0 |

## License texts

- Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>
- Creative Commons Attribution-NonCommercial 3.0: <https://creativecommons.org/licenses/by-nc/3.0/legalcode>
- GNU Lesser General Public License 2.1: <https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html>
- SIL Open Font License 1.1: <https://openfontlicense.org/open-font-license-official-text/>
