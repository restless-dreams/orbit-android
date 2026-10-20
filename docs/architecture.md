# How Orbit for Android is built


```
app/src/main/java/com/hyuchia/orbit/
  MainActivity.kt, OrbitApp.kt   splash, shortcuts and shared files; navigation (Navigation 3, list and detail panes)
  OrbitApplication.kt            what the screens share: maps, settings, pages
  data/                          maps as folders (MapRepository), their text (MapText) and history (MapVersions),
                                 the folder they're kept in (FolderSync, DocumentFolder), imports, templates, settings
  page/                          the WebViews that draw maps: the page, its protocol, the warm pool, pictures, printing
  present/                       the audience's screen while presenting: a second screen, a TV over Google Cast, or browsers
  nearby/                        the local network: a small web server, presenting to browsers in the room,
                                 and sending maps between devices (finding them, sealing, offering, answering)
  home/                          your maps, cards and thumbnails, templates
  map/                           the map screen and its panels: editor, details, outline, search, style, text, export, presenting
  capture/                       ideas caught without opening a map: the sheet that catches them, the Quick Settings tile,
                                 and text shared from other apps (QuickCapture adds them through a page)
  voice/                         talking ideas onto a map, through whatever speech service the phone has
  widget/                        a map on the home screen (Jetpack Glance), its setup and pinning it from the app
  media/                         pictures and videos full screen: the viewer, the video player, YouTube's and Vimeo's players
  settings/                      settings and about
  theme/, ui/                    Material You, Orbit's styles and palettes, shared pieces
app/src/main/assets/
  map/                           the pages inside the WebViews, built from web/: the map's (index.html, bridge.js),
                                 the audience's (audience.html, audience.js), a shared map's (web-page.html,
                                 web-page.js) and a browser's in the room (browser.html, browser.js, join.html)
  emoji/                         the icon search's emoji and their names and keywords, from Unicode's data
  welcome.md, samples/, templates/
web/
  bridge.ts                      the map page's script: draws maps with Orbit and talks to the app
  reading.ts, reading.test.ts    whether a map's text can be changed and saved: not when a newer Orbit wrote it,
                                 nor when Orbit couldn't read all of it
  protocol-examples.ts           one of each message between the app and its pages, which the app's tests read
  audience.ts, audience.html     the audience's page: the map, still to the touch, with captions, following the presenter
  audience.css                   its styles, shared with the web page
  web-page.ts, web-page.html     a map shared as one HTML file: explore it, or present it with captions
  print.ts, print.test.ts        a map laid out for paper, with the app's look in light: the PDFs and prints
  other-apps.ts, other-apps.test.ts  maps from OPML, FreeMind and Freeplane, and XMind, read with Orbit's importMap
  second-screen.ts               the audience's page on a second screen, driven by the app
  browser-audience.ts, join.html the audience's page in any browser, following the phone's events; and asking for its code
  cast-receiver.ts               the audience's page on a TV, as a Google Cast receiver
  receiver-protocol.ts           how the phone and a TV's receiver say their versions, and commands that come in parts
                                 (receiver-protocol.test.ts)
  look.ts                        how the app's maps look anywhere: kinds of ideas, styles, colors, rings by themselves,
                                 and the map's tags
  rings.test.ts, tags.test.ts    which ideas show rings by themselves, how rings count, and the map's setting; its tags
  goal.ts, goal.test.ts          goal ideas: their numbers, how far along they are, and the text their rings fit
  words.ts, words.test.ts        what the pages say themselves, in the app's language: the app hands them its strings
  paste.ts, paste.test.ts        text pasted onto a map as orbit-md: plain lines become a list, nested as they're indented
  build.ts                       bundles the scripts and Orbit into app/src/main/assets/map/, and the receiver into dist/,
                                 in no newer JavaScript than ES2022; and writes the protocol's examples for the app's tests
  typecheck.ts                   type-checks the pages against the Orbit at ORBIT_PATH
scripts/
  emoji-names.ts                 writes assets/emoji/ from Unicode's emoji list and ranking and CLDR's names
  release-data/                  the maps each release's files for the upgrade tests start from (see below)
```

- **Maps are folders.** Each holds:
  - `map.md`, the map in orbit-md;
  - `media/`, the pictures added to it, kept at most 2048 pixels on their longer side, as JPEG or PNG, and the videos added from the phone as they came (MP4, WebM or QuickTime, by their type), each with a frame from it beside it as its poster;
  - `meta.json`, whether it's pinned, when it was opened, where you left it, its lasting identity, and its file in the folder maps are kept in;
  - beside the maps, `history/<map>/` holds its earlier versions: one as each visit begins, one for every stretch of editing and one before each big change, thinned out as they age, and `<time>.original.md` for the file as it came in from elsewhere, never thinned;
  - `trash/<map>-<time>/` holds a deleted map, with its history, for 30 days;
  - `maps/.layout.json` says which layout the files are in: `StorageLayout` runs each step from an older one (a copy of the maps' text first, in `backups/`), and leaves a newer one alone.

  The settings are a DataStore, `files/datastore/settings.preferences_pb`.

  A map travels between devices as a zip of its text and the pictures and videos it points at, with `orbit.json` carrying its identity and place. Files kept only for its earlier versions stay on the device. Big files are written, copied and synced a piece at a time, never whole in memory.

  Edits are saved as they happen, one write after another, each only while the file is still the text it was made from (`MapRepository.write`), and a write never leaves half a file (`writeAtomically`: written beside, synced to disk, then renamed). Folders of new maps are filled while hidden and then named, so a crash never leaves half a map.

  Android's backup goes through `OrbitBackupAgent`, within the rules in `res/xml`: it keeps each backup inside the quota its transport gives (`FullBackupDataOutput.getQuota`), the maps' text and the settings first, then their pictures and videos smallest first (`backupFiles`), and takes the history only when moving to a new phone. Restoring is Android's own, but for each file's time, which Android sets in seconds where it takes milliseconds (`onRestoreFile`).
- **Every version opens what the earlier ones left, and keeps what later ones wrote.** A change to what the app keeps follows these rules:
  - **The files' layout** (`StorageLayout`): a change that moves or renames files is a new step in `LAYOUT_STEPS` and raises `LAYOUT_VERSION`. A step must be safe to run again after a crash midway, and gets a test that starts from the older layout's files (`MapRepositoryTest`). `LAYOUT_COMPATIBLE` is the oldest layout whose app may still change files laid out this way: raise it too when an older app would harm the new layout by writing to it, and that app then only reads the maps.
  - **`meta.json`** (`MapMetadata`): a new field is optional, with a default that means what files without it mean. A field is never renamed or given another shape: add a new one instead. What this version doesn't know or can't read stays in the file when it writes it (`MetadataFile`), so going back to an older version and forward again loses nothing.
  - **Settings** (`SettingsRepository`): a new key has a default, a key is never renamed, and a choice is kept by its name, so one this version doesn't know reads as the default.
  - **The maps' text** is Orbit's format, which has versions of its own (Orbit's `docs/format.md`, "Versions"): a map written for a newer one opens read-only.
  - **What crosses to other devices carries a version:** an offer's in its `X-Orbit-Version` header and in `GET /key`'s answer. A change to how offers work raises it, and a device refuses an offer in a version it doesn't speak before reading any of it, saying which device to update. The TV receiver works the other way round: it lives apart from the app, so it keeps following every older protocol's commands, and the phone sends it only the commands its protocol knows ("Versions between the parts that live apart", below).
  - **Each release's files are tests.** `app/src/test/resources/releases/` holds what every release left on a phone, made by its own code, and `ReleaseDataTest` opens each one with this version: every map, picture, video, version, trashed map and setting has to be there, opening them must write nothing, and they take changes without losing what they had. A release adds its own (`RELEASE.md`).
- **The folder maps are kept in** is reached through Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` and `DocumentsContract`), so it can be any folder the system's picker offers. Each map remembers its file and how both sides were when they last matched: the text's hash, and the file's time and size, and when a sync first missed the file. So a sync tells which side changed, and a file just as it was isn't read again. `planSync` plans each sync as plain data, which the tests cover, and `FolderSync` carries it out. Files are read in whatever encoding they were written (`decodeText`), so writing them back never loses a character.
- **Pages, ready before they're needed.** A WebView with Orbit's page is made once the app is idle, so a map draws the moment it opens. The map's view model keeps its page through rotations and trips to other screens. When the map closes, the page goes back to the pool.
- **The servers listen only in the room** (`nearby/LocalServer.kt`, `nearby/Addresses.kt`): on the addresses of the Wi-Fi, a hotspot, a cable or USB tethering (`roomAddresses`), following them as the phone joins and leaves networks. They answer only devices in the room's ranges, never another app on the phone itself through the Wi-Fi's own address, and send their pictures and videos with a content policy that keeps an SVG's script, say, from ever running. A request's head has 15 seconds to arrive, and its body 15 seconds and 30 more for each megabyte it says it holds (`ServerTimings`), so a device sending a byte at a time can't hold a connection. Nothing big is made in memory: a body past 256 KiB goes to a file, and a shared map's zip is made in a file for each download and deleted once it's sent.
- **What pages may load.** The pages come with a content security policy: their own scripts and nothing else to run, pictures and videos from the web over HTTPS, and YouTube's and Vimeo's players framed (`PAGE_POLICY` in `page/OrbitPage.kt`). Nothing goes over the web in the clear: the policy has WebView ask for anything at an `http` address over `https` (`upgrade-insecure-requests`; without it, WebView refuses such a picture), and the app's own views do the same (`pictureAddress`); a video at one plays in the browser, as links open there. Of the maps' files, a page gets only the pictures and videos in the media folder of the map it shows, or of one it's drawing a picture of, sent so they never run as a page (`page/MapFiles.kt`): a map can't point it at another map's text or pictures. An SVG export takes in only what answers as a picture.
- **What other apps can hand Orbit.** Files and text another app opens or shares with Orbit (`VIEW` and `SEND` in the manifest) become maps, as Open with and Share are meant to. So an app on the phone could add maps no one asked for, but Orbit comes to the front with each one, which goes to the trash like any other map, and nothing goes back: Orbit only reads what it's handed, and answers no app with anything.
- **The spare page works off screen.** Thumbnails, style previews and exports are Orbit's `renderToSVGString`, turned into WebP or PNG on a canvas in the page. Ideas caught without opening their map are Orbit's `addNode` on a map no one sees, so the text comes back the way Orbit writes it. Maps from other apps (OPML, FreeMind and Freeplane, XMind) are Orbit's `importMap` there (`web/other-apps.ts`): `data/OtherApps.kt` tells them from Orbit's own files by name, type and content, and the importer adds each map with its pictures and says, in the app's words, what Orbit kept another way or couldn't keep. An OPML or `.mm` file is kept in the map's history as it came in, and read the same way when it's shown or restored. OPML export is Orbit's `exportOPML`. PDFs and prints are the map's web page laid out for paper (Orbit's `renderPrintLayout`, in `web/print.ts`) in a web view of their own that no one sees, which Android's print framework turns into pages (`page/MapPrinter.kt`).
- **Widgets are Jetpack Glance,** with a layout for each of six sizes; the launcher shows the size closest to the widget's among those that fit. Their pictures are the home screen's thumbnails, ready before a widget draws so it never opens on a placeholder. Widgets draw again once changes to the maps settle, when the home screen turns light or dark, and when the wallpaper's colors are turned on or off.
- **Maps between devices are sealed** (`nearby/OfferEncryption.kt`), with standard algorithms from the JDK and Android:
  - A device taking maps makes a P-256 key pair each launch, and gives the public key in its advertisement (`key`, with the `version` of offers it takes) and at `GET /key`.
  - The sender makes a fresh pair, agrees a secret with ECDH, and derives its keys with HKDF-SHA256, salted with both public keys. What's sealed travels in AES-256-GCM chunks of 64 KiB, each with its own nonce, authenticating how many chunks there are and where each goes. Only the sender's public key and the version travel in the clear.
  - An offer starts with a hello (`POST /offer`): the sender's name and its commitment to its half of the check code, sealed. The receiver answers with its own half, the sender reveals its half (`POST /offer/<id>`), and both show the code, which comes from the agreed keys and both halves: no one in between can pick keys until the codes match.
  - The map and its pictures go (`POST /offer/<id>/map`), sealed with a key of their own, only once the person sending says the other device shows the same code. So a device that answers in another's place on the Wi-Fi gets nothing of the map. An offer holds its place under the limits from its hello on, and the receiver refuses a map it isn't waiting for, or has no room for, before reading any of it.
  - Version 2 is the one spoken. Orbit 0.2.0 and earlier (version 1) sent the map first: neither side offers to the other, and each says which device to update.
- **Versions between the parts that live apart.** Each pair says which version it speaks, and handles the other being older or newer:

  | Between | What each side says | When one is older | When one is newer |
  | --- | --- | --- | --- |
  | The phone and a TV's receiver (`present/ScreenVersions.kt`, `web/receiver-protocol.ts`) | The phone says hello first, with its protocol (`AUDIENCE_PROTOCOL`, 2) and its version. The receiver answers with its protocol, the newest map format its Orbit reads, and its Orbit's commit. Apps 0.1.1 and 0.2.0 say nothing (protocol 1), and a receiver that doesn't answer within 3 seconds counts as protocol 1 with format 1. | A receiver older than a command doesn't get it (`AudienceCommand.protocol`). For a map written for a newer format than its Orbit reads, the presenter hears that parts may look different on the TV, which still shows it as well as it can. Below `OLDEST_RECEIVER_PROTOCOL`, the presenter hears that the TV can't show this app's presentations yet. | The receiver keeps following every older protocol's commands, so an older app never notices. |
  | Two phones sending a map (`nearby/`) | The offers' `version`, in the advertisement and at `GET /key`, and `X-Orbit-Version` on each offer. | Neither sends to the other. The sender says which device to update, and the device taking maps refuses an older offer before reading any of it. | The same, the other way round. |
  | The phone and browsers presenting or opening a map (`nearby/BrowserPresenting.kt`) | Nothing: the phone serves their pages itself, so they're always its own version. | | |
  | The app and its files (`data/StorageLayout.kt`) | `maps/.layout.json`: the layout (`LAYOUT_VERSION`, 2) and the oldest app that may change files in it (`LAYOUT_COMPATIBLE`, 1). | Moved to this layout step by step, a copy of every map's text kept first. | Read but left unchanged, unless this app is at least the oldest one allowed to change them. |
  | The app and a map's text (Orbit's format) | `schemaVersion`, in the frontmatter or at the top of a YAML map; none means 1. | Upgraded as it's read, and written as the current version at the next save. | Opened read-only, with a note. Nothing in it is lost, and attributes Orbit doesn't know are always kept. |
  | The app and `meta.json` | Fields by name. | | Fields a newer version wrote stay when this one changes the file. |
  | Devices through the synced folder | The maps' markdown files, as above; `meta.json` stays on each device. | As for a map's text. | As for a map's text. |
  | Android's backup | The files as they were, the layout record included. | As for the app's files. | As for the app's files. |
  | The app and GitHub's releases (the update check, `data/Updates.kt`) | `releases/latest`: its tag, whether it's a draft or a pre-release, and its page. | | A draft, a pre-release, a tag that isn't `v` and a version, or a page that isn't HTTPS is ignored. |

  The receiver is the one part every version of the app shares. So it only ever adds to what it understands, and a new receiver is published before an app that needs it ships (RELEASE.md). Anyone on the network can join a TV's Cast session, so it takes nothing on trust: it joins a command's parts in whatever order they come, and drops a part that isn't one, a command of more than `MOST_PARTS` parts or `MOST_COMMAND_LENGTH` characters, and parts that don't make JSON.
- **Pictures and videos full screen** (`media/`) are a window of their own over the app, so they cover the list beside the map on a tablet, take the keys and Back, and keep light system bars on the dark. Back's predictive swipe shrinks them as it goes.
  - **Pictures** are [Telephoto](https://github.com/saket/telephoto)'s zoomable images on the app's Coil: big ones are read a part at a time (subsampling), so they stay sharp zoomed in without filling memory, and its flick closes the viewer. The gallery is the map's pictures and videos in the order the page reports its nodes, the outline's (`galleryOf`).
  - **Video files** play in Media3's ExoPlayer, with Media3's Compose surface and Material controls. The player is made when a video opens and released when it closes, so maps carry nothing of it.
  - **YouTube and Vimeo** play in their own embedded players, in a web view apart from the map's: no channel to the app, no files or content, JavaScript only because the players need it, no third-party cookies, and nothing loads in it but the players' frames (a content policy says so too); other pages open in a tab. YouTube's player refuses to play ("Error 153") for a page that doesn't say where it is, so the player's page is loaded with `loadDataWithBaseURL` as the app's id, `https://com.hyuchia.orbit`, as YouTube asks of apps, and its frame's requests carry that Referer. Vimeo plays its videos protected, so its player may use the phone's protected-content system, and nothing else a browser offers.
- **Pictures and videos to the audience** go as commands (`AudienceCommand.Media`, `Playback`, `Seek` and `CloseMedia` in `page/Protocol.kt`), which the audience's page turns into Orbit's media viewer (`openMedia` with `startAt`, `playMedia`, `pauseMedia`, `seekMedia`, `unmuteMedia`). The phone reckons where a video is from what it sends (`present/ShownMedia.kt`); a second screen's and a TV's word on their video (`mediaState`) corrects it, and a browser that joins halfway opens the video where the phone reckons it is.
  - **Videos seek** because both servers answer for part of a file. The phone's web server answers a `Range` request with 206 and `Content-Range`, reading only that part (`nearby/ByteRanges.kt`). In the WebView, maps' files answer the same way (`page/MapFiles.kt`), holding the whole file: WebView skips to the part a request asks for by itself, in any stream an app answers with.
- **App and page talk in JSON.**
  - The page says hello on a `WebMessageListener` channel limited to its own origin and its main frame, and talks to the app on it (`MapMessage` in `page/Protocol.kt`).
  - The app sends its commands back on the same channel, to the frame that said hello (`MapCommand`): the page reads them as JSON, so nothing the app sends, text from other apps included, is ever run as code.
  - Jobs such as pictures and conversions carry a number that their answer returns. A job is let go once it's answered, given up on after a minute, or no longer wanted by whoever asked.
  - Commands sent before the page says hello wait for it, only as long as they matter (`WaitingCommands`): a page that didn't load takes none, and a map loaded or unloaded makes the commands about the one before it moot.
  - After every change the page sends the whole map: each node with its attributes, its custom ones included, and the color it's drawn with (Orbit's `getNodeColor`).
  - Both sides are held to one set of examples: `web/protocol-examples.ts` has one of each command and message, held to the pages' own types with a key for every kind, and the pages' build writes them into `app/src/test/resources/page-protocol.json`. `ProtocolTest` checks that the app writes each command exactly so, reads each message with all it says, and knows the same kinds. A message the app still can't read is an error in the log of every build, by its kind alone.
  - The app changes a goal's numbers by merging them into the idea's custom attributes, and steps it with `addToGoal`, which adds to the number the page has, so quick steps all count.
- **Orbit's own features, used as they're meant to be:**
  - `editable` with `longPressDelay` set to Android's long-press timeout;
  - `focus` to reopen a map where you left it;
  - node types with `render` hooks for people, progress rings and goals. Orbit draws again only the nodes an edit touched, so the app draws its rings again in place after each edit, for the Progress idea above a goal, moving to the new share;
  - `nodeTypeFor`, which draws ideas with to-dos or goals under them as Progress ideas without writing the kind into the map, on the map, the audience's screens and web pages alike;
  - `showActionButtons: false`, with the idea's buttons in the native toolbar instead, and `onAction` taking over the detail sheet for the native one;
  - `insets`, so the map fits around the toolbar, the rail, sheets and captions;
  - `baseUrl` serving each map's pictures from its folder;
  - themes and palettes from the frontmatter, and `serializeOrbitMarkdown` and `serializeOrbitYAML` for files.
- **Keys go where they belong:**
  - The map's page gets the keys first, and the app's shortcuts take what it leaves (`WebViewClient.onUnhandledKeyEvent`). So typing in an idea keeps Ctrl+Z for its text.
  - Shortcuts no text field uses, such as Ctrl+F, the views and Ctrl+/, are caught before anything else.
  - The page keeps its own focus when the view takes the keyboard's (`setNeedInitialFocus(false)`). The map takes the keyboard's focus back whenever nothing covers it, even after the layout moves it.
- **Native feel, on purpose:**
  - **The page is transparent:** Compose paints behind it, so there's never a white flash.
  - **No web tells:** no text selection, callouts or tap highlights.
  - **Haptics:** Orbit's spin haptics play native clock ticks, and long presses and new ideas get their own.
  - **Back:** predictive back on every screen.
  - **The keyboard:** it comes up when a menu opens the field for typing, and the map makes room for it at once.
