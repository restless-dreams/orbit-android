import { describe, expect, test } from 'bun:test';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { parseOrbit, serializeOrbitMarkdown, type OrbitDocument, type OrbitNode } from '@restless-dreams/orbit';
import { isGoal, readGoal } from './goal';
import { nodeTypes } from './look';
import { readingOf } from './reading';

// What every release of the app left on a phone (app/src/test/resources/releases/, which ReleaseDataTest opens with
// the app): its maps, their versions and its trash, read with the Orbit these pages are built with, as the map's
// page reads them. A change in Orbit or here that misread one, or locked it, would leave people's maps only shown.
const releases = join(import.meta.dir, '..', 'app/src/test/resources/releases');

const mapFiles = (folder: string): string[] => {
  return readdirSync(folder).sort().flatMap((name) => {
    const path = join(folder, name);
    return statSync(path).isDirectory() ? mapFiles(path) : name.endsWith('.md') ? [path] : [];
  });
};

/** Each release's maps, their versions and the maps in its trash, by where they are under releases/. */
const maps = readdirSync(releases)
  .filter((release) => statSync(join(releases, release)).isDirectory())
  .flatMap((release) => ['maps', 'history', 'trash'].flatMap((kind) => mapFiles(join(releases, release, 'files', kind))))
  .map((path) => ({ name: relative(releases, path), text: readFileSync(path, 'utf8') }));

/** A map written as YAML: its ideas under a top-level `nodes:` key. Saving writes it as markdown, as the map's page does. */
const isYaml = (text: string): boolean => /^nodes:\s*$/m.test(text);

/** Frontmatter the app writes for itself, which Orbit keeps as it is: Home's tags. */
const APP_KEYS = new Set(['tags']);

const read = (text: string) => parseOrbit(text, { untitled: 'Untitled map' });

/** Everything a map says, written out the same way whatever it was written as: two maps that say the same are the same text. */
const meaning = (map: OrbitDocument): string => serializeOrbitMarkdown(map, { keepSource: false });

const everyNode = (node: OrbitNode): OrbitNode[] => [node, ...(node.children ?? []).flatMap(everyNode)];

describe("each release's maps", () => {
  test('are all here to read', () => {
    expect(maps.length).toBeGreaterThan(50);
    expect(maps.filter(({ name }) => name.startsWith('0.3.0/')).length).toBeGreaterThan(10);
  });

  test('use kinds of ideas the app draws, and their goals keep their numbers', () => {
    const nodes = maps.flatMap(({ name, text }) => everyNode(read(text).root).map((node) => ({ name, node })));
    const drawn = new Set(Object.keys(nodeTypes()));
    const goals = nodes.filter(({ node }) => node.type === 'goal');

    expect(nodes.filter(({ node }) => node.type !== undefined && !drawn.has(node.type)).map(({ name, node }) => `${name}: ${node.type}`)).toEqual([]);
    expect(goals.length).toBeGreaterThan(0);
    expect(goals.filter(({ node }) => !isGoal(node) || readGoal(node).target === null).map(({ name, node }) => `${name}: ${node.text}`)).toEqual([]);
  });

  for (const { name, text } of maps) {
    test(`${name} reads without a problem, and can be changed`, () => {
      const parsed = read(text);

      expect(readingOf(parsed, text)).toEqual({ lock: null, problem: null });
      // Orbit says only that it leaves the app's own frontmatter alone.
      expect(parsed.diagnostics.filter((item) => item.severity !== 'info' && item.code !== 'unknown-frontmatter-key')).toEqual([]);
      expect(Object.keys(parsed.extra ?? {}).filter((key) => !APP_KEYS.has(key))).toEqual([]);
    });

    test(`${name} is saved as it was written`, () => {
      const parsed = read(text);
      const saved = serializeOrbitMarkdown(parsed);

      // A YAML map is saved as markdown that says all it said.
      if (isYaml(text)) {
        expect(meaning(read(saved))).toBe(meaning(parsed));
      } else {
        expect(saved).toBe(text);
      }
    });
  }
});
