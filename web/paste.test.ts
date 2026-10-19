import { describe, expect, test } from 'bun:test';
import { parseOrbit } from '@restless-dreams/orbit';
import type { OrbitNode } from '@restless-dreams/orbit';
import { pastedText } from './paste';

/** What a paste adds: each idea's text, nested, with its attributes that matter here. */
const pasted = (text: string): unknown[] => {
  const { markdown, whole } = pastedText(text);
  const root = parseOrbit(markdown).root;

  const shape = (node: OrbitNode): unknown => {
    const details = [node.status && `status ${node.status}`, node.link && `link ${node.link}`].filter(Boolean);
    const name = details.length > 0 ? `${node.text} (${details.join(', ')})` : node.text;
    return node.children.length > 0 ? { [name]: node.children.map(shape) } : name;
  };

  return (whole ? [root] : root.children).map(shape);
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
    const { markdown, whole } = pastedText(copied);
    const root = parseOrbit(markdown).root;

    expect(whole).toBe(true);
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
  });
});
