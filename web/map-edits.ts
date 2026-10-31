// Changes made to a map from outside its screen: a new title or new tags
// from Home, and the title of a copy. They go through Orbit's own reading of
// the map, markdown or YAML, as the map's screen makes them, and the map is
// written back as orbit-md, as its screen writes it: only what changed is
// written again in markdown. A map Orbit can only show (a newer Orbit wrote
// it, or it can't read all of it) is never changed, and the app hears why.

import { createMapTools, parseOrbit, serializeOrbitMarkdown } from '@restless-dreams/orbit';
import type { OrbitDocument } from '@restless-dreams/orbit';
import { withTags } from './look';
import { readingOf } from './reading';
import type { Reading } from './reading';

/** What the app asks to change; null leaves it as it is. */
export interface MapEdit {
  title: string | null;
  tags: string[] | null;
}

/** The map with the change made, or why it wasn't: Orbit can only show it. */
export interface EditedMap {
  markdown: string | null;
  refused: Reading['lock'];
}

/**
 * The map with `title` as its name. A map's name is its own title, when it
 * has one (in its frontmatter, or at the top of YAML), and its center's text
 * otherwise, as its screen says. So the title changes, and the center with
 * it when the center is named by it, as Orbit names the one it makes up for
 * several top ideas; a center with a name of its own keeps it. Without a
 * title, the center is renamed as its screen renames it.
 */
const retitled = (document: OrbitDocument, title: string): OrbitDocument => {
  const own = document.options.title;
  let edited = document;

  if (own === undefined || document.root.text === own) {
    const tools = createMapTools(document);
    tools.run('update_node', { id: document.root.id, text: title });
    edited = tools.document ?? document;
  }

  return own === undefined ? edited : { ...edited, options: { ...edited.options, title } };
};

/** `text` with `edit` made, as orbit-md; `untitled` names the center Orbit makes up, in the app's language. */
export const editedMap = (text: string, edit: MapEdit, untitled: string): EditedMap => {
  const parsed = parseOrbit(text, { untitled });
  const { lock } = readingOf(parsed, text);

  if (lock !== null) {
    return { markdown: null, refused: lock };
  }

  let document: OrbitDocument = parsed;

  if (edit.title !== null) {
    document = retitled(document, edit.title);
  }

  if (edit.tags !== null) {
    document = withTags(document, edit.tags);
  }

  return { markdown: serializeOrbitMarkdown(document), refused: null };
};
