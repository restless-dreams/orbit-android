// When the map on the page can be changed. Nothing changes it, and nothing
// saves it, while it's locked: Orbit can't change it as it read it (see
// reading.ts), or the app keeps it from changes. Nor while it's an earlier
// version from its history, on show to be looked at: whatever the app asks
// then, the map's file stays exactly as it is.

/**
 * Why a map is only shown, never changed or saved: Orbit couldn't read all
 * of its text (`unreadable`), a newer Orbit wrote it (`newer`), or the app
 * says so: the map was deleted, or the app's files are a newer version's.
 */
export type Lock = 'unreadable' | 'newer' | 'deleted' | 'storage';

/** The app's commands that change the map. */
export const CHANGES: ReadonlySet<string> = new Set(['edit', 'editNew', 'add', 'update', 'addToGoal', 'checkNote', 'addFiles', 'removeFile', 'remove', 'move', 'duplicate', 'options']);

/** Whether the map can be changed and saved now: it isn't locked, and isn't an earlier version on show. */
export const changeable = (lock: Lock | null, showingVersion: boolean): boolean => {
  return lock === null && !showingVersion;
};

/** Whether the app's command of type `type` is refused now: it would change a map that can't be changed. */
export const refused = (type: string, lock: Lock | null, showingVersion: boolean): boolean => {
  return CHANGES.has(type) && !changeable(lock, showingVersion);
};

/** What came of a change the app asked for: made, with what it gave, or declined by Orbit, with why. */
export type Attempt<Value> = { made: true; value: Value } | { made: false; reason: string };

/**
 * Makes a change the app asked for. Orbit declines one with a value it can't
 * read (a picture without an address, say) with a `TypeError`, before it
 * changes anything, so the map stays as it was and the app is told why. Any
 * other error is a fault, and goes on up.
 */
export const attempt = <Value>(change: () => Value): Attempt<Value> => {
  try {
    return { made: true, value: change() };
  } catch (error) {
    if (error instanceof TypeError) {
      return { made: false, reason: error.message };
    }

    throw error;
  }
};
