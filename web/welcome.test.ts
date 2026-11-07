import { describe, expect, test } from 'bun:test';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { parseOrbit, type OrbitNode } from '@restless-dreams/orbit';

// The welcome map a first launch puts in (MapRepository's AssetStarterMaps), in each
// language: its text, its details (welcome.json) and its media, shared by the languages.
const assets = join(import.meta.dir, '..', 'app/src/main/assets');
const media = join(assets, 'welcome');
const languages = [
  { name: 'English', folder: assets },
  { name: 'Spanish', folder: join(assets, 'es') },
  { name: 'Portuguese', folder: join(assets, 'pt') },
];

const welcome = (folder: string) => {
  const map = parseOrbit(readFileSync(join(folder, 'welcome.md'), 'utf8'));
  const details = JSON.parse(readFileSync(join(folder, 'welcome.json'), 'utf8')) as { path: string[] };
  return { map, details };
};

const everyNode = (node: OrbitNode): OrbitNode[] => {
  return [node, ...(node.children ?? []).flatMap(everyNode)];
};

/** What a node is besides its words, which the languages must agree on. */
const shape = (node: OrbitNode): unknown => {
  return {
    icon: node.icon,
    type: node.type,
    status: node.status,
    goal: node.attrs?.target,
    image: node.image?.src,
    video: [node.video?.src, node.video?.thumbnail],
    audio: node.audio?.src,
    link: node.link !== undefined,
    note: node.note !== undefined,
    children: (node.children ?? []).map(shape),
  };
};

const mediaOf = (node: OrbitNode): string[] => {
  return [node.image?.src, node.video?.src, node.video?.thumbnail, node.audio?.src].filter((src): src is string => src !== undefined);
};

describe('the welcome map', () => {
  for (const { name, folder } of languages) {
    test(`reads in ${name} without anything for Orbit to say about it`, () => {
      expect(welcome(folder).map.diagnostics ?? []).toEqual([]);
    });

    test(`presents along a path of its ideas in ${name}, from its center through each planet`, () => {
      const { map, details } = welcome(folder);
      const ids = new Set(everyNode(map.root).map((node) => node.id));

      expect(details.path.filter((id) => !ids.has(id))).toEqual([]);
      expect(details.path).toEqual([map.root.id, ...(map.root.children ?? []).map((node) => node.id)]);
    });

    test(`links to ideas it has in ${name}`, () => {
      const nodes = everyNode(welcome(folder).map.root);
      const ids = new Set(nodes.map((node) => node.id));
      const links = nodes.map((node) => node.link).filter((link): link is string => link?.startsWith('#') ?? false);

      expect(links.length).toBeGreaterThan(0);
      expect(links.filter((link) => !ids.has(link.slice(1)))).toEqual([]);
    });
  }

  test('is the same map in every language, only in its own words', () => {
    const [english, ...others] = languages.map(({ folder }) => shape(welcome(folder).map.root));

    for (const other of others) {
      expect(other).toEqual(english);
    }
  });

  test('shows a picture, a video with its poster and a sound, from its own media, and nothing else from there', () => {
    const used = everyNode(welcome(assets).map.root).flatMap(mediaOf);
    const files = readdirSync(media);

    expect(used.every((src) => src.startsWith('media/'))).toBe(true);
    expect(used.map((src) => src.slice('media/'.length)).sort()).toEqual([...files].sort());
    expect(files.some((file) => file.endsWith('.webp'))).toBe(true);
    expect(files.filter((file) => file.endsWith('.mp4'))).toHaveLength(2);
  });

  test('keeps its media small: a fresh install carries it in every copy of the app', () => {
    const bytes = readdirSync(media).reduce((sum, file) => sum + statSync(join(media, file)).size, 0);
    expect(bytes).toBeLessThan(300 * 1024);
  });

  test('has a tiny deck of cards to study: ideas with the answer under them', () => {
    const study = everyNode(welcome(assets).map.root).find((node) => node.id === 'study');
    const cards = study?.children ?? [];

    expect(cards).toHaveLength(3);
    expect(cards.every((card) => card.children?.length === 1)).toBe(true);
  });

  test('is the only map a fresh install starts with: the samples are only in debug builds', () => {
    expect(existsSync(join(assets, 'samples'))).toBe(false);
    expect(existsSync(join(assets, 'es', 'samples'))).toBe(false);
  });
});
