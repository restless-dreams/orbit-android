import { describe, expect, test } from 'bun:test';
import { parseOrbit, serializeOrbitMarkdown } from '@restless-dreams/orbit';
import { aboutAppSettings, tagsOf, withTags } from './look';

describe('a map\'s tags', () => {
  test('are read from its frontmatter, as a list or words apart with commas', () => {
    expect(tagsOf(parseOrbit('---\ntags: [work, japan trip]\n---\n\n# Trip\n'))).toEqual(['work', 'japan trip']);
    expect(tagsOf(parseOrbit('---\ntags:\n  - work\n  - "2027"\n---\n\n# Trip\n'))).toEqual(['work', '2027']);
    expect(tagsOf(parseOrbit('---\ntags: work, trip\n---\n\n# Trip\n'))).toEqual(['work', 'trip']);
    expect(tagsOf(parseOrbit('# Trip\n'))).toEqual([]);
  });

  test('are written where they were, the way the app writes them, and taken out with the last one', () => {
    const map = parseOrbit('---\ntheme: night\ntags: [work]\n---\n\n# Trip\n\n- One\n');

    expect(serializeOrbitMarkdown(withTags(map, ['work', 'japan trip']))).toBe('---\ntheme: night\ntags: [work, japan trip]\n---\n\n# Trip\n\n- One\n');
    expect(serializeOrbitMarkdown(withTags(map, ['2027']))).toBe('---\ntheme: night\ntags:\n  - "2027"\n---\n\n# Trip\n\n- One\n');
    expect(serializeOrbitMarkdown(withTags(map, []))).toBe('---\ntheme: night\n---\n\n# Trip\n\n- One\n');
    expect(serializeOrbitMarkdown(withTags(parseOrbit('# Trip\n'), ['work']))).toBe('---\ntags: [work]\n---\n\n# Trip\n');
  });

  test('are the app\'s, so what Orbit says about the key isn\'t shown', () => {
    const notes = parseOrbit('---\ntags: [work]\n---\n\n# Trip\n').diagnostics ?? [];

    expect(notes.length).toBeGreaterThan(0);
    expect(notes.every(aboutAppSettings)).toBe(true);
    expect(aboutAppSettings({ code: 'unknown-frontmatter-key', message: 'Unknown frontmatter key "tagz" (ignored).' })).toBe(false);
  });
});
