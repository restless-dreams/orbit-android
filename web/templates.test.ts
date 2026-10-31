import { describe, expect, test } from 'bun:test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const read = (file: string): string => readFileSync(join(import.meta.dir, file), 'utf8');

/**
 * The elements a page's script takes for granted, found once as it loads (`querySelector(…)!`,
 * `getElementById(…) as HTMLElement`): a page without one breaks the first time the script reaches for it.
 */
const requiredElements = (script: string): string[] => {
  const source = read(script);
  const bySelector = Array.from(source.matchAll(/querySelector<[^>]+>\('([^']+)'\)!/g), (match) => match[1]);
  const byId = Array.from(source.matchAll(/getElementById\('([^']+)'\) as HTML\w*Element/g), (match) => `#${match[1]}`);
  return [...bySelector, ...byId];
};

/** Whether `html` has an element `selector` (a single class or id) finds. */
const hasElement = (html: string, selector: string): boolean => {
  if (selector.startsWith('#')) {
    return new RegExp(`\\sid="${selector.slice(1)}"`).test(html);
  }

  const name = selector.replace(/^\./, '');
  return Array.from(html.matchAll(/\sclass="([^"]*)"/g), (match) => match[1].split(/\s+/)).some((classes) => classes.includes(name));
};

describe('the pages that present', () => {
  // The audience's script runs in the second screen's, browsers' and the TV's page, and in a map shared as a web page.
  const audience = requiredElements('audience.ts');

  test('the audience script reaches for elements it takes for granted', () => {
    expect(audience).toContain('.audience__still');
    expect(audience).toContain('#map');
  });

  test.each(['audience.html', 'web-page.html'])('%s has every element the audience script takes for granted', (template) => {
    const html = read(template);
    expect(audience.filter((selector) => !hasElement(html, selector))).toEqual([]);
  });
});
