# How Orbit for Android is built


```
app/src/main/java/com/hyuchia/orbit/
  MainActivity.kt, OrbitApp.kt   splash, shortcuts and shared files; navigation (Navigation 3, list and detail panes)
  OrbitApplication.kt            what the screens share: maps, settings, pages
  data/                          maps as folders (MapRepository), their text (MapText) and history (MapVersions),
                                 the folder they're kept in (FolderSync, DocumentFolder), imports, templates, settings
  page/                          the WebViews that draw maps: the page, its protocol, the warm pool, pictures
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
  map/                           the pages inside the WebViews, built from web/: the map's (index.html, bridge.js)
                                 and the audience's (audience.html, audience.js)
  emoji/                         the icon search's emoji and their names and keywords, from Unicode's data
  welcome.md, samples/, templates/
web/
  bridge.ts                      the map page's script: draws maps with Orbit and talks to the app
  audience.ts, audience.html     the audience's page: the map, still to the touch, with captions, following the presenter
  audience.css                   its styles, shared with the web page
  web-page.ts, web-page.html     a map shared as one HTML file: explore it, or present it with captions
  second-screen.ts               the audience's page on a second screen, driven by the app
  browser-audience.ts, join.html the audience's page in any browser, following the phone's events; and asking for its code
  cast-receiver.ts               the audience's page on a TV, as a Google Cast receiver
  look.ts                        how the app's maps look anywhere: kinds of ideas, styles, colors, rings by themselves
  rings.test.ts                  which ideas show rings by themselves, how rings count, and the map's setting
  goal.ts, goal.test.ts          goal ideas: their numbers, how far along they are, and the text their rings fit
  words.ts, words.test.ts        what the pages say themselves, in the app's language: the app hands them its strings
  paste.ts, paste.test.ts        text pasted onto a map as orbit-md: plain lines become a list, nested as they're indented
  build.ts                       bundles the scripts and Orbit into app/src/main/assets/map/, and the receiver into dist/
scripts/
  emoji-names.ts                 writes assets/emoji/ from Unicode's emoji list and ranking and CLDR's names
```

- **Maps are folders.** Each holds:
  - `map.md`, the map in orbit-md;
  - `media/`, the pictures added to it, kept at most 2048 pixels on their longer side, as JPEG or PNG, and the videos added from the phone as they came (MP4, WebM or QuickTime, by their type), each with a frame from it beside it as its poster;
  - `meta.json`, whether it's pinned, when it was opened, where you left it, its lasting identity, and its file in the folder maps are kept in;
  - beside the maps, `history/<map>/` holds its earlier versions: one as each visit begins, one for every stretch of editing and one before each big change, thinned out as they age, and `<time>.original.md` for the file as it came in from elsewhere, never thinned;
  - `trash/<map>-<time>/` holds a deleted map, with its history, for 30 days;
  - `maps/.layout.json` says which layout the files are in: `StorageLayout` runs each step from an older one (a copy of the maps' text first, in `backups/`), and leaves a newer one alone.

  A map travels between devices as a zip of its text and the pictures and videos it points at, with `orbit.json` carrying its identity and place. Files kept only for its earlier versions stay on the device. Big files are written, copied and synced a piece at a time, never whole in memory.

  Edits are saved as they happen, one write after another, each only while the file is still the text it was made from (`MapRepository.write`), and a write never leaves half a file (`writeAtomically`: written beside, synced to disk, then renamed). Folders of new maps are filled while hidden and then named, so a crash never leaves half a map.

  Android's backup goes through `OrbitBackupAgent`, within the rules in `res/xml`: it keeps each backup inside the quota its transport gives (`FullBackupDataOutput.getQuota`), the maps' text and the settings first, then their pictures and videos smallest first (`backupFiles`), and takes the history only when moving to a new phone. Restoring is Android's own, but for each file's time, which Android sets in seconds where it takes milliseconds (`onRestoreFile`).
- **The folder maps are kept in** is reached through Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` and `DocumentsContract`), so it can be any folder the system's picker offers. Each map remembers its file and how both sides were when they last matched: the text's hash, and the file's time and size, and when a sync first missed the file. So a sync tells which side changed, and a file just as it was isn't read again. `planSync` plans each sync as plain data, which the tests cover, and `FolderSync` carries it out. Files are read in whatever encoding they were written (`decodeText`), so writing them back never loses a character.
- **Pages, ready before they're needed.** A WebView with Orbit's page is made once the app is idle, so a map draws the moment it opens. The map's view model keeps its page through rotations and trips to other screens. When the map closes, the page goes back to the pool.
- **The spare page works off screen.** Thumbnails, style previews and exports are Orbit's `renderToSVGString`, turned into WebP or PNG on a canvas in the page. Ideas caught without opening their map are Orbit's `addNode` on a map no one sees, so the text comes back the way Orbit writes it.
- **Widgets are Jetpack Glance,** with a layout for each of six sizes; the launcher shows the size closest to the widget's among those that fit. Their pictures are the home screen's thumbnails, ready before a widget draws so it never opens on a placeholder. Widgets draw again once changes to the maps settle, and when the home screen turns light or dark.
- **Maps between devices are sealed** (`nearby/OfferEncryption.kt`), with standard algorithms from the JDK and Android:
  - A device taking maps makes a P-256 key pair each launch, and gives the public key in its advertisement (`key`) and at `GET /key`.
  - The sender makes a fresh pair, agrees a secret with ECDH, and derives its keys with HKDF-SHA256, salted with both public keys. The map, its pictures and the sender's name travel in AES-256-GCM chunks of 64 KiB, each with its own nonce, authenticating how many chunks there are and where each goes. Only the sender's public key and the version travel in the clear.
  - The check code comes from the agreed keys and a random half from each device. The sender commits to its half inside the sealed map, gets the receiver's, then reveals its own, so no one in between can pick keys until the codes match. The offer shows only after that, and holds its place under the limits while it waits.
- **Pictures and videos full screen** (`media/`) are a window of their own over the app, so they cover the list beside the map on a tablet, take the keys and Back, and keep light system bars on the dark. Back's predictive swipe shrinks them as it goes.
  - **Pictures** are [Telephoto](https://github.com/saket/telephoto)'s zoomable images on the app's Coil: big ones are read a part at a time (subsampling), so they stay sharp zoomed in without filling memory, and its flick closes the viewer. The gallery is the map's pictures and videos in the order the page reports its nodes, the outline's (`galleryOf`).
  - **Video files** play in Media3's ExoPlayer, with Media3's Compose surface and Material controls. The player is made when a video opens and released when it closes, so maps carry nothing of it.
  - **YouTube and Vimeo** play in their own embedded players, in a web view apart from the map's: no channel to the app, no files or content, JavaScript only because the players need it, no third-party cookies, and nothing loads in it but the players' frames (a content policy says so too); other pages open in a tab. YouTube's player refuses to play ("Error 153") for a page that doesn't say where it is, so the player's page is loaded with `loadDataWithBaseURL` as the app's id, `https://com.hyuchia.orbit`, as YouTube asks of apps, and its frame's requests carry that Referer. Vimeo plays its videos protected, so its player may use the phone's protected-content system, and nothing else a browser offers.
- **Pictures and videos to the audience** go as commands (`AudienceCommand.Media`, `Playback`, `Seek` and `CloseMedia` in `page/Protocol.kt`), which the audience's page turns into Orbit's media viewer (`openMedia` with `startAt`, `playMedia`, `pauseMedia`, `seekMedia`, `unmuteMedia`). The phone reckons where a video is from what it sends (`present/ShownMedia.kt`); a second screen's and a TV's word on their video (`mediaState`) corrects it, and a browser that joins halfway opens the video where the phone reckons it is.
  - **Videos seek** because both servers answer for part of a file. The phone's web server answers a `Range` request with 206 and `Content-Range`, reading only that part (`nearby/ByteRanges.kt`). In the WebView, maps' files answer the same way (`page/MapFileParts.kt`), holding the whole file: WebView skips to the part a request asks for by itself, in any stream an app answers with.
- **App and page talk in JSON.**
  - The app sends commands through `evaluateJavascript` (`MapCommand` in `page/Protocol.kt`).
  - The page answers through a `WebMessageListener` limited to its own origin (`MapMessage`).
  - Jobs such as pictures and conversions carry a number that their answer returns.
  - After every change the page sends the whole map: each node with its attributes, its custom ones included, and the color it's drawn with (Orbit's `getNodeColor`).
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
