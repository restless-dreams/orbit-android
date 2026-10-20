/**
 * The audience's screen while a map is presented: a second screen beside
 * the phone, or a TV the map is cast to. It draws the map with Orbit, full
 * screen and still to the touch, flies to each idea as the presenter steps
 * through them, and shows the idea's note as a caption. The presenter can
 * show an idea's picture or play its video big over the map, in Orbit's
 * media viewer, which the page drives: it takes no input of its own.
 *
 * The presenter's phone drives it with commands: messages on the app's
 * channel in its WebView (see second-screen.ts), or Cast messages on a TV
 * (see cast-receiver.ts). It speaks the phone's language: the phone says which,
 * with its words, before a presentation starts (`language`).
 */
import { createOrbitMap, findNodeById, parseOrbit, renderNoteMarkdown, themeTokens } from '@restless-dreams/orbit';
import type { OrbitInsets, OrbitMediaState, OrbitNode, OrbitSVGRenderer } from '@restless-dreams/orbit';
import { automaticRings, knownStyle, nodeTypeFor, nodeTypes, themeOption } from './look';
import type { AppTheme } from './look';
import { pageWords, speak } from './words';
import type { PageWords } from './words';

export type MediaKind = 'image' | 'video';

export type AudienceCommand =
  | { type: 'language'; words: Partial<PageWords> }
  | { type: 'show'; markdown: string; theme: AppTheme; baseUrl?: string | null; focus?: string | null; captions?: boolean }
  | { type: 'step'; id: string; index: number; total: number }
  | { type: 'captions'; on: boolean }
  | { type: 'media'; id: string; kind: MediaKind; playing: boolean; at: number }
  | { type: 'playback'; playing: boolean }
  | { type: 'seek'; at: number }
  | { type: 'closeMedia' }
  | { type: 'end' };

/** What a screen tells the presenter's phone about the video it plays. */
export interface MediaReport {
  type: 'mediaState';
  id: string;
  playing: boolean;
  time: number;
  duration: number | null;
  ended: boolean;
  muted: boolean;
}

/** The picture or video the presenter shows over the map, and whether the presenter's video plays. */
export interface Shown {
  id: string;
  kind: MediaKind;
  playing: boolean;
}

/** Screens at least this much wider than tall show captions beside the map rather than under it. */
const BESIDE_RATIO = 1.25;
/** How long the old caption takes to fade before the new one comes in, in ms. */
const CAPTION_SWAP = 180;
/** While a video plays, its time alone goes to the phone at most this often, in ms: the phone keeps time between. */
const REPORT_EVERY = 1000;
/** A video further than this from where the presenter's is, in seconds, moves there as its sound comes on. */
const CATCH_UP = 2;

let map: OrbitSVGRenderer | null = null;
let baseUrl: string | undefined;
let captionsOn = true;
let current: string | null = null;
/** Under the map, the captions' room only grows during a presentation, so the map doesn't move with every step. */
let tallestCaption = 0;
let swap = 0;
let shown: Shown | null = null;
/** Where the presenter's video is, as this page reckons it from the commands: `at` seconds in at `since` (ms). */
let reckoned = { at: 0, since: 0 };
let lastState: OrbitMediaState | null = null;
let lastReport: { report: MediaReport; at: number } | null = null;
const reporters: Array<(report: MediaReport) => void> = [];
const watchers: Array<(shown: Shown | null, state: OrbitMediaState | null) => void> = [];

const page = document.body;
const mapElement = document.getElementById('map') as HTMLElement;
const caption = document.querySelector<HTMLElement>('.audience__caption')!;
const title = document.querySelector<HTMLElement>('.audience__title')!;
const note = document.querySelector<HTMLElement>('.audience__note')!;
const progress = document.querySelector<HTMLElement>('.audience__progress-bar')!;
const still = document.querySelector<HTMLElement>('.audience__still')!;
const stillPicture = document.querySelector<HTMLImageElement>('.audience__still-picture')!;
const stillTitle = document.querySelector<HTMLElement>('.audience__still-title')!;
const stillText = document.querySelector<HTMLElement>('.audience__still-text')!;

/**
 * Follows a command from the presenter. One that can't be followed (malformed, say, or from a newer app) is
 * reported on the console and skipped, as the map's page does, rather than lost without a word.
 */
export const receive = (command: AudienceCommand): void => {
  try {
    follow(command);
  } catch (error) {
    console.error(`Orbit couldn't follow the presenter's "${String((command as { type?: unknown } | null)?.type)}" command:`, error);
  }
};

const follow = (command: AudienceCommand): void => {
  switch (command.type) {
    case 'language':
      hear(command.words);
      return;
    case 'show':
      show(command);
      return;
    case 'step':
      step(command.id, command.index, command.total);
      return;
    case 'captions':
      captionsOn = command.on;
      layout();
      return;
    case 'media':
      showMedia(command);
      return;
    case 'playback':
      playback(command.playing);
      return;
    case 'seek':
      seek(command.at);
      return;
    case 'closeMedia':
      closeMedia();
      return;
    case 'end':
      end();
      return;
  }
};

/**
 * Hears how the video on screen goes, for the phone: at once when it
 * starts, pauses, ends, jumps, is muted or learns its length, and otherwise
 * about once a second while it plays.
 */
export const reportMedia = (listener: (report: MediaReport) => void): void => {
  reporters.push(listener);
};

/** Hears what the presenter shows over the map, and how its video goes on this screen. */
export const watchMedia = (listener: (shown: Shown | null, state: OrbitMediaState | null) => void): void => {
  watchers.push(listener);
};

/**
 * Speaks the phone's language, with its words: the waiting screen says
 * them, and the map on screen names its kinds of ideas, its rings' words
 * and Orbit's own words in it. English for any words left out.
 */
export const hear = (words: Partial<PageWords> | null | undefined): void => {
  speak(words);
  const waiting = document.querySelector<HTMLElement>('.audience__waiting');

  if (waiting) {
    waiting.textContent = pageWords().waiting;
  }

  // Setting the options again writes Orbit's words in the page's new language, and new node types draw the ideas again.
  map?.setOptions({ nodeTypes: nodeTypes() });
};

/**
 * From a press on the page: the video's sound comes on, and a video that
 * should be playing plays, from where the presenter's is.
 */
export const playWithSound = (): void => {
  if (!map || shown?.kind !== 'video') {
    return;
  }

  const target = presenterTime();

  if (lastState === null || Math.abs(lastState.time - target) > CATCH_UP) {
    map.seekMedia(target);
  }

  map.unmuteMedia();

  if (shown.playing) {
    map.playMedia();
  }
};

const show = (command: Extract<AudienceCommand, { type: 'show' }>): void => {
  end();
  baseUrl = command.baseUrl ?? undefined;
  captionsOn = command.captions ?? true;
  tallestCaption = 0;
  const parsed = parseOrbit(command.markdown, { untitled: pageWords().untitledMap });
  const style = knownStyle(parsed.options.theme);
  page.classList.toggle('audience--dark', style ? isDark(themeTokens(style).surface) : command.theme.dark);
  map = createOrbitMap(mapElement, parsed, {
    interactive: false,
    showControls: false,
    showActionButtons: false,
    showDetailSheet: false,
    focus: command.focus ?? undefined,
    viewMode: 'focus',
    baseUrl,
    theme: themeOption(command.theme, style),
    nodeTypes: nodeTypes(),
    nodeTypeFor: nodeTypeFor(() => automaticRings(parsed)),
    onMediaState: mediaState,
  });
  page.classList.add('audience--showing');

  if (command.focus) {
    fillCaption(command.focus);
  }

  layout();
};

const step = (id: string, index: number, total: number): void => {
  closeMedia();
  progress.style.transform = `scaleX(${total > 0 ? (index + 1) / total : 0})`;

  if (!map || id === current) {
    return;
  }

  // The bar takes the color of the idea it reached.
  const color = map.getNodeColor(id);

  if (color) {
    page.style.setProperty('--audience-idea', color);
  }

  map.flyTo(id);
  // The old caption fades out, and the new one comes in as the map arrives.
  caption.classList.add('audience__caption--swapping');
  const mine = ++swap;

  window.setTimeout(() => {
    if (mine === swap) {
      fillCaption(id);
      caption.classList.remove('audience__caption--swapping');
      layout();
    }
  }, CAPTION_SWAP);
};

const end = (): void => {
  closeMedia();
  map?.destroy();
  map = null;
  current = null;
  mapElement.replaceChildren();
  page.classList.remove('audience--showing');
  progress.style.transform = 'scaleX(0)';
  caption.classList.add('audience__caption--empty');
};

/** The idea's picture, or its video from where the presenter's is, big over the map. */
const showMedia = (command: Extract<AudienceCommand, { type: 'media' }>): void => {
  const node = map ? findNode(command.id) : null;

  if (!map || !node) {
    return;
  }

  shown = { id: command.id, kind: command.kind, playing: command.playing };
  reckoned = { at: command.at, since: performance.now() };
  lastState = null;
  lastReport = null;

  if (command.kind === 'video' && !reachable(node)) {
    // A video file kept on the phone, on a screen that can't reach it: its poster, title and caption.
    map.closeMedia();
    showStill(node);
  } else {
    hideStill();
    const opened = map.openMedia(command.id, { kind: command.kind, autoplay: command.playing, startAt: command.at > 0 ? command.at : undefined });

    if (!opened) {
      shown = null;
    }
  }

  tellWatchers();
};

const playback = (playing: boolean): void => {
  if (!map || !shown || shown.kind !== 'video') {
    return;
  }

  reckoned = { at: presenterTime(), since: performance.now() };
  shown = { ...shown, playing };

  if (playing) {
    map.playMedia();
  } else {
    map.pauseMedia();
  }

  tellWatchers();
};

const seek = (at: number): void => {
  if (!map || shown?.kind !== 'video') {
    return;
  }

  reckoned = { at, since: performance.now() };
  map.seekMedia(at);
};

const closeMedia = (): void => {
  if (shown === null && still.hidden) {
    return;
  }

  shown = null;
  lastState = null;
  hideStill();
  map?.closeMedia();
  tellWatchers();
};

/** Where the presenter's video is now, in seconds, from the commands this page heard. */
const presenterTime = (): number => {
  const played = shown?.playing ? (performance.now() - reckoned.since) / 1000 : 0;
  return Math.max(0, reckoned.at + played);
};

const mediaState = (state: OrbitMediaState): void => {
  if (!shown || state.node.id !== shown.id) {
    return;
  }

  lastState = state;
  tellWatchers();
  const report: MediaReport = {
    type: 'mediaState',
    id: state.node.id,
    playing: state.playing,
    time: state.time,
    duration: state.duration,
    ended: state.ended,
    muted: state.muted,
  };
  const now = performance.now();
  const last = lastReport;

  if (last) {
    const same = last.report.playing === report.playing && last.report.ended === report.ended && last.report.muted === report.muted && last.report.duration === report.duration;
    const expected = last.report.time + (last.report.playing ? (now - last.at) / 1000 : 0);

    if (same && Math.abs(report.time - expected) < 1 && now - last.at < REPORT_EVERY) {
      return;
    }
  }

  lastReport = { report, at: now };

  for (const listener of reporters) {
    listener(report);
  }
};

const tellWatchers = (): void => {
  for (const listener of watchers) {
    listener(shown, lastState);
  }
};

const findNode = (id: string): OrbitNode | null => {
  const tree = map?.getDocument()?.root;
  return tree ? findNodeById(tree, id) : null;
};

/** Whether this screen can load a node's video: a file kept on the phone has no address on a screen that can't reach it. */
const reachable = (node: OrbitNode): boolean => {
  const source = node.video?.src.trim() ?? '';
  return baseUrl !== undefined || source.startsWith('//') || /^[a-z][a-z0-9+.-]*:/i.test(source);
};

const showStill = (node: OrbitNode): void => {
  const video = node.video;
  const poster = video?.thumbnail?.trim();
  stillPicture.hidden = !poster;

  if (poster) {
    stillPicture.src = poster;
  } else {
    stillPicture.removeAttribute('src');
  }

  stillTitle.textContent = [node.icon, node.text].filter(Boolean).join(' ');
  const text = video?.caption?.trim() ?? '';
  stillText.textContent = text;
  stillText.hidden = text === '';
  still.hidden = false;
  page.classList.add('audience--still');
};

const hideStill = (): void => {
  still.hidden = true;
  stillPicture.removeAttribute('src');
  page.classList.remove('audience--still');
};

/** The idea's name, and its note when it has one. The top of the map without a note needs no caption. */
const fillCaption = (id: string): void => {
  current = id;
  const tree = map?.getDocument()?.root;
  const node = tree ? findNodeById(tree, id) : null;

  if (!node || (!node.note?.trim() && node.id === tree?.id)) {
    caption.classList.add('audience__caption--empty');
    return;
  }

  caption.classList.remove('audience__caption--empty');
  title.textContent = [node.icon, node.text].filter(Boolean).join(' ');
  const text = node.note?.trim() ?? '';
  note.replaceChildren(text && node.noteFormat !== 'plain' ? renderNoteMarkdown(text, { baseUrl, resolveLink: (href) => linksTo(tree, href) }) : document.createTextNode(text));
};

const linksTo = (tree: OrbitNode | undefined, href: string): boolean => {
  return Boolean(tree && href.startsWith('#') && findNodeById(tree, href.slice(1)));
};

/** Captions beside the map on wide screens, under it elsewhere; the map fits into the rest. */
const layout = (): void => {
  const beside = innerWidth / innerHeight >= BESIDE_RATIO;
  page.classList.toggle('audience--beside', beside);
  page.classList.toggle('audience--captions', captionsOn);

  if (!map) {
    return;
  }

  let insets: OrbitInsets = {};

  if (captionsOn) {
    const box = caption.getBoundingClientRect();

    if (beside) {
      insets = { right: innerWidth - box.left };
    } else {
      tallestCaption = Math.max(tallestCaption, innerHeight - box.top);
      insets = { bottom: tallestCaption };
    }
  }

  map.setOptions({ insets });
};

/** Whether a CSS color is dark, judged by its luminance; unreadable colors count as light. */
const isDark = (color: string | undefined): boolean => {
  const context = document.createElement('canvas').getContext('2d');

  if (!color || !context) {
    return false;
  }

  context.fillStyle = '#ffffff';
  context.fillStyle = color;
  const value = String(context.fillStyle);
  const channels = value.startsWith('#')
    ? [1, 3, 5].map((start) => parseInt(value.slice(start, start + 2), 16))
    : (value.match(/[\d.]+/g) ?? []).slice(0, 3).map(Number);

  if (channels.length < 3) {
    return false;
  }

  const [red, green, blue] = channels.map((channel) => channel / 255);
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue < 0.45;
};

window.addEventListener('resize', layout);
