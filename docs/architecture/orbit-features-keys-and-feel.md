# Orbit's features, keys and native feel

- **Orbit's own features, used as they're meant to be:**
  - `editable` with `longPressDelay` set to Android's long-press timeout;
  - `focus` to reopen a map where you left it;
  - `hideChildren` and `revealChildren` for study mode's cards;
  - node types with `render` hooks for people, progress rings and goals.
    Orbit draws again only the nodes an edit touched, so the app draws its rings again in place after each edit, for the Progress idea above a goal, moving to the new share;
  - `nodeTypeFor`, which draws ideas with to-dos or goals under them as Progress ideas without writing the kind into the map, on the map, the audience's screens and web pages alike;
  - `showActionButtons: false`, with the idea's buttons in the native toolbar instead, and `onAction` taking over the detail sheet for the native one;
  - `insets`, so the map fits around the toolbar, the rail, sheets and captions;
  - `baseUrl` serving each map's pictures from its folder;
  - themes and palettes from the frontmatter, and `serializeOrbitMarkdown` and `serializeOrbitYAML` for files.
- **Keys go where they belong:**
  - The map's page gets the keys first, and the app's shortcuts take what it leaves (`WebViewClient.onUnhandledKeyEvent`).
    So typing in an idea keeps Ctrl+Z for its text.
  - Shortcuts no text field uses, such as Ctrl+F, the views and Ctrl+/, are caught before anything else.
  - The page keeps its own focus when the view takes the keyboard's (`setNeedInitialFocus(false)`).
    The map takes the keyboard's focus back whenever nothing covers it, even after the layout moves it.
  - **Views with keys of their own** (a video, the pictures and sounds full screen, a presentation, a study session) leave Space and Enter to a button Tab went to (`ui/ViewKeys.kt`).
    The media views hear the other keys before their buttons do and Space and Enter after, when nothing focused took them (`viewKeys`), so a picture or a slider with the focus still plays and pauses.
    A presentation and a study session hear every key first, before Android's touch mode spends one, and let Space and Enter by while one of their controls has the focus; the map under them takes none, so Tab goes from control to control.
    Controls that go by themselves stay while one of them has the focus (`ControlsStepAside`): gone, they'd take the focus with them, and the keys would go nowhere.
    Once they've gone, Tab or Shift+Tab with the focus on the view brings them back and moves on to the first or the last of them, a frame later, once they're laid out (`PresentationFocus.onTab` in a presentation).
- **Native feel, on purpose:**
  - **The page is transparent:**
    Compose paints behind it, so there's never a white flash.
  - **Notes are drawn natively** (`ui/MarkdownNote`), with the markdown renderer's own pieces for text, lists and pictures, and the note's for tables and checklists: a table is a layout of its own (`ui/NoteTable`), each column as wide as its cells on one line up to a cap, scrolling sideways when it's wider than the note, and a list with checkboxes (`ui/NoteChecklist`) makes each item one row that toggles, as a Material checkbox with its words.
    Neither reads alignment lines as it measures, so both lay out in a pane's lookahead pass on a tablet.
    The editor's preview checks a box in the note being written (`withNoteBox`), saved with it.
  - **Messages over sheets:** while a panel is a sheet over the map, the map's messages show over it (`LocalSheetMessages`), as the map's own place for them is under the sheet: in the sheet's own window, after its content, so a keyboard's Tab and TalkBack reach their Undo with the sheet's buttons (a window of their own couldn't take focus), at the foot of the screen, risen from the sheet's own foot when it stops partway below the screen (`WithSheetMessages`).
    A sheet taken away at once, as the search's is when a result is picked, never says it's on its way out: once no panel is over the map (`MapLayout.panelOver`), the map forgets where the sheet rested (`rememberSheetTop`), and its messages and their Undo show over the map again.
  - **A panel's field above the keyboard:** on a phone on its side the keyboard takes most of the height (261 dp of 411 on a Pixel), and leaves less room under the top bar than a panel beside the map takes for its title alone.
    While the search's field there is in use (`keptAboveKeyboard`), the panel rises over the top bar with the keyboard, drawn over it, only as far as the field needs (`PanelBeside`, `panelRise`): up to the status bar, and on a smaller phone under it too.
    It settles back as the keyboard goes, and the map stays as it is.
  - **No web tells:** no text selection, callouts or tap highlights.
  - **Haptics:**
    Orbit's spin haptics play native clock ticks, and long presses and new ideas get their own.
  - **Back:** predictive back on every screen.
  - **One of several choices** is Material 3 Expressive's connected buttons when there are a few (`ui/Choices`, `ConnectedChoice`), which become a list of radio buttons when their words don't fit on one line, and chips when there are many (`ChoiceChips`).
    TalkBack hears both as radio buttons in a group.
    Material's filter chips are checkboxes to it, so `ChoiceChips` sets the radio role outside the chip's own, which Compose applies last, as the editor's Status and Kind chips do (`NodeEditor`'s `Choice`).
  - **The keyboard:** it comes up when a menu opens the field for typing, and the map makes room for it at once, as it does under a panel that lifts the map (`MapLayout.roomForKeyboard`).
    A keyboard for any other field isn't the map's, and the map stays as it is: the search's, over the map or beside it, the map's text, a sketch's description, or a sheet or dialog with a field of its own (an icon's search, the map's tags).
    The map's page drawn anew at another size, just as the first keys go in, held Orbit up for seconds on a slow phone.
    That keyboard stays someone else's until it has gone (`keyboardElsewhere`), so the search closing with it up doesn't make room for it for a moment, then take it back.
    The search folds the map's text once for the map, and searches each key away from the main thread (`SearchIndex`).
    Search pressed on the keyboard before the search for what's typed has ended opens its first match once it has, rather than folding the map on the main thread for it.
  - **The search and the outline are kept above their panels** (`MapSearch`, `rememberMapSearch`; `MapOutline`, `rememberMapOutline`): what's typed, the folded ideas, what was found, the outline's folds, and where each list is (`ListPlace`).
    Turning the screen moves a panel between a sheet over the map and the map's side, where it's made anew, and each goes on where it was; closed, each starts afresh.
    Search pressed, then the words changed in any way, even back to the same words or cleared and typed again, opens nothing once the search ends.
  - **Times and dates** follow the phone's 12- or 24-hour clock, its time zone and its language (`ui/TimeWords`), the clock and the zone as they change while they're on screen (`rememberTimeWords` hears Android say so):
    Android's pattern for the fields wanted (`DateFormat.getBestDateTimePattern`), since java.time's localized styles ignore the clock setting.
    A pattern ICU had to pad (`├…┤`), or one without the fields asked for, gives way to a plain one, and a calendar or hour cycle in the language's tag changes nothing.
