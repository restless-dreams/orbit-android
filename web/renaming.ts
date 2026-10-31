/**
 * An idea's new id, when new text gives it one: Orbit gives an idea an id
 * from its text, unless the map names one, so typing a new name changes it.
 * The app keeps things about an idea by its id (its place on the path, its
 * rehearsal's time, how its card is going in study mode), and moves them to
 * the new one when a save says so (`renamed`).
 */
import type { OrbitNode } from '@restless-dreams/orbit';

/** A node that got a new id from new text, so the app can follow it. */
export interface Renamed {
  from: string;
  to: string;
}

/**
 * Tells which idea an edit gave a new id: the one the app changed, or the
 * one whose text was just typed on the map. Orbit closes the field for
 * typing on the map before it changes the idea, with the idea as it was,
 * and the edit that follows is the same idea with what was typed, so only
 * that one edit, of that very idea, is taken for the field's.
 */
export const renamings = () => {
  let typedInto: { node: OrbitNode; id: string } | null = null;

  return {
    /** The field for typing on the map closed, on `node`, or on a new idea (null). */
    closed: (node: OrbitNode | null): void => {
      typedInto = node ? { node, id: node.id } : null;
    },

    /** What an edit of `kind` to `node` renamed, if anything: `updating` is the id of the node the app changed, if it did. */
    edited: (kind: string, node: OrbitNode | null, updating: string | null): Renamed | null => {
      const before = updating ?? (node && typedInto?.node === node ? typedInto.id : null);
      typedInto = null;
      return kind === 'update' && node && before && node.id !== before ? { from: before, to: node.id } : null;
    },
  };
};
