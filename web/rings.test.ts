import { describe, expect, test } from 'bun:test';
import { parseOrbit, serializeOrbitMarkdown, serializeOrbitYAML } from '@restless-dreams/orbit';
import type { OrbitNode } from '@restless-dreams/orbit';
import { aboutAppSettings, automaticRings, automaticType, nodeTypeFor, progressOf, progressRing, withAutomaticRings } from './look';

const WEEKEND = `# Weekend

- 🧳 Trip
  - [x] Book the train
  - [ ] Pack
    - [x] Socks
    - [ ] Shirts
  - Hotel <!-- orbit type: goal, target: 4, current: 2 -->
- Chores
  - Laundry
    - [x] Whites
    - [ ] Colors
- Savings plan
  - Someday <!-- orbit type: goal -->
- Launch <!-- orbit type: milestone -->
  - [ ] Write the post
- Garden <!-- orbit image: garden.jpg -->
  - [ ] Plant tulips
- Talk <!-- orbit video: https://www.youtube.com/watch?v=dQw4w9WgXcQ -->
  - [ ] Rehearse
- Ideas
  - Read more
`;

const find = (node: OrbitNode, text: string): OrbitNode => {
  if (node.text === text) {
    return node;
  }

  for (const child of node.children) {
    const found = findOrNull(child, text);

    if (found) {
      return found;
    }
  }

  throw new Error(`No ${text}`);
};

const findOrNull = (node: OrbitNode, text: string): OrbitNode | null => {
  try {
    return find(node, text);
  } catch {
    return null;
  }
};

const everyNode = (node: OrbitNode): OrbitNode[] => {
  return [node, ...node.children.flatMap(everyNode)];
};

/** The ideas a map draws with a ring by themselves, by name. */
const ringed = (root: OrbitNode, typeFor: (node: OrbitNode) => string | undefined): string[] => {
  return everyNode(root).filter((node) => typeFor(node) === 'progress').map((node) => node.text);
};

describe('rings by themselves', () => {
  test('an idea with a to-do, or a goal with a target, right under it shows a ring', () => {
    const root = parseOrbit(WEEKEND).root;
    // Pack is a to-do with to-dos of its own. Chores only has them further down, Savings plan a goal without a target.
    expect(ringed(root, automaticType)).toEqual(['Trip', 'Pack', 'Laundry']);
    expect(automaticType(find(root, 'Chores'))).toBeUndefined();
    expect(automaticType(find(root, 'Savings plan'))).toBeUndefined();
    expect(automaticType(find(root, 'Ideas'))).toBeUndefined();
    expect(automaticType(root)).toBeUndefined();
  });

  test('kinds and pictures keep their look', () => {
    const root = parseOrbit(WEEKEND).root;
    expect(automaticType(find(root, 'Launch'))).toBeUndefined();
    expect(automaticType(find(root, 'Garden'))).toBeUndefined();
    expect(automaticType(find(root, 'Talk'))).toBeUndefined();
    // A goal right under the map's top: the top shows a ring, as the Goals map does.
    const goals = parseOrbit('# Goals\n\n- Books <!-- orbit type: goal, target: 12, current: 5 -->\n- Run <!-- orbit type: goal, target: 100, current: 80, unit: km -->\n').root;
    expect(automaticType(goals)).toBe('progress');
  });

  test('a status that is a label, not a to-do, neither shows a ring nor counts', () => {
    const tables = parseOrbit(`# Library

- 📇 copies
  - 🔑 id <!-- orbit status: "primary key" -->
  - 🔗 series_id <!-- orbit status: "foreign key" -->
- Launch
  - Beta <!-- orbit status: closed -->
  - Docs <!-- orbit status: In-Progress -->
  - Talk <!-- orbit status: WIP -->
  - Name <!-- orbit status: "maybe later" -->
`).root;
    expect(automaticType(find(tables, 'copies'))).toBeUndefined();
    expect(progressOf(find(tables, 'copies'))).toEqual({ done: 0, total: 0 });
    // Closed is done; in progress and WIP are to-dos still open; "maybe later" is a label.
    expect(automaticType(find(tables, 'Launch'))).toBe('progress');
    expect(progressOf(find(tables, 'Launch'))).toEqual({ done: 1, total: 3 });
  });

  test('a map with the setting off shows none', () => {
    const root = parseOrbit(WEEKEND).root;
    expect(ringed(root, nodeTypeFor(() => true))).toEqual(['Trip', 'Pack', 'Laundry']);
    expect(ringed(root, nodeTypeFor(() => false))).toEqual([]);
    const off = parseOrbit(`---\nprogressRings: false\n---\n\n${WEEKEND}`);
    expect(automaticRings(off)).toBe(false);
    expect(ringed(off.root, nodeTypeFor(() => automaticRings(off)))).toEqual([]);
  });

  test('a ring says how much of the to-dos and goals under it is done, each counted once', () => {
    const root = parseOrbit(WEEKEND).root;
    // The train booked, Socks packed, Shirts not yet, the Hotel halfway: Pack counts through its own to-dos.
    expect(progressOf(find(root, 'Trip'))).toEqual({ done: 2.5, total: 4 });
    expect(progressRing(find(root, 'Trip')).text.main).toEqual(['63%']);
    expect(progressRing(find(root, 'Pack')).text.main).toEqual(['50%']);
    expect(progressRing(find(root, 'Laundry')).text.main).toEqual(['50%']);
  });

  test('a Progress idea above counts only the to-dos and goals under these ideas, never the ideas themselves', () => {
    const root = parseOrbit(WEEKEND.replace('# Weekend', '# Weekend <!-- orbit type: progress -->')).root;
    // Trip's 4, Laundry's 2, and the to-dos under Launch, Garden and Talk; never Trip, Pack or Laundry.
    expect(progressOf(root)).toEqual({ done: 3.5, total: 9 });
    expect(progressRing(root).text.main).toEqual(['39%']);
  });

  test('drawing rings never writes a type: saving keeps the map as it was', () => {
    const document = parseOrbit(WEEKEND);
    const typeFor = nodeTypeFor(() => true);

    for (const node of everyNode(document.root)) {
      typeFor(node);
    }

    expect(find(document.root, 'Trip').type).toBeUndefined();
    expect(serializeOrbitMarkdown(document)).toBe(WEEKEND);
    expect(serializeOrbitMarkdown(document, { keepSource: false })).not.toContain('type: progress');
  });
});

describe('the progress rings setting', () => {
  const TRIP = `---
title: Trip
theme: cosmos   # the night look
author: someone
---

# Trip

- [x] Book the train
- [ ] Renew the passport
`;

  test('is on unless the map says progressRings: false', () => {
    expect(automaticRings(parseOrbit(TRIP))).toBe(true);
    expect(automaticRings(parseOrbit(TRIP.replace('author: someone', 'progressRings: true')))).toBe(true);
    expect(automaticRings(parseOrbit(TRIP.replace('author: someone', 'progressRings: false')))).toBe(false);
    expect(automaticRings(null)).toBe(true);
  });

  test('off is saved as progressRings: false, keeping the rest of the frontmatter as written', () => {
    const off = serializeOrbitMarkdown(withAutomaticRings(parseOrbit(TRIP), false));
    expect(off).toBe(TRIP.replace('author: someone\n', 'author: someone\nprogressRings: false\n'));
    const read = parseOrbit(off);
    expect(automaticRings(read)).toBe(false);
    expect(read.extra).toEqual({ author: 'someone', progressRings: false });
  });

  test('on takes the key out again, and never writes true', () => {
    const off = parseOrbit(serializeOrbitMarkdown(withAutomaticRings(parseOrbit(TRIP), false)));
    expect(serializeOrbitMarkdown(withAutomaticRings(off, true))).toBe(TRIP);
    expect(serializeOrbitMarkdown(withAutomaticRings(parseOrbit(TRIP), true))).toBe(TRIP);
    expect(withAutomaticRings(parseOrbit('# Trip\n'), true).extra).toEqual({});
  });

  test('off and on again, saving keeps the keys other apps keep in the frontmatter', () => {
    const text = '---\naliases: [Holiday]\n---\n\n# Trip\n\n- [ ] Renew the passport\n';
    const withKey = '---\naliases: [Holiday]\nprogressRings: false\n---\n\n# Trip\n\n- [ ] Renew the passport\n';
    // As the page does it: the same document, off and then on again, saved each time.
    const off = withAutomaticRings(parseOrbit(text), false);
    expect(serializeOrbitMarkdown(off)).toBe(withKey);
    expect(serializeOrbitMarkdown(withAutomaticRings(off, true))).toBe(text);
    // And a map that was saved with its rings off, read again.
    expect(serializeOrbitMarkdown(withAutomaticRings(parseOrbit(withKey), true))).toBe(text);
    expect(parseOrbit(text).extra).toEqual({ aliases: ['Holiday'] });
  });

  test('a map without frontmatter gets one while off, and loses it again', () => {
    const plain = '# Trip\n\n- [ ] Renew the passport\n';
    const off = serializeOrbitMarkdown(withAutomaticRings(parseOrbit(plain), false));
    expect(off).toBe(`---\nprogressRings: false\n---\n\n${plain}`);
    expect(serializeOrbitMarkdown(withAutomaticRings(parseOrbit(off), true))).toBe(plain);
  });

  test('Orbit\'s note that it doesn\'t read the key is the app\'s to keep to itself', () => {
    const off = parseOrbit(TRIP.replace('author: someone', 'author: someone\nprogressRings: false'));
    const notes = (off.diagnostics ?? []).filter((note) => !aboutAppSettings(note)).map((note) => note.message);
    expect(notes).toEqual([expect.stringContaining('"author"')]);
    expect(off.warnings.filter((message) => !aboutAppSettings({ message }))).toHaveLength(1);
    const yaml = parseOrbit('progressRings: false\nnodes:\n  - text: Trip\n');
    expect((yaml.diagnostics ?? []).filter((note) => !aboutAppSettings(note))).toEqual([]);
  });

  test('YAML maps keep it with their other keys', () => {
    const yaml = parseOrbit('nodes:\n  - text: Trip\n    children:\n      - text: Renew the passport\n        status: todo\n');
    const off = serializeOrbitYAML(withAutomaticRings(yaml, false));
    expect(off).toContain('progressRings: false');
    expect(automaticRings(parseOrbit(off))).toBe(false);
  });
});
