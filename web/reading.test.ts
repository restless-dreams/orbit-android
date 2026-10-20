import { describe, expect, test } from 'bun:test';
import { parseOrbit, SUPPORTED_SCHEMA_VERSION } from '@restless-dreams/orbit';
import { readingOf } from './reading';
import type { Reading } from './reading';

const reading = (text: string): Reading => {
  return readingOf(parseOrbit(text), text);
};

/** The next version of Orbit's format, which this Orbit doesn't read. */
const NEXT = SUPPORTED_SCHEMA_VERSION + 1;

describe('a map from a newer Orbit', () => {
  test('is locked as newer, with what Orbit says about it, in markdown', () => {
    const read = reading(`---\nschemaVersion: ${NEXT}\nlayout: radial\n---\n# Plan\n## A <!-- orbit owner: Ana -->\n`);

    expect(read.lock).toBe('newer');
    expect(read.problem).toContain(`version ${NEXT}`);
  });

  test('is locked as newer in YAML', () => {
    expect(reading(`schemaVersion: ${NEXT}\nnodes:\n  - text: Plan\n`).lock).toBe('newer');
  });

  test('is newer before it is unreadable: updating Orbit is what helps', () => {
    const text = `---\nschemaVersion: ${NEXT}\n---\n# Plan\n`;
    const parsed = parseOrbit(text);
    parsed.diagnostics.unshift({ code: 'invalid-frontmatter', message: 'Something a newer Orbit writes', severity: 'error' });

    expect(readingOf(parsed, text).lock).toBe('newer');
  });
});

describe('a map Orbit can read', () => {
  test('in this version of the format is not locked', () => {
    expect(reading(`---\nschemaVersion: ${SUPPORTED_SCHEMA_VERSION}\n---\n# Plan\n## A\n`)).toEqual({ lock: null, problem: null });
    expect(reading('# Trip\n- Tokyo\n- Kyoto\n')).toEqual({ lock: null, problem: null });
  });
});

describe('a map Orbit can\'t read all of', () => {
  test('is locked as unreadable, with Orbit\'s words for what went wrong', () => {
    const read = reading('---\ntheme: [night\n---\n# Plan\n');

    expect(read.lock).toBe('unreadable');
    expect(read.problem).toContain('frontmatter');
  });

  test('says when Orbit found no ideas in text that isn\'t empty', () => {
    const text = '---\ntheme: night\n---\n';
    const parsed = parseOrbit(text);
    parsed.root.text = '';

    expect(readingOf(parsed, text)).toEqual({ lock: 'unreadable', problem: null, noIdeas: true });
  });
});
