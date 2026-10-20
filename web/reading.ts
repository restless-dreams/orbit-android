// Whether a map's text, as Orbit read it, can be changed and saved without
// losing any of it. The map's page locks a map it can't change that way, and
// says why: the app tells people to update Orbit for a map a newer Orbit
// wrote, and to fix the text when Orbit couldn't read part of it.

import type { OrbitDocument } from '@restless-dreams/orbit';

/** What reading a map's text found: whether it can be changed without losing any of it. */
export interface Reading {
  lock: 'unreadable' | 'newer' | null;

  /** What Orbit couldn't read, or why it can't change the map, in its words. */
  problem: string | null;

  /** Orbit found no ideas at all in text that isn't empty: the page says so in the app's words, in the language it has when it says it. */
  noIdeas?: boolean;
}

/**
 * Whether the map in `text`, as Orbit read it, can be changed and saved
 * without losing any of the text: not when a newer Orbit wrote it, nor when
 * Orbit couldn't read part of it (an error, or text it found nothing in).
 * Such a map is only shown. Orbit reports a map from a newer Orbit as an
 * error too, so that comes first: updating Orbit is what to do, whatever
 * else this one couldn't read of it. Text without headings or lists can be
 * changed: it's the note of a map with no ideas yet, and saving keeps it.
 */
export const readingOf = (parsed: OrbitDocument, text: string): Reading => {
  const diagnostics = parsed.diagnostics ?? [];
  const newer = diagnostics.find((item) => item.code === 'unsupported-schema-version');

  if (newer) {
    return { lock: 'newer', problem: newer.message };
  }

  const error = diagnostics.find((item) => item.severity === 'error');

  if (error) {
    return { lock: 'unreadable', problem: error.message };
  }

  if (parsed.root.text.trim() === '' && parsed.root.children.length === 0 && text.trim() !== '') {
    return { lock: 'unreadable', problem: null, noIdeas: true };
  }

  return { lock: null, problem: null };
};
