import { afterAll, describe, expect, spyOn, test } from 'bun:test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { OrbitSVGRenderer } from '@restless-dreams/orbit';
import type { AudienceCommand } from './audience';

// The audience's page as the presenter's phone drives it: its commands go in, and the map and its caption show where
// the presenter is.

// happy-dom lays nothing out, and a map with no size draws nothing: this one is a TV's.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 1280, 720));

// Where the map flies, and the map that flies there.
const flyTo = OrbitSVGRenderer.prototype.flyTo;
const flights: string[] = [];
let drawn: OrbitSVGRenderer | null = null;

const flying = spyOn(OrbitSVGRenderer.prototype, 'flyTo').mockImplementation(function (this: OrbitSVGRenderer, id: string): boolean {
  drawn = this;
  flights.push(id);
  return flyTo.call(this, id);
});

// The page's own elements, from its HTML, ahead of anything other tests left in the page.
const html = readFileSync(join(import.meta.dir, 'audience.html'), 'utf8');
const body = /<body[^>]*>([\s\S]*)<\/body>/.exec(html)?.[1] ?? '';
const holder = document.createElement('div');
holder.innerHTML = body;
const elements = [...holder.children];
document.body.prepend(...elements);
const { receive } = await import('./audience');

afterAll(() => {
  receive({ type: 'end' });
  elements.forEach((element) => element.remove());
  flying.mockRestore();
  measuring.mockRestore();
});

/** Longer than the caption takes to swap as the map arrives. */
const settle = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 500));

const caption = (): string => document.querySelector('.audience__title')?.textContent ?? '';

const show: AudienceCommand = {
  type: 'show',
  markdown: '# Talk\n\n## Alpha\n\nWhere it starts.\n\n## Beta\n\nWhere it goes.\n',
  theme: { dark: false, tokens: {} },
  focus: 'talk',
  captions: true,
};

describe('stepping', () => {
  test('quick steps there and back leave the map and its caption where the presenter is', async () => {
    receive(show);
    receive({ type: 'step', id: 'alpha', index: 1, total: 3 });
    await settle();
    expect(caption()).toBe('Alpha');

    // On to Beta and straight back to Alpha, quicker than Beta's caption comes in.
    receive({ type: 'step', id: 'beta', index: 2, total: 3 });
    receive({ type: 'step', id: 'alpha', index: 1, total: 3 });
    await settle();

    expect(flights.at(-1)).toBe('alpha');
    expect(drawn?.getFocusedNode()?.id).toBe('alpha');
    expect(caption()).toBe('Alpha');
  });

  test('a step to where the map already is stays put', async () => {
    receive(show);
    await settle();
    const before = flights.length;
    receive({ type: 'step', id: 'talk', index: 0, total: 3 });
    await settle();

    expect(flights.length).toBe(before);
    expect(caption()).toBe('Talk');

    receive({ type: 'step', id: 'beta', index: 2, total: 3 });
    receive({ type: 'step', id: 'beta', index: 2, total: 3 });
    await settle();
    expect(flights.slice(before)).toEqual(['beta']);
    expect(caption()).toBe('Beta');
  });
});

describe('a new map', () => {
  test('a caption on its way for the map before never shows on the new one', async () => {
    receive(show);
    receive({ type: 'step', id: 'alpha', index: 1, total: 3 });
    // Another map, before Alpha's caption came in.
    receive({ type: 'show', markdown: '# Trip\n\n## Lisbon\n\nBy the river.\n', theme: { dark: false, tokens: {} }, focus: 'lisbon', captions: true });
    await settle();

    expect(caption()).toBe('Lisbon');
    expect(document.querySelector('.audience__caption')?.classList.contains('audience__caption--empty')).toBe(false);
    expect(document.querySelector('.audience__caption')?.classList.contains('audience__caption--swapping')).toBe(false);
  });

  test('a command to show a map that can\'t be followed leaves the map on screen as it was', async () => {
    receive(show);
    receive({ type: 'step', id: 'beta', index: 2, total: 3 });
    await settle();
    const errors = spyOn(console, 'error').mockImplementation(() => undefined);

    // From anyone on a TV's network, say: a map that isn't text, and one without the app's colors.
    receive({ type: 'show', markdown: 42, theme: null } as unknown as AudienceCommand);
    receive({ type: 'show', markdown: '# Someone else\'s' } as unknown as AudienceCommand);
    const reported = errors.mock.calls.length;
    errors.mockRestore();

    expect(reported).toBe(2);
    expect(document.body.classList.contains('audience--showing')).toBe(true);
    expect(document.querySelector('#map svg')).not.toBeNull();
    expect(drawn?.getFocusedNode()?.id).toBe('beta');
    expect(caption()).toBe('Beta');
  });
});

describe('in a browser in the room', () => {
  test('people can pinch to zoom, which fights nothing: the map on the page takes no touch of its own', async () => {
    const viewport = /<meta name="viewport" content="([^"]*)"/.exec(html)?.[1] ?? '';
    expect(viewport).toContain('width=device-width');
    expect(viewport).not.toMatch(/user-scalable\s*=\s*(no|0)|maximum-scale/);

    // Orbit's still map leaves touches to the browser (touch-action: auto in its styles), its zoom included.
    receive(show);
    await settle();
    expect(document.querySelector('#map .orbit--static')).not.toBeNull();
  });
});
