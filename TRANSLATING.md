# Translating Orbit

Orbit speaks English and Spanish. This is where each kind of text lives, how to add a language, and the words and style the app uses in each one.

## Where the text lives

| What | Where |
| --- | --- |
| The app's screens, menus, messages and what TalkBack reads | `app/src/main/res/values/strings_<area>.xml`, one file per part of the app, in English; each language has the same files in `values-<language>/` |
| Words every part of the app uses (Cancel, Undo, Rename…) | `strings_common.xml` |
| The launcher, the shortcuts, the share sheet, the tile and the widget | `strings.xml` |
| The map's own controls and what screen readers hear on a map | the Orbit library's languages (see Orbit's `docs/languages.md`); the app tells each map its language |
| What the map's page and the pages in browsers and on TVs say themselves: kinds of ideas, a goal ring's words, presenting in browsers, the page that asks for a name, a map shared as a web page | `strings_pages.xml`, which the app hands to the pages (`web/words.ts`); `pages_language` is the language's code, the pages' `lang` |
| What a TV says before a phone casts a presentation to it: its waiting screen and its status | `TV_WORDS` in `web/words.ts`, since a TV can't read the app's strings |
| The welcome map, the templates and the samples | `app/src/main/assets/<language>/`, the same files as in `app/src/main/assets/`, which a language without them falls back to |
| The privacy policy shown in the app | `app/src/main/assets/<language>/privacy.md` |
| The store listings | `fastlane/metadata/android/<locale>/` |

The English files are the source: a string is added or changed there first, then in every language.

## Adding a language

1. Copy each `app/src/main/res/values/strings*.xml` into `values-<language>/` and translate it. Android lint reports any string a language is missing (`MissingTranslation`), so `./gradlew lintLibreDebug` lists what's left.
2. Add the language to `localeFilters` in `app/build.gradle.kts`. The app's language setting and Android's per-app language list come from the languages in `res/`.
3. Translate the maps under `app/src/main/assets/` into `app/src/main/assets/<language>/`. Keep the markdown's structure, comments and attributes as they are (`<!-- orbit status: done -->`, `- [x]`, front matter keys): only the words people read change.
4. If the Orbit library doesn't have the language yet, add it there (`registerLanguage`), so the map's controls match the rest of the app.
5. Add the TV's words to `TV_WORDS` in `web/words.ts`, and rebuild the pages and the Cast receiver (see CONTRIBUTING.md, "The map's pages" and "Casting to TVs").
6. Add the store listings in `fastlane/metadata/android/<locale>/`: the texts, and the screenshots and feature graphic in the language (phones at 1080 × 1920, tablets at 2560 × 1440, the feature graphic at 1024 × 500), framed under captions in it.

## How strings are written

- **Placeholders:** `%1$s` for text and `%1$d` for numbers, numbered whenever a string has more than one, so a translation can put them in any order.
- **Counts:** anything with a number of things is a `<plurals>`, never "%d maps" in a `<string>`. Give each language the quantities it needs: English uses `one` and `other`, Spanish `one`, `many` and `other` (`many` reads like `other`).
- **Whole sentences:** never build a sentence from pieces, because word order changes between languages. Give each case its own string.
- **Apostrophes:** write `\'` for a straight apostrophe in XML, or use the typographic ’.
- **Not translated:** Orbit's name, markdown syntax, the words in map attributes (`status: done`, `theme: night`), file formats and anything a computer reads.

## Spanish

Neutral Latin American Spanish, the way people across Latin America and the US write to each other on their phones.

- **You:** *tú*, never *usted* or *vos*.
- **Tone:** friendly and short, like the English. Prefer a verb to a noun: *Cambiar nombre*, not *Cambio de nombre*.
- **Capitals:** sentence case, as in English: *Historial de versiones*.
- **Punctuation:** open questions and exclamations with *¿* and *¡*, and quote with “ ”, like the English.
- **Gender:** prefer wording that needs no gender (*quien presenta*, *las personas que ven la presentación*) when it reads naturally.
- **Words with two spellings:** *video* (not *vídeo*), *computadora* (not *ordenador*), *celular* only where *teléfono* doesn't fit.

### Words

| English | Spanish |
| --- | --- |
| map | mapa (*el mapa*) |
| idea | idea |
| note | nota |
| planet, moon, sun | planeta, luna, sol |
| orbit (the ring) | órbita |
| focus (the view) | enfoque |
| overview | vista general |
| outline | esquema |
| template | plantilla |
| goal | meta |
| progress | avance |
| goal (target, amount, currency) | meta (objetivo, monto, moneda) |
| reached (a goal) | alcanzada |
| done (a task) | hecho, hecha |
| status | estado |
| statuses: To do, In progress, Done, Blocked, No status | Por hacer, En curso, Hecho, Bloqueado, Sin estado |
| kinds: Milestone, Question, Decision, Risk, Source, Person | Hito, Pregunta, Decisión, Riesgo, Fuente, Persona |
| tag, priority, kind | etiqueta, prioridad, tipo |
| to-do | tarea |
| pin, pinned | fijar, fijado |
| trash | papelera |
| restore | restaurar |
| add | agregar |
| undo, redo | deshacer, rehacer |
| delete | eliminar |
| remove | quitar |
| rename | cambiar nombre |
| duplicate | duplicar |
| share | compartir |
| send | enviar |
| export | exportar |
| bring in (import) | importar |
| file, folder | archivo, carpeta |
| zip | archivo zip |
| sync | sincronizar, sincronización |
| version history | historial de versiones |
| present, presenting | presentar, presentación |
| presentation path | recorrido (de la presentación) |
| rehearse, rehearsal | ensayar, ensayo |
| captions (presenting) | subtítulos |
| pace, on time, up next | ritmo, a tiempo, a continuación |
| presenter view | vista de quien presenta |
| audience | público |
| guest (in a presentation) | participante |
| room (a presentation's) | sala |
| join | unirse |
| second screen | segunda pantalla |
| device | dispositivo |
| phone | teléfono |
| tablet | tablet |
| computer | computadora |
| laptop, hotspot | laptop, hotspot |
| offline | sin conexión |
| app store | tienda de apps |
| email | correo |
| storage | almacenamiento |
| browser | navegador |
| web page | página web |
| link | enlace |
| address | dirección |
| code (to join) | código |
| check code | código de verificación |
| PIN, PINs | PIN |
| picture | imagen |
| caption (of a picture or video) | leyenda |
| icon | ícono |
| video | video |
| catch (an idea, quickly) | capturar |
| talk ideas onto a map | dictar ideas |
| speech service | servicio de voz, reconocimiento de voz |
| nearby | cerca, cercanos |
| widget | widget |
| home screen | pantalla de inicio |
| settings | configuración |
| appearance, like the phone | apariencia, como el teléfono |
| wallpaper | fondo de pantalla |
| haptics | respuesta háptica |
| about, privacy | acerca de, privacidad |
| source code, free software | código fuente, software libre |
| crash, crash report | fallo, informe del fallo |
| style, layout | estilo, diseño |
| colors | colores |
| keyboard shortcuts | combinaciones de teclas |
| light, dark | claro, oscuro |
| search | buscar |
| OK, Got it, Dismiss | Aceptar, Entendido, Descartar |
| save, create | guardar, crear |
| keep (both) | conservar |
| in step (synced) | sincronizado |
| try again | volver a intentarlo |
| decline | rechazar |
| unpin | dejar de fijar |
| empty trash, for good | vaciar papelera, para siempre |
| fold away, folded | contraer, contraída |
| fly to | volar a |
| long-press, hold | mantener presionado |
| just now, yesterday | hace un momento, ayer |
| read-only | solo lectura |

### Styles and colors

| English | Spanish |
| --- | --- |
| Paper | Papel |
| Night | Noche |
| Glass, Glass night | Cristal, Cristal nocturno |
| Cosmos | Cosmos |
| Blueprint | Cianotipo |
| Ink | Tinta |
| Contrast | Contraste |
| Flat, Flat night | Plano, Plano nocturno |
| Material, Material night | Material, Material nocturno |
| Liquid glass, Liquid glass night | Cristal líquido, Cristal líquido nocturno |
| The style's own (colors) | Los del estilo |
| Mint, Sky, Lilac, Apricot, Pink, Teal, Amber | Menta, Cielo, Lila, Durazno, Rosa, Turquesa, Ámbar |
| Sorbet, Ocean, Forest, Sunset, Vivid, Mono | Sorbete, Océano, Bosque, Atardecer, Vivos, Monocromo |
| App colors, High contrast | Colores de la app, Alto contraste |

### Keys

| English | Spanish |
| --- | --- |
| Shift, Enter, Space, Backspace | Mayús, Enter, Espacio, Retroceso |
| Delete, Insert, Menu | Supr, Insert, Menú |
| Page Up, Page Down, Home, End | Re Pág, Av Pág, Inicio, Fin |

### Agreement

- Counts of finished things agree with how many are finished: *1 de 3 hecha*, *2 de 3 hechas*.
- Statuses read as their own words (*Hecho*, *Bloqueado*); words about an idea agree with *idea* (*contraída*).
