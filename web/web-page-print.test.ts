import { afterAll, describe, expect, spyOn, test } from 'bun:test';
import { eventually, showPage, testClock } from './page-for-tests';

// A map's web page as the app prints it (MapPrinter.kt): only the map laid out for paper, which tells the app once
// its pictures are in; and the same page opened in a browser, which lays the map out for paper by itself once it's
// quiet, so it prints right however the browser starts printing.

const MARKDOWN = [
  '# Trip to Lisbon',
  '',
  '## Alfama',
  '',
  'The old town, on its hill.',
  '',
  '## Belém',
  '![The tower](media/tower.jpg)',
  '',
  'By the river.',
  '',
  '- Pastéis',
  '- The monastery',
  '',
].join('\n');

/** The page as the app writes it, with `print` when it prints it. */
const showWebPage = (print: { view: 'focus' | 'all'; focus?: string | null } | null): void => {
  showPage('web-page.html', {
    '<!-- data -->': `<script id="orbit-map" type="application/json">${JSON.stringify({ markdown: MARKDOWN, theme: { dark: true, tokens: {} }, print })}</script>`,
  });
};

// happy-dom lays nothing out, and a map with no size draws nothing.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 800, 1100));

/** The pictures of the layout, each read when the test says. */
const reading: (() => void)[] = [];
const decoding = spyOn(HTMLImageElement.prototype, 'decode').mockImplementation(() => new Promise<void>((resolve) => reading.push(resolve)));

/** What the page tells the app on the channel the app gives a page it prints. */
const told: string[] = [];
window.orbitPrint = { postMessage: (message) => told.push(message) };

const clock = testClock();

afterAll(() => {
  measuring.mockRestore();
  decoding.mockRestore();
});

describe('a map the app prints', () => {
  test('is only the map laid out for paper, as the app asks for it, and says it\'s ready once its pictures are in', async () => {
    showWebPage({ view: 'focus', focus: 'belem' });
    await import('./web-page.ts?printed');
    const printed = document.getElementById('print')!;

    // The picture is of the idea the app asked for, with its orbit; the outline under it has the whole map.
    const picture = printed.querySelector('svg')?.textContent ?? '';
    expect(picture).toContain('Belém');
    expect(picture).toContain('The monastery');
    expect(picture).not.toContain('Alfama');
    expect(printed.textContent).toContain('The old town');
    // Nothing to explore or present: the map's page only prints.
    expect(document.getElementById('explore')!.childElementCount).toBe(0);
    expect(printed.querySelectorAll('img').length).toBe(1);

    await eventually(() => reading.length === 1);
    await Promise.resolve();
    expect(told).toEqual([]);
    reading[0]();
    await eventually(() => told.length > 0);
    expect(told).toEqual(['ready']);
  });
});

describe('a map\'s web page in a browser', () => {
  test('lays the map out for paper by itself once the page is quiet', async () => {
    showWebPage(null);
    await import('./web-page.ts?browser');
    const printed = document.getElementById('print')!;

    expect(printed.childElementCount).toBe(0);
    clock.advance(1_999);
    expect(printed.childElementCount).toBe(0);
    clock.advance(1);
    // The whole map, as the browser prints it.
    expect(printed.textContent).toContain('The old town');
    expect(printed.textContent).toContain('By the river.');
    // A browser has no app to tell.
    expect(told).toEqual(['ready']);
  });
});
