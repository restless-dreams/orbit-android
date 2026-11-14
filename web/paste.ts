// Text pasted onto a map, as orbit-md for Orbit to read. A map or an idea copied from Orbit, or
// any markdown that starts with a heading (`#`, or a line underlined with `===` or `---` with more
// markdown after it) or frontmatter, is read as it is. Anything else becomes a list: an idea a
// line, nested as the lines are indented, without the bullets or numbers they had, and an address
// on a line of its own becomes an idea that opens it; a line that only underlines or rules off is
// none. Text that makes a new map (mapFromText), and ideas caught on the go (caughtIdea), take the
// same shape.

import type { OrbitDocument, OrbitNewNode, OrbitNode } from '@restless-dreams/orbit';

export interface PastedText {
  /** The text as orbit-md. */
  markdown: string;

  /**
   * Whether it's markdown that's a map of its own, as written, rather than a list made of plain
   * text's lines: the map Orbit reads from it may then be rooted at the idea to paste, as an idea
   * copied from Orbit is (see `pastedIdeas`).
   */
  document: boolean;
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
const TAB_WIDTH = 4;

/** A line under a heading's text that makes it one (a setext heading), as Orbit reads it: `===` for a top heading, `---` for one under it. */
const UNDERLINE = /^ {0,3}(?:=+|-+)\s*$/;

/** A heading written with `#`, as Orbit reads one. */
const HASH_HEADING = /^ {0,3}#{1,6}\s/;

/** A list item, as Orbit reads one: a bullet or a number, then its text. */
const LIST_ITEM = /^\s*(?:[-*+]|\d{1,9}[.)])\s+\S/;

/** A rule, as Orbit reads one: three or more of `-`, `*` or `_`, spaced or not. Orbit reads `* * *` as one, not as a list item. */
const RULE = /^ {0,3}([-*_])(?:\s*\1){2,}\s*$/;

/**
 * A line Orbit reads as something other than a paragraph's text, so no underline makes a heading
 * of it: a heading, a list item, a quote, a rule, a code fence, a comment, a picture or a link on
 * a line of its own.
 */
const NOT_A_PARAGRAPH = [
  HASH_HEADING,
  LIST_ITEM,
  /^\s*>/,
  RULE,
  /^ {0,3}(?:```|~~~)/,
  /^\s*<!--/,
  /^\s*\[?!\[[^\]]*\]\([^)]*\)(?:\]\([^)]*\))?\s*$/,
  /^\s*\[[^\]]*\]\([^)]+\)\s*$/,
];

/** A line of plain text that only underlines or rules off the lines around it, as `=====`, `-----` or `* * *` do: no idea of its own. */
const RULE_LINE = /^(?:=+|-+|(?:[-*_]\s*){3,})$/;

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

/** Whether `lines[at]` is one line of text with `===` or `---` right under it: a heading, as Orbit reads one (a setext heading). */
const underlinedAt = (lines: string[], at: number): boolean =>
  at + 1 < lines.length && lines[at].trim() !== '' && UNDERLINE.test(lines[at + 1]) && !NOT_A_PARAGRAPH.some((block) => block.test(lines[at]));

/**
 * Whether the lines from `from` on hold more markdown: a heading written with `#`, a list item (a
 * rule is none), or another underlined heading, after a blank line.
 */
const moreMarkdown = (lines: string[], from: number): boolean => {
  for (let index = from; index < lines.length; index += 1) {
    const line = lines[index];

    if (HASH_HEADING.test(line) || (LIST_ITEM.test(line) && !RULE.test(line)) || (lines[index - 1]?.trim() === '' && underlinedAt(lines, index))) {
      return true;
    }
  }

  return false;
};

/**
 * Whether text is markdown that's a map of its own: it starts with a heading or frontmatter. A
 * heading underlined with `===` or `---` counts when it's one line of text, as headings are, and
 * more markdown follows it: plain text underlines a line too, a title over lines or a name over
 * a signature, and its lines are ideas, as they always were.
 */
const isDocument = (lines: string[]): boolean => {
  const start = lines.findIndex((line) => line.trim() !== '');

  if (start < 0) {
    return false;
  }

  const first = lines[start];
  return first === '---' || HEADING.test(first) || (underlinedAt(lines, start) && moreMarkdown(lines, start + 2));
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

    if (content === '' || RULE_LINE.test(content)) {
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

/**
 * How much of `text` is kept to end by `end`: up to the last whole character, as people see one, that ends by it
 * (Unicode's grapheme clusters): an emoji with its skin tone, a family joined by zero-width joiners, a flag, a
 * letter with its accent. A cut through one would leave half of it, or another one.
 */
const wholeCharactersTo = (text: string, end: number): number => {
  let kept = 0;

  for (const { index, segment } of new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text)) {
    if (index + segment.length > end) {
      break;
    }

    kept = index + segment.length;
  }

  return kept;
};

/**
 * `text` cut to at most `length` characters at a word's end, with "…" when it was cut, as the app shortens names
 * (MapText.shorten): both hold to the cases in app/src/test/resources/shortened-names.json.
 */
export const shorten = (text: string, length: number): string => {
  const single = text.replace(/\s+/g, ' ').trim();

  if (single.length <= length) {
    return single;
  }

  // Never through an emoji, a flag or a letter with its accent: only whole characters, as people see them.
  const cut = single.slice(0, wholeCharactersTo(single, length - 1));
  const space = cut.lastIndexOf(' ');
  return `${(space >= length / 2 ? cut.slice(0, space) : cut).replace(/[ ,;:.-]+$/, '')}…`;
};

export const pastedText = (text: string): PastedText => {
  const lines = linesOf(text);

  if (isDocument(lines)) {
    return { markdown: `${lines.join('\n')}\n`, document: true };
  }

  const items = outlineOf(lines).map(({ depth, content }) => `${'  '.repeat(depth)}- ${title(content)}`);
  return { markdown: `# Pasted\n\n${items.join('\n')}\n`, document: false };
};

/**
 * Whether Orbit rooted the map it read at an idea of the text's own (its one top heading, or its
 * one list item) rather than at one it made up for several, or for none. Orbit puts a root it
 * makes up on the line of the idea it holds first, which an idea of the text's own shares with
 * none under it; and it says when it found neither headings nor lists. What Orbit reads as a
 * heading decides, so a line in a note's code block that looks like one doesn't count.
 */
const rootedInText = ({ root, diagnostics = [] }: OrbitDocument): boolean =>
  !diagnostics.some(({ code }) => code === 'no-structure' || code === 'empty-document')
  && !root.children.some((child) => child.source?.line === root.source?.line);

/**
 * The ideas a paste adds, from the map Orbit read its markdown as (`read`). Markdown that's a map
 * of its own adds the idea it's rooted at, with what it holds, when that's one of its own (one top
 * heading, as in an idea copied from Orbit); with several top headings or none, they're the ideas.
 * Plain text's lines are always the ideas.
 */
export const pastedIdeas = (pasted: PastedText, read: OrbitDocument): OrbitNode[] =>
  pasted.document && rootedInText(read) ? [read.root] : read.root.children;

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

  const single = (line: string): string => line.replace(/\s+/g, ' ').trim();
  const named = subject ? single(subject) || null : null;
  const middle = roots[0];

  if (!middle && !named) {
    return '';
  }

  // The first line names the map when it's the subject too, however it's spaced: it's not an idea again under it.
  const byLine = !named || (middle !== undefined && single(middle.content) === named);
  const name = byLine ? middle.content.replace(CHECKBOX, '') : named;
  const ideas = byLine ? [...middle.children, ...roots.slice(1)] : roots;

  // An address stays a link the map opens; any other name too long is cut, and kept whole as the map's note.
  const whole = single(name);
  const heading = ADDRESS.test(whole) ? title(whole) : shorten(whole, TITLE_LENGTH);
  const note = heading !== whole && !ADDRESS.test(whole) ? `\n${whole}\n` : '';
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
