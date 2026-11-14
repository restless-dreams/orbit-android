# How Orbit for Android is built

```
app/src/main/java/com/hyuchia/orbit/
  MainActivity.kt, OrbitApp.kt   splash, shortcuts and shared files; navigation (Navigation 3, list and detail panes)
  OrbitApplication.kt            what the screens share: maps, settings, pages
  Words.kt, Logs.kt              the app's strings for code that makes text without a screen (the resources, or the
                                 files in tests); the app's log, which says nothing of what people write in a release
  data/                          maps as folders (MapRepository), their text (MapText) and history (MapVersions),
                                 the folder they're kept in (FolderSync, DocumentFolder) and the devices that use it
                                 (FolderDevices), what leaves the phone (OutgoingMedia, MediaSummary), imports,
                                 templates, settings
  page/                          the WebViews that draw maps: the page, its protocol, the warm pool, pictures, printing
  present/                       the audience's screen while presenting: a second screen, a TV over Google Cast, or browsers
  nearby/                        the local network: a small web server, presenting to browsers in the room,
                                 and sending maps between devices (finding them, sealing, offering, answering)
  home/                          your maps, cards and thumbnails, templates
  map/                           the map screen and its panels: editor, details, outline, search, style, text, export, presenting
  capture/                       ideas caught without opening a map: the sheet that catches them, the Quick Settings tile,
                                 and text shared from other apps (QuickCapture adds them through a page)
  voice/                         talking ideas onto a map, through whatever speech service the phone has
  study/                         study mode: the cards under an idea, their schedule (Study), a session as it goes
                                 (StudyControl) and its screen (StudyMode)
  sketch/                        sketching on a planet: strokes and their outlines (StrokeOutline, StrokeShape), the SVG
                                 they're kept in (SketchSvg), the drawing as it goes (SketchSession, StrokeInk), and its
                                 canvas and toolbar
  widget/                        a map on the home screen (Jetpack Glance), its setup and pinning it from the app
  reminders/                     ideas' dates reminding as they come: what the maps' dates make (Reminders), Android's
                                 alarms and notifications for them, and the idea a reminder opens its map at
  privacy/                       private maps: when they lock (PrivateLock), the phone's prompt (Unlocking),
                                 the lock in front of a map (PrivateMapGate) and keeping them out of Recents
  media/                         pictures, videos and sounds full screen: the viewer, the video player, YouTube's and Vimeo's
                                 players, and the sounds' player and the voice recorder
  settings/                      settings and about
  theme/, ui/                    Material You, Orbit's styles and palettes, the window, bars and splash in the app's light or
                                 dark (AppWindow.kt), shared pieces, and the lines screens change their layout at
                                 (ScreenShapes.kt)
app/src/main/assets/
  map/                           the pages inside the WebViews, built from web/: the map's (index.html, bridge.js),
                                 the audience's (audience.html, audience.js), a shared map's (web-page.html,
                                 web-page.js) and a browser's in the room (browser.html, browser.js, join.html)
  emoji/                         the icon search's emoji and their names and keywords, from Unicode's data
  welcome.md, welcome.json       the welcome map a first launch puts in, and the presentation path it starts with
  welcome/                       its picture, video and sound, drawn by art/welcome.ts, the same in every language
  templates/                     the maps Start a map offers
  es/                            the Spanish welcome map, templates and privacy policy
  pt/                            the Brazilian Portuguese welcome map, templates and privacy policy
app/src/debug/assets/samples/   the city library, a map of 2,400 ideas that debug builds put in beside the welcome map,
                                 to try big maps on (and its Spanish and Portuguese copies in es/samples/ and pt/samples/)
web/
  bridge.ts                      the map page's script: draws maps with Orbit and talks to the app
  bridge.test.ts                 the map's page driven as the app drives it: its commands in, its messages out
  whole-numbers.ts               a map's numbers the app reads as whole ones, sent as the nearest its Int holds
  reading.ts, reading.test.ts    whether a map's text can be changed and saved: not when a newer Orbit wrote it,
                                 nor when Orbit couldn't read all of it
  changing.ts, changing.test.ts  when the map on the page takes changes: not while it's locked, nor while an earlier
                                 version from its history is on show
  protocol-examples.ts           one of each message between the app and its pages, which the app's tests read
  audience.ts, audience.html     the audience's page: the map, still to the touch, with captions, following the presenter
  audience.css                   its styles, shared with the web page
  web-page.ts, web-page.html     a map shared as one HTML file, its pictures and sounds inside: explore it, or present
                                 it with captions (web-page.test.ts: presenting with keys, swipes and full screen;
                                 web-page-print.test.ts: printed by the app or a browser)
  print.ts, print.test.ts        a map laid out for paper, with the app's look in light: the PDFs and prints
  other-apps.ts, other-apps.test.ts  maps from OPML, FreeMind and Freeplane, and XMind, read with Orbit's importMap
  second-screen.ts               the audience's page on a second screen, driven by the app, telling it how its videos go
                                 (second-screen.test.ts)
  browser-audience.ts, join.html the audience's page in any browser, following the phone's events, and finding the phone
                                 again as it drops, sleeps or turns it away (browser-audience.test.ts); and asking for
                                 its code
  cast-receiver.ts               the audience's page on a TV, as a Google Cast receiver (cast-receiver.test.ts)
  receiver-protocol.ts           how the phone and a TV's receiver say their versions, and commands that come in parts
                                 (receiver-protocol.test.ts)
  look.ts                        how the app's maps look anywhere: kinds of ideas, styles, colors, rings by themselves,
                                 and the map's tags
  media.ts, media.test.ts        a picture, video, sound or page the app changes: what it changed, merged into the
                                 node's own, so the fields the app doesn't know stay
  rings.test.ts, tags.test.ts    which ideas show rings by themselves, how rings count, what a drawn map shows of them,
                                 and the map's setting; its tags
  goal.ts, goal.test.ts          goal ideas: their numbers, how far along they are, and the text their rings fit
  words.ts, words.test.ts        what the pages say themselves, in the app's language: the app hands them its strings
  paste.ts, paste.test.ts        text pasted onto a map as orbit-md: plain lines become a list, nested as they're indented
  map-edits.ts, map-edits.test.ts  a map renamed, tagged or copied from Home, as Orbit reads it, markdown or YAML; never one
                                 Orbit can only show
  study.ts, study.test.ts        study mode on the page: the ideas under each card hidden until they're revealed
  build.ts                       bundles the scripts and Orbit into app/src/main/assets/map/, and the receiver into dist/,
                                 in no newer JavaScript than ES2022; and writes the protocol's examples for the app's tests
  typecheck.ts                   type-checks the pages against the Orbit at ORBIT_PATH
  orbit-build.ts                 the Orbit build at ORBIT_PATH that the build, the tests and the type check use: they stop,
                                 saying so, when it's missing or older than Orbit's sources (orbit-build.test.ts)
  orbit-for-tests.ts             loads the Orbit at ORBIT_PATH for the tests, as the bundles have it (bunfig.toml's preload)
  dom-for-tests.ts               gives the tests a pretend browser, happy-dom's, as the library's tests have, so a test
                                 can draw a map (bunfig.toml's preload too)
  page-for-tests.ts              a whole page for a test: its own elements from its HTML, and a clock for its long waits
                                 (each test file runs on its own document: bun test --isolate)
  eslint.config.js               the code style of web/, art/ and scripts/: the library's rules, from ORBIT_PATH
app/src/androidTest/             the device tests, in Orbit's process on a phone or an emulator: a map on the device's own
                                 WebView, edited and saved, a page that doesn't load or whose process dies, a job that
                                 never ends (OnDevice reads and taps the map's page through its own document)
journeys/                        journeys through whichever Orbit is installed, through its screen alone, in a process of
                                 their own: everyday journeys on the release builds, a presentation, Orbit in Spanish
                                 and Portuguese, files and text from another app, Quick Settings, the map widget, and
                                 screens Android brings back after stopping Orbit
scripts/
  smoke-test.sh                  a release build on a device: installed, started cold, a map drawn, the journeys, and
                                 Orbit's notice on a WebView too old for maps
  device-tests.sh                the device tests and the process death journey on the debug build
  aosp-webview.sh                Chromium 101's WebView for Android 10's emulator, the closest to Orbit's minimum (99)
  with-emulator.sh               a command on an emulator of its own, started fresh and stopped after
  check-pages.sh                 checks a commit's pages are what its web/ builds, as CI does, in a temporary folder
  compare-pages.sh               compares the pages a build just wrote with the commit's, as check-pages.sh and CI do:
                                 every file, and orbit.json's Orbit version and map format
  emoji-names.ts                 writes assets/emoji/ from Unicode's emoji list and ranking and CLDR's names
  release-data/                  the maps each release's files for the upgrade tests start from (see below)
  live-photos/make-samples.py    makes the small live photos the tests read (app/src/test/resources/live/), each the way
                                 a phone writes it
```

## Areas

- [Files and upgrades](architecture/files-and-upgrades.md): Maps are folders; Every version opens what the earlier ones left, and keeps what later ones wrote; The folder maps are kept in.
- [Pages, and what crosses the app's edge](architecture/pages-and-boundaries.md): Pages, ready before they're needed; What leaves the phone says no more than it shows; The servers listen only in the room; What pages may load; An idea's file opens in another app; What Orbit hands other apps; What other apps can hand Orbit.
- [Drag and drop](architecture/drag-and-drop.md): Drag and drop.
- [The spare page](architecture/spare-page.md): The spare page works off screen.
- [Private maps and widgets](architecture/private-maps-and-widgets.md): Private maps are locked in Orbit, not encrypted; Widgets are Jetpack Glance.
- [Between devices](architecture/between-devices.md): Maps between devices are sealed; Versions between the parts that live apart.
- [Media, sketches and voice](architecture/media-sketches-and-voice.md): Pictures, videos and sounds full screen; Sketches; Talking ideas onto a map; Voice memos; Pictures and videos to the audience.
- [App and page talk in JSON](architecture/app-and-page-protocol.md): App and page talk in JSON.
- [Reminders and study mode](architecture/reminders-and-study.md): Dates remind like a calendar's; Study mode never writes the map.
- [Orbit's features, keys and native feel](architecture/orbit-features-keys-and-feel.md): Orbit's own features, used as they're meant to be; Keys go where they belong; Native feel, on purpose.
