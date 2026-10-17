/**
 * The page inside the app's WebViews. It draws a map with Orbit and talks
 * to the app: commands arrive through `window.orbitApp.receive()`, and
 * messages go back through the `orbitNative` channel the app adds.
 *
 * A page also works without a map on it: the app asks it for thumbnails,
 * pictures and conversions (commands with a `job` number), which it answers
 * with a `result` message carrying the same number.
 *
 * The page speaks the app's language: the app tells it which as it starts,
 * and again if it changes (`language`), with its words for what the page
 * says itself (see words.ts). Orbit's own words follow the page's `lang`.
 */
import {
  createOrbitMap,
  parseOrbit,
  renderToSVGString,
  serializeOrbitMarkdown,
  serializeOrbitYAML,
  themeTokens,
} from '@restless-dreams/orbit';
import type {
  OrbitDocument,
  OrbitDocumentOptions,
  OrbitInsets,
  OrbitNode,
  OrbitNodeInput,
  OrbitRendererTheme,
  OrbitSpinOptions,
  OrbitSVGRenderer,
  ViewMode,
} from '@restless-dreams/orbit';
import { mergeAttributes, readGoal, stepGoal } from './goal';
import { aboutAppSettings, automaticRings, knownStyle, nodeTypeFor, nodeTypes, refreshRings, themeOption, withAutomaticRings } from './look';
import type { AppTheme } from './look';
import { pageWords, speak } from './words';
import type { PageWords } from './words';

/** How the app wants maps to behave. */
interface MapSettings {
  haptics: boolean;
  snap: boolean;
  longPressDelay: number;
}

/** The frontmatter options the app shows and changes; `null` removes one. */
interface MapOptions {
  theme?: string | null;
  palette?: string[] | null;
  showRings?: boolean | null;
  orbitAnimation?: boolean | null;
  orbitSpeed?: number | null;
  maxDepth?: number | null;
  viewMode?: ViewMode | null;

  /** The app's own: `false` turns off the rings ideas with to-dos or goals show by themselves. Saved only while off. */
  progressRings?: boolean | null;
}

interface Media {
  src: string;
  alt: string | null;
  caption: string | null;
  provider: string | null;
  thumbnail: string | null;
}

/** A node, as the app's outline, search and editor see it. */
interface NodeInfo {
  id: string;
  text: string;
  parentId: string | null;
  depth: number;
  index: number;
  childCount: number;
  icon: string | null;

  /** The node's own color. */
  color: string | null;

  /** The color it is drawn with. */
  drawnColor: string | null;

  status: string | null;
  priority: number | null;
  tags: string[];
  collapsed: boolean;
  type: string | null;
  link: string | null;
  note: string | null;
  noteFormat: string | null;
  image: Media | null;
  video: Media | null;
  embed: Media | null;

  /** Attributes Orbit doesn't use itself, as they were read: a goal's numbers, or another app's. */
  attrs: Record<string, unknown> | null;
}

/** The whole map for the app: every node, its options and the reader's place. */
interface MapState {
  title: string;
  nodes: NodeInfo[];
  options: MapOptions;
  path: string[];
  mode: ViewMode;

  /** The style the map picks, when Orbit knows it. */
  style: string | null;

  /** That style's background, for the screen around the map; null when it has none. */
  surface: string | null;

  /** Why the map can't be changed here, when it can't (see `Lock`). */
  readOnly: Lock | null;

  /** What Orbit couldn't read, in its words, when that's why. */
  problem: string | null;
}

/**
 * Why a map is only shown, never changed or saved: Orbit couldn't read all
 * of its text (`unreadable`), a newer Orbit wrote it (`newer`), or the app
 * says so: the map was deleted, or the app's files are a newer version's.
 */
type Lock = 'unreadable' | 'newer' | 'deleted' | 'storage';

/** What reading a map's text found: whether it can be changed without losing any of it. */
interface Reading {
  lock: 'unreadable' | 'newer' | null;

  /** What Orbit couldn't read, in its words. */
  problem: string | null;

  /** Orbit found no ideas at all in text that isn't empty: the page says so in the app's words, in the language it has when it says it. */
  noIdeas?: boolean;
}

/** A node that got a new id from new text, so the app can follow it. */
interface Renamed {
  from: string;
  to: string;
}

interface Diagnostic {
  message: string;
  line: number | null;
  severity: string;
}

interface SnapshotJob {
  type: 'snapshot';
  job: number;
  format: 'png' | 'webp' | 'svg';

  /** A map to draw; without one, the map on screen as it is now. */
  markdown?: string | null;

  width?: number | null;
  height?: number | null;

  /** Pixels per CSS pixel in the picture. */
  scale?: number | null;

  theme?: AppTheme | null;

  /** No background behind the map. */
  transparent?: boolean | null;

  /** Draw the map in this style instead of its own; an empty name draws it in the app's colors. */
  style?: string | null;

  viewMode?: ViewMode | null;
  focus?: string | null;
  baseUrl?: string | null;
}

/** An idea caught on the go: its text, and the whole of it as its note when the text is cut short. */
interface CapturedIdea {
  text: string;
  note?: string | null;
}

type Command =
  | { type: 'language'; words: Partial<PageWords> }
  | { type: 'load'; markdown: string; theme: AppTheme; settings: MapSettings; insets?: OrbitInsets | null; baseUrl?: string | null; focus?: string | null; view?: ViewMode | null; mapId?: string | null }
  | { type: 'insets'; insets: OrbitInsets }
  | { type: 'unload' }
  | { type: 'theme'; theme: AppTheme }
  | { type: 'settings'; settings: MapSettings }
  | { type: 'edit'; id: string }
  | { type: 'editNew'; parentId: string; index?: number | null }
  | { type: 'stopEditing'; save: boolean }
  | { type: 'add'; parentId: string; node: Record<string, unknown>; index?: number | null }
  | { type: 'update'; id: string; changes: Record<string, unknown> }
  | { type: 'addToGoal'; id: string; amount: number }
  | { type: 'remove'; id: string }
  | { type: 'move'; id: string; parentId: string; index?: number | null }
  | { type: 'duplicate'; id: string }
  | { type: 'focus'; id: string }
  | { type: 'view'; mode: ViewMode }
  | { type: 'zoom'; factor: number }
  | { type: 'fit' }
  | { type: 'restore'; markdown: string }
  | { type: 'preview'; markdown: string; editable: boolean }
  | { type: 'source'; text: string }
  | { type: 'options'; options: MapOptions }
  | { type: 'present'; on: boolean }
  | { type: 'lock'; reason: Lock | null }
  | SnapshotJob
  | { type: 'convert'; job: number; text: string; to: 'markdown' | 'yaml' }
  | { type: 'check'; job: number; text: string }
  | { type: 'addIdeas'; job: number; markdown: string; ideas: (string | CapturedIdea)[]; parentId: string | null };

type Job = Extract<Command, { job: number }>;

type Message =
  | { type: 'hello' }
  | { type: 'ready'; state: MapState }
  | { type: 'saved'; kind: string; markdown: string; state: MapState; added: string | null; renamed: Renamed | null; removed: string | null }
  | { type: 'state'; state: MapState }
  | { type: 'focus'; path: string[]; mode: ViewMode }
  | { type: 'context'; id: string; x: number; y: number }
  | { type: 'held' }
  | { type: 'drag'; phase: 'start' | 'over' | 'end'; id: string; parentId: string | null; index: number | null }
  | { type: 'details'; id: string }
  | { type: 'editorOpen'; id: string | null; parentId: string | null }
  | { type: 'editorClose' }
  | { type: 'source'; diagnostics: Diagnostic[]; drawn: boolean }
  | { type: 'open'; url: string }
  | { type: 'haptic' }
  | { type: 'result'; job: number; value?: unknown; error?: string }
  | { type: 'error'; message: string };

declare global {
  interface Window {
    orbitNative?: { postMessage(message: string): void };
    orbitApp: { receive(command: Command): void };
  }
}

let map: OrbitSVGRenderer | null = null;
/** The app's id for the map on the page: every message about it says so, so none reaches another map's screen. */
let currentMap: string | null = null;
/** What reading the map on the page found. */
let reading: Reading = { lock: null, problem: null };
/** Why the app keeps the map from changes, if it does. */
let appLock: Lock | null = null;
let appTheme: AppTheme = { dark: false, tokens: {} };
let settings: MapSettings = { haptics: true, snap: true, longPressDelay: 400 };
let baseUrl: string | undefined;
let presenting = false;
/** Whether the map on the page shows rings by itself on ideas with to-dos or goals: its frontmatter says. */
let ringsOn = true;
/** The parts of the map the app covers with its toolbar or captions: Orbit fits views into the rest. */
let insets: OrbitInsets = {};
/** While several edits make one change, they are saved once, at the end. */
let batching = false;
/** The node the app is changing, to tell it when the node's id changes with its text. */
let updating: string | null = null;
/** The app is removing a node, and offers its own Undo for it; a node removed on the page, by its Delete key, the app hears of. */
let removing = false;
/** A finger, pen or mouse button is down on the page. */
let pressing = false;
/**
 * A long press's menu, waiting for the finger to lift. The idea lifts at
 * once, and moving on drags it; a menu opened under the finger would take
 * the rest of the press from the map, so it opens once the finger lifts
 * without moving.
 */
let heldMenu: { id: string; x: number; y: number } | null = null;

const post = (message: Message): void => {
  window.orbitNative?.postMessage(JSON.stringify(currentMap === null ? message : { ...message, map: currentMap }));
};

/**
 * A map's text as Orbit reads it. The idea Orbit makes up at the top of a
 * map that's just a list is named in the app's language; saving writes the
 * list as it was, whatever its name.
 */
const parse = (text: string): OrbitDocument => {
  return parseOrbit(text, { untitled: pageWords().untitledMap });
};

/** Diagnostics that say the map came from a newer Orbit, which may have written what this one doesn't know. */
const NEWER = new Set(['unsupported-schema-version', 'newer-format']);

/**
 * Whether the map in `text`, as Orbit read it, can be changed and saved
 * without losing any of the text: not when Orbit couldn't read part of it
 * (an error, or text it found nothing in), nor when a newer Orbit wrote it.
 * Such a map is only shown. Text without headings or lists can be changed:
 * it's the note of a map with no ideas yet, and saving keeps it.
 */
const readingOf = (parsed: OrbitDocument, text: string): Reading => {
  const diagnostics = parsed.diagnostics ?? [];
  const error = diagnostics.find((item) => item.severity === 'error');

  if (error) {
    return { lock: 'unreadable', problem: error.message };
  }

  if (parsed.root.text.trim() === '' && parsed.root.children.length === 0 && text.trim() !== '') {
    return { lock: 'unreadable', problem: null, noIdeas: true };
  }

  const newer = diagnostics.find((item) => NEWER.has(item.code));

  if (newer) {
    return { lock: 'newer', problem: newer.message };
  }

  return { lock: null, problem: null };
};

/** What a reading found wrong, in Orbit's words, or in the app's when Orbit found no ideas at all. */
const problemOf = (read: Reading): string | null => {
  return read.noIdeas ? pageWords().noIdeas : read.problem;
};

/** Why the map can't be changed now, if it can't. */
const lock = (): Lock | null => {
  return appLock ?? reading.lock;
};

/** Whether ideas can be added, typed, moved and removed on the map now. */
const editable = (): boolean => {
  return !presenting && lock() === null;
};

// The map as the app sees it

const media = (value: OrbitNode['image'] | OrbitNode['video'] | OrbitNode['embed']): Media | null => {
  if (!value?.src) {
    return null;
  }

  const item = value as Partial<Media> & { src: string };
  return {
    src: item.src,
    alt: item.alt ?? null,
    caption: item.caption ?? null,
    provider: item.provider ?? null,
    thumbnail: item.thumbnail ?? null,
  };
};

const nodeInfo = (renderer: OrbitSVGRenderer, node: OrbitNode, depth: number, index: number): NodeInfo => {
  return {
    id: node.id,
    text: node.text,
    parentId: node.parent?.id ?? null,
    depth,
    index,
    childCount: node.children.length,
    icon: node.icon ?? null,
    color: node.color ?? null,
    drawnColor: renderer.getNodeColor(node.id),
    status: node.status ?? null,
    priority: typeof node.priority === 'number' && Number.isFinite(node.priority) ? Math.round(node.priority) : null,
    tags: node.tags ?? [],
    collapsed: node.collapsed === true,
    type: node.type ?? null,
    link: node.link ?? null,
    note: node.note ?? null,
    noteFormat: node.noteFormat ?? null,
    image: media(node.image),
    video: media(node.video),
    embed: media(node.embed),
    attrs: node.attrs && Object.keys(node.attrs).length > 0 ? node.attrs : null,
  };
};

const stateOf = (renderer: OrbitSVGRenderer): MapState | null => {
  const current = renderer.getDocument();

  if (!current) {
    return null;
  }

  const nodes: NodeInfo[] = [];

  const visit = (node: OrbitNode, depth: number, index: number) => {
    nodes.push(nodeInfo(renderer, node, depth, index));
    node.children.forEach((child, childIndex) => visit(child, depth + 1, childIndex));
  };

  visit(current.root, 0, 0);
  const { options } = current;
  const style = knownStyle(options.theme);
  const surface = style ? themeTokens(style).surface : null;
  return {
    title: options.title ?? current.root.text,
    nodes,
    options: {
      theme: options.theme ?? null,
      palette: options.palette ?? null,
      showRings: options.showRings ?? null,
      orbitAnimation: options.orbitAnimation ?? null,
      orbitSpeed: options.orbitSpeed ?? null,
      maxDepth: options.maxDepth ?? null,
      viewMode: options.viewMode ?? null,
      progressRings: automaticRings(current) ? null : false,
    },
    path: renderer.getFocusPath(),
    mode: renderer.getViewMode(),
    style,
    surface: surface && surface !== 'transparent' ? surface : null,
    readOnly: lock(),
    problem: problemOf(reading),
  };
};

const postFocus = (): void => {
  if (map) {
    post({ type: 'focus', path: map.getFocusPath(), mode: map.getViewMode() });
  }
};

const postSaved = (kind: string, node: OrbitNode | null = null): void => {
  // A map that can't be changed is never saved: its file stays exactly as it is.
  if (!map || batching || lock() !== null) {
    return;
  }

  const state = stateOf(map);

  if (state) {
    const added = kind === 'add' ? node?.id ?? null : null;
    const renamed = kind === 'update' && node && updating && node.id !== updating ? { from: updating, to: node.id } : null;
    const removed = kind === 'remove' && node && !removing ? node.text : null;
    post({ type: 'saved', kind, markdown: map.toMarkdown(), state, added, renamed, removed });
  }
};

/** The map's state after a change that isn't saved, such as new colors from the app. */
const postState = (): void => {
  const state = map ? stateOf(map) : null;

  if (state) {
    post({ type: 'state', state });
  }
};

/** Runs several edits as one change: one save, and one step to undo. */
const batch = (kind: string, change: () => OrbitNode | null): void => {
  batching = true;
  let result: OrbitNode | null = null;

  try {
    result = change();
  } finally {
    batching = false;
  }

  postSaved(kind, result);
};

/** Orbit's spin haptics call navigator.vibrate(); the app plays a native tick instead. */
const routeHaptics = (): void => {
  Object.defineProperty(navigator, 'vibrate', {
    configurable: true,
    value: () => {
      post({ type: 'haptic' });
      return true;
    },
  });
};

const spinOptions = (): OrbitSpinOptions => {
  return { haptics: settings.haptics, snap: settings.snap ? 'planet' : false };
};

const load = (command: Extract<Command, { type: 'load' }>): void => {
  const element = document.getElementById('map');

  if (!element) {
    return;
  }

  unload();
  appTheme = command.theme;
  settings = command.settings;
  insets = command.insets ?? {};
  baseUrl = command.baseUrl ?? undefined;
  currentMap = command.mapId ?? null;
  appLock = null;
  const parsed = parse(command.markdown);
  reading = readingOf(parsed, command.markdown);
  ringsOn = automaticRings(parsed);
  map = createOrbitMap(element, parsed, {
    showControls: false,
    showDetailSheet: false,
    // The idea in focus offers its details and editor in the app's toolbar instead.
    showActionButtons: false,
    insets,
    editable: editable(),
    // With a keyboard, Tab and Shift+Enter add ideas and Delete removes them, as in mind-map apps.
    editingKeys: true,
    // Where the reader left the map: the app remembers it, the file doesn't.
    focus: command.focus ?? undefined,
    viewMode: command.view ?? undefined,
    baseUrl,
    theme: themeOption(appTheme, knownStyle(parsed.options.theme)),
    spin: spinOptions(),
    zoom: { wheel: 'always' },
    longPressDelay: command.settings.longPressDelay,
    nodeTypes: nodeTypes(),
    nodeTypeFor: nodeTypeFor(() => ringsOn),
    onFocusChange: postFocus,
    onViewModeChange: postFocus,
    onNodeContext: ({ node, x, y }) => {
      if (!pressing) {
        // The context menu key: no press to wait for.
        post({ type: 'context', id: node.id, x, y });
        return;
      }

      heldMenu = { id: node.id, x, y };
      post({ type: 'held' });
    },
    onNodeDrag: ({ phase, node, parent, index }) => {
      if (phase === 'start') {
        heldMenu = null;
      }

      post({ type: 'drag', phase, id: node.id, parentId: parent?.id ?? null, index });
    },
    onEdit: ({ kind, node }) => {
      refreshRings();
      postSaved(kind, node);
    },
    onEditorOpen: ({ node, parent }) => post({ type: 'editorOpen', id: node?.id ?? null, parentId: parent?.id ?? null }),
    onEditorClose: () => post({ type: 'editorClose' }),
    onAction: ({ node, action }) => {
      if (action === 'link') {
        if (node.link && !node.link.startsWith('#')) {
          post({ type: 'open', url: new URL(node.link, baseUrl ?? location.href).href });
          return false;
        }

        // A link to another node of the map: Orbit flies there.
        return undefined;
      }

      // Notes, pictures, videos, pages and details open in the app's own sheet.
      post({ type: 'details', id: node.id });
      return false;
    },
  });
  const state = stateOf(map);

  if (state) {
    post({ type: 'ready', state });
  }
};

const unload = (): void => {
  map?.destroy();
  map = null;
  presenting = false;
  currentMap = null;
  reading = { lock: null, problem: null };
  appLock = null;
  document.getElementById('map')?.replaceChildren();
};

/** Changes the map's frontmatter options, as the style and layout sheet does. */
const setOptions = (changes: MapOptions): void => {
  const current = map?.getDocument();

  if (!map || !current) {
    return;
  }

  const { progressRings, ...orbitChanges } = changes;
  const options: Record<string, unknown> = { ...current.options };

  for (const [key, value] of Object.entries(orbitChanges)) {
    if (value === null || value === undefined) {
      delete options[key];
    } else {
      options[key] = value;
    }
  }

  let next: OrbitDocument = { ...current, options: options as OrbitDocumentOptions };

  if (progressRings !== undefined) {
    // Kept in the frontmatter with keys Orbit doesn't read: only while off, and never as `true`.
    next = withAutomaticRings(next, progressRings !== false);
  }

  ringsOn = automaticRings(next);
  map.setOptions({ theme: themeOption(appTheme, knownStyle(next.options.theme)) });
  map.render(next);

  if (changes.viewMode) {
    map.setViewMode(changes.viewMode);
  }

  // Planets of a new style take new colors: rings take the ink that reads on them.
  requestAnimationFrame(() => refreshRings());
  postSaved('options');
};

/**
 * `null` in a change from the app removes that attribute. Custom attributes
 * (`attrs`) are merged into the node's own, so the app changes a goal's
 * numbers without knowing every other attribute; `null` there removes one.
 */
const nodeChanges = (changes: Record<string, unknown>, node: OrbitNode | null = null): OrbitNodeInput => {
  const input: Record<string, unknown> = Object.fromEntries(Object.entries(changes).map(([key, value]) => [key, value === null ? undefined : value]));

  if (changes.attrs && typeof changes.attrs === 'object' && !Array.isArray(changes.attrs)) {
    input.attrs = mergeAttributes(node?.attrs, changes.attrs as Record<string, unknown>);
  }

  return input as OrbitNodeInput;
};

/** What `addNode` needs to make a node like this one: its text and attributes. */
const nodeInput = (node: OrbitNode): OrbitNodeInput & { text: string } => {
  const { id: _id, level: _level, children: _children, source: _source, ...attributes } = node;
  return { ...attributes, text: node.text } as OrbitNodeInput & { text: string };
};

const findNode = (root: OrbitNode, id: string): OrbitNode | null => {
  if (root.id === id) {
    return root;
  }

  for (const child of root.children) {
    const found = findNode(child, id);

    if (found) {
      return found;
    }
  }

  return null;
};

/** A copy of a node and everything under it, right after it. */
const duplicate = (renderer: OrbitSVGRenderer, id: string): OrbitNode | null => {
  const current = renderer.getDocument();
  const node = current ? findNode(current.root, id) : null;
  const parent = node?.parent;

  if (!node || !parent) {
    return null;
  }

  const copyUnder = (original: OrbitNode, parentId: string, index?: number): OrbitNode | null => {
    const copy = renderer.addNode(parentId, nodeInput(original), index);

    if (copy) {
      for (const child of original.children) {
        copyUnder(child, copy.id);
      }
    }

    return copy;
  };

  return copyUnder(node, parent.id, parent.children.indexOf(node) + 1);
};

/** What Orbit says about a map's text, but for the keys the app keeps in its frontmatter itself. */
const diagnosticsOf = (parsed: OrbitDocument): Diagnostic[] => {
  if (parsed.diagnostics) {
    return parsed.diagnostics
      .filter((item) => !aboutAppSettings(item))
      .map((item) => ({ message: item.message, line: item.line ?? null, severity: item.severity }));
  }

  return parsed.warnings.filter((message) => !aboutAppSettings({ message })).map((message) => ({ message, line: null, severity: 'warning' }));
};

/** Draws text from the app's source editor as it is typed, and says what Orbit made of it. */
const showSource = (text: string): void => {
  let parsed: OrbitDocument;

  try {
    parsed = parse(text);
  } catch (error) {
    post({ type: 'source', diagnostics: [{ message: error instanceof Error ? error.message : String(error), line: null, severity: 'error' }], drawn: false });
    return;
  }

  reading = readingOf(parsed, text);

  if (map) {
    ringsOn = automaticRings(parsed);
    map.setOptions({ theme: themeOption(appTheme, knownStyle(parsed.options.theme)), editable: editable() });
    map.render(parsed);
  }

  post({ type: 'source', diagnostics: diagnosticsOf(parsed), drawn: map !== null });
  postState();
};

/** Shows the map to an audience: no editing. */
const present = (on: boolean): void => {
  presenting = on;
  map?.stopEditing(true);
  map?.setOptions({ editable: editable() });
};

/** The id Orbit gives the idea it makes up at the top of a map that's just a list, in every language. */
const UNTITLED_MAP = 'untitled-map';

/**
 * Speaks the app's language, as the page starts and whenever it changes:
 * Orbit's own words follow the page's `lang`, and the map on the page names
 * its kinds of ideas, its rings' words and the idea Orbit made up at its top
 * in the new language. Nothing about the map's text changes.
 */
const language = (words: Partial<PageWords>): void => {
  const before = pageWords();
  const now = speak(words);

  if (!map) {
    return;
  }

  // Setting the options again writes Orbit's words in the page's new language, and new node types draw the ideas again.
  map.setOptions({ nodeTypes: nodeTypes() });
  const current = map.getDocument();

  if (current && lock() === null && current.root.id === UNTITLED_MAP && current.root.text === before.untitledMap && now.untitledMap !== before.untitledMap) {
    // A map that's just a list, read again: its text is the same, only the made-up idea's name changes.
    map.render(parse(map.toMarkdown()));
  }

  postState();
};

// Pictures, files and conversions

/** Pictures in an SVG drawn as an image can't load files: put them in the SVG itself. */
const inlinePictures = async (svg: string): Promise<string> => {
  if (!svg.includes('<image')) {
    return svg;
  }

  const parsed = new DOMParser().parseFromString(svg, 'image/svg+xml');

  await Promise.all(Array.from(parsed.querySelectorAll('image')).map(async (image) => {
    const href = image.getAttribute('href') ?? image.getAttribute('xlink:href');

    if (!href || href.startsWith('data:')) {
      return;
    }

    try {
      const response = await fetch(href);

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();

      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(blob);
      });

      image.setAttribute('href', data);
      image.removeAttribute('xlink:href');
    } catch {
      // A picture from a site that doesn't allow it: the export goes without it.
      image.remove();
    }
  }));

  return new XMLSerializer().serializeToString(parsed);
};

/** The SVG without the rectangle that paints its background. */
const withoutBackground = (svg: string): string => {
  const parsed = new DOMParser().parseFromString(svg, 'image/svg+xml');
  const root = parsed.documentElement;
  const background = Array.from(root.children).find((child) => child.tagName === 'rect'
    && child.getAttribute('width') === root.getAttribute('width')
    && child.getAttribute('height') === root.getAttribute('height'));

  if (!background) {
    return svg;
  }

  background.remove();
  return new XMLSerializer().serializeToString(parsed);
};

const rasterize = async (svg: string, width: number, height: number, scale: number, type: string): Promise<string> => {
  const url = URL.createObjectURL(new Blob([await inlinePictures(svg)], { type: 'image/svg+xml' }));

  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(width * scale));
    canvas.height = Math.max(1, Math.round(height * scale));
    const context = canvas.getContext('2d');

    if (!context) {
      throw new Error('No canvas to draw the picture on');
    }

    context.drawImage(image, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL(type, 0.92);
  } finally {
    URL.revokeObjectURL(url);
  }
};

/** The theme for a picture of a map: its own style, or the app's colors, with or without their background. */
const snapshotTheme = (parsed: OrbitDocument, job: SnapshotJob): OrbitRendererTheme | string => {
  const style = job.style != null ? knownStyle(job.style || undefined) : knownStyle(parsed.options.theme);

  if (style) {
    return style;
  }

  // The app's colors, as the map on screen has them: Orbit's Material theme grown from the app's color.
  const option = themeOption(job.theme ?? appTheme, null)!;
  return job.transparent ? { ...option, tokens: { ...option.tokens, surface: 'transparent' } } : option;
};

let snapshotCount = 0;

const snapshot = async (job: SnapshotJob): Promise<string> => {
  let svg: string;
  let width: number;
  let height: number;

  if (job.markdown != null) {
    const parsed = parse(job.markdown);
    width = job.width ?? 800;
    height = job.height ?? 500;
    snapshotCount += 1;
    svg = renderToSVGString(parsed, {
      width,
      height,
      viewMode: job.viewMode ?? 'focus',
      focus: job.focus ?? undefined,
      theme: snapshotTheme(parsed, job),
      baseUrl: job.baseUrl ?? undefined,
      // The picture's few words of Orbit's own, such as an empty map's, in the app's language.
      language: pageWords().language,
      nodeTypes: nodeTypes(),
      nodeTypeFor: nodeTypeFor(() => automaticRings(parsed)),
      idPrefix: `snapshot-${snapshotCount}`,
    });
  } else {
    if (!map) {
      throw new Error('No map on screen');
    }

    svg = map.toSVGString();
    const size = /<svg[^>]*?\swidth="([\d.]+)"[^>]*?\sheight="([\d.]+)"/.exec(svg);
    width = Number(size?.[1] ?? 800);
    height = Number(size?.[2] ?? 600);

    if (job.transparent) {
      svg = withoutBackground(svg);
    }
  }

  if (job.format === 'svg') {
    return inlinePictures(svg);
  }

  return rasterize(svg, width, height, job.scale ?? 2, job.format === 'png' ? 'image/png' : 'image/webp');
};

/** A map's text in the other flavor. Text Orbit couldn't read all of has no conversion: it would lose what wasn't read. */
const convert = (text: string, to: 'markdown' | 'yaml'): string => {
  const parsed = parse(text);
  const read = readingOf(parsed, text);

  if (read.lock === 'unreadable') {
    throw new Error(problemOf(read) ?? pageWords().textNotRead);
  }

  return to === 'yaml' ? serializeOrbitYAML(parsed) : serializeOrbitMarkdown(parsed);
};

/**
 * A map's text with `ideas` added under `parentId` (default: its top), for
 * ideas caught without the map open. Orbit adds them on a map no one sees,
 * so the text comes back the way Orbit writes it, ids and all.
 */
const addIdeas = (markdown: string, ideas: (string | CapturedIdea)[], parentId: string | null): string => {
  const parsed = parse(markdown);
  // A map Orbit can't read all of, or one a newer Orbit wrote, isn't written over: it would lose what wasn't read.
  const { lock: unread } = readingOf(parsed, markdown);

  if (unread === 'unreadable') {
    throw new Error(pageWords().mapNotAddedTo);
  }

  if (unread === 'newer') {
    throw new Error(pageWords().mapFromNewerOrbit);
  }

  const host = document.createElement('div');
  host.style.cssText = 'position:fixed;left:-10000px;top:0;width:1px;height:1px;overflow:hidden;visibility:hidden';
  document.body.appendChild(host);
  const capture = createOrbitMap(host, parsed, { interactive: false, nodeTypes: nodeTypes() });

  try {
    const parent = parentId ?? capture.getDocument()?.root.id;

    if (!parent) {
      throw new Error('No map to add to');
    }

    for (const idea of ideas) {
      const input = typeof idea === 'string' ? { text: idea } : { text: idea.text, note: idea.note ?? undefined };

      if (!capture.addNode(parent, input)) {
        throw new Error(`No idea ${parent} to add to`);
      }
    }

    return capture.toMarkdown();
  } finally {
    capture.destroy();
    host.remove();
  }
};

/** What a file someone opens would make: its title, its size, what Orbit says about it, and the map as orbit-md. */
const check = (text: string): { title: string; nodes: number; diagnostics: Diagnostic[]; markdown: string } => {
  const parsed = parse(text);
  let nodes = 0;

  const count = (node: OrbitNode) => {
    nodes += 1;
    node.children.forEach(count);
  };

  count(parsed.root);
  return {
    title: parsed.options.title ?? parsed.root.text,
    nodes,
    diagnostics: diagnosticsOf(parsed),
    markdown: serializeOrbitMarkdown(parsed),
  };
};

const runJob = async (command: Job): Promise<void> => {
  try {
    let value: unknown;

    switch (command.type) {
      case 'snapshot':
        value = await snapshot(command);
        break;
      case 'convert':
        value = convert(command.text, command.to);
        break;
      case 'check':
        value = check(command.text);
        break;
      case 'addIdeas':
        value = addIdeas(command.markdown, command.ideas, command.parentId);
        break;
    }

    post({ type: 'result', job: command.job, value });
  } catch (error) {
    post({ type: 'result', job: command.job, error: error instanceof Error ? error.message : String(error) });
  }
};

// Commands from the app

/** Commands that change the map: none run while it can't be saved. */
const CHANGES = new Set<Command['type']>(['edit', 'editNew', 'add', 'update', 'addToGoal', 'remove', 'move', 'duplicate', 'options']);

const handle = (command: Command): void => {
  switch (command.type) {
    case 'language':
      language(command.words);
      return;
    case 'load':
      load(command);
      return;
    case 'unload':
      unload();
      return;
    case 'snapshot':
    case 'convert':
    case 'check':
    case 'addIdeas':
      void runJob(command);
      return;
    case 'theme':
      appTheme = command.theme;
      map?.setOptions({ theme: themeOption(appTheme, knownStyle(map.getDocument()?.options.theme)) });
      requestAnimationFrame(() => refreshRings());
      postState();
      return;
    case 'settings':
      settings = command.settings;
      map?.setOptions({ spin: spinOptions(), longPressDelay: settings.longPressDelay });
      return;
    case 'insets':
      insets = command.insets;
      map?.setOptions({ insets });
      return;
  }

  const renderer = map;

  if (!renderer) {
    return;
  }

  if (lock() !== null && CHANGES.has(command.type)) {
    // Nothing changes a map that can't be saved: what's on screen stays what's in its file.
    return;
  }

  switch (command.type) {
    case 'edit':
      renderer.editNode(command.id);
      break;
    case 'editNew':
      renderer.editNewNode(command.parentId, command.index ?? undefined);
      break;
    case 'stopEditing':
      renderer.stopEditing(command.save);
      break;
    case 'add':
      renderer.addNode(command.parentId, { text: '', ...nodeChanges(command.node) } as OrbitNodeInput & { text: string }, command.index ?? undefined);
      break;
    case 'update': {
      const tree = renderer.getDocument()?.root;
      const node = tree ? findNode(tree, command.id) : null;
      updating = command.id;

      try {
        renderer.updateNode(command.id, nodeChanges(command.changes, node));
      } finally {
        updating = null;
      }

      break;
    }
    case 'addToGoal': {
      // Added here, to the number the map has now, so quick steps from the app all count, however fast they come.
      const tree = renderer.getDocument()?.root;
      const node = tree ? findNode(tree, command.id) : null;

      if (!node || !Number.isFinite(command.amount)) {
        break;
      }

      const current = stepGoal(readGoal(node), command.amount);
      renderer.updateNode(command.id, { attrs: mergeAttributes(node.attrs, { current }) });
      break;
    }
    case 'remove':
      removing = true;

      try {
        renderer.removeNode(command.id);
      } finally {
        removing = false;
      }

      break;
    case 'move':
      renderer.moveNode(command.id, command.parentId, command.index ?? undefined);
      break;
    case 'duplicate':
      batch('add', () => duplicate(renderer, command.id));
      break;
    case 'focus':
      renderer.flyTo(command.id);
      break;
    case 'view':
      renderer.setViewMode(command.mode);
      break;
    case 'zoom':
      renderer.zoomBy(command.factor);
      break;
    case 'fit':
      renderer.fit();
      break;
    case 'restore': {
      // The map's text as the app has it now (an undo, a version brought back, a change from elsewhere): drawn, never
      // saved, since the app saves that very text itself, and Orbit writing it its own way could change it.
      const parsed = parse(command.markdown);
      reading = readingOf(parsed, command.markdown);
      ringsOn = automaticRings(parsed);
      renderer.setOptions({ theme: themeOption(appTheme, knownStyle(parsed.options.theme)), editable: editable() });
      renderer.render(parsed);
      postState();
      postFocus();
      break;
    }
    case 'preview': {
      // A version to look at, not saved: one from the history, read-only, or the map as it is now again.
      const parsed = parse(command.markdown);
      renderer.stopEditing(true);

      if (command.editable) {
        reading = readingOf(parsed, command.markdown);
      }

      ringsOn = automaticRings(parsed);
      renderer.setOptions({ theme: themeOption(appTheme, knownStyle(parsed.options.theme)), editable: command.editable && editable() });
      renderer.render(parsed);
      postFocus();
      break;
    }
    case 'lock':
      appLock = command.reason;
      renderer.stopEditing(appLock === null);
      renderer.setOptions({ editable: editable() });
      postState();
      break;
    case 'source':
      showSource(command.text);
      break;
    case 'options':
      setOptions(command.options);
      break;
    case 'present':
      present(command.on);
      break;
  }
};

window.orbitApp = {
  receive(command) {
    try {
      handle(command);
    } catch (error) {
      post({ type: 'error', message: error instanceof Error ? error.message : String(error) });
    }
  },
};
window.addEventListener('error', (event) => post({ type: 'error', message: event.message }));

window.addEventListener('pointerdown', () => {
  pressing = true;
}, true);

window.addEventListener('pointerup', () => {
  pressing = false;

  // Let go without moving on: the long press's menu opens.
  if (heldMenu) {
    post({ type: 'context', ...heldMenu });
    heldMenu = null;
  }
}, true);

window.addEventListener('pointercancel', () => {
  pressing = false;
  heldMenu = null;
}, true);

/** Keys the map answers to. */
const MAP_KEYS = new Set([
  'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'Enter', ' ', 'Tab', 'Insert', 'Delete',
  'F2', 'F10', 'ContextMenu', 'Escape', 'Backspace', '+', '=', '-', '_', '0',
]);

// The first key a keyboard sends, with nothing on the page focused yet, finds the map: its focused idea
// takes the focus and answers the key, as it would have once the map had the focus. Keys the map leaves
// go on to the app, such as Escape at the top of the map.
window.addEventListener('keydown', (event) => {
  const focused = document.activeElement;

  if (!map || event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey || !MAP_KEYS.has(event.key)
    || (focused && focused !== document.body && focused !== document.documentElement)) {
    return;
  }

  const stop = document.getElementById('map')?.querySelector<SVGElement>('[data-orbit-node-id][tabindex="0"]');

  if (!stop) {
    return;
  }

  stop.focus({ preventScroll: true });
  const answer = new KeyboardEvent('keydown', { key: event.key, code: event.code, shiftKey: event.shiftKey, bubbles: true, cancelable: true });

  if (!stop.dispatchEvent(answer)) {
    event.preventDefault();
  }
}, true);

routeHaptics();
post({ type: 'hello' });
