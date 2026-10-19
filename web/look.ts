/**
 * How the app's maps look, wherever they're drawn: in the app, on a second
 * screen, or on a TV they're cast to.
 */
import { isThemeName, materialTheme } from '@restless-dreams/orbit';
import type { OrbitDocument, OrbitNode, OrbitNodeType, OrbitRendererTheme } from '@restless-dreams/orbit';
import { CAP_HEIGHT, CHECK_RADIUS, DETAIL_WEIGHT, ENGLISH_RING_WORDS, MAIN_WEIGHT, goalShare, isGoal, layoutRing, readGoal, ringText } from './goal';
import type { RingLayout, RingText, RingWords } from './goal';
import { pageWords } from './words';

/**
 * The colors the app's Material theme gives maps that don't pick a style:
 * Orbit's Material theme grown from `seed`, with `tokens` the app sets exactly.
 */
export interface AppTheme {
  dark: boolean;
  tokens: Record<string, string>;
  seed?: string | null;
}

/** A style Orbit knows by name, or null for the app's own colors. */
export const knownStyle = (name: string | undefined): string | null => {
  return name !== undefined && name !== 'auto' && isThemeName(name) ? name : null;
};

/** The app's colors, unless the map picks a style of its own in its frontmatter. */
export const themeOption = (theme: AppTheme, style: string | null): OrbitRendererTheme | undefined => {
  if (style) {
    return undefined;
  }

  if (theme.seed) {
    try {
      const material = materialTheme(theme.seed, { dark: theme.dark });
      return { preset: material.preset, tokens: { ...material.tokens, ...theme.tokens } };
    } catch {
      // A seed Orbit can't read: the plain colors below.
    }
  }

  return { preset: theme.dark ? 'night' : 'paper', tokens: theme.tokens };
};

// Node types

const SVG_NAMESPACE = 'http://www.w3.org/2000/svg';
/** Statuses Orbit draws as done: a to-do with one is done. */
const DONE_STATUS = /^(done|complete|completed|shipped|finished|closed)$/;
/**
 * Statuses that make an idea a to-do: the words Orbit draws as to do, in
 * progress, blocked or done. Any other status, such as "primary key" in a
 * map of a database, is a label.
 */
const TO_DO_STATUS = /^(todo|to[ -]?do|planned|backlog|next|open|active|doing|in[ -]?progress|wip|started|ongoing|blocked|stuck|at[ -]?risk|failed|done|complete|completed|shipped|finished|closed)$/;
const RING_FONT = '"Google Sans Flex", Roboto, sans-serif';
const RING_RADIUS = 36;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;
/** How long a ring takes to fill or empty to a new share, in ms. */
const RING_MOTION = 600;
/** Material's emphasized deceleration: quick to start, gentle to land. */
const RING_EASING = 'cubic-bezier(0.05, 0.7, 0.1, 1)';

const statusWord = (node: OrbitNode): string => {
  return node.status?.trim().toLowerCase() ?? '';
};

/** A to-do: an idea whose status is one of a to-do's (see `TO_DO_STATUS`). */
const isToDo = (node: OrbitNode): boolean => {
  return TO_DO_STATUS.test(statusWord(node));
};

/** A goal with a target of its own, which shows its own numbers. */
const hasOwnTarget = (node: OrbitNode): boolean => {
  return isGoal(node) && readGoal(node).target !== null;
};

/** Whether a to-do, or a goal with a target, is right under the idea. */
export const hasItemsUnder = (node: OrbitNode): boolean => {
  return node.children.some((child) => isToDo(child) || hasOwnTarget(child));
};

/**
 * Whether an idea counts through the goals and to-dos under it rather than
 * as one of them: a Progress idea, a goal without a target of its own, and
 * any other idea with to-dos or goals with targets right under it. A goal
 * with a target counts by its own numbers, whatever is under it.
 */
const countsThrough = (node: OrbitNode): boolean => {
  if (isGoal(node)) {
    return readGoal(node).target === null;
  }

  return node.type?.trim().toLowerCase() === 'progress' || hasItemsUnder(node);
};

/**
 * Of the goals and to-dos under a node, how many there are and how much of
 * them is done: a to-do counts as done or not, a goal by how far along it
 * is. An idea that counts through the ones under it (see `countsThrough`)
 * isn't one of them, so nothing is counted twice.
 */
export const progressOf = (node: OrbitNode): { done: number; total: number } => {
  let done = 0;
  let total = 0;

  const visit = (current: OrbitNode) => {
    for (const child of current.children) {
      if (!countsThrough(child)) {
        if (isGoal(child)) {
          total += 1;
          done += goalShare(readGoal(child));
        } else if (isToDo(child)) {
          total += 1;

          if (DONE_STATUS.test(statusWord(child))) {
            done += 1;
          }
        }
      }

      visit(child);
    }
  };

  visit(node);
  return { done, total };
};

/** The frontmatter key a map turns its automatic progress rings off with: `progressRings: false`. */
export const PROGRESS_RINGS = 'progressRings';

/** The frontmatter key a map's tags are kept under, where notes apps look for them too: `tags: [work, trip]`. */
export const TAGS = 'tags';

/**
 * Whether what Orbit says about a map is about a key the app keeps in its
 * frontmatter itself, such as `progressRings` or `tags`: Orbit doesn't read
 * it and says so, but it's the app's, so there's nothing to tell people.
 */
export const aboutAppSettings = (note: { code?: string; message: string }): boolean => {
  const unknownKey = note.code === undefined || note.code === 'unknown-frontmatter-key' || note.code === 'unknown-yaml-key';
  return unknownKey && [PROGRESS_RINGS, TAGS].some((key) => note.message.includes(`key "${key}"`));
};

/** A map's tags, as its frontmatter has them: a list, or words apart with commas. */
export const tagsOf = (document: Pick<OrbitDocument, 'extra'> | null | undefined): string[] => {
  const tags = document?.extra?.[TAGS];
  const list = Array.isArray(tags) ? tags : typeof tags === 'string' ? tags.split(',') : [];
  return list.map((tag) => String(tag).trim()).filter((tag) => tag !== '');
};

/** The map with these tags in its frontmatter; none takes the key out. */
export const withTags = (document: OrbitDocument, tags: string[]): OrbitDocument => {
  const extra: Record<string, unknown> = { ...document.extra };

  if (tags.length > 0) {
    extra[TAGS] = tags;
  } else {
    delete extra[TAGS];
  }

  return { ...document, extra };
};

/** Whether a map shows progress rings by itself on ideas with to-dos or goals: unless it says `progressRings: false`. */
export const automaticRings = (document: Pick<OrbitDocument, 'extra'> | null | undefined): boolean => {
  return document?.extra?.[PROGRESS_RINGS] !== false;
};

/**
 * The map with its automatic progress rings on or off. Off is written in its
 * frontmatter as `progressRings: false`; on leaves the key out. The keys are
 * always an object, even an empty one: a document without any keeps the ones
 * its file has, so on would leave `progressRings: false` where it was.
 */
export const withAutomaticRings = (document: OrbitDocument, on: boolean): OrbitDocument => {
  const extra: Record<string, unknown> = { ...document.extra };

  if (on) {
    delete extra[PROGRESS_RINGS];
  } else {
    extra[PROGRESS_RINGS] = false;
  }

  return { ...document, extra };
};

/**
 * The type an idea is drawn as without one of its own: a Progress ring, for
 * an idea with no kind and no picture or video that has a to-do, or a goal
 * with a target, right under it. For display only: it's never written.
 */
export const automaticType = (node: OrbitNode): string | undefined => {
  return !node.type && !node.image?.src && !node.video?.src && hasItemsUnder(node) ? 'progress' : undefined;
};

/** Orbit's `nodeTypeFor` for a map: automatic Progress rings while `on()` says so. */
export const nodeTypeFor = (on: () => boolean): (node: OrbitNode) => string | undefined => {
  return (node) => (on() ? automaticType(node) : undefined);
};

const svgElement = (name: string, attributes: Record<string, string | number>): SVGElement => {
  const element = document.createElementNS(SVG_NAMESPACE, name);

  for (const [key, value] of Object.entries(attributes)) {
    element.setAttribute(key, String(value));
  }

  return element;
};

let measuring: CanvasRenderingContext2D | null | undefined;
const measured = new Map<string, number>();

/** How wide text is in the ring's font, in ems, as the page draws it. */
const measure = (text: string, weight: number): number => {
  const key = `${weight} ${text}`;
  let width = measured.get(key);

  if (width === undefined) {
    measuring ??= document.createElement('canvas').getContext('2d');

    if (!measuring) {
      return text.length * 0.62;
    }

    measuring.font = `${weight} 100px ${RING_FONT}`;
    width = measuring.measureText(text).width / 100;
    measured.set(key, width);
  }

  return width;
};

/** The colors of a ring and its text: dark on light planets, light on dark ones. */
interface Ink {
  track: string;
  arc: string;
  main: string;
  detail: string;
  badge: string;
  check: string;
}

const INKS: Record<'dark' | 'light', Ink> = {
  dark: {
    track: 'rgba(16, 24, 40, 0.14)',
    arc: 'rgba(16, 24, 40, 0.74)',
    main: 'rgba(16, 24, 40, 0.86)',
    detail: 'rgba(16, 24, 40, 0.62)',
    badge: 'rgba(16, 24, 40, 0.8)',
    check: '#FFFFFF',
  },
  light: {
    track: 'rgba(255, 255, 255, 0.2)',
    arc: 'rgba(255, 255, 255, 0.86)',
    main: 'rgba(255, 255, 255, 0.95)',
    detail: 'rgba(255, 255, 255, 0.74)',
    badge: 'rgba(255, 255, 255, 0.9)',
    check: '#101828',
  },
};

/** The dark ink's luminance: rgb(16, 24, 40). */
const DARK_INK = 0.0092;

/** WCAG's contrast ratio between a lighter and a darker luminance. */
const contrast = (lighter: number, darker: number): number => {
  return (lighter + 0.05) / (darker + 0.05);
};

let painting: CanvasRenderingContext2D | null | undefined;
const inks = new Map<string, 'dark' | 'light'>();

/** sRGB channels of any color the page can draw, from 0 to 1, read back from a pixel. */
const channels = (color: string): [number, number, number] | null => {
  painting ??= (() => {
    const canvas = document.createElement('canvas');
    canvas.width = 1;
    canvas.height = 1;
    return canvas.getContext('2d', { willReadFrequently: true });
  })();

  if (!painting) {
    return null;
  }

  painting.clearRect(0, 0, 1, 1);
  painting.fillStyle = '#000000';
  painting.fillStyle = color;
  painting.fillRect(0, 0, 1, 1);
  const [red, green, blue] = painting.getImageData(0, 0, 1, 1).data;
  return [red / 255, green / 255, blue / 255];
};

/** How light a color looks, from 0 for black to 1 for white (WCAG's relative luminance). */
const luminance = ([red, green, blue]: [number, number, number]): number => {
  const linear = (channel: number) => (channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
  return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
};

/**
 * The ink that reads best on the node's planet, as Orbit picks its labels'
 * text: whichever of dark and light has more contrast on the planet's color.
 */
const inkOn = (element: HTMLElement): Ink => {
  const id = element.dataset.orbitFor;
  const root = element.closest('.orbit__contents')?.parentElement;
  const disc = id && root ? root.querySelector<SVGElement>(`.orbit-node[data-orbit-node-id="${CSS.escape(id)}"] .orbit-node__disc`) : null;
  const fill = disc ? getComputedStyle(disc).fill : '';

  if (!fill || !fill.trim() || fill === 'none') {
    return INKS.dark;
  }

  let ink = inks.get(fill);

  if (!ink) {
    const color = channels(fill);
    const planet = color ? luminance(color) : 1;
    ink = contrast(1, planet) > contrast(planet, DARK_INK) ? 'light' : 'dark';
    inks.set(fill, ink);
  }

  return INKS[ink];
};

/** What a ring shows: how full it is, its text laid out inside it, and its ink. */
interface RingLook {
  share: number;
  layout: RingLayout;
  ink: Ink;
}

/** A ring's share as it last moved: from one share to another, starting at a time, so a new drawing goes on from where it is. */
interface RingMotion {
  from: number;
  to: number;
  at: number;
}

/** The share each node's ring last went to, by node, so the next drawing of it moves on from there. */
const motions = new WeakMap<OrbitNode, RingMotion>();

/** Where a ring is now, partway through its motion. */
const shareNow = (motion: RingMotion, now: number): number => {
  const progress = Math.min(1, Math.max(0, (now - motion.at) / RING_MOTION));
  // Close to the easing above, enough to pick a motion up where it was.
  const eased = 1 - (1 - progress) ** 4;
  return motion.from + (motion.to - motion.from) * eased;
};

const reducedMotion = (): boolean => {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
};

const dash = (share: number): string => {
  return `${RING_LENGTH * share} ${RING_LENGTH}`;
};

/**
 * Draws a ring into a node's box: the track, the share filled with round
 * ends from the top, and the text. From another share, the ring moves there.
 */
const paintRing = (element: HTMLElement, look: RingLook, from: number | null): void => {
  const svg = svgElement('svg', { viewBox: '0 0 100 100', width: 100, height: 100 });
  const ring = { cx: 50, cy: 50, r: RING_RADIUS, fill: 'none', 'stroke-width': 10 };
  const ink = look.ink;
  svg.append(svgElement('circle', { ...ring, stroke: ink.track }));
  const moving = from !== null && Math.abs(from - look.share) > 0.0005 && !reducedMotion();

  if (look.share > 0 || moving) {
    const arc = svgElement('circle', {
      ...ring,
      stroke: ink.arc,
      'stroke-linecap': 'round',
      'stroke-dasharray': dash(look.share),
      transform: 'rotate(-90 50 50)',
    });

    // An empty ring has no arc: its round ends would still show as a dot.
    if (look.share <= 0) {
      arc.setAttribute('opacity', '0');
    }

    svg.append(arc);

    if (moving && from !== null) {
      arc.animate(
        [
          { strokeDasharray: dash(from), opacity: from > 0 ? 1 : 0 },
          { strokeDasharray: dash(look.share), opacity: look.share > 0 ? 1 : 0 },
        ],
        { duration: RING_MOTION, easing: RING_EASING },
      );
    }
  }

  const { main, detail, check } = look.layout;
  svg.append(ringLine(main.text, main.size, main.y, MAIN_WEIGHT, ink.main));

  if (detail) {
    svg.append(ringLine(detail.text, detail.size, detail.y, DETAIL_WEIGHT, ink.detail));
  }

  if (check !== null) {
    const badge = svgElement('g', { transform: `translate(50 ${check})` });
    badge.append(svgElement('circle', { r: CHECK_RADIUS, fill: ink.badge }));
    badge.append(svgElement('path', {
      d: 'M -3.6 0.2 L -1.1 2.7 L 3.8 -2.3',
      fill: 'none',
      stroke: ink.check,
      'stroke-width': 2.1,
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
    }));
    svg.append(badge);

    // Reached just now: the check comes in as the ring closes.
    if (moving && from !== null && from < 1) {
      badge.animate(
        [{ opacity: 0, transform: `translate(50px, ${check}px) scale(0.4)` }, { opacity: 1, transform: `translate(50px, ${check}px) scale(1)` }],
        { duration: 260, delay: RING_MOTION * 0.55, easing: RING_EASING, fill: 'backwards' },
      );
    }
  }

  element.replaceChildren(svg);
};

/** A line of the ring's text, its digits centered on `y`. */
const ringLine = (text: string, size: number, y: number, weight: number, fill: string): SVGElement => {
  const line = svgElement('text', {
    x: 50,
    y: Math.round((y + (CAP_HEIGHT * size) / 2) * 100) / 100,
    'text-anchor': 'middle',
    'font-size': Math.round(size * 100) / 100,
    'font-weight': weight,
    'font-family': RING_FONT,
    fill,
  });
  line.textContent = text;
  return line;
};

/** A ring on the map, drawn again in place when what it shows changes. */
interface LiveRing {
  node: OrbitNode;
  element: HTMLElement;
  look: () => RingLook;

  /** What it shows now, to tell when that changes. */
  drawn: string;
}

const liveRings = new Set<LiveRing>();

const drawn = (look: RingLook): string => {
  return JSON.stringify(look);
};

/** Draws a ring and keeps it up to date; the cleanup forgets it. */
const liveRing = (node: OrbitNode, element: HTMLElement, look: () => RingLook): () => void => {
  const ring: LiveRing = { node, element, look, drawn: '' };
  showRing(ring, true);
  liveRings.add(ring);

  return () => {
    liveRings.delete(ring);
  };
};

/** Draws a ring as it is now, moving from where its last drawing was when `move`. */
const showRing = (ring: LiveRing, move: boolean): void => {
  const look = ring.look();
  const now = performance.now();
  const before = motions.get(ring.node);
  const from = before ? shareNow(before, now) : null;

  if (!before || before.to !== look.share) {
    motions.set(ring.node, { from: from ?? look.share, to: look.share, at: now });
  }

  paintRing(ring.element, look, move ? from : null);
  ring.drawn = drawn(look);
};

/**
 * Draws again the rings whose ideas changed: a goal stepped on, or an idea
 * under a Progress ring checked off. The map only draws again the nodes an
 * edit touched, and a ring shows what's under its node too. After new
 * colors, rings take the ink that reads on their planets again.
 */
export const refreshRings = (): void => {
  for (const ring of liveRings) {
    if (drawn(ring.look()) !== ring.drawn) {
      showRing(ring, true);
    }
  }
};

if (typeof document !== 'undefined' && document.fonts && typeof document.fonts.addEventListener === 'function') {
  // Text measured while the page drew it in a fallback font is measured again once the font is in.
  document.fonts.addEventListener('loadingdone', () => {
    measured.clear();

    for (const ring of liveRings) {
      showRing(ring, false);
    }
  });
}

/** A ring's share and text: how full it is, and what it says. */
export interface Ring {
  share: number;
  text: RingText;
}

/** The ring of a Progress idea: the share of the goals and to-dos under it done, in the middle, or "–" with none. */
export const progressRing = (node: OrbitNode): Ring => {
  const { done, total } = progressOf(node);
  const share = total === 0 ? 0 : done / total;
  return { share, text: { main: [total === 0 ? '–' : `${Math.round(share * 100)}%`], detail: null, check: false } };
};

/**
 * The ring of a goal: as far along as the goal is, with its number big in
 * the middle and its target under it, or "left" when it counts down; reached,
 * the ring closes and a check shows under the number. A goal without a
 * target of its own shows the goals and to-dos under it, as a Progress idea.
 * `words` are the ring's, in the page's language.
 */
export const goalRing = (node: OrbitNode, locale?: string, words: RingWords = ENGLISH_RING_WORDS): Ring => {
  const goal = readGoal(node);
  return goal.target === null ? progressRing(node) : { share: goalShare(goal), text: ringText(goal, locale, words) };
};

/** A ring that fills as the node's goals and to-dos get done, with the share in the middle. */
const drawProgress = (node: OrbitNode, element: HTMLElement): () => void => {
  return liveRing(node, element, () => {
    const { share, text } = progressRing(node);
    return { share, layout: layoutRing(text, measure), ink: inkOn(element) };
  });
};

/** A goal's ring, as `goalRing` says, in the page's words. */
const drawGoal = (node: OrbitNode, element: HTMLElement): () => void => {
  return liveRing(node, element, () => {
    const { share, text } = goalRing(node, undefined, pageWords());
    return { share, layout: layoutRing(text, measure), ink: inkOn(element) };
  });
};

/** A person's initials, big, the way an avatar shows them. */
const drawInitials = (node: OrbitNode, element: HTMLElement): void => {
  const initials = node.text.split(/\s+/).filter(Boolean).slice(0, 2).map((word) => Array.from(word)[0]?.toUpperCase() ?? '').join('');
  const badge = document.createElement('div');
  badge.textContent = initials || '?';
  badge.style.cssText = 'width:100px;height:100px;display:grid;place-items:center;font:700 40px/1 "Google Sans Flex", Roboto, sans-serif;letter-spacing:-0.02em;color:rgba(16,24,40,0.82)';
  element.replaceChildren(badge);
};

/** What the kinds of ideas are called in English, until the app names them in its language (see words.ts). */
export const KIND_NAMES: Record<string, string> = {
  milestone: 'Milestone',
  question: 'Question',
  decision: 'Decision',
  risk: 'Risk',
  source: 'Source',
  person: 'Person',
  progress: 'Progress',
  goal: 'Goal',
};

/**
 * The kinds of ideas the app offers, each with its own look, named in the
 * page's language: Orbit shows the names in tooltips and the detail sheet,
 * and says them to screen readers. Each call makes them anew, so a map
 * given them again after the language changes names its ideas again.
 */
export const nodeTypes = (): Record<string, OrbitNodeType> => {
  const names = { ...KIND_NAMES, ...pageWords().kinds };
  return {
    milestone: { label: names.milestone, icon: '🏁', tokens: { 'node-stroke': '#B45309', 'node-stroke-width': '3', 'node-stroke-tint': '100%' } },
    question: { label: names.question, icon: '❓', tokens: { 'node-stroke': '#7C3AED', 'node-stroke-width': '2.5', 'node-stroke-tint': '100%' } },
    decision: { label: names.decision, icon: '⚖️', tokens: { 'node-stroke': '#0E7490', 'node-stroke-width': '2.5', 'node-stroke-tint': '100%' } },
    risk: { label: names.risk, icon: '⚠️', tokens: { 'node-stroke': '#DC2626', 'node-stroke-width': '3', 'node-stroke-tint': '100%' } },
    source: { label: names.source, icon: '📚' },
    person: { label: names.person, render: drawInitials },
    progress: { label: names.progress, render: drawProgress },
    goal: { label: names.goal, icon: '🎯', render: drawGoal },
  };
};
