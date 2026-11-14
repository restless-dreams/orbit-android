# Your maps

- **Each map as Orbit draws it.**
  Home shows every map as a picture of how it opens, in its own style or the app's colors.
  Orbit draws the pictures off screen, and again as a map changes while it shows, as when pictures, videos or sounds its text points at arrive after it, from the folder maps are kept in or a zip.
  A map with to-dos or goals says how far along they are ("2 of 7 done", with a bar), counted as its outline counts them.
- **Finding maps:**
  - search titles, ideas, notes and tags, with how many maps match above them, which screen readers hear as it changes.
    A YAML map is searched for its ideas and notes, never for the words its structure uses, such as `children`, `text` or `status`, which no map would match on;
  - pin maps to the top;
  - sort by last edited, last opened, newest or title.
    The order sorts the pinned maps too, so it's beside them when every map shown is pinned.
- **Tags** group maps however you think of them, and a map can have several.
  They're kept in each map's file, as `tags:` in its frontmatter, where notes apps like Obsidian read them too, so they go wherever the map goes; they're read as Orbit reads YAML, so a comment after them isn't a tag, and a quoted tag keeps its commas, quotes and escapes.
  Home shows the tags in use as chips under the search: tapping one shows only its maps, and a map made meanwhile takes that tag, unless only private maps that locked since have it.
  Each card shows its map's tags.
  Edit tags renames a tag, or takes it out of every map; one it couldn't change (Orbit can only show it, the storage couldn't take the change, it kept changing meanwhile, or the page that draws maps couldn't make the change) keeps the tag, and the sheet says so right there, naming the map when it's one (never a private one), and why.
  Home keeps showing the old tag's maps while some still have it, rather than following the tag to its new name.
  A map's tags are picked or added from its card's menu, or from its own menu while it's open, with Undo there.
- **Card menus:** present or study a map, pin, rename, tag, duplicate, share or delete it, and its Private switch.
  A deleted map comes back with Undo, and from the trash for 30 days; Home says so when a map can't go to the trash, or Undo can't bring it back, naming it as private maps are named.
  - **Renamed, tagged and copied as Orbit reads the map:** these go through Orbit's own reading of the map, as its screen would change it, so a map in markdown keeps every line but the ones that change, and one in YAML keeps every idea and what it holds (it's written as markdown from then on, as its screen writes it).
    Renaming changes the map's own title when it has one, and its center's name when that's the map's name.
    A version is kept before a rename and before new tags.
    A map Orbit can only show, made by a newer Orbit or one it can't read all of, is left as it is, and Home says why; a tag renamed in every map stays on those.
    So is one when the page that draws maps can't make the change (a WebView too old to draw maps, a page that stopped or didn't answer):
    Home says what didn't happen and why, over the map's tags while they're open, which show the map's tags again as they are, and Orbit stays open.
    A page that stopped, as Android frees memory, or that couldn't change one map, gets another try on a new page; when no page can make changes for a while (a WebView too old, a page that didn't load or didn't answer), a tag renamed in every map stays on the maps not yet changed, and the sheet says so; those changed before keep the change.
    A map's tags sheet keeps the tags just picked while they're on their way to its file, and follows the file when it changes elsewhere.
- **What's due:** a map studied in study mode says on its card how many of its cards are due ("5 to study"), counting, as a session does, only the ideas that were still cards when the map was last open on this device; it moves on at midnight, and as the phone's date, time or time zone changes.
  A locked private map's card doesn't say.
- **Private maps:** the Private switch in a map's menu, on its card or while it's open, keeps it for its owner, and turns that off again.
  Screen readers hear a switch, with its state.
  It opens with the phone's own fingerprint, face or screen lock, through Android's prompt:
  Orbit never sees any of them.
  One unlock opens every private map, until Orbit locks them again.
  - **Locked,** a private map's card keeps its shape and place, with an empty orbit and a lock where its picture was, and "Private map" for its name: no title, tags or progress.
    Each one's orbit has a color and planets of its own, made from its id and never from what it holds, so locked maps tell apart at a glance.
    Home's search doesn't find it, from the moment they lock, its tags aren't among Home's, and a picked tag leaves it out.
    A tap asks for the screen lock, and every private card gives way to its map's, the lock opening as the map's picture grows in.
    Unlocked, a private map's card has a small lock beside its pin, and Home's bar a lock that locks them all at once.
  - **A cover of its owner's:**
    "Cover while locked", beside the Private switch, gives a private map an emoji and a short name, such as "📓 Journal", shown only while it's locked, in place of "Private map": on its card (where screen readers hear "Private map, Journal, locked"), its lock, its widget and pinned shortcut, the trash, the folder's notes, and when another version of it arrives.
    Nothing of the map itself shows.
    It goes where being private goes, into backups and Export all, and a copy that stays private keeps it, with "(copy)" as its title has.
  - **Hidden instead,** as Settings can choose: locked, private maps aren't on Home at all, nor in its searches, tags or count.
    The open lock in Home's bar unlocks them and shows them again; it's there whether any private map is or not, so it gives nothing away, and never says how many.
    They fade away as they lock on screen and back as they're unlocked, and a Home where every map is private looks as a new one does.
    Nothing else gives them away either: quick capture, the share sheet's list and the widget's list leave them out and offer the same open lock, there whether any private map is or not; the trash leaves them out, and emptying it empties only what it shows; Settings doesn't count them in the trash, the folder's notes, or what exporting and importing say; and another version of one arriving from another device or in a file is asked about as a new map, whose Open asks for the screen lock first.
    Off by default: locked cards keep their place.
  - **Opened while locked** (from a shortcut, a widget, or beside the list on a tablet), a private map shows its lock first and asks at once, and nothing of it loads until it's unlocked; at a cold start too, before Orbit has read which maps are private, and after Android closed Orbit and brings the map's screen back.
    A map that locks while it's open is covered whole, its sheets, menus, dialogs and pictures full screen included, however it locks (from its menu, or Ctrl+L with a sheet open on a phone, as much as with Orbit away); locked from its menu or Ctrl+L with nothing open over it, a tablet keeps its list of maps beside the lock.
    It is just as it was once unlocked, but for its messages: an Undo for an idea just deleted goes as it locks, and the deletion stays; a sound of it that was playing stops, and its small player goes.
    The share sheet Orbit opened goes as well, while nothing was chosen in it, and so does a print dialog that hasn't printed: nothing of a private map stays offered to other apps or a printer over the lock.
    A share, save or print still being made as the map locks, its zip with videos say, opens nothing once it's ready, and the file made for it is deleted; so does a picture, video or sound shared from it full screen, and a computer it's open on, or the browsers it's presented in, get none of what was being made for them.
    Under its lock, screen readers and the keyboard can't reach it: none of the map's shortcuts work, nor does presenting, studying or undoing.
    The lock fits any window: in half a phone, in split screen, it stands alone above smaller words.
    What's typed into a private map, in its planets and its fields, asks keyboards not to learn from it, so its words don't come back as suggestions elsewhere; so do its title, tags and cover typed on Home, and ideas quick capture adds to it.
  - **Locking again:** when Orbit has been off screen for longer than Settings says (right away, after 1 minute, which is the default, 5 or 15 minutes, counted on a clock that setting the phone's time can't move), when the screen turns off, and from Home's lock or Settings, or Lock now: in a private map's menu and its card's on Home, the lock in the map's top bar on tablets, and Ctrl+L.
    Locking now is as Orbit being away: what's being typed on the map is saved first, the map goes behind its lock at once, and everything that closes as private maps lock closes.
    A new start of Orbit starts locked.
    A private map being presented stops as it locks: browsers watching hear it ended, and a TV or second screen goes back to waiting, a TV at once, even while a map was still on its way to it, which then never shows, while the phone shows the lock; unlocked, Orbit offers to present it again rather than going on by itself.
  - **Everywhere else:** a private map's widget shows only a lock and "Private map", and opens to its lock, and the preview in the launcher's list of widgets (Android 15 and later) never shows it, drawn again as a map is made private, however it was made private, and taken away when the launcher won't take a new one; a widget already showing a map shows its lock the moment it's made private; the widget's list and quick capture leave it nameless or out until unlocked, and quick capture can unlock from its list of maps.
    When the map quick capture was adding to, or added to last, is a private one that's locked, no map is chosen until one is picked or it's unlocked: ideas never quietly go to another map.
    It leaves the launcher's shortcuts, and one pinned to the home screen is renamed "Private map".
    Nothing outside the map says its name while it's locked: the trash shows "Private map" and when it was deleted, not how many ideas it has; sorted by title, Home puts locked cards after the others, by when they were made; another app that asks Orbit to open a map by its id gets a hidden private map only from Orbit's own shortcuts and widgets, which carry a mark of this install; the folder's notes in Settings count it as "a private map" (and its pictures and videos too big for it likewise), and a version of it arriving from another device or in a file is "another version of a private map", whose Update asks for the screen lock first (cancelled, Keep both is still there).
    Locking them takes away a message, or a rename, tags or send sheet on Home, that named one; and a message that comes once something's done doesn't name a private map that locked meanwhile:
    Undo for a map deleted from Home names it by its cover, if it has one, or as "a private map", and says nothing while they hide; named so, or about a map that isn't private, it names none of them, and stays as they lock, Undo and all; quick capture says its ideas were added, to no map named; and that a map is private now goes unsaid, as its card shows its lock.
    While something private is on screen, quick capture's and the widget's lists naming an unlocked private map included, as are Settings' folder notes and a dialog about another version of one, Recents gets no picture of Orbit: on Android 13 and later only that, and on Android 10 to 12 Orbit's window is kept from screenshots and screen casting too, which is the only way there, pictures and videos full screen included, and every sheet, quick capture's among them, from the moment something private shows, not only if it did as the sheet opened.
    Printing a private map shows it in Android's own print screen, which Orbit can't keep out of Recents:
    Recents may picture it there until the map locks.
  - **Honest about it:** private maps are locked in Orbit, not encrypted.
    Their files stay plain markdown, so a sync app, the folder they're kept in or a backup reads them as any other map, and Settings says so.
    Being private goes with a map's details, into backups and Export all, but not with the map itself: sent to another device, shared or synced through a folder, it arrives there as any other map.
    Details Orbit can't read, damaged ones say, count as private: such a map shows locked like any other private map, here, in backups and coming back from Export all, and its owner unlocks it and can make it not private.
    Export all and choosing a folder to keep maps in take every map out as it is, so while a private map is locked they ask for the screen lock first; while private maps hide, they ask whether any is there or not.
    A copy of a private map is private too, from the moment it's there: a duplicate, the folder's version kept beside it when both changed, a version from another device kept as well, a sync app's conflict copy of its file (Syncthing's `.sync-conflict-…`, or a "conflicted copy" from Dropbox or Nextcloud), and its file back in the folder after the map was deleted here, renamed there or not.
    A file new in the folder that may be a private map whose own file the folder can't find, as one renamed and changed on a computer (the same center, or most of its ideas), comes in private too, and Settings says which maps came in so and why; the others come in as they are.
    The folder maps are kept in updates a locked private map as any other, without asking for the screen lock first, on purpose: it's the owner's own sync, kept in step in the background, and asking would stall it until they unlocked.
    A version sent from another device or opened from a file does ask, since it comes from outside.
  - **Without a screen lock,** there's nothing to open private maps with: making a map private asks to set one first, in the phone's settings, and the map becomes private once there is one.
    Private maps on a phone whose screen lock was removed show unlocked, and Settings says how to lock them again.
- **Starting a map:**
  - a blank map;
  - one of eleven templates (project plan, study notes, talk outline, decision, SWOT, goals and more), each previewed live in its own style;
  - a markdown, YAML or zip file, from Open a file, or another app's Open with or Share, whatever type that app gives it (markdown is kept just as it's written, its lists too);
  - a map from another app, with its notes, links, pictures and folded branches:
    OPML, from outliners and most mind map apps, FreeMind and Freeplane (`.mm`), and XMind (`.xmind`, from XMind 8 on), where each sheet becomes a map of its own, the sheets all coming in together or, when one can't, none of them.
    They come from Open a file, Import maps in Settings, or another app's Open with or Share, and a note says what Orbit kept another way (XMind's floating topics, summaries, callouts and boundaries, extra links) or left out (icons past an idea's first, pictures the file doesn't hold, links to ideas it doesn't have);
  - text shared from any app, or opened as a plain text file (`.txt`): its first line goes in the middle, and the other lines around it, nested as they're indented, as pasting nests them (bullets and numbers go, to-dos stay to-dos, tabs count as four spaces).
    One line with everything else indented under it is the middle.
    With several lines at the top, the first one is, and the others keep their nesting around it, so a plain list makes the map it always has.
    A title shared with the text, such as an article's, names the map instead, and every line goes around it;
  - map files and text dragged onto the list of maps from another app beside Orbit, which come in as they would from Open a file or a share, several files at once too: those that are other versions of maps here are asked about one after another, as one file is ("2 of 3" above each question), then what the rest brought is said.
    A file opened with Orbit, or shared to it, comes in even when Orbit's screen is made again meanwhile, for a new language, say, or Android stops Orbit while it's read: it's read again as Orbit comes back.
    Update or Keep both, once answered, happens once, even when Android stops Orbit right after: it's finished as Orbit starts again.
    A map changed after Update was answered isn't written over: Orbit says so and asks again, and so it does when there's no room to take the map in, keeping its files until it's answered.
    A text file comes whole, up to the 8 MB a map's text may be, and a longer one is refused, saying so.
    A zone over the list says what letting go makes, quiet while the drag is elsewhere and lit up once it's over the list.
- **First launch:** one map, the welcome map, shows Orbit off: a planet for each thing it does, to try right there, with a picture, a video and a sound of its own, to-dos and a goal, a tiny deck to study, and a path ready to present as a quick tour.
  About in Settings opens it again, or brings it back.
  An update leaves the maps an earlier version started with, the samples among them, where they are.
