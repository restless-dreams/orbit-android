// A picture, video, sound or embedded page the app changes on a node. The
// app sends only what it changed of the node's own (a caption, a
// description, a live photo's motion), and the page merges that into the
// value the node has, so the fields the app doesn't know stay as they are:
// the ones Orbit reads, and the ones it keeps for a newer version or another
// app, which Orbit carries through edits of the same file. A value with
// another file's address is a new one, and takes nothing from the old.

/** The attributes that are a file on a node. */
export const MEDIA_KEYS: ReadonlySet<string> = new Set(['image', 'video', 'audio', 'embed']);

/** `value` without the fields set to null: those the app takes out. */
const withoutNulls = (value: Record<string, unknown>): Record<string, unknown> => {
  return Object.fromEntries(Object.entries(value).filter(([, field]) => field !== null));
};

/**
 * The value a node's picture, video, sound or embed takes from the app's
 * `change` to it, given the node's `current` one: `change` merged into
 * `current` when it's for the same file (it names no `src`, or `current`'s),
 * with the fields it sets to null taken out; `change` alone for another file;
 * undefined when it names no file and the node has none to change.
 */
export const mergedMedia = (current: unknown, change: Record<string, unknown>): Record<string, unknown> | undefined => {
  const own = current !== null && typeof current === 'object' ? current as Record<string, unknown> : null;

  if (typeof change.src === 'string' && change.src !== own?.src) {
    return withoutNulls(change);
  }

  if (!own) {
    return undefined;
  }

  return withoutNulls({ ...own, ...change });
};
