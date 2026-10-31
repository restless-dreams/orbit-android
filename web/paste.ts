// Text pasted onto a map, as orbit-md for Orbit to read. A map or an idea copied from Orbit, or
// any markdown that starts with a heading or frontmatter, is read as it is. Anything else becomes
// a list: an idea a line, nested as the lines are indented, without the bullets or numbers they
// had, and an address on a line of its own becomes an idea that opens it. Text that makes a new
// map (mapFromText), and ideas caught on the go (caughtIdea), take the same shape.

import type { OrbitNewNode } from '@restless-dreams/orbit';

export interface PastedText {
  /** The text as orbit-md. */
  markdown: string;

  /**
   * Whether the map it makes is itself the idea to paste, as an idea copied from Orbit is,
   * rather than a holder for the ideas under it.
   */
  whole: boolean;
}

/** A line of plain text as an idea: how deep it goes, and its text, without the bullet or number it had. */
interface OutlineLine {
  depth: number;
  content: string;
}

/** An idea of a new map, with the ideas under it. */
interface OutlineIdea {
  content: string;
  children: OutlineIdea[];
}

/** A list item's bullet or number, as notes apps and word processors copy them. */
const BULLET = /^(?:[-*+•◦▪‣·]|\d{1,3}[.)])\s+/u;

/** A to-do's box, as markdown writes it: the middle of a map is no to-do. */
const CHECKBOX = /^\[[ xX/]\]\s+/;

/** An address on its own, one markdown can make a link of: no spaces, parentheses, quotes or brackets. */
const ADDRESS = /^https?:\/\/[^\s()<>"'\\[\]]+$/i;

const HEADING = /^#{1,6}\s/;
const TOP_HEADING = /^#\s/;
const TAB_WIDTH = 4;

/** The longest a map's name gets from text (Importer's TITLE_LENGTH); a longer line is kept whole as the map's note. */
const TITLE_LENGTH = 80;

/** The longest an idea caught on the go gets (QuickCapture's); a longer one is kept whole as its note. */
const CAUGHT_LENGTH = 200;

/** How far a line is indented, a tab counting as four spaces. */
const indentWidth = (indent: string): number => [...indent].reduce((width, character) => width + (character === '\t' ? TAB_WIDTH : 1), 0);

/** A line as an idea's title: an address becomes a link named without "https://" and "www.". */
const title = (text: string): string => {
  if (!ADDRESS.test(text)) {
    return text;
  }

  const name = text.replace(/^https?:\/\//i, '').replace(/^www\./i, '').replace(/\/$/, '');
  return `[${name}](${text})`;
};

/** Text's lines, with Windows' and old Macs' line endings read too, and no spaces left at their ends. */
const linesOf = (text: string): string[] => text.replace(/\r\n?/g, '\n').split('\n').map((line) => line.replace(/\s+$/u, ''));

/** Whether text is markdown that's a map of its own: it starts with a heading or frontmatter. */
const isDocument = (lines: string[]): boolean => {
  const first = lines.find((line) => line.trim() !== '') ?? '';
  return first === '---' || HEADING.test(first);
};

/**
 * Plain text's lines as an outline. Each line goes as deep as its indent:
 * deeper than the line above, it's under it, and a line indented less than
 * the one above, but more than the one before, is under the one before.
 */
const outlineOf = (lines: string[]): OutlineLine[] => {
  const widths: number[] = [];
  const outline: OutlineLine[] = [];

  for (const line of lines) {
    const content = line.trimStart();

    if (content === '') {
      continue;
    }

    const width = indentWidth(line.slice(0, line.length - content.length));

    while (widths.length > 0 && width < widths[widths.length - 1]) {
      widths.pop();
    }

    if (widths.length === 0 || width > widths[widths.length - 1]) {
      widths.push(width);
    }

    outline.push({ depth: widths.length - 1, content: content.replace(BULLET, '') });
  }

  return outline;
};

/** An outline's ideas as a markdown list, nested as they are. */
const listOf = (ideas: OutlineIdea[]): string[] => {
  const lines: string[] = [];
  // A map can go thousands of ideas deep: a list kept by hand, rather than a call for each level.
  const waiting = ideas.map((idea) => ({ idea, depth: 0 })).reverse();

  while (waiting.length > 0) {
    const { idea, depth } = waiting.pop()!;
    lines.push(`${'  '.repeat(depth)}- ${title(idea.content)}`);

    for (let index = idea.children.length - 1; index >= 0; index -= 1) {
      waiting.push({ idea: idea.children[index], depth: depth + 1 });
    }
  }

  return lines;
};

/** `text` cut to at most `length` characters at a word's end, with "…" when it was cut, as the app shortens names (MapText.shorten). */
export const shorten = (text: string, length: number): string => {
  const single = text.replace(/\s+/g, ' ').trim();

  if (single.length <= length) {
    return single;
  }

  const cut = single.slice(0, length - 1);
  const space = cut.lastIndexOf(' ');
  return `${(space >= length / 2 ? cut.slice(0, space) : cut).replace(/[ ,;:.-]+$/, '')}…`;
};

export const pastedText = (text: string): PastedText => {
  const lines = linesOf(text);

  // Markdown that's a map of its own: one top heading is the idea pasted; with more or none, they're the ideas.
  if (isDocument(lines)) {
    const tops = lines.filter((line) => TOP_HEADING.test(line)).length;
    return { markdown: `${lines.join('\n')}\n`, whole: tops === 1 };
  }

  const items = outlineOf(lines).map(({ depth, content }) => `${'  '.repeat(depth)}- ${title(content)}`);
  return { markdown: `# Pasted\n\n${items.join('\n')}\n`, whole: false };
};

/**
 * A new map from text, with its branches as the text nests them, as pasting
 * makes them (see `pastedText`). One line with everything else indented
 * under it is the map's middle. Otherwise the first line is, and the other
 * lines go around it, nested as they are among themselves. With `subject`
 * (the title an article is shared with, say), that's the middle and every
 * line goes around it. A middle too long for a name is named by its start,
 * and kept whole as the map's note. Markdown that's a map of its own is kept
 * as it is.
 */
export const mapFromText = (text: string, subject: string | null = null): string => {
  const lines = linesOf(text);

  if (isDocument(lines)) {
    return `${lines.join('\n').replace(/\n+$/, '')}\n`;
  }

  // Each idea under the nearest line above it that's less deep: an outline's depths grow a level at a time.
  const roots: OutlineIdea[] = [];
  const open: OutlineIdea[] = [];

  for (const { depth, content } of outlineOf(lines)) {
    const idea: OutlineIdea = { content, children: [] };
    open.length = depth;
    (depth === 0 ? roots : open[depth - 1].children).push(idea);
    open.push(idea);
  }

  const named = subject?.replace(/\s+/g, ' ').trim() || null;
  const middle = roots[0];

  if (!middle && !named) {
    return '';
  }

  // The first line names the map when it's the subject too.
  const byLine = !named || (middle !== undefined && middle.content === named);
  const name = byLine ? middle.content.replace(CHECKBOX, '') : named;
  const ideas = byLine ? [...middle.children, ...roots.slice(1)] : roots;

  // An address stays a link the map opens; any other name too long is cut, and kept whole as the map's note.
  const single = name.replace(/\s+/g, ' ').trim();
  const heading = ADDRESS.test(single) ? title(single) : shorten(single, TITLE_LENGTH);
  const note = heading !== single && !ADDRESS.test(single) ? `\n${single}\n` : '';
  const list = ideas.length > 0 ? `\n${listOf(ideas).join('\n')}\n` : '';
  return `# ${heading}\n${note}${list}`;
};

/**
 * An idea caught on the go, and the ones under it, as it joins a map: a
 * name kept to one line of single spaces, and one too long for a name cut
 * at a word's end, kept whole as its note.
 */
export const caughtIdea = (idea: OrbitNewNode): OrbitNewNode => {
  const text = idea.text.replace(/\s+/g, ' ').trim();
  const children = idea.children?.map(caughtIdea);
  // A note the idea had stays, after the whole name.
  const shortened = text.length > CAUGHT_LENGTH ? { text: shorten(text, CAUGHT_LENGTH), note: idea.note ? `${text}\n\n${idea.note}` : text } : { text };
  return { ...idea, ...shortened, ...(children ? { children } : {}) };
};
