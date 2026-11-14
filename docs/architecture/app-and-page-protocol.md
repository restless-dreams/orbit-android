# App and page talk in JSON

- **App and page talk in JSON.**
  - The page says hello on a `WebMessageListener` channel limited to its own origin and its main frame, and talks to the app on it (`MapMessage` in `page/Protocol.kt`).
  - The app sends its commands back on the same channel, to the frame that said hello (`MapCommand`): the page reads them as JSON, so nothing the app sends, text from other apps included, is ever run as code.
  - Jobs such as pictures and conversions carry a number that their answer returns.
    A job is let go once it's answered, given up on after a minute, or no longer wanted by whoever asked, as a thumbnail that scrolled away; one the page already has, the app then calls off (`cancel`): the page aborts its pictures' loading and stops at its next step, before it reads or draws the picture on a canvas, and answers nothing (`working` in `web/bridge.ts`).
  - A change whose outcome the app says something about (a sketch kept, a picture or files copied in) is an `update`, or `addFiles`, with a job: the page answers it once the idea took the change, its save posted first, so the app has the map with it by then; false for an idea the map doesn't have now (removed, or renamed meanwhile), a file `onto` no longer names, or a map that can't change now; and Orbit's reason, rather than `declined`, when Orbit declined it.
    A page that stopped, or answers too late, may yet have made it, so the app keeps its files for the tidying up rather than delete them.
  - Commands sent before the page says hello wait for it, only as long as they matter (`WaitingCommands`): a page that didn't load takes none, and a map loaded or unloaded makes the commands about the one before it moot.
  - A page that never says hello, or never says the map it was sent is ready, is given up on as one that didn't load (`OrbitPage`'s watchdogs, on the main thread's handler, since the spare page is in no window): 90 seconds from its start, 15 once WebView says it has loaded (its script says hello as it runs), and 90 from the map reaching it to its `ready` or `error`, counting only time Orbit is in sight (`SightedWait`, following `ProcessLifecycleOwner`):
    Android may freeze Orbit and its pages while it's away, and a page frozen halfway isn't late.
    What waits for it fails, the map's screen offers Load it again, and what the page says from then on goes nowhere, to the page's listener or the screen's state, so a map ready after all never shows, nor takes edits the page would no longer save right.
    A page destroyed meanwhile is left alone: its watchdogs stop with its view.
  - After every change the page sends the whole map: each node with its attributes, its custom ones included, and the color it's drawn with (Orbit's `getNodeColor`).
  - A map's numbers reach the app as numbers its types hold.
    Orbit takes any number for how many levels a map shows, an idea's priority and the version of a kind of idea (`maxDepth: 1.5`, `priority: 1e12`, `goal@99999999999`), and a message with one the app's `Int` can't hold would be lost whole: the map never ready, or a save with its edit.
    So the page sends each as the whole number nearest what it means, within an `Int`'s range (`web/whole-numbers.ts`: 1.5 levels show what 2 do, and a version past the range is still newer than any the app knows), and the app reads any number there (`MapWholeNumberSerializer`).
    The map's text keeps them as they were written.
  - Both sides are held to one set of examples:
    `web/protocol-examples.ts` has one of each command and message, held to the pages' own types with a key for every kind, and the pages' build writes them into `app/src/test/resources/page-protocol.json`.
    `ProtocolTest` checks that the app writes each command exactly so, reads each message with all it says, and knows the same kinds.
    A message the app still can't read is an error in the log of every build, by its kind alone.
  - The app changes a goal's numbers by merging them into the idea's custom attributes, and steps it with `addToGoal`, which adds to the number the page has, so quick steps all count.
    A step adds as decimals do, counting in the smallest unit either number is written in, but numbers too large to count that way exactly add as they are, and a step up that would pass what readers take (1e15) leaves the number where it is (`stepGoal`).
    The editor's fields save only what's typed (`NumberField`): a number with more decimals than they show stays as it's written, and one typed rounds as the map keeps it before it's checked against that bound (`GoalNumbers.read`).
    Their drafts are kept by the editor's opening, not the idea's id, so a new name that gives the idea a new id keeps them, and they save on it.
  - A box of a note's checklist checked in the details goes as `checkNote`, the idea and the box's line in its note, and the page checks it in the note it has (Orbit's `setNoteCheckbox`), so quick presses all count and the file changes on that line alone:
    Orbit writes a note's changed lines in place when it can.
    One no longer on its line, as after the note changed, is `declined`.
    Until a save shows the box so, the app shows it as it was pressed (`MapUi.noteChecks`), and that save's message offers Undo while it's still the last change.
  - An earlier version from the history is drawn with `preview`, and the page takes no change and saves nothing until the map as it is now comes back (`web/changing.ts`): an editable `preview` of it, a `restore` or a `load`.
    The app brings it back itself before any change it sends (`showNow`), text typed in the source editor included, and closes the history before what takes the map over (`closeHistory`), so a change always lands on the map as it is now; the page takes `source` for the map as it is now too.
    While a version is on show, the app keeps the map's state as it is now, and takes only whether it can be changed from what the page says, for the screen; whether the map as it is now can be is kept apart (`readOnlyNow`).
    Restoring a version waits for text typed in the source editor a moment before, which goes into the history with the map as it was, and the editor then shows the version (`restoreVersion`), so what's typed next is typed on it.
  - A picture's, video's, sound's or page's caption, description or live photo's motion goes to the page alone (`mediaChange`, `motionChange`), and the page merges it into the node's own value (`web/media.ts`): every other field stays as it is, those Orbit reads and those it keeps for a newer version or another app, which it carries through edits of the same file.
    Only another file is a whole new value.
    A motion goes `onto` the picture it was copied in for, and the page leaves it once that picture was replaced (`stillOnto`).
    The app keeps the latest picture too: a picture copied in, from the phone or a drop, takes a turn on its idea (`map/PictureTurns.kt`), and a movie notes the turn there is; another picture set there since, or the picture taken off, ends it (a caption or description doesn't), so one that finishes late stays out, and its copy goes (`takeBack`).
