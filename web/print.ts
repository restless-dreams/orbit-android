/**
 * A map laid out for paper (Orbit's print layout) with the app's look: its
 * kinds of ideas, and its colors in light, since paper is white whatever
 * mode the phone is in. The app prints it through Android's print framework
 * (MapPrinter.kt), and a shared web page prints it from the browser.
 */
import { printTheme, themeTokens } from '@restless-dreams/orbit';
import type { OrbitDocument, OrbitNode, OrbitNodeType, OrbitPrintOptions, OrbitRendererTheme } from '@restless-dreams/orbit';
import { automaticRings, goalRing, nodeTypeFor, nodeTypes, progressRing, themeOption } from './look';
import type { AppTheme, Ring } from './look';
import { pageWords } from './words';

/** What the app asks of a map it prints: what its picture shows. */
export interface PrintRequest {
  /** `focus`, an idea with its orbit, or `all`, the whole map. */
  view: 'focus' | 'all';

  /** The idea in the middle of a focused picture; the top idea without one. */
  focus?: string | null;
}

/**
 * The app's colors on paper: as they are when they're light, otherwise Orbit's
 * Material theme grown from the same color in light. The dark scheme's own
 * colors (its background, text and chips) were chosen for a dark screen.
 */
export const lightTheme = (theme: AppTheme): AppTheme => {
  return theme.dark ? { dark: false, seed: theme.seed, tokens: {} } : theme;
};

/** What a ring says, on one line: its full number, its target or "left", and a check once a goal is reached. */
export const ringLine = (ring: Ring): string => {
  const { main, detail, check } = ring.text;
  return [main[0], detail?.[0], check ? '✓' : undefined].filter(Boolean).join(' ');
};

/** A ring as paper shows it, under its idea's name: a bar as full as the ring, and what the ring says. */
const writeRing = (ring: (node: OrbitNode) => Ring) => (node: OrbitNode, element: HTMLElement): void => {
  const shown = ring(node);
  const line = document.createElement('span');
  line.className = 'print-ring';
  const track = document.createElement('span');
  track.className = 'print-ring__track';
  const fill = document.createElement('span');
  fill.className = 'print-ring__fill';
  fill.style.width = `${Math.round(Math.min(1, Math.max(0, shown.share)) * 100)}%`;
  track.appendChild(fill);
  line.append(track, document.createTextNode(ringLine(shown)));
  element.appendChild(line);
};

/**
 * The app's kinds of ideas on paper, which has no rings: a goal and a
 * Progress idea say how far along they are under their name instead, in
 * the page's words.
 */
export const printNodeTypes = (language: string): Record<string, OrbitNodeType> => {
  const types = nodeTypes();
  return {
    ...types,
    progress: { ...types.progress, details: writeRing(progressRing) },
    goal: { ...types.goal, details: writeRing((node) => goalRing(node, language, pageWords())) },
  };
};

/**
 * The print layout's options for a map: its own style, which Orbit prints in
 * its light counterpart, or the app's colors in light, with the app's kinds
 * of ideas, in the page's language.
 */
export const printOptions = (map: OrbitDocument, theme: AppTheme, style: string | null, request: PrintRequest | null | undefined, language: string): OrbitPrintOptions => {
  return {
    theme: themeOption(lightTheme(theme), style),
    view: request?.view,
    focus: request?.focus ?? undefined,
    language,
    nodeTypes: printNodeTypes(language),
    nodeTypeFor: nodeTypeFor(() => automaticRings(map)),
  };
};

/** How long a picture from the web may take to arrive for a print, in ms: one that takes longer goes without. */
export const PICTURE_WAIT = 15_000;

/**
 * Whether a picture is one a print could go without by chance: one inside
 * the page (a data: URL) or from the web. A map's picture that stays out of
 * the page on purpose (kept on the device by the person sharing, or past the
 * page's room) keeps its place in the map's folder (`media/…`, beside the
 * page), where nothing answers: the print goes without it as it was meant
 * to. Told by that address, not by origin: a page saved and opened from its
 * file has none of its own in Firefox (`null`, as every data: URL's is), and
 * would take its own pictures for ones left out.
 */
const mayGoMissing = (image: HTMLImageElement): boolean => {
  try {
    return !new URL(image.src, location.href).href.startsWith(new URL('media/', location.href).href);
  } catch {
    return true;
  }
};

/**
 * Takes the map's pictures left out of the page on purpose out of its layout
 * for paper, `element`, as the map leaves them off its planets: a browser
 * would print each as a broken picture. An idea's picture goes with its
 * caption, a video's poster leaves the video's name and address, and a
 * picture in a note leaves the note's words.
 */
export const withoutPicturesLeftOut = (element: ParentNode): void => {
  for (const image of Array.from(element.querySelectorAll('img'))) {
    if (mayGoMissing(image)) {
      continue;
    }

    const figure = image.parentElement;

    if (figure?.classList.contains('orbit-print__media') && !figure.classList.contains('orbit-print__media--video')) {
      figure.remove();
    } else {
      image.remove();
    }
  }
};

/**
 * Settles once every picture in `element` has loaded, failed to, or taken
 * longer than `PICTURE_WAIT`, with how many of them didn't load: a page
 * prints only what it has drawn, and the app says what it went without.
 * Pictures left out of the page on purpose aren't waited for or counted.
 */
export const picturesLoaded = async (element: ParentNode): Promise<number> => {
  const loaded = await Promise.all(Array.from(element.querySelectorAll('img')).filter(mayGoMissing).map((image) => Promise.race([
    image.decode().then(() => true, () => false),
    new Promise<boolean>((resolve) => setTimeout(() => resolve(false), PICTURE_WAIT)),
  ])));

  return loaded.filter((drawn) => !drawn).length;
};

/**
 * The colors of the print layout's chips and links, as Orbit prints them in
 * `theme` (the map's own style, when it has one), under Orbit's public names
 * for them. Set on the layout's container, they're what its chips take, and
 * what the rings the app writes beside them take too (web-page.html).
 */
export const printColors = (theme: string | OrbitRendererTheme | undefined): Record<string, string> => {
  const printed = printTheme(theme);
  const tokens: Record<string, string | undefined> = { ...themeTokens(printed.preset), ...printed.tokens };
  return {
    '--orbit-print-chip-background': tokens['chip-background'] ?? '',
    '--orbit-print-link-text': tokens['link-text'] ?? tokens.text ?? '',
  };
};
