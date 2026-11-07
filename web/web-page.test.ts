import { afterAll, describe, expect, mock, spyOn, test } from 'bun:test';
import { OrbitSVGRenderer } from '@restless-dreams/orbit';
import { eventually, showPage, testClock } from './page-for-tests';

// A map shared as a web page, opened in a browser: explored, presented one idea at a time with keys, clicks and
// swipes, full screen where the browser allows, and printed from the browser as the map laid out for paper. The app
// printing it is web-page-print.test.ts.

const MARKDOWN = [
  '# Trip to Lisbon',
  '',
  '## Alfama',
  '',
  'The old town, on its hill.',
  '',
  '## Belém',
  '',
  'By the river.',
  '',
  '## Sintra',
  '',
  'A day away.',
  '',
].join('\n');

// The page as the app writes it: the map, the app's colors, the presentation's path and its words, in Spanish.
showPage('web-page.html', {
  '<!-- data -->': `<script id="orbit-map" type="application/json">${JSON.stringify({
    markdown: MARKDOWN,
    theme: { dark: false, tokens: {} },
    path: ['trip-to-lisbon', 'alfama', 'sintra', 'belem'],
    download: '/map.zip',
    words: { language: 'es', untitledMap: 'Mapa sin título' },
  })}</script>`,
});

// happy-dom lays nothing out, and a map with no size draws nothing: this one is a laptop's.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 1280, 800));

/** Where each map flew, and which: the one to explore, or the one presented. */
const flights: { map: string; id: string }[] = [];
const flyTo = OrbitSVGRenderer.prototype.flyTo;

const flying = spyOn(OrbitSVGRenderer.prototype, 'flyTo').mockImplementation(function (this: OrbitSVGRenderer, id: string): boolean {
  flights.push({ map: this.getSVGElement().closest('#explore') ? 'explore' : 'presented', id });
  return flyTo.call(this, id);
});

// Full screen, as a browser that allows it has it: the page asks for it, and the browser says when it leaves it.
let fullScreen: Element | null = null;

const requestFullscreen = mock(async () => {
  fullScreen = document.documentElement;
});

const exitFullscreen = mock(async () => {
  fullScreen = null;
});

document.documentElement.requestFullscreen = requestFullscreen;
document.exitFullscreen = exitFullscreen;
Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => fullScreen });
Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, get: () => true });

const clock = testClock();
await import('./web-page');

afterAll(() => {
  measuring.mockRestore();
  flying.mockRestore();
});

const page = document.body;
const presentButton = document.querySelector<HTMLButtonElement>('button.page__present')!;
const title = document.querySelector<HTMLElement>('.audience__title')!;
const count = (): string => document.querySelector('.page__count')?.textContent ?? '';
const presenting = (): boolean => page.classList.contains('page--presenting');
const controlsShow = (): boolean => page.classList.contains('page--controls');

const press = (key: string, options: KeyboardEventInit = {}): KeyboardEvent => {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...options });
  document.body.dispatchEvent(event);
  return event;
};

const swipe = (from: number, to: number, y = 400): void => {
  page.dispatchEvent(new PointerEvent('pointerdown', { clientX: from, clientY: y, pointerType: 'touch', bubbles: true }));
  page.dispatchEvent(new PointerEvent('pointerup', { clientX: to, clientY: y, pointerType: 'touch', bubbles: true }));
};

/** The presentation is on `name`, with its caption, as the n-th of its path. */
const on = async (name: string, place: string): Promise<void> => {
  await eventually(() => title.textContent === name);
  expect(count()).toBe(place);
};

const stop = async (): Promise<void> => {
  if (presenting()) {
    press('Escape');
  }

  expect(presenting()).toBe(false);
};

describe('a map shared as a web page', () => {
  test('offers the map itself beside Present when a device on the network serves it', () => {
    const download = document.querySelector<HTMLAnchorElement>('.page__download')!;

    expect(download.hidden).toBe(false);
    expect(download.getAttribute('href')).toBe('/map.zip');
    expect(page.classList.contains('page--downloadable')).toBe(true);
    // In the app's language, which Orbit's own words follow.
    expect(document.documentElement.lang).toBe('es');
  });

  test('is the map laid out for paper when the browser prints it, and only once', () => {
    const printed = document.getElementById('print')!;
    expect(printed.childElementCount).toBe(0);

    window.dispatchEvent(new Event('beforeprint'));
    expect(printed.textContent).toContain('Trip to Lisbon');
    expect(printed.textContent).toContain('By the river.');

    // Printed again, or the page quiet at last: the layout stays as it was laid out. A layout starts with the print
    // colors, so one more is stopped there, before it draws: drawn again in happy-dom's pretend layout, the print
    // layout never settles.
    let again = 0;
    const setProperty = CSSStyleDeclaration.prototype.setProperty;

    const colors = spyOn(CSSStyleDeclaration.prototype, 'setProperty').mockImplementation(function (this: CSSStyleDeclaration, name: string, ...rest: [string | null, string?]): void {
      if (name.startsWith('--orbit-print-')) {
        again += 1;
        throw new Error('The map was laid out for paper again');
      }

      setProperty.call(this, name, ...rest);
    });

    window.dispatchEvent(new Event('beforeprint'));
    clock.advance(2_000);
    colors.mockRestore();
    expect(again).toBe(0);
  });

  test('presents from the top along its path, full screen, the focus and the keys on the idea shown', async () => {
    presentButton.click();

    expect(presenting()).toBe(true);
    expect(controlsShow()).toBe(true);
    expect(requestFullscreen).toHaveBeenCalledTimes(1);
    expect(document.activeElement).toBe(title);
    await on('Trip to Lisbon', '1 / 4');

    // Its controls step aside after a moment without the pointer, and the presentation stays.
    clock.advance(2_500);
    expect(controlsShow()).toBe(false);
    expect(presenting()).toBe(true);
    await stop();
  });

  test('steps along its path with the keys, never past either end, and Escape stops it where it was', async () => {
    press('p');
    await on('Trip to Lisbon', '1 / 4');

    for (const key of ['ArrowRight', ' ', 'PageDown']) {
      expect(press(key).defaultPrevented).toBe(true);
    }

    await on('Belém', '4 / 4');
    press('ArrowDown');
    await on('Belém', '4 / 4');
    press('ArrowLeft');
    await on('Sintra', '3 / 4');
    press('Backspace');
    press('ArrowUp');
    await on('Trip to Lisbon', '1 / 4');
    press('PageUp');
    await on('Trip to Lisbon', '1 / 4');
    press('Enter');
    await on('Alfama', '2 / 4');

    // A key with a modifier is the browser's.
    expect(press('ArrowRight', { ctrlKey: true }).defaultPrevented).toBe(false);
    await on('Alfama', '2 / 4');

    const flown = flights.length;
    press('Escape');
    expect(presenting()).toBe(false);
    expect(document.activeElement).toBe(presentButton);
    expect(exitFullscreen).toHaveBeenCalled();
    // The map to explore picks up where the presentation was.
    expect(flights.slice(flown)).toEqual([{ map: 'explore', id: 'alfama' }]);
  });

  test('presents again from the idea being explored', async () => {
    press('F5');
    await on('Alfama', '2 / 4');
    await stop();
  });

  test('steps with swipes, and a tap brings its controls back', async () => {
    press('P');
    await on('Alfama', '2 / 4');
    clock.advance(2_500);
    expect(controlsShow()).toBe(false);

    swipe(900, 300);
    await on('Sintra', '3 / 4');
    swipe(300, 900);
    await on('Alfama', '2 / 4');
    // Too short to be a swipe, or more up and down than across, a gesture doesn't step.
    swipe(500, 540);
    page.dispatchEvent(new PointerEvent('pointerdown', { clientX: 500, clientY: 100, pointerType: 'touch', bubbles: true }));
    page.dispatchEvent(new PointerEvent('pointerup', { clientX: 580, clientY: 600, pointerType: 'touch', bubbles: true }));
    await on('Alfama', '2 / 4');

    expect(controlsShow()).toBe(false);
    swipe(500, 503);
    expect(controlsShow()).toBe(true);
    // Its buttons step too, and close it.
    document.querySelector<HTMLButtonElement>('[data-step="1"]')!.click();
    await on('Sintra', '3 / 4');
    document.querySelector<HTMLButtonElement>('[data-close]')!.click();
    expect(presenting()).toBe(false);
  });

  test('stops when the browser leaves full screen by itself', async () => {
    presentButton.click();
    await on('Sintra', '3 / 4');
    expect(fullScreen).not.toBeNull();

    // The browser's own Escape leaves full screen before the page sees the key.
    fullScreen = null;
    document.dispatchEvent(new Event('fullscreenchange'));
    expect(presenting()).toBe(false);
  });
});
