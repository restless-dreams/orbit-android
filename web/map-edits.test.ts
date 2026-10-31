import { describe, expect, test } from 'bun:test';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseOrbit, SUPPORTED_SCHEMA_VERSION } from '@restless-dreams/orbit';
import type { OrbitNode } from '@restless-dreams/orbit';
import { tagsOf } from './look';
import { editedMap } from './map-edits';

const UNTITLED = 'Untitled map';

const renamed = (text: string, title: string): string => {
  return editedMap(text, { title, tags: null }, UNTITLED).markdown ?? '';
};

const tagged = (text: string, tags: string[]): string => {
  return editedMap(text, { title: null, tags }, UNTITLED).markdown ?? '';
};

/** Every idea under the center, as text with its depth, and what Orbit reads on it that a map can lose. */
const ideas = (text: string): string[] => {
  const lines: string[] = [];

  const visit = (node: OrbitNode, depth: number): void => {
    lines.push(`${depth} ${node.text} ${node.icon ?? ''} ${node.status ?? ''} ${node.note ?? ''}`.trim());
    node.children.forEach((child) => visit(child, depth + 1));
  };

  parseOrbit(text).root.children.forEach((child) => visit(child, 1));
  return lines;
};

/** A YAML map, as the release data has one: a center with an icon, statuses and a note under it. */
const YAML_MAP = 'nodes:\n  - text: Photosynthesis\n    icon: 🌱\n    children:\n      - text: Light reactions\n        status: done\n        note: In the thylakoids.\n      - text: Calvin cycle\n        status: todo\n';

describe('renaming a map from Home', () => {
  test('names a center Orbit made up for several top ideas with the map\'s title, and changes no idea', () => {
    const text = '## Monday <!-- orbit icon: 📅 -->\n- Gym\n\n## Tuesday\n- Piano\n';
    const after = renamed(text, 'Week plan');

    expect(parseOrbit(after).root.text).toBe('Week plan');
    expect(after.endsWith(text)).toBe(true);
    expect(ideas(after)).toEqual(ideas(text));
  });

  test('changes the title a list is named by, and keeps every item of the list', () => {
    const text = '---\ntitle: Groceries\n---\n- Milk\n- Eggs\n- [ ] Bread\n';
    const after = renamed(text, 'Shopping');

    expect(parseOrbit(after).root.text).toBe('Shopping');
    expect(parseOrbit(after).options.title).toBe('Shopping');
    expect(ideas(after)).toEqual(ideas(text));
  });

  test('changes only the center\'s line of a map with one, as its screen does', () => {
    const text = '# Trip <!-- orbit icon: ✈️ -->\n\n## Food\n- *Tacos*  al pastor\n\n<!-- a comment -->\n';
    const after = renamed(text, 'Voyage');
    const read = parseOrbit(after);

    expect(read.root.text).toBe('Voyage');
    expect(read.root.icon).toBe('✈️');
    expect(after.slice(after.indexOf('\n'))).toBe(text.slice(text.indexOf('\n')));
  });

  test('keeps a center that\'s a list item checked off, with its other attributes', () => {
    const after = renamed('- [x] *Plan* <!-- orbit priority: 1 -->\n  - Step\n', 'Roadmap');
    const read = parseOrbit(after);

    expect(read.root.text).toBe('Roadmap');
    expect(read.root.status).toBe('done');
    expect(read.root.priority).toBe(1);
    expect(read.root.children.map((child) => child.text)).toEqual(['Step']);
  });

  test('changes a map\'s own title, and keeps the name of a center that has one of its own', () => {
    const read = parseOrbit(renamed('---\ntitle: Old\n---\n# Trip\n## Food\n', 'Voyage'));
    expect(read.options.title).toBe('Voyage');
    expect(read.root.text).toBe('Trip');

    const notes = parseOrbit(renamed('title: Study notes\nnodes:\n  - text: Photosynthesis\n    icon: 🌿\n', 'Biology notes'));
    expect(notes.options.title).toBe('Biology notes');
    expect(notes.root.text).toBe('Photosynthesis');
    expect(notes.root.icon).toBe('🌿');
  });

  test('renames a center named by the map\'s title with it', () => {
    const read = parseOrbit(renamed('---\ntitle: Week\n---\n## Monday\n## Tuesday\n', 'Week plan'));
    expect(read.options.title).toBe('Week plan');
    expect(read.root.text).toBe('Week plan');
    expect(read.root.children.map((child) => child.text)).toEqual(['Monday', 'Tuesday']);
  });

  test('renames a YAML map, keeping its icons, statuses and notes', () => {
    const after = renamed(YAML_MAP, 'Plants');
    const read = parseOrbit(after);

    expect(read.root.text).toBe('Plants');
    expect(read.root.icon).toBe('🌱');
    expect(read.diagnostics.filter((item) => item.severity === 'error')).toEqual([]);
    expect(ideas(after)).toEqual(ideas(YAML_MAP));
  });
});

describe('tagging a map from Home', () => {
  test('puts its tags in its frontmatter, and writes the rest as it was', () => {
    const text = '# Trip\n\n## Food\n- *Tacos*  al pastor\n\n<!-- a comment -->\n';
    const after = tagged(text, ['work', 'Plans']);

    expect(tagsOf(parseOrbit(after))).toEqual(['work', 'Plans']);
    expect(after.endsWith(`---\n\n${text}`)).toBe(true);
  });

  test('takes them out, and keeps the frontmatter\'s other keys', () => {
    const after = tagged('---\ntheme: dawn\ntags: [work]\ncustom: keep me\n---\n\n# Trip\n', []);

    expect(tagsOf(parseOrbit(after))).toEqual([]);
    expect(after).toContain('theme: dawn\n');
    expect(after).toContain('custom: keep me\n');
  });

  test('tags a YAML map without it being read as markdown, keeping every idea and what it holds', () => {
    const after = tagged(YAML_MAP, ['Biology']);
    const read = parseOrbit(after);

    expect(tagsOf(read)).toEqual(['Biology']);
    expect(read.root.text).toBe('Photosynthesis');
    expect(read.diagnostics.filter((item) => item.severity === 'error')).toEqual([]);
    expect(ideas(after)).toEqual(ideas(YAML_MAP));
  });

  test('renames and tags at once', () => {
    const read = parseOrbit(editedMap('# Trip\n## Food\n', { title: 'Voyage', tags: ['travel'] }, UNTITLED).markdown ?? '');
    expect(read.root.text).toBe('Voyage');
    expect(tagsOf(read)).toEqual(['travel']);
  });
});

describe('a map written with Windows line endings', () => {
  test('keeps them, and every line but the one that changes, when it\'s renamed or tagged', () => {
    const text = '---\r\ntheme: dawn\r\n---\r\n\r\n# Trip\r\n\r\n## Food\r\n- Tacos\r\n';
    const after = editedMap(text, { title: 'Voyage', tags: ['travel'] }, UNTITLED).markdown ?? '';

    expect(after.replace(/\r\n/g, '')).not.toContain('\n');
    expect(after).toContain('# Voyage\r\n\r\n## Food\r\n- Tacos\r\n');
    expect(tagsOf(parseOrbit(after))).toEqual(['travel']);
  });
});

describe('the YAML map every release has kept (the app\'s release data)', () => {
  const releases = join(import.meta.dir, '../app/src/test/resources/releases');
  const maps = readdirSync(releases).map((release) => join(releases, release, 'files/maps/text-photosynthesis/map.md')).filter((file) => existsSync(file));

  test('is there to try', () => {
    expect(maps.length).toBeGreaterThan(0);
  });

  test('is renamed and tagged from Home without losing an idea, or what Orbit reads on it', () => {
    for (const file of maps) {
      const text = readFileSync(file, 'utf8');
      const after = editedMap(text, { title: 'Biology notes', tags: ['Biology'] }, UNTITLED).markdown ?? '';
      const read = parseOrbit(after);

      expect(read.diagnostics.filter((item) => item.severity === 'error')).toEqual([]);
      expect(read.options.title ?? read.root.text).toBe('Biology notes');
      expect(tagsOf(read)).toEqual(['Biology']);
      expect(ideas(after)).toEqual(ideas(text));
    }
  });
});

describe('a map Orbit can only show', () => {
  test('from a newer Orbit is never changed, and says so', () => {
    const newer = `---\nschemaVersion: ${SUPPORTED_SCHEMA_VERSION + 1}\n---\n# Plan\n## A\n`;

    expect(editedMap(newer, { title: 'Roadmap', tags: null }, UNTITLED)).toEqual({ markdown: null, refused: 'newer' });
    expect(editedMap(newer, { title: null, tags: ['work'] }, UNTITLED)).toEqual({ markdown: null, refused: 'newer' });
    expect(editedMap(`schemaVersion: ${SUPPORTED_SCHEMA_VERSION + 1}\nnodes:\n  - text: Plan\n`, { title: 'Roadmap', tags: null }, UNTITLED).refused).toBe('newer');
  });

  test('that Orbit can\'t read all of is never changed, and says so', () => {
    expect(editedMap('nodes:\n  - text: Plan\n   bad: indentation\n', { title: 'Roadmap', tags: null }, UNTITLED)).toEqual({ markdown: null, refused: 'unreadable' });
  });
});
