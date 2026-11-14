import { describe, expect, test } from 'bun:test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseOrbit } from '@restless-dreams/orbit';
import type { OrbitNewNode, OrbitNode } from '@restless-dreams/orbit';
import { caughtIdea, mapFromText, pastedIdeas, pastedText, shorten } from './paste';

/** What a paste adds: each idea's text, nested, with its attributes that matter here. */
const pasted = (text: string): unknown[] => {
  const read = pastedText(text);

  const shape = (node: OrbitNode): unknown => {
    const details = [node.status && `status ${node.status}`, node.link && `link ${node.link}`].filter(Boolean);
    const name = details.length > 0 ? `${node.text} (${details.join(', ')})` : node.text;
    return node.children.length > 0 ? { [name]: node.children.map(shape) } : name;
  };

  return pastedIdeas(read, parseOrbit(read.markdown)).map(shape);
};

describe('plain text', () => {
  test('makes an idea of each line', () => {
    expect(pasted('Milk\nEggs\n\nBread\n')).toEqual(['Milk', 'Eggs', 'Bread']);
  });

  test('nests the lines as they are indented, with spaces or tabs', () => {
    expect(pasted('Trip\n  Flights\n    Window seat\n  Hotels\nChores')).toEqual([
      { Trip: [{ Flights: ['Window seat'] }, 'Hotels'] },
      'Chores',
    ]);

    expect(pasted('Trip\n\tFlights\n\t\tWindow seat\nChores')).toEqual([{ Trip: [{ Flights: ['Window seat'] }] }, 'Chores']);
  });

  test('takes a line indented less than the one above, but more than the one before, under the one before', () => {
    expect(pasted('A\n    B\n  C\nD')).toEqual([{ A: ['B', 'C'] }, 'D']);
  });

  test('drops bullets and numbers, keeping to-dos', () => {
    expect(pasted('• One\n◦ Two\n1. Three\n2) Four\n- [ ] Five\n- [x] Six')).toEqual([
      'One',
      'Two',
      'Three',
      'Four',
      'Five (status todo)',
      'Six (status done)',
    ]);
  });

  test('keeps numbers that are part of the text', () => {
    expect(pasted('2026 plans\n3.5 kg of flour')).toEqual(['2026 plans', '3.5 kg of flour']);
  });

  test('makes an idea that opens an address on a line of its own', () => {
    expect(pasted('https://www.example.com/trips/\nSee https://example.com')).toEqual([
      'example.com/trips (link https://www.example.com/trips/)',
      'See https://example.com',
    ]);
  });

  test('reads Windows line endings', () => {
    expect(pasted('One\r\n  Two\r\nThree')).toEqual([{ One: ['Two'] }, 'Three']);
  });
});

describe('markdown', () => {
  test('pastes an idea copied from Orbit whole, with what it holds', () => {
    const copied = '# Flights <!-- orbit color: "#12295E" -->\nBook early.\n\n## Tokyo <!-- orbit status: done -->\n- Window seat\n\n## Osaka\n';
    const read = pastedText(copied);
    const [root] = pastedIdeas(read, parseOrbit(read.markdown));

    expect(root.text).toBe('Flights');
    expect(root.color).toBe('#12295E');
    expect(root.note).toBe('Book early.');
    expect(pasted(copied)).toEqual([{ Flights: [{ 'Tokyo (status done)': ['Window seat'] }, 'Osaka'] }]);
  });

  test('pastes a whole map from its frontmatter on', () => {
    expect(pasted('---\ntheme: night\n---\n\n# Trip\n\n## Flights\n\n- Tokyo\n')).toEqual([{ Trip: [{ Flights: ['Tokyo'] }] }]);
  });

  test('pastes the sections of markdown without one top heading', () => {
    expect(pasted('## Groceries\n\n- Milk\n\n## Errands\n\n- Bank\n')).toEqual([{ Groceries: ['Milk'] }, { Errands: ['Bank'] }]);
    expect(pasted('# One\n\n- a\n\n# Two\n\n- b\n')).toEqual([{ One: ['a'] }, { Two: ['b'] }]);
    // A title names the root Orbit makes for them, which isn't pasted either.
    expect(pasted('---\ntitle: Week\n---\n# One\n\n# Two\n')).toEqual(['One', 'Two']);
    // Nor is the line before them that names it.
    expect(pasted('---\ntags: [home]\n---\nWeek\n\n# One\n\n# Two\n')).toEqual(['One', 'Two']);
    // A deeper heading first: the top ones aren't the first, so they're all ideas.
    expect(pasted('## Before\n# One\n')).toEqual(['Before', 'One']);
  });

  test('pastes the idea a heading-like line in a note\'s code block belongs to, whole', () => {
    // As markdown written elsewhere has it: Orbit's own copy puts a note's code in a quote.
    expect(pasted('# Flights\nBook early.\n\n```\n# not a heading\n```\n\n## Tokyo\n')).toEqual([{ Flights: ['Tokyo'] }]);

    const read = pastedText('# Flights\nBook early.\n\n~~~\n# not a heading\n~~~\n\n## Tokyo\n');
    const [root] = pastedIdeas(read, parseOrbit(read.markdown));
    expect(root.note).toBe('Book early.\n\n~~~\n# not a heading\n~~~');
  });

  test('pastes one top heading whole at any level, and a list\'s one top item', () => {
    expect(pasted('## Groceries\n\n- Milk\n- Eggs\n')).toEqual([{ Groceries: ['Milk', 'Eggs'] }]);
    expect(pasted('---\ntags: [home]\n---\n- Groceries\n  - Milk\n')).toEqual([{ Groceries: ['Milk'] }]);
    expect(pasted('---\ntags: [home]\n---\n- Milk\n- Eggs\n')).toEqual(['Milk', 'Eggs']);
  });

  test('pastes markdown that starts with a heading underlined with === or --- as the markdown it is', () => {
    // A README's sections, as markdown written by hand often underlines them.
    expect(pasted('Groceries\n=========\n\nWhat we need.\n\nThis week\n---------\n\n- Milk\n- Eggs\n\nNext week\n---------\n\n- Bread\n')).toEqual([
      { Groceries: [{ 'This week': ['Milk', 'Eggs'] }, { 'Next week': ['Bread'] }] },
    ]);

    // With a list, or a heading written with #, after it.
    expect(pasted('Groceries\n---------\n- Milk\n- Eggs')).toEqual([{ Groceries: ['Milk', 'Eggs'] }]);
    expect(pasted('Trip\n====\nBook early.\n\n## Flights\n')).toEqual([{ Trip: ['Flights'] }]);

    // Another underlined heading counts after a blank line: right under the first one's lines, it's plain text's rule.
    const read = pastedText('Lisbon\n======\nBy the river.\n\nAlfama\n------\nOn the hill.');
    const [root] = pastedIdeas(read, parseOrbit(read.markdown));
    expect(read.document).toBe(true);
    expect([root.text, root.note, root.children.map((child) => [child.text, child.note])]).toEqual(['Lisbon', 'By the river.', [['Alfama', 'On the hill.']]]);
  });

  test('pastes plain text that underlines a line as its lines, as ever, without an idea for the underline', () => {
    // A title over lines, a name over a signature, a table's header, rules between lines, an outline under its title.
    expect(pasted('Groceries\n---------\n\nMilk\nEggs\nBread')).toEqual(['Groceries', 'Milk', 'Eggs', 'Bread']);
    expect(pasted('ORDER\n=====\nItem A\nItem B')).toEqual(['ORDER', 'Item A', 'Item B']);
    expect(pasted('Thanks\n--\nAna')).toEqual(['Thanks', 'Ana']);
    expect(pasted('name,qty\n--------\nmilk,2\neggs,12')).toEqual(['name,qty', 'milk,2', 'eggs,12']);
    expect(pasted('Milk\n---\nEggs\n---\nBread')).toEqual(['Milk', 'Eggs', 'Bread']);
    expect(pasted('Trip\n====\n  Flights\n    Window seat\n  Hotels')).toEqual([{ Trip: [{ Flights: ['Window seat'] }, 'Hotels'] }]);
    expect(pasted('Section\n--------\nBody')).toEqual(['Section', 'Body']);
    expect(pasted('Trip\n=====')).toEqual(['Trip']);
    // A break between parts, `* * *` or `- - -`, is a rule, as Orbit reads it, not a list item: no more markdown.
    expect(pasted('Story\n=====\nPart one\n* * *\nPart two')).toEqual(['Story', 'Part one', 'Part two']);
    expect(pasted('Story\n=====\nPart one\n- - -\nPart two')).toEqual(['Story', 'Part one', 'Part two']);
  });

  test('keeps plain text whose first line is no heading\'s plain, and makes no idea of a line that only underlines or rules off', () => {
    // A list's item, a quote, a link or a rule over a line of dashes is no heading: the lines are ideas, as before.
    expect(pastedText('- Milk\n---\n- Eggs').document).toBe(false);
    expect(pastedText('> Milk\n---\n# Eggs').document).toBe(false);
    expect(pastedText('Milk\n\n=====\nEggs').document).toBe(false);
    expect(pasted('[Notes](https://example.com/notes)\n---\nFlights\n- Window seat')).toEqual(['Notes (link https://example.com/notes)', 'Flights', 'Window seat']);
    // A heading over several lines, which hardly anyone writes, is taken for plain lines with a rule under them.
    expect(pastedText('Milk\nEggs\n---\n- Bread').document).toBe(false);
    expect(pasted('Milk\nEggs\n=====\nBread\n- - -\nButter\n___')).toEqual(['Milk', 'Eggs', 'Bread', 'Butter']);
  });

  test('pastes nothing from markdown with no ideas in it', () => {
    expect(pasted('---\ntags: [home]\n---\n')).toEqual([]);
    expect(pasted('---\ntitle: Week\n---\nJust a line.\n')).toEqual([]);
  });
});

/** The map a text makes: its middle, then each idea, nested, with its status or link where it has one. */
const mapOf = (text: string, subject: string | null = null): unknown => {
  const root = parseOrbit(mapFromText(text, subject)).root;

  const shape = (node: OrbitNode): unknown => {
    const details = [node.status && `status ${node.status}`, node.link && `link ${node.link}`].filter(Boolean);
    const name = details.length > 0 ? `${node.text} (${details.join(', ')})` : node.text;
    return node.children.length > 0 ? { [name]: node.children.map(shape) } : name;
  };

  return shape(root);
};

describe('a new map from text', () => {
  test('one line with everything else indented under it is the middle', () => {
    expect(mapFromText('Trip\n  Flights\n    Window seat\n  Hotels')).toBe('# Trip\n\n- Flights\n  - Window seat\n- Hotels\n');
  });

  test('with several lines at the top, the first is the middle, and the others keep their nesting around it', () => {
    expect(mapFromText('Trip\n  Flights\nPacking\n  Passport\n    Copies\nBudget')).toBe(
      '# Trip\n\n- Flights\n- Packing\n  - Passport\n    - Copies\n- Budget\n',
    );
  });

  test('a plain list without indents makes the map it always has: the first line in the middle', () => {
    expect(mapFromText('Milk\nEggs\nBread')).toBe('# Milk\n\n- Eggs\n- Bread\n');
  });

  test('reads tabs as four spaces, mixed with spaces', () => {
    expect(mapFromText('Trip\n\tFlights\n\t\tWindow seat\n    Hotels\n        Lisbon')).toBe('# Trip\n\n- Flights\n  - Window seat\n- Hotels\n  - Lisbon\n');
  });

  test('takes a line indented less than the one above, but more than the one before, under the one before', () => {
    expect(mapFromText('Trip\n    Flights\n  Hotels')).toBe('# Trip\n\n- Flights\n- Hotels\n');
  });

  test('drops bullets and numbers of any kind, and keeps to-dos as to-dos', () => {
    expect(mapOf('• Groceries\n    1. Milk\n    2) Eggs\n    - [ ] Bread\n    * [x] Butter\n    + [/] Coffee\n◦ Errands\n    ▪ Bank')).toEqual({
      Groceries: ['Milk', 'Eggs', 'Bread (status todo)', 'Butter (status done)', 'Coffee (status active)', { Errands: ['Bank'] }],
    });
  });

  test('reads a markdown list as any other indented text, its top line in the middle', () => {
    expect(mapFromText('- Trip\n  - Flights\n  - Hotels')).toBe('# Trip\n\n- Flights\n- Hotels\n');
    // The list ImporterTest shares as text; in a markdown file it's kept as written instead.
    expect(mapFromText('- Trip\n  - Flights\n    - Window seat\n  - Hotels\n- Packing\n  - Passport\n')).toBe(
      '# Trip\n\n- Flights\n  - Window seat\n- Hotels\n- Packing\n  - Passport\n',
    );
  });

  test('makes an idea that opens an address on a line of its own, and a middle that is one opens it', () => {
    expect(mapOf('Reading\n  https://www.example.com/books/')).toEqual({ Reading: ['example.com/books (link https://www.example.com/books/)'] });
    expect(mapOf('https://example.com/trips\n  Lisbon')).toEqual({ 'example.com/trips (link https://example.com/trips)': ['Lisbon'] });
  });

  test('a to-do in the middle is just its name there', () => {
    expect(mapFromText('- [ ] Pack\n  - Passport')).toBe('# Pack\n\n- Passport\n');
  });

  test('a subject shared with the text names the map, and every line goes around it', () => {
    expect(mapFromText('Flights\n  Window seat\nHotels', 'Summer trip')).toBe('# Summer trip\n\n- Flights\n  - Window seat\n- Hotels\n');
    // A subject that is the first line doesn't name the map twice.
    expect(mapFromText('Trip\n  Flights', 'Trip')).toBe('# Trip\n\n- Flights\n');
    // However either is spaced: shared articles' titles and their first lines often differ only there.
    expect(mapFromText('Trip  to\tLisbon\n  Flights', 'Trip to Lisbon')).toBe('# Trip to Lisbon\n\n- Flights\n');
    expect(mapFromText('Trip to Lisbon\n  Flights', '  Trip   to Lisbon ')).toBe('# Trip to Lisbon\n\n- Flights\n');
  });

  test('a middle too long for a name is named by its start, and kept whole as the map\'s note', () => {
    const line = 'Remember to ask the venue about the projector, the stage lights and whether the sound check can move to Friday morning';
    const markdown = mapFromText(`${line}\n  Projector`);
    expect(markdown).toBe(`# Remember to ask the venue about the projector, the stage lights and whether…\n\n${line}\n\n- Projector\n`);
    expect(parseOrbit(markdown).root.note).toBe(line);
  });

  test('markdown that is a map of its own is kept as written', () => {
    expect(mapFromText('# Trip\n\n## Flights\n- Window seat\n')).toBe('# Trip\n\n## Flights\n- Window seat\n');
  });

  test('reads Windows line endings, and leaves blank lines out', () => {
    expect(mapFromText('Trip\r\n\r\n  Flights\r\n')).toBe('# Trip\n\n- Flights\n');
  });

  test('markdown that starts with an underlined heading is kept as written, and plain text\'s underline is no idea', () => {
    expect(mapFromText('Trip\n=====\n\nFlights\n-------\n- Window seat\n')).toBe('Trip\n=====\n\nFlights\n-------\n- Window seat\n');
    expect(mapOf('Trip\n=====\n\nFlights\n-------\n- Window seat\n')).toEqual({ Trip: [{ Flights: ['Window seat'] }] });
    // Plain text makes the map it always made.
    expect(mapFromText('Trip\n=====')).toBe('# Trip\n');
    expect(mapOf('Groceries\n---------\n\nMilk\nEggs\nBread')).toEqual({ Groceries: ['Milk', 'Eggs', 'Bread'] });
    expect(mapOf('ORDER\n=====\nItem A\nItem B')).toEqual({ ORDER: ['Item A', 'Item B'] });
    expect(mapOf('Thanks\n--\nAna')).toEqual({ Thanks: ['Ana'] });
    expect(mapOf('name,qty\n--------\nmilk,2\neggs,12')).toEqual({ 'name,qty': ['milk,2', 'eggs,12'] });
    expect(mapOf('Milk\n---\nEggs\n---\nBread')).toEqual({ Milk: ['Eggs', 'Bread'] });
    expect(mapOf('Trip\n====\n  Flights\n    Window seat\n  Hotels')).toEqual({ Trip: [{ Flights: ['Window seat'] }, 'Hotels'] });
    expect(mapOf('[Notes](https://example.com/notes)\n---\nFlights')).toEqual({ 'Notes (link https://example.com/notes)': ['Flights'] });
    expect(mapOf('Milk\nEggs\n=====\nBread')).toEqual({ Milk: ['Eggs', 'Bread'] });
  });
});

describe('ideas caught on the go', () => {
  /** What text caught into a map adds: the ideas pasting makes of it, as they join the map. */
  const caught = (text: string): OrbitNewNode[] => {
    const root = parseOrbit(pastedText(text).markdown).root;

    const idea = (node: OrbitNode): OrbitNewNode => ({
      text: node.text,
      ...(node.status ? { status: node.status } : {}),
      ...(node.note ? { note: node.note } : {}),
      children: node.children.map(idea),
    });

    return root.children.map(idea).map(caughtIdea);
  };

  test('nest as pasting nests them, to-dos and all', () => {
    expect(caught('Venue\n  - [ ] Call them\n  - Ask about\n      the projector\nBand')).toEqual([
      { text: 'Venue', children: [{ text: 'Call them', status: 'todo', children: [] }, { text: 'Ask about', children: [{ text: 'the projector', children: [] }] }] },
      { text: 'Band', children: [] },
    ]);
  });

  test('a long idea is named by its start, and kept whole as its note', () => {
    const line = 'word '.repeat(100).trim();
    const [idea] = caught(line);
    expect(idea.text.length).toBeLessThanOrEqual(200);
    expect(idea.text.endsWith('…')).toBe(true);
    expect(idea.note).toBe(line);
  });

  test('spaces inside an idea collapse', () => {
    expect(caught('A  logo for\tthe shop')[0].text).toBe('A logo for the shop');
  });
});

describe('names cut short', () => {
  test('at a word\'s end, with an ellipsis, as the app cuts them', () => {
    expect(shorten('Ideas for the weekend', 80)).toBe('Ideas for the weekend');
    expect(shorten('The quick brown fox jumps over the lazy dog', 20)).toBe('The quick brown…');
    expect(shorten('Supercalifragilisticexpialidocious', 10)).toBe('Supercali…');
  });

  test('never through a character as people see one, as the app cuts them too', () => {
    // The cases MapTextTest holds the app's cut to: emoji, a family, a flag, a skin tone, an accent.
    const cases = JSON.parse(readFileSync(join(import.meta.dir, '../app/src/test/resources/shortened-names.json'), 'utf8')) as { why: string; text: string; length: number; shortened: string }[];

    for (const { why, text, length, shortened } of cases) {
      expect(`${why}: ${shorten(text, length)}`).toBe(`${why}: ${shortened}`);
    }
  });
});
