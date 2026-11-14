# Translating Orbit

Orbit speaks English, Spanish and Brazilian Portuguese. This is where each kind of text lives, how to add a language, and the words and style the app uses in each one.

## Where the text lives

| What | Where |
| --- | --- |
| The app's screens, menus, messages and what TalkBack reads | `app/src/main/res/values/strings_<area>.xml`, one file per part of the app, in English; each language has the same files in `values-<language>/` |
| Words every part of the app uses (Cancel, Undo, Rename…) | `strings_common.xml` |
| The launcher, the shortcuts, the share sheet, the tile and the widget | `strings.xml` |
| The map's own controls and what screen readers hear on a map | the Orbit library's languages (see Orbit's `docs/languages.md`); the app tells each map its language |
| What the map's page and the pages in browsers and on TVs say themselves: kinds of ideas, a goal ring's words, presenting in browsers, the page that asks for a name, a map shared as a web page | `strings_pages.xml`, which the app hands to the pages (`web/words.ts`); `pages_language` is the language's code, the pages' `lang` |
| What a TV says before a phone casts a presentation to it: its waiting screen and its status | `TV_WORDS` in `web/words.ts`, since a TV can't read the app's strings |
| The welcome map and the templates | `app/src/main/assets/<language>/`, the same files as in `app/src/main/assets/`, which a language without them falls back to. The welcome map's picture, video and sound are the same in every language, in `app/src/main/assets/welcome/`: only their captions and descriptions, in `welcome.md`, are translated |
| The privacy policy shown in the app | `app/src/main/assets/<language>/privacy.md` |
| The store listings | `fastlane/metadata/android/<locale>/` |

The English files are the source: a string is added or changed there first, then in every language.

## Adding a language

1. Copy each `app/src/main/res/values/strings*.xml` into `values-<language>/` and translate it. Android lint reports any string a language is missing (`MissingTranslation`), so `./gradlew lintLibreDebug` lists what's left.
2. The build takes the language from its folder in `res/` by itself: the app's language setting and Android's per-app language list then offer it.
3. Translate the maps under `app/src/main/assets/` into `app/src/main/assets/<language>/`. Keep the markdown's structure, comments and attributes as they are (`<!-- orbit status: done -->`, `- [x]`, front matter keys): only the words people read change. The welcome map's `welcome.json` lists the ideas its presentation goes through, by their ids: its center and each planet, as Orbit names them from their words (lowercase, without accents or emoji, dashes between the words), so it changes with them. A link between ideas, like `(#study)`, takes the new id too. `web/welcome.test.ts` checks that every language's welcome map is the English one in its own words.
4. If the Orbit library doesn't have the language yet, add it there (`registerLanguage`), so the map's controls match the rest of the app.
5. Add the TV's words to `TV_WORDS` in `web/words.ts`, and rebuild the pages and the Cast receiver (see CONTRIBUTING.md, "The map's pages" and "Casting to TVs").
6. Add the language's CLDR locales to `LANGUAGES` in `scripts/emoji-names.ts`, and run `bun scripts/emoji-names.ts`: the icon search then finds emoji by their names in it (`app/src/main/assets/emoji/<language>.txt`).
7. Add the store listings in `fastlane/metadata/android/<locale>/`: the texts, and the screenshots and feature graphic in the language (phones at 1080 × 1920, tablets at 2560 × 1440, the feature graphic at 1024 × 500), framed under captions in it.

`LanguagesTest` checks that each language in `res/` has the rest: its maps, the TV's words, the emoji names and a store listing. The tests pick each language's plurals by Unicode's rules, as Android does.

## How strings are written

- **Placeholders:** `%1$s` for text and `%1$d` for numbers, numbered whenever a string has more than one, so a translation can put them in any order.
- **Counts:** anything with a number of things is a `<plurals>`, never "%d maps" in a `<string>`. Give each language the quantities it needs: English uses `one` and `other`, Spanish and Portuguese `one`, `many` and `other` (`many` reads like `other`). In Brazilian Portuguese `one` is for 0 as well as 1 (see Brazilian Portuguese, below).
- **Whole sentences:** never build a sentence from pieces, because word order changes between languages. Give each case its own string.
- **Apostrophes:** write `\'` for a straight apostrophe in XML, or use the typographic ’.
- **Not translated:** Orbit's name, markdown syntax, the words in map attributes (`status: done`, `theme: night`), file formats and anything a computer reads.
- **This device:** a string about the device it's shown on says "this device" (*este dispositivo*), since that may be a tablet or a Chromebook. "Your phone" stays where it describes how people typically use Orbit, as in the privacy policy and the store listings.

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
| an idea's files, attach (a file to an idea) | los archivos de una idea, adjuntar |
| spreadsheet, presentation, archive (kinds of file) | hoja de cálculo, presentación, archivo comprimido |
| zip | archivo zip |
| sync | sincronizar, sincronización |
| version history | historial de versiones |
| present, presenting | presentar, presentación |
| presentation path | recorrido (de la presentación) |
| rehearse, rehearsal | ensayar, ensayo |
| study (a map), studying | estudiar, al estudiar |
| card (in study mode) | tarjeta |
| live photo, Live (its mark) | foto en vivo, En vivo |
| Again, Knew it (a card's answers) | Otra vez, Lo sabía |
| all caught up, to study (due) | al día, por estudiar |
| date (an idea's), time | fecha, hora |
| reminder, Remind me | recordatorio, Recordarme |
| overdue (an idea whose date is past) | vencida |
| captions (presenting) | subtítulos |
| pace, on time, up next | ritmo, a tiempo, a continuación |
| presenter view | vista de quien presenta |
| audience | público |
| guest (in a presentation) | participante |
| room (a presentation's) | sala |
| join | unirse |
| second screen | segunda pantalla |
| device, this device | dispositivo, este dispositivo |
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
| caption (of a picture, video or sound) | leyenda |
| icon | ícono |
| video | video |
| sound (an idea's: a voice memo, a file or a web address) | audio (*el audio*) |
| voice memo | nota de voz |
| sketch (on a planet), to sketch | dibujo, dibujar |
| pen, eraser, stroke (in a sketch) | lápiz, borrador, trazo |
| record, recording, take | grabar, grabación, toma |
| play, pause, turn the sound off/on | reproducir, pausar, silenciar/activar el sonido |
| catch (an idea, quickly) | capturar |
| talk ideas onto a map | dictar ideas |
| speech service | servicio de voz, reconocimiento de voz |
| nearby | cerca, cercanos |
| widget | widget |
| home screen | pantalla de inicio |
| shortcut (on the home screen or the launcher) | acceso directo |
| clear (a search, a field) | borrar |
| settings | configuración |
| appearance, system (light or dark, and the language, as the device has them) | apariencia, sistema |
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
| private map, make private, Private (the switch) | mapa privado, hacer privado, Privado |
| lock, unlock, locked | bloquear, desbloquear, bloqueado |
| screen lock | bloqueo de pantalla |
| fingerprint, face | huella, rostro |
| Recents (the overview of open apps) | Recientes |

### Styles and colors

| English | Spanish |
| --- | --- |
| Paper | Papel |
| Night | Noche |
| Glass, Glass night | Cristal, Cristal nocturno |
| Cosmos | Cosmos |
| Blueprint | Cianotipo |
| Ink | Tinta |
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

## Brazilian Portuguese

Brazilian Portuguese (`pt-BR`), the way people across Brazil write to each other on their phones, spelled as the 2009 spelling agreement has it (*ideia*, *voo*, *frequente*).

- **You:** *você*, never *tu* or *o senhor*. Everything agrees with it: *seu mapa*, *suas ideias*, never *teu*.
- **Tone:** friendly and short, like the English. Buttons and menu items are verbs in the infinitive (*Excluir*, *Renomear*, *Adicionar uma ideia*); instructions speak to the person (*Toque em +*, *Libere espaço*). Prefer a verb to a noun: *Renomear*, not *Renomeação*.
- **Capitals:** sentence case, as in English: *Histórico de versões*.
- **Punctuation:** quote with “ ”, like the English.
- **Gender:** prefer wording that needs no gender (*quem apresenta*, *as pessoas que assistem*) when it reads naturally.
- **Brazil's words, not Portugal's:** *celular* (not *telemóvel*), *tela* (not *ecrã*), *arquivo* (not *ficheiro*), *baixar* (not *descarregar*), *excluir* (not *eliminar*), *o app*.
- **Words Brazil uses as they are:** *app*, *link*, *widget*, *tablet*, *backup*, *layout*, *emoji*, *e-mail*, *PIN*, *hotspot*, *notebook* (a laptop).
- **Orbit** takes *o* where a sentence needs an article, as Brazilians say *o WhatsApp*: *O Orbit salva seus mapas*.
- **Counts:** Unicode's plural rules for Brazilian Portuguese give 0 the `one` form, as they do 1, so look at where a count comes from before writing its forms. Where it can't be 0, `one` is the singular (*1 ideia*, *%1$d ideias*). Where it can, every form says it the same way, as a label that reads right for any number (*Ideias: %1$d*, *Navegadores: %d*), so a list doesn't change its wording between one row and the next. `many` reads like `other`.
- **The number in `one`:** since `one` is for 0 too, lint asks for the number in every `one` form (`ImpliedQuantity`): *%1$d mapa foi para a lixeira*. Only a form whose count is never 0 and where a number would read oddly, such as a button's words, leaves it out, with `tools:ignore="ImpliedQuantity"` and a comment saying why the count can't be 0.

### Words

| English | Portuguese |
| --- | --- |
| map | mapa (*o mapa*) |
| idea | ideia |
| note | nota |
| planet, moon, sun | planeta, lua, sol |
| orbit (the ring) | órbita |
| focus (the view) | foco |
| overview | visão geral |
| outline | tópicos (the map as a list: *Tópicos*); *estrutura de tópicos* where *tópicos* alone could read as subjects |
| template | modelo |
| goal | meta |
| progress | progresso |
| goal (target, amount, currency) | meta (objetivo, valor, moeda) |
| reached (a goal) | alcançada |
| done (a task) | concluído, concluída |
| status | status |
| statuses: To do, In progress, Done, Blocked, No status | A fazer, Em andamento, Concluído, Bloqueado, Sem status |
| kinds: Milestone, Question, Decision, Risk, Source, Person | Marco, Pergunta, Decisão, Risco, Fonte, Pessoa |
| tag, priority, kind | etiqueta, prioridade, tipo |
| to-do | tarefa |
| pin, pinned | fixar, fixado |
| unpin | desafixar |
| trash | lixeira |
| restore | restaurar |
| add | adicionar |
| undo, redo | desfazer, refazer |
| delete | excluir |
| remove | remover |
| rename | renomear |
| duplicate | duplicar |
| share | compartilhar |
| send | enviar |
| export | exportar |
| bring in (import) | importar |
| file, folder | arquivo, pasta |
| an idea's files, attach (a file to an idea) | os arquivos de uma ideia, anexar |
| spreadsheet, presentation, archive (kinds of file) | planilha, apresentação, arquivo compactado |
| zip | arquivo zip |
| sync | sincronizar, sincronização |
| version history | histórico de versões |
| backup, back up | backup, fazer backup |
| present, presenting | apresentar, apresentação |
| presentation path | roteiro (da apresentação) |
| rehearse, rehearsal | ensaiar, ensaio |
| study (a map), studying | estudar, ao estudar |
| card (in study mode) | cartão |
| live photo, Live (its mark) | foto ao vivo, Ao vivo |
| Again, Knew it (a card's answers) | De novo, Eu sabia |
| all caught up, to study (due) | em dia, para estudar |
| captions (presenting) | legendas |
| pace, on pace, up next | ritmo, no ritmo, a seguir |
| presenter view | visão de quem apresenta |
| audience | público |
| guest (in a presentation) | participante |
| room (a presentation's) | sala |
| join | entrar |
| second screen | segunda tela |
| device, this device | dispositivo, este dispositivo |
| phone | celular |
| tablet | tablet |
| computer | computador |
| laptop, hotspot | notebook, hotspot |
| offline | sem conexão |
| app store | loja de apps |
| email | e-mail |
| storage | armazenamento |
| browser | navegador |
| web page | página da web |
| link | link |
| address | endereço |
| code (to join) | código |
| check code | código de verificação |
| PIN, PINs | PIN, PINs |
| picture | imagem |
| date (an idea's), time | data, horário |
| reminder, Remind me | lembrete, Lembrar |
| overdue (an idea whose date is past) | atrasada |
| caption (of a picture, video or sound) | legenda |
| icon | ícone |
| video | vídeo |
| sound (an idea's: a voice memo, a file or a web address) | áudio (*o áudio*) |
| voice memo | nota de voz |
| sketch (on a planet), to sketch | desenho, desenhar |
| pen, eraser, stroke (in a sketch) | caneta, borracha, traço |
| record, recording | gravar, gravação |
| play, pause, turn the sound off/on | reproduzir, pausar, desativar/ativar o som |
| catch (an idea, quickly) | capturar |
| talk ideas onto a map | ditar ideias |
| speech service | serviço de voz, reconhecimento de voz |
| nearby | por perto, próximos |
| widget | widget |
| home screen | tela inicial |
| shortcut (on the home screen or the launcher) | atalho |
| clear (a search, a field) | limpar |
| settings | configurações |
| appearance, system (light or dark, and the language, as the device has them) | aparência, sistema |
| wallpaper | plano de fundo |
| haptics | retorno tátil |
| about, privacy | sobre, privacidade |
| source code, free software | código-fonte, software livre |
| crash, crash report | falha, relatório de falha |
| style, layout | estilo, layout |
| colors | cores |
| keyboard shortcuts | atalhos de teclado |
| light, dark | claro, escuro |
| search | pesquisar |
| OK, Got it, Dismiss | OK, Entendi, Dispensar |
| Done (a button that closes) | Concluído |
| save, create | salvar, criar |
| keep (both) | manter |
| keep (maps, in a folder or on this device) | guardar |
| as it is (a file shared without cleaning) | como está |
| drop (from another app), drag | soltar, arrastar |
| branch (an idea and the ideas under it) | ramo |
| changes (a map's) | alterações |
| web address | endereço da web |
| make smaller (a video), made smaller | reduzir, reduzido |
| in step (synced) | sincronizado |
| try again | tentar de novo |
| decline | recusar |
| empty trash, for good | esvaziar a lixeira, para sempre |
| fold away, folded | recolher, recolhida |
| fly to | voar até |
| tap, double-tap | tocar, tocar duas vezes |
| long-press, hold | tocar e manter pressionado, manter pressionado |
| drag, swipe | arrastar, deslizar |
| just now, yesterday | agora mesmo, ontem |
| read-only | somente leitura |
| private map, make private, Private (the switch) | mapa privado, tornar privado, Privado |
| lock, unlock, locked | bloquear, desbloquear, bloqueado |
| screen lock | bloqueio de tela |
| fingerprint, face | impressão digital, rosto |
| Recents (the overview of open apps) | Recentes |

### Styles and colors

| English | Portuguese |
| --- | --- |
| Paper | Papel |
| Night | Noite |
| Glass, Glass night | Vidro, Vidro noturno |
| Cosmos | Cosmos |
| Blueprint | Cianótipo |
| Ink | Tinta |
| Flat, Flat night | Plano, Plano noturno |
| Material, Material night | Material, Material noturno |
| Liquid glass, Liquid glass night | Vidro líquido, Vidro líquido noturno |
| The style's own (colors) | As do estilo |
| Mint, Sky, Lilac, Apricot, Pink, Teal, Amber | Menta, Céu, Lilás, Damasco, Rosa, Turquesa, Âmbar |
| Sorbet, Ocean, Forest, Sunset, Vivid, Mono | Sorvete, Oceano, Floresta, Pôr do sol, Vivas, Mono |
| App colors, High contrast | Cores do app, Alto contraste |

### Keys

Brazilian keyboards print most keys' names in English, so they stay as they're printed.

| English | Portuguese |
| --- | --- |
| Shift, Enter, Space, Backspace | Shift, Enter, Espaço, Backspace |
| Delete, Insert, Menu | Delete, Insert, Menu |
| Page Up, Page Down, Home, End | Page Up, Page Down, Home, End |

### Agreement

- Counts of finished things can be 0, so they're labels, in the feminine of *tarefa* and *meta*: *Concluídas: 1 de 3*, *Alcançadas: 0 de 2*.
- Statuses read as their own words (*Concluído*, *Bloqueado*); words about an idea agree with *ideia* (*recolhida*), and words about a map with *mapa* (*fixado*, *bloqueado*).
