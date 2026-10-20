/**
 * A map laid out for paper (Orbit's print layout) with the app's look: its
 * kinds of ideas, and its colors in light, since paper is white whatever
 * mode the phone is in. The app prints it through Android's print framework
 * (MapPrinter.kt), and a shared web page prints it from the browser.
 */
import type { OrbitDocument, OrbitNode, OrbitNodeType, OrbitPrintOptions } from '@restless-dreams/orbit';
import { automaticRings, goalRing, nodeTypeFor, nodeTypes, progressRing, themeOption } from './look';
import type { AppTheme, Ring } from './look';
import { pageWords } from './words';

/** What the app asks of a map it prints: what its picture shows. */
export interface PrintRequest {
  /** `focus`, an idea with its orbit, or `all`, the whole map. */
  viewMode: 'focus' | 'all';

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
    viewMode: request?.viewMode,
    focus: request?.focus ?? undefined,
    language,
    nodeTypes: printNodeTypes(language),
    nodeTypeFor: nodeTypeFor(() => automaticRings(map)),
  };
};

/** Settles once every picture in `element` has loaded or failed to: a page prints only what it has drawn. */
export const picturesLoaded = async (element: ParentNode): Promise<void> => {
  await Promise.all(Array.from(element.querySelectorAll('img'), (image) => image.decode().catch(() => undefined)));
};
