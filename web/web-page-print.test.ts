import { afterAll, describe, expect, spyOn, test } from 'bun:test';
import { eventually, pageAt, showPage, testClock } from './page-for-tests';

// A map's web page as the app prints it (MapPrinter.kt): only the map laid out for paper, which tells the app once
// it's laid out and once its pictures are in; and the same page opened in a browser, which lays the map out for paper by itself once it's
// quiet, so it prints right however the browser starts printing.

/** A picture of the map's own, inside the page as the app writes it there (WebPage.kt): a data: URL. */
const TOWER = 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ==';

const MARKDOWN = [
  '# Trip to Lisbon',
  '',
  '## Alfama',
  '',
  'The old town, on its hill.',
  '',
  '## Belém',
  `![The tower](${TOWER})`,
  '',
  'By the river.',
  '',
  '- Pastéis',
  '- The monastery',
  '',
].join('\n');

/** The page as the app writes it, with `print` when it prints it. */
const showWebPage = (print: { view: 'focus' | 'all'; focus?: string | null } | null, markdown = MARKDOWN): void => {
  showPage('web-page.html', {
    '<!-- data -->': `<script id="orbit-map" type="application/json">${JSON.stringify({ markdown, theme: { dark: true, tokens: {} }, print })}</script>`,
  });
};

// happy-dom lays nothing out, and a map with no size draws nothing.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 800, 1100));

/** The pictures of the layout, each read, or failing to be, when the test says. */
const reading: { source: string | null; resolve: () => void; reject: (error: Error) => void }[] = [];

const decoding = spyOn(HTMLImageElement.prototype, 'decode').mockImplementation(function (this: HTMLImageElement) {
  return new Promise<void>((resolve, reject) => reading.push({ source: this.getAttribute('src'), resolve, reject }));
});

/** What the page tells the app on the channel the app gives a page it prints. */
const told: string[] = [];
window.orbitPrint = { postMessage: (message) => told.push(message) };

const clock = testClock();

afterAll(() => {
  measuring.mockRestore();
  decoding.mockRestore();
});

describe('a map the app prints', () => {
  test('is only the map laid out for paper, as the app asks for it, says so, and says it\'s ready once its pictures are in', async () => {
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

    // Laid out, before its pictures are in: the app gives them their time from here, however long a big map took.
    expect(told).toEqual(['laid-out']);
    await eventually(() => reading.length === 1);
    await Promise.resolve();
    expect(told).toEqual(['laid-out']);
    reading[0].resolve();
    await eventually(() => told.length > 1);
    expect(told).toEqual(['laid-out', 'ready']);
  });

  test('goes without a picture that can\'t be loaded, or takes too long to, and says how many it went without', async () => {
    told.length = 0;
    reading.length = 0;
    // A picture of the map's own, and one from the web, on a phone that can't reach it now.
    showWebPage({ view: 'all' }, `${MARKDOWN}\n## The tram\n![Tram 28](https://example.com/tram.jpg)\n`);
    await import('./web-page.ts?missing');
    const printed = document.getElementById('print')!;
    expect(printed.querySelectorAll('img').length).toBe(2);

    await eventually(() => reading.length === 2);
    reading[0].reject(new Error('The picture couldn\'t be loaded'));
    await Promise.resolve();
    expect(told).toEqual(['laid-out']);

    // The other never comes: the print goes without both.
    clock.advance(14_999);
    await Promise.resolve();
    expect(told).toEqual(['laid-out']);
    clock.advance(1);
    await eventually(() => told.length > 1);
    expect(told).toEqual(['laid-out', 'ready:2']);
  });

  test('leaves out a picture left out of the page on purpose, and doesn\'t wait for it, nor count it as one it went without', async () => {
    told.length = 0;
    reading.length = 0;
    // The map's own picture inside the page, one the person sharing kept on the phone (it keeps its place in the map's
    // folder, which names nothing on the page's host), and one from the web.
    showWebPage({ view: 'all' }, `${MARKDOWN}\n## Home\n![The street](media/street.heic)\n\n## The tram\n![Tram 28](https://example.com/tram.jpg)\n`);
    await import('./web-page.ts?left-out');
    const printed = document.getElementById('print')!;
    // The one kept on the phone isn't printed, as a broken picture or at all: the idea is there, without it.
    expect(Array.from(printed.querySelectorAll('img')).map((image) => image.getAttribute('src'))).toEqual([TOWER, 'https://example.com/tram.jpg']);
    expect(printed.textContent).toContain('Home');
    expect(printed.textContent).not.toContain('The street');

    await eventually(() => reading.length > 0);
    await Promise.resolve();
    expect(reading.map(({ source }) => source)).toEqual([TOWER, 'https://example.com/tram.jpg']);

    for (const picture of reading) {
      picture.resolve();
    }

    await eventually(() => told.length > 1);
    expect(told).toEqual(['laid-out', 'ready']);
  });
});

describe('a map\'s web page in a browser', () => {
  test('lays the map out for paper by itself once the page is quiet', async () => {
    const before = [...told];
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
    expect(told).toEqual(before);
  });

  test('prints the pictures inside it when it\'s saved and opened from its file, as Firefox opens it', async () => {
    // Opened from its file, the page has the origin `null` in Firefox, as every data: URL has.
    pageAt('file:///home/someone/Downloads/Trip%20to%20Lisbon.html');

    try {
      const river = 'data:image/png;base64,iVBORw0KGgo=';
      showWebPage(null, `${MARKDOWN}\n## The river\nAt dusk ![The river](${river}) from the pier.\n\n## Home\n![The street](media/street.heic)\n`);
      await import('./web-page.ts?saved-file');
      window.dispatchEvent(new Event('beforeprint'));
      const printed = document.getElementById('print')!;

      // The idea's picture and the note's are inside the page, and print; the one kept on the phone stays out.
      expect(Array.from(printed.querySelectorAll('img')).map((image) => image.getAttribute('src'))).toEqual([TOWER, river]);
      expect(printed.textContent).toContain('The tower');
      expect(printed.textContent).toContain('Home');
      expect(printed.textContent).not.toContain('The street');
    } finally {
      pageAt('http://localhost');
    }
  });
});
