# Between your devices

- **Send to a device:** from a map's menu or its card.
  Your other devices with Orbit open on the same Wi-Fi show up by name, with their address, found with Android's own network service discovery, so it works without Google services.
  One tap sends the map, encrypted end to end, so no one else on the Wi-Fi can read it on the way.
  The device says it's sending from that tap, while the map is made ready, and another tap on it sends nothing more; closing the sheet then leaves no copy of the map behind.
  The other device asks before taking it, then opens it where you left off, presentation path included, and the sender sees it opened: once the codes match, only that device can say so, never another on the Wi-Fi.
  The sheet says it sends a copy, with the map's pictures, videos, sounds and ideas' files counted (a sound is what an idea plays as one, so a voice memo's `.mp4` counts as audio), and the other device's question counts them the same way, and, while the maps aren't kept in a folder, that only a synced folder keeps maps in step between devices.
- **A check code:** before the map goes, both devices show four digits, and the map leaves only once you say they match.
  When they don't, another device on the Wi-Fi answered in its place: cancel, and nothing of the map leaves the phone.
  The other device shows the code with the map's sender, and asks again once the map arrives.
- **One answer:** the other device has three minutes to open, keep both or decline the map, and the sender hears which; a second tap changes nothing, and one that comes after the map's time ran out finds it declined.
  Declining a map still on its way, or closing the send sheet as it goes, stops it there and then, rather than send the rest for nothing; a Decline tapped just as the map arrives declines the map it became.
  A device that stops answering is waited for no longer than it would keep the map waiting, and closing the sheet stops waiting for it there and then.
- **Older versions:** a device with Orbit 0.2.0 or earlier sends maps before checking codes, so the two can't send maps to each other: each says which device to update, at most once every ten minutes, so a device offering again and again can't keep the phone's message up.
  An older Orbit since then still takes maps, and the dialog with the code says first what won't open there: the files of kinds it doesn't keep, which stay on this device (Orbit 0.3.0 has no sounds, and keeps only voice memos, unplayed), and a map it can only show, being written for a newer format than it reads.
  The map still goes once you say the codes match, and what such a device says of it, opened or not, is taken at its word.
  Orbit 0.4.0 and earlier take maps of up to 64 MB: a bigger map goes no further than saying hello, so its code never shows there, and the sender reads "It has an older Orbit, which can't take a map this big (180 MB).
  Update Orbit there, then send it again."
  This version takes maps of up to the gigabyte a map holds, and says so as it answers.
- **Maps keep their identity:** a map sent back and forth updates in place instead of piling up copies.
  An open map catches up at once, undo brings its earlier version back, and the version it replaced stays in the map's history.
  Its own pictures, videos and sounds stay as they are, with all their files say, and the ones it hasn't come in before its new text.
  "Keep both" makes a copy instead.
  A file of a map that's here asks again where it was if Android stops Orbit before it's answered (as it may while the screen lock is asked for, to update a private map), and what it brought waits only in Orbit's own files, never in a backup; started anew instead, Orbit lets it go.
- **Other ways:**
  - share it as a file, through Quick Share, Huawei Share, Bluetooth, email or a chat, which opens straight into Orbit, updating or adding; a zip of every map, from Export all, brings in all its maps;
  - open it on a computer: a Chromebook or laptop opens a short address in its browser to explore, present or download the map, which the send sheet's next step, with the address and a way back, says goes with its pictures and videos.
    The phone lists who opened it, and can ask for a PIN, limit how many at once, stop anyone new, or remove someone;
  - type the other device's address, shown in Settings as it is now, from whichever network the phone is on, where a network keeps devices from finding each other.
- **Your choice:**
  Settings can stop nearby devices from offering maps, and choose the name they see: the device's own until you change it.
- **Only the room:** the phone listens only while Orbit is open and there's a reason to (taking maps, a map open on a computer, presenting in browsers), only on the Wi-Fi, hotspot, cable or USB tethering, never over mobile data, only to other devices there, never to another app on the phone itself, and only at the address it shows, so a website can't reach it under a name of its own.
  Codes have six digits, and a device that types five wrong ones waits before it can try again, longer each time; browsers already in are never kept waiting.
  After a hundred wrong codes from every device together, new devices wait too, up to an hour, so guessing from address after address gets nowhere; a device on the Wi-Fi can use that to keep newcomers out for a while, and anyone in a room on a Wi-Fi they don't trust can be kept out the same way, which is the price of codes that can't be guessed.
  A picture, a form, a fetch or a frame on another website gets nothing of any room, joins no one and counts as no one's wrong code, in browsers that say where a request came from (all since Safari 16.4 and Firefox 90).
  A room's address opened from another site goes to a page that opens it again from the phone's own address, by itself once in a browser tab and after that with a tap, so a link in a chat or an email on the web still opens it, while a window another site opened and sends from code to code tries one by itself, and each after that only with the person's tap.
  A crowd, or a slow or stuck browser, never holds up the rest of the app, and a device that sends a byte at a time is cut off.
  A map arriving has to start coming within three minutes of the codes showing, and to have room on the phone before any of it comes: it opens as it arrives, straight from the connection, into its own files, so it needs room for itself once; one that runs out of room leaves nothing behind, and one cut short on its way can come again whole while it has time.
  The browsers in a room share one zip of the map, made once for each version of it, only once there's room for all of it.
  Whatever the phone was still doing for a device stops as it stops listening.
- **Keep maps in a folder:**
  Settings can keep every map as a markdown file in a folder you choose: on the phone, on an SD card, or a sync app's or cloud drive's folder.
  Android's own folder picker chooses it, so it works without Google services.
  - **Both ways:** an edit here is written to the map's file once it settles, and a file changed there, by a sync app such as Syncthing, on a computer or on another device, changes its map.
    An open map catches up at once, and its earlier text stays in its history.
  - **When:** as Orbit opens and closes, every two minutes while it's open, and on "Check the folder now".
    A map, or a step, the folder's app fails on is tried again by the next sync while the others go on, and a problem that lasts more than one sync shows on Home too, with "Try again" and a way to Settings.
    So do pictures, videos or sounds that go on failing to arrive from the folder for ten minutes, and Orbit's own record of the maps deleted here when it can't be read: the folder then waits, rather than bring a deleted map back.
    One deletion in it that can't be read whole doesn't stop the folder: it counts as a private map's, and a file that may be that map comes in private.
    Choosing the same folder again, as after restoring a phone, keeps Orbit's access to it past a restart.
  - **New and deleted:** a file new in the folder becomes a map.
    Deleting a map deletes its file, and the other way round, unless the map is open or changed since; a map deleted while the maps were kept in another folder takes its file there once they're kept there again, and one whose details are damaged still takes its file as it was.
    A deletion is kept before the map goes to the trash, so Orbit stopping right then never loses it, and a deleted map's file gone from the folder keeps its deletion until a sync ten minutes later agrees, as a map does; brought back from the trash after that, the map gets a file of its own.
    A file renamed there keeps its map.
    When more than three maps' files leave the folder at once (moved into a subfolder, say, or lost by a sync app), Home asks first: they go to the trash, or stay and are written back.
    Maps the folder sends to the trash show on Home too, with the way to the trash, and stay there until that's read, however long it takes; while private maps hide, "Got it" leaves theirs for when they show.
    A map that can't go to the trash right then stays, and goes at a later sync, rather than coming back to the folder and to the devices that deleted it.
    A deleted private map's file changed or renamed elsewhere comes back private even when a sync fails or Orbit stops as it comes in, or while it's too big to be a map until it's trimmed.
    So does a private map's file written back by a device that edited it after this one deleted it, and a renamed or changed copy of a private map the folder sent to the trash, for the ten minutes it would have waited, while that map's own file brought back elsewhere stays in the folder, private here.
  - **Nothing lost:** a map changed both here and there is kept twice, the folder's as "… (changed elsewhere)", named as Orbit names a map, markdown or YAML (one a newer Orbit made is kept just as it came, and only its file's name says it's a copy), once: if a sync stops right after, the next one writes this map's text rather than keeping both again.
    A folder with none of the maps' files left, emptied or swapped for another, gets them written again rather than every map deleted, once a sync ten minutes later agrees, so a folder still filling (a restored phone's, as its sync app catches up) isn't written over with older text.
    A map whose details came from another phone's backup waits a day, rather than ten minutes, for its file to come back before it follows it out.
    A folder with markdown files already asks before taking them in, and maps it has already, from another device, are followed rather than copied.
  - **Pictures, videos, sounds and ideas' files** go in a `media` folder beside the files, where the maps point at them.
    A copy there that this device's sync cut short is written again, even after Orbit stopped midway, and one still arriving there isn't taken until it's whole; another map's file that only holds the start of this one's (two ideas' "notes.txt", one longer) is another file, and stays as it is.
    One that reaches the folder after its map's text is fetched by a later sync.
    Each is copied a piece at a time, and only once this device has room for it and some to spare, both ways when the folder is on its storage, a copy written again over one cut short included: until then it waits, its map with it when it's going to the folder, and the sync says "A picture, video or sound needs more room than this device has free".
    A copy Orbit was writing when it stopped is written again whole, and never another device's file by the same name that only starts as it does.
    The folder has one `media` for every map, so when another map's file already has a picture's name there (as two maps from zips each with their "photo.jpg", of one size or not: what they hold tells them apart), the picture gets a name of its own ("photo-2.jpg"), one none of its map's files has either, in the folder and in its map.
    One there too big for a map (see [Your maps are safe](your-maps-are-safe.md#your-maps-are-safe)) stays only in the folder, and Settings says which, until it's replaced there or its map has room for it.
    A device with Orbit 0.4.0 or earlier keeps less of a map (50 MB a file, 90 MB a map), and each device's record in the folder says how much it keeps, so Settings names, beside such a device, the maps it keeps only part of:
    "Keeps only part of “Trip” and “Concert”: files over 50 MB, and what's past 90 MB a map, stay in the folder there.
    Update Orbit there to keep them all".
    A picture the folder's app renames as it's put there (some keep names in lower case, or number a name that's taken) keeps its map pointing at it, and a map with a file's very text but another picture by the same name gives its own a name of its own, so every device shows the same.
  - **Devices with other versions:** each device keeps a small record of itself in the folder's hidden `.orbit/devices/`: a random id made for its install, which names the record and is never backed up, so a new phone is a new device, its name, its Orbit, the newest map format it reads, the kinds of file it carries, and when it last synced (written again once a day at most while nothing else in it changes).
    No release reads that folder for maps.
    Settings lists the devices that use the folder with their versions, and what one with another Orbit can't open: files of a kind it doesn't carry, such as sounds (".mp3 and .ogg") for one from before them, while voice memos, `.mp4`s, go to every version, or maps saved for a newer format, which it only shows.
    A map Orbit writes there for a newer format than one of them reads is in Settings' notes, as one that device can only show.
    When that's so for files the folder holds, Home says so once for each new version, asking to update the older device.
    Devices show once they sync with this version of Orbit or a later one, and a record not written in three months is of a device that stopped using the folder.
    A device's own record that a newer Orbit wrote, which this one would misread, is left as it is.
