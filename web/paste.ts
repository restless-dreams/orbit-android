// Text pasted onto a map, as orbit-md for Orbit to read. A map or an idea copied from Orbit, or
// any markdown that starts with a heading or frontmatter, is read as it is. Anything else becomes
// a list: an idea a line, nested as the lines are indented, without the bullets or numbers they
// had, and an address on a line of its own becomes an idea that opens it.

export interface PastedText {
  /** The text as orbit-md. */
  markdown: string;

  /**
   * Whether the map it makes is itself the idea to paste, as an idea copied from Orbit is,
   * rather than a holder for the ideas under it.
   */
  whole: boolean;
}

/** A list item's bullet or number, as notes apps and word processors copy them. */
const BULLET = /^(?:[-*+•◦▪‣·]|\d{1,3}[.)])\s+/u;

/** An address on its own, one markdown can make a link of: no spaces, parentheses, quotes or brackets. */
const ADDRESS = /^https?:\/\/[^\s()<>"'\\[\]]+$/i;

const HEADING = /^#{1,6}\s/;
const TOP_HEADING = /^#\s/;
const TAB_WIDTH = 4;

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

export const pastedText = (text: string): PastedText => {
  const lines = text.replace(/\r\n?/g, '\n').split('\n').map((line) => line.replace(/\s+$/u, ''));
  const first = lines.find((line) => line.trim() !== '') ?? '';

  // Markdown that's a map of its own: one top heading is the idea pasted; with more or none, they're the ideas.
  if (first === '---' || HEADING.test(first)) {
    const tops = lines.filter((line) => TOP_HEADING.test(line)).length;
    return { markdown: `${lines.join('\n')}\n`, whole: tops === 1 };
  }

  // Each line goes as deep as its indent: deeper than the line above, it's under it.
  const widths: number[] = [];
  const items: string[] = [];

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

    items.push(`${'  '.repeat(widths.length - 1)}- ${title(content.replace(BULLET, ''))}`);
  }

  return { markdown: `# Pasted\n\n${items.join('\n')}\n`, whole: false };
};
