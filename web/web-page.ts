/**
 * A map shared as a web page: one HTML file with Orbit, the map and its
 * pictures inside, which opens in any browser, offline. Readers explore it
 * with Orbit's own controls, or present it: one idea at a time with its note
 * as a caption, stepped with keys, clicks or swipes, full screen where the
 * browser allows.
 *
 * The app writes the map into the page as JSON (#orbit-map): its markdown,
 * the app's colors for maps without a style, the presentation's path, and
 * the app's words, in its language, which the page's `lang` names.
 */
import { createOrbitMap, parseOrbit } from '@restless-dreams/orbit';
import type { OrbitNode } from '@restless-dreams/orbit';
import { receive } from './audience';
import { automaticRings, knownStyle, nodeTypeFor, nodeTypes, themeOption } from './look';
import type { AppTheme } from './look';
import { pageWords, speak } from './words';
import type { PageWords } from './words';

interface PageData {
  markdown: string;
  theme: AppTheme;

  /** The ideas to present, in order; every idea in reading order when empty. */
  path?: string[];

  /** Where to download the map for Orbit, when a device on the local network serves the page. */
  download?: string | null;

  /** What the page says itself, in the app's language: the kinds of ideas, a goal ring's words, a list's made-up top idea. English without them. */
  words?: Partial<PageWords> | null;
}

/** A swipe at least this long, in px, steps to the next idea or back. */
const SWIPE = 60;
/** The controls step aside after this long without the pointer moving, in ms. */
const CONTROLS_LINGER = 2_500;

const readingOrder = (root: OrbitNode): string[] => {
  const ids: string[] = [];

  const visit = (node: OrbitNode) => {
    ids.push(node.id);
    node.children.forEach(visit);
  };

  visit(root);
  return ids;
};

const data = JSON.parse(document.getElementById('orbit-map')?.textContent ?? '{}') as PageData;
speak(data.words);
const page = document.body;
const parsed = parseOrbit(data.markdown, { untitled: pageWords().untitledMap });
const style = knownStyle(parsed.options.theme);
const explore = createOrbitMap(document.getElementById('explore') as HTMLElement, parsed, {
  theme: themeOption(data.theme, style),
  nodeTypes: nodeTypes(),
  nodeTypeFor: nodeTypeFor(() => automaticRings(parsed)),
});
const order = data.path?.length ? data.path : readingOrder(parsed.root);
const count = document.querySelector<HTMLElement>('.page__count')!;
const download = document.querySelector<HTMLAnchorElement>('.page__download');

if (data.download && download) {
  download.href = data.download;
  download.hidden = false;
  page.classList.add('page--downloadable');
}

let index = 0;
let presenting = false;
let linger = 0;

/** Presents from the idea being explored, or from the top. */
const present = (): void => {
  presenting = true;
  index = Math.max(0, order.indexOf(explore.getFocusedNode()?.id ?? ''));
  page.classList.add('page--presenting');
  receive({ type: 'show', markdown: data.markdown, theme: data.theme, focus: order[index], captions: true });
  step(0);
  showControls();
  document.documentElement.requestFullscreen?.().catch(() => undefined);
};

const stop = (): void => {
  presenting = false;
  receive({ type: 'end' });
  page.classList.remove('page--presenting', 'page--controls');

  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => undefined);
  }

  // The map to explore picks up where the presentation was.
  explore.flyTo(order[index]);
};

const step = (by: number): void => {
  index = Math.min(order.length - 1, Math.max(0, index + by));
  receive({ type: 'step', id: order[index], index, total: order.length });
  count.textContent = `${index + 1} / ${order.length}`;
};

const showControls = (): void => {
  page.classList.add('page--controls');
  window.clearTimeout(linger);
  linger = window.setTimeout(() => page.classList.remove('page--controls'), CONTROLS_LINGER);
};

document.querySelector('.page__present')?.addEventListener('click', present);
document.querySelector('[data-close]')?.addEventListener('click', stop);

document.querySelectorAll<HTMLElement>('[data-step]').forEach((button) => {
  button.addEventListener('click', () => {
    step(Number(button.dataset.step));
    showControls();
  });
});

document.addEventListener('keydown', (event) => {
  if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) {
    return;
  }

  if (!presenting) {
    if (event.key === 'p' || event.key === 'P' || event.key === 'F5') {
      event.preventDefault();
      present();
    }

    return;
  }

  const forward = ['ArrowRight', 'ArrowDown', 'PageDown', ' ', 'Enter'];
  const back = ['ArrowLeft', 'ArrowUp', 'PageUp', 'Backspace'];

  if (forward.includes(event.key)) {
    event.preventDefault();
    step(1);
  } else if (back.includes(event.key)) {
    event.preventDefault();
    step(-1);
  } else if (event.key === 'Escape') {
    stop();
  }
});

// Leaving full screen with the browser's own Escape also ends the presentation.
document.addEventListener('fullscreenchange', () => {
  if (presenting && !document.fullscreenElement && document.fullscreenEnabled) {
    stop();
  }
});

// While presenting, a swipe steps and a tap shows the controls; the pointer moving shows them too.
let start: { x: number; y: number } | null = null;

page.addEventListener('pointerdown', (event) => {
  if (presenting && !(event.target as Element).closest('.page__controls')) {
    start = { x: event.clientX, y: event.clientY };
  }
});

page.addEventListener('pointerup', (event) => {
  if (!presenting || !start) {
    return;
  }

  const dx = event.clientX - start.x;
  const dy = event.clientY - start.y;
  start = null;

  if (Math.abs(dx) >= SWIPE && Math.abs(dx) > Math.abs(dy)) {
    step(dx < 0 ? 1 : -1);
  } else if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
    showControls();
  }
});

page.addEventListener('pointermove', (event) => {
  if (presenting && event.pointerType === 'mouse') {
    showControls();
  }
});
