// The numbers a map writes that the app reads as whole ones, in its Kotlin
// Int: how many levels it shows, an idea's priority, the version of a kind
// of idea. Orbit takes any number for them (`maxDepth: 1.5`, `priority:
// 1e12`, `type: goal@99999999999`), and a message with a number the app's
// Int can't hold never reaches it: a map that doesn't get ready, a save
// lost with the edit in it. So each goes to the app as the whole number
// nearest what it means there, within an Int's range. The map's text keeps
// it as it was written.

/** The largest whole number the app holds (Kotlin's `Int.MAX_VALUE`): more levels, and a newer version, than any map has. */
export const LARGEST_WHOLE = 2_147_483_647;

/** The smallest whole number the app holds (Kotlin's `Int.MIN_VALUE`). */
export const SMALLEST_WHOLE = -2_147_483_648;

/**
 * `value` as a whole number the app holds: made whole by `whole` (up, with
 * `Math.ceil`, for a level or a version, where one past a whole one counts as
 * the next, as Orbit counts them; `Math.round` for a priority), and past an
 * Int's range, the nearest end of it. Null when it's no number.
 */
export const wholeNumber = (value: unknown, whole: (value: number) => number): number | null => {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return null;
  }

  return Math.min(Math.max(whole(value), SMALLEST_WHOLE), LARGEST_WHOLE);
};
