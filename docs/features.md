# What Orbit for Android does

Everything the app does, area by area. The website shows the highlights, with pictures: https://orbit.hyuchia.com/android.


## Your maps

- **Each map as Orbit draws it.** Home shows every map as a picture of how it opens, in its own style or the app's colors. Orbit draws the pictures off screen. A map with to-dos or goals says how far along they are ("2 of 7 done", with a bar), counted as its outline counts them.
- **Finding maps:**
  - search titles, ideas, notes and tags;
  - pin maps to the top;
  - sort by last edited, last opened, newest or title.
- **Tags** group maps however you think of them, and a map can have several. They're kept in each map's file, as `tags:` in its frontmatter, where notes apps like Obsidian read them too, so they go wherever the map goes. Home shows the tags in use as chips under the search: tapping one shows only its maps, and a map made meanwhile takes that tag. Each card shows its map's tags. Edit tags renames a tag, or takes it out of every map. A map's tags are picked or added from its card's menu, or from its own menu while it's open, with Undo there.
- **Card menus:** pin, rename, tag, duplicate, share or delete a map. A deleted map comes back with Undo, and from the trash for 30 days.
- **Starting a map:**
  - a blank map;
  - one of eleven templates (project plan, study notes, talk outline, decision, SWOT, goals and more), each previewed live in its own style;
  - a markdown, YAML or zip file;
  - text shared from any app: a plain list becomes a map with the first line in the middle.
- **First launch:** a welcome map shows you around, with four samples beside it. The last, a city library's database in the cosmos style, has 2,400 ideas, to show how Orbit handles a big map.

## On the map

- **Getting around:**
  - tap a planet to fly into it;
  - spin the orbit, or pinch to zoom in and out;
  - switch to the overview.
- **Going back:** back and the swipe gesture go up a level, and the map shrinks under your finger. Breadcrumbs lead back up too.
- **Picking up where you left off:** a map reopens on the idea and view you left it in.
- **Adding ideas:**
  - tap the **+** in the orbit (or the floating +) and type right inside the new planet;
  - Enter adds the idea and opens the next one, so ideas keep coming.
- **Talking ideas onto the map:**
  - hold the floating **+** and talk: each pause, or each sentence where the phone's speech service punctuates, becomes a planet in the orbit in focus as you speak;
  - let go to finish, or slide up while holding to keep listening hands-free until Done; TalkBack's long press does the same;
  - one Undo takes the whole talk back;
  - it listens through whatever speech service the phone has, not only Google's. Where there's only another app's listening screen, that screen hears one go. Where there's no speech service at all, as on many phones without Google, the new idea's field opens for typing or the keyboard's microphone.
- **The long-press menu,** where you pressed, once you let go:
  - for a goal, its numbers first: −1 and +1 for a count, with the menu staying open for the next step, or an amount to add, take off or pay in a small dialog;
  - add ideas under or next to an idea, or rename it on the map;
  - set its status, color or icon. Icons come from Android's emoji picker, with a search above it that finds emoji by name or keyword, in the app's language and in English, the emoji people use most first. A long press on a match offers its skin tones;
  - fold its ideas away, or move it earlier, later, in or out;
  - duplicate it, or delete it with Undo;
  - copy it with everything under it, as text other apps paste as an outline, or paste into it, with one Undo: text becomes ideas under it (a line each, nested as the lines are indented; a markdown list, or an idea copied in Orbit, keeps its shape, to-dos, colors and icons), an address on a line of its own an idea that opens it, and a picture its picture. An idea pasted into another map brings its pictures and videos. Orbit only reads the clipboard when you paste, so Android's notice that it did comes with what you asked for.
- **Dragging an idea somewhere else:** a long press lifts it, and moving on drags it, its moons with it.
  - Onto another idea: it lights up, and the idea goes inside it, shrinking as it's about to. Moved under another idea, a message says where, with Undo.
  - Along the orbit: its place is held where it was, and the other planets make room as it passes them. Over a planet's rim it's still the orbit; near the middle, the planet takes it.
  - A light tick on each idea it could land in, and a tap when it lands. Undo takes it back like any change.
- **The toolbar** floats over the map: the outline, search, the overview and styles, then the idea in focus's own buttons (its picture or video, what it holds, and its editor), and **+**. On a narrow phone, a map with pictures or videos keeps Style and layout in its menu, so the toolbar stays the same as you fly around.
- **The editor** (the pencil in the toolbar) covers everything an idea can be:
  - its text, icon, color, status and priority. A color is one of Orbit's, one the map's ideas already have (most used first), or any other from the color picker: hue, saturation and brightness, or its hex code, with the idea shown in it. The long-press menu offers the same colors;
  - its kind: milestone, question, decision, risk, source, person, progress, which draws a ring that fills as the ideas under it get done, or goal;
  - a goal's numbers, at the top for a goal: saving up to a target in a currency (the phone's, or any other), paying something off, or counting things, like 5 apples. The numbers are typed in the phone's language, on the decimal keyboard, and what isn't a number is said under its field;
  - tags, a markdown note with a preview, and a link to a web page or to another idea;
  - a photo or a video from the Photo Picker, a YouTube, Vimeo or video address, and a web page. A video from the phone is copied into the map as it is, with its progress showing and a way to stop it, and a frame from it becomes its poster, which its planet shows.

  The sheet stops halfway, and the map glides into the room above it as the sheet comes up, so each change shows on the map as you make it.
- **Goals** track a number toward a target. A goal's planet shows a ring like a Progress idea's, with its number big in the middle and its target under it ("of 5", "of $3K"), or "left" when it counts down; a goal reached closes its ring and shows a check. Numbers are written in the phone's language, money in its currency. A Progress idea counts each goal under it by how far along it is, and a goal reached gives a light tap. A goal left without a target follows the goals and to-dos under it instead, with a ring like a Progress idea's.
- **Progress rings by themselves:** an idea with a to-do (to do, in progress, blocked or done; other status words, like "primary key", are labels), or a goal with a target, right under it shows a Progress ring without being made one, while it has no kind and no picture or video of its own, and its emoji moves onto its name. The ring is only drawn: the map's file never gets a kind for it. Each to-do and goal counts once, for every idea above it: an idea whose ring follows the ones under it isn't counted itself. A map can turn these rings off in Style and layout, which writes `progressRings: false` in its frontmatter; turning them on again takes the line out.
- **Details:** an idea's note, picture, video and links open in a native sheet, from the toolbar or by tapping them on the map. A goal's details show how far along it is, its numbers in words ("$1,250 of $3,000 · 42% · $1,750 to go"), and −/+ or a way to add an amount. An idea whose ring follows the ideas under it says how far along they are ("2 of 4 done · 63%"). Notes render natively with links: web links open in a tab, and links to other ideas fly there. A tap on its picture shows it full screen, and one on its video plays it.
- **Pictures full screen:** the toolbar's picture button shows the idea's picture over everything, on the dark, fitted to the screen.
  - Pinch, double-tap where you want a closer look, or use the wheel or + and -, then pan and fling; 0 fits it again. Big photos stay sharp at any zoom, read a part at a time.
  - Swipe, or use the arrow keys, through the map's pictures and videos in the outline's order, with "3 of 10" at the top. A video's page shows its poster, to play.
  - A tap hides and shows the controls: close and share at the top (the file itself, or a picture's web address), and the caption and description at the bottom. The description is what screen readers say.
  - Back closes it, shrinking as you swipe, and so do Escape and a flick up or down when the picture isn't zoomed, with the map showing through as it goes.
- **Videos play in the app:** from the toolbar's play button, the details or the editor, or a video's page among the pictures.
  - Videos in the map and video files on the web play at once, fitted to the screen whichever way the phone turns, with play and pause, ten seconds back and on, the time, a slider and the sound. The screen stays on while they play, and they pause when you leave the app.
  - YouTube and Vimeo videos play in their own players inside the app, YouTube's in its privacy-enhanced mode, with their own controls and fullscreen, and a button to watch them on their site or in their app.
  - Keys: space plays and pauses, the arrows go back and on, and Escape closes.
- **Outline:** the whole map as a list, with each idea's color, progress for every branch, the share of each ring that follows the ideas under it, checkboxes to tick to-dos off (a status that's a label, like "primary key", reads with the tags instead), and each goal's numbers ("2/5", "$1.3K of $3K") with a small ring. It is also how a screen reader or keyboard gets around.
- **Search:** finds ideas by text, tags or notes, shows the path to each, and flies to the one you pick.
- **Style and layout:**
  - Orbit's styles, each previewed with your map: paper and night, flat, Material, liquid glass and glass (each light and dark), cosmos, blueprint, ink and high contrast;
  - "App colors", the default: Orbit's Material style grown from your wallpaper's color, so the sun and every branch take tones in harmony with it;
  - color sets;
  - whether the map opens on one idea or the whole map;
  - orbit rings, progress rings on ideas with to-dos or goals, a slow drift, and how many levels show.

  The screen around the map takes on its style.
- **Undo and redo** for every change.
- **Version history:** "Version history" in a map's menu lists the map as it was: a version for every ten minutes of editing, all of today's, then one an hour for a week, one a day for three months, and one a week after that. Choosing one shows it on the map to look around in. Restore makes it the map again, with Undo, and the map as it was joins the history too.
- **Edit as text:** the map's orbit-md, colored as Orbit reads it, with the map redrawing above as you type and Orbit's notes about the text below. It also shows as YAML.
- **Export and share:**
  - a web page: one HTML file with Orbit, the map and its pictures inside, which flies in any browser, offline, and presents along the map's path with captions, keys and swipes (videos from the phone stay out of it, and their posters show);
  - a PNG of the current view, the whole map or the map as it opens, with or without its background;
  - an SVG;
  - markdown, YAML, or a zip with the map's pictures and videos.
- **Present:** see [Presenting](#presenting).

## Presenting

- **One tap away:** Present has a button of its own on every map, starting at the idea in focus, and each map's menu on the home screen presents it from the top.
- **Full screen:** one idea at a time, in reading order or along your path, with its note as the caption, under the map or beside it on a screen wider than tall. The controls step aside after a moment, and a tap brings them back. Turn the captions off for the map alone.
- **Your own path:** "Presentation path" in a map's menu starts at the top of the map: tap the ideas in the order you'll present them, and each joins a numbered strip along the bottom. Flying back up to an idea already on the path doesn't add it again, so going from branch to branch stays simple, and "Add again" comes back to one on purpose. Tap a step to fly to it, long-press it to move it, or take it off with its ×. Without a path, maps present in reading order.
- **Rehearse:** run through the presentation with a clock on each idea. Afterwards Orbit shows how long it took and where the time went, idea by idea. Presenting, the controls then show whether you're on pace, behind or ahead, and the presenter's view counts down each idea's rehearsed time. Rehearse a part on its own and only its times change.
- **Like a clicker:** swipe across the screen, or use arrow keys, page keys, volume keys or a presentation clicker. It can also move on by itself, and the screen stays on.
- **On a second screen:** a TV on HDMI or USB-C, or a wireless display, shows the audience the map and its captions. The phone becomes the presenter's view:
  - the map as the audience sees it;
  - the idea's whole note, and what comes next;
  - a clock, your pace against the last rehearsal, and big buttons.

  Unplug the screen and the presentation carries on, on the phone.
- **In any browser in the room:** "Show in a browser" serves the presentation from the phone over the local Wi-Fi, or its hotspot, with no server and no cost. A laptop on a projector, or the phones in the room, open a short address with a six-digit code, or scan its QR code, and follow every step; the presenter view counts who's watching. It runs only while presenting with it on.
  - **Who's watching:** people type their name as they join, or come in as Guest 1, Guest 2 and so on, and the phone lists them with when each joined. Remove someone and their browser says so, and they can't join again.
  - **Who may join:** a 4-digit PIN to say aloud, besides the code (it's never in the address or the QR code), a limit on how many at once, and a switch that stops anyone new joining once everyone's in. The next presentation starts with the same names, PIN and limit choices.
  - **A phone that sleeps** during the talk picks up where the presentation is when it wakes, without joining again.
- **On a TV, with Google Cast (the play build):** the TV runs the audience's page, and Orbit draws the map there, pictures included; the phone is the presenter's view. The receiver is Orbit's own, at orbit.hyuchia.com/cast (see [Casting to TVs](../CONTRIBUTING.md#casting-to-tvs)). Built without its ID, the presentation's cast button opens Android's own screen casting, which shows the phone's screen on the TV.
- **Pictures and videos, big:** when the idea in focus has a picture or a video, the presenter's view offers "Show the picture" and "Play the video". The audience's screens show it over the map, with its title and caption, and the preview shows what they see.
  - **A video** plays with its sound on a second screen and a TV, and the phone has play and pause, "Start over" and its time ("0:42 / 3:10"). A browser that won't play sound before it's touched shows "Tap for sound", and one that joins halfway starts where the others are. A video file kept on the phone can't reach a TV, which shows its poster with its title and caption instead, and the phone says so.
  - **Back to the map:** its button, a step or Esc. A presentation that moves on by itself waits while a picture or video shows, and gives the idea its whole time again after.
  - **On the phone alone,** they open full screen over the presentation; a step key or the clicker closes them and steps.

## Between your devices

- **Send to a device:** from a map's menu or its card. Your other devices with Orbit open on the same Wi-Fi show up by name, with their address, found with Android's own network service discovery, so it works without Google services. One tap sends the map, encrypted end to end, so no one else on the Wi-Fi can read it on the way. The other device asks before taking it, then opens it where you left off, presentation path included, and the sender sees it opened.
- **A check code:** while the map waits for an answer, both devices show four digits. When they match, the map went straight to the device in front of you. When they don't, another device on the Wi-Fi answered in its place, and may have seen the map: decline it, and send it another way.
- **Maps keep their identity:** a map sent back and forth updates in place instead of piling up copies. An open map catches up at once, undo brings its earlier version back, and the version it replaced stays in the map's history. "Keep both" makes a copy instead.
- **Other ways:**
  - share it as a file, through Quick Share, Huawei Share, Bluetooth, email or a chat, which opens straight into Orbit, updating or adding; a zip of every map, from Export all, brings in all its maps;
  - open it on a computer: a Chromebook or laptop opens a short address in its browser to explore, present or download the map. The phone lists who opened it, and can ask for a PIN, limit how many at once, stop anyone new, or remove someone;
  - type the other device's address, shown in Settings, where a network keeps devices from finding each other.
- **Your choice:** Settings can stop nearby devices from offering maps, and choose the name they see: the device's own until you change it.
- **Only the room:** the phone listens only while Orbit is open and there's a reason to (taking maps, a map open on a computer, presenting in browsers), only to devices on the same Wi-Fi, hotspot, cable or USB tethering, never over mobile data, and only at the address it shows, so a website can't reach it under a name of its own. Codes have six digits, and a device that types five wrong ones waits before it can try again, longer each time; browsers already in are never kept waiting. A crowd, or a slow or stuck browser, never holds up the rest of the app.
- **Keep maps in a folder:** Settings can keep every map as a markdown file in a folder you choose: on the phone, on an SD card, or a sync app's or cloud drive's folder. Android's own folder picker chooses it, so it works without Google services.
  - **Both ways:** an edit here is written to the map's file once it settles, and a file changed there, by a sync app such as Syncthing, on a computer or on another device, changes its map. An open map catches up at once, and its earlier text stays in its history.
  - **When:** as Orbit opens and closes, every two minutes while it's open, and on "Check the folder now".
  - **New and deleted:** a file new in the folder becomes a map. Deleting a map deletes its file, and the other way round, unless the map is open or changed since. A file renamed there keeps its map.
  - **Nothing lost:** a map changed both here and there is kept twice, the folder's as "… (changed elsewhere)". A folder with none of the maps' files left, emptied or swapped for another, gets them written again rather than every map deleted. A folder with markdown files already asks before taking them in, and maps it has already, from another device, are followed rather than copied.
  - **Pictures and videos** go in a `media` folder beside the files, where the maps point at them. A copy there that a sync cut short is written again. One there too big for a map (see [Your maps are safe](#your-maps-are-safe)) stays only in the folder, and Settings says which.

## Without opening a map

- **A map on the home screen:** "Add to home screen" in a map's menu asks the launcher to place a widget for it, previewed as it will look. The widget shows the map as Orbit draws it, its name, and how far along its to-dos and goals are, as its outline counts them. A tap opens the map, and + catches an idea into it.
  - **Any size:** one cell shows the map and its name; a row puts the name and progress beside the picture; two rows show it all.
  - **Always current:** it follows the map as it changes, in the home screen's light or dark.
  - **From the launcher's list:** a widget added there asks which map to show, and its settings change it. On Android 15 and later, the list previews the map a new widget would show.
- **Catching ideas:** a sheet over whatever is on screen, with one idea on each line, into the map you pick. Next time it offers the map ideas went to last. A map open on screen catches up at once. Three ways in:
  - **A widget's +,** for its map;
  - **"Add an idea" in Quick Settings,** which waits until the phone is unlocked;
  - **"Add to an Orbit map" in another app's share sheet:** a shared list loses its bullets, numbers and checkboxes, and each line becomes an idea. "New Orbit map" makes a map of it instead.

## Everywhere

- **Phones and tablets, either way up:**
  - **Tablets and foldables:** your maps sit beside the open map, which is the one you opened last when the app starts. The outline, editor, details, search and styles open beside the map, and while they're open, or the map's text or a presentation is, the list steps aside for the map.
  - **A phone on its side:** the map's tools stand in a rail at the side, the breadcrumbs and the idea's buttons move into the top bar, and panels open beside the map. Home fits its title and search into one bar, so the maps get the room.
  - **The map always fits what's left:** Orbit fits each view into the room the toolbar, the rail, a sheet or the captions leave, and glides when that changes.
- **Keyboards and mice,** on tablets and Chromebooks:
  - **Building a map from the keyboard:** Tab adds an idea under the one with keyboard focus, Shift+Enter one beside it, F2 renames it, Delete deletes it, with Undo, and Ctrl+C, Ctrl+X and Ctrl+V copy, cut and paste into it. While typing, Enter brings the next idea, Tab goes under the one just typed, and Shift+Tab comes back up. Arrows move between ideas, Enter flies in, and Escape goes back.
  - **Shortcuts:**
    - Ctrl+Z and Ctrl+Shift+Z undo and redo, and Ctrl+F searches.
    - Ctrl+1, 2 and 3 switch between the orbit, the whole map and the outline.
    - Ctrl+E edits the idea in focus, and F5 or Ctrl+Enter presents.
    - On the list, Ctrl+N starts a map, Ctrl+O opens a file, and Ctrl+, opens the settings.
    - Ctrl+/ lists them all, as does Android's own list (Meta+/).
  - **Escape** closes dialogs, sheets and panels, and otherwise goes back, as Back does.
  - **The mouse:** a button shows its name and keys as the pointer rests on it. A right click opens an idea's or a map's menu where it was clicked, and the wheel zooms the map, or a picture full screen, around the pointer.
  - **Pictures and videos:** the arrows go through the pictures, + and - zoom and 0 fits; in a video, space or K plays and pauses, J and L go ten seconds back and on, the arrows too (five seconds in YouTube's and Vimeo's players, as on their sites), and M turns the sound off and on. Escape closes them.
- **Material You,** light and dark, or Orbit's own colors. You pick in Settings, along with haptics, snapping the orbit to a planet, the style for new maps and how long presentations wait.
- **Launcher shortcuts:** a new map, and the maps you opened last.
- **Updates without a store:** Orbit downloaded from its website or GitHub asks GitHub once a day whether a new version is out. The home screen then says so, with a button to its download page and Not now, which waits for the next one; About shows what the last look found and looks again when tapped. Settings can turn it off. The builds from Google Play, F-Droid and AppGallery leave it to their store.
- **Without Google services,** on Huawei phones and others, everything works:
  - **Maps draw** with the phone's own WebView, Android System WebView or Huawei's, from Chromium 99 on. An older one gets a notice saying what to update, with a button to its page in the phone's app store (Google Play, AppGallery or another) when there's a store that opens it. Your maps stay safe meanwhile.
  - **Sending maps** finds devices with Android's own network service discovery, and keeping maps in a folder uses Android's own folder picker.
  - **Presenting:** without Google Cast, the cast button opens Android's own screen casting where the phone has it, and presenting in browsers works on any network.
  - **Speaking ideas:** without a speech service, a held + opens the new idea for typing or the keyboard's microphone.
  - **Crashes:** a report of what went wrong (Orbit's and Android's versions and where in the code, nothing from your maps) stays on the phone, and the next launch offers to share it or delete it.
- **Your data:** maps are plain files, backed up with your phone (see below).

## Your maps are safe

A map holds someone's thinking, so Orbit never loses one, or any part of one:

- **Every write is whole.** Maps, their details, their versions and pictures are written beside the old file, reach the disk, and only then take its place, so a crash or a dead battery never leaves half a map.
- **Nothing empties a map by mistake.** Text with no ideas in it never replaces a map that has some, and a save made before the map changed elsewhere (another device, the folder, ideas caught on the go) never writes over that change: it goes into the history instead, and Undo brings it back.
- **Maps Orbit can't fully read are only shown.** When Orbit can't read part of a map's text, or a newer version of Orbit made it, the map opens read-only with a note saying why, and its file stays exactly as it is until the text is fixed or Orbit is updated. Undo, versions brought back, and changes from elsewhere are drawn as they are, never rewritten.
- **Versions at the right moments:** as each visit to a map begins, every ten minutes of editing, and before big changes: an idea removed with the ones under it, a version brought back, the text edited by hand, a version from elsewhere arriving. A file that came from elsewhere (an import, a share, the folder) is kept as it came in, for good, before Orbit ever writes it.
- **A trash:** deleted maps wait 30 days in Settings, Trash, history and all, to be restored or deleted for good. A map deleted while it's open becomes read-only, with a way to restore it.
- **The folder never deletes on a hunch.** A map only follows its file out of the folder once two syncs at least ten minutes apart both miss it, and then into the trash. A file that can't be read is left for the next sync, writes to the folder are read back before they count, a file a write left cut short is written again, and one emptied there gets the map's ideas back.
- **Pictures and videos within limits:** a video from the phone may be up to 50 MB, and a map's pictures and videos up to 90 MB together. So a map always fits in its zip and comes back from it, and one with a video still goes straight to a nearby device. Larger ones are refused, saying why, and nothing of them stays. Ones in the folder maps are kept in get the same room, and those past it stay only there.
- **Upgrades and going back:** the app's files carry their layout's version, and an update moves older files to its layout after keeping a copy of every map's text. Details a newer version wrote stay in a map's `meta.json` when an older one changes it, and files a newer version laid out in a way an older one can't change safely are only shown by it.
- **Backups:** Android's backup keeps the maps and settings. A backup to the cloud stops altogether past 25 MB, so it takes the maps' pictures and videos smallest first, as many as fit, and a big video never stops it; moving to a new phone takes them all, and the maps' history. Settings, Export all maps writes every map, pictures, videos and history included, into one zip to keep anywhere, and Import maps brings them back on any device, leaving maps already there as they are. Uninstalling offers to keep the maps.

## What's next

- Keep maps in step between devices without a sync app in between.
