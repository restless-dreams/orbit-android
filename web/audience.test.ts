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
