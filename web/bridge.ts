/**
 * The page inside the app's WebViews. It draws a map with Orbit and talks
 * to the app on the `orbitNative` channel the app adds: messages go out on
 * it, and the app's commands come back on it as JSON, once the page says
 * hello. Nothing the app sends is ever run as code.
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
  exportOPML,
  parseOrbit,
  renderToSVGString,
  serializeOrbitMarkdown,
  serializeOrbitYAML,
  setNoteCheckbox,
  themeTokens,
} from '@restless-dreams/orbit';
import type {
  OrbitDocument,
  OrbitDocumentOptions,
  OrbitFile,
  OrbitInsets,
  OrbitNewNode,
  OrbitNode,
  OrbitNodeInput,
  OrbitRendererTheme,
  OrbitSpinOptions,
  OrbitSVGRenderer,
  OrbitViewMode,
} from '@restless-dreams/orbit';
import { attempt, changeable, refused } from './changing';
import type { Lock } from './changing';
import { mergeAttributes, readGoal, stepGoal } from './goal';
import { aboutAppSettings, automaticRings, knownStyle, nodeTypeFor, nodeTypes, refreshRings, tagsOf, themeOption, withAutomaticRings, withTags } from './look';
import type { AppTheme } from './look';
import { editedMap } from './map-edits';
import { MEDIA_KEYS, mergedMedia } from './media';
import { importFile } from './other-apps';
import { reportErrors } from './page-errors';
import { caughtIdea, mapFromText, pastedText } from './paste';
import { readingOf } from './reading';
import type { Reading } from './reading';
import { renamings } from './renaming';
import type { Renamed } from './renaming';
import { study } from './study';
import type { StudyCommand } from './study';
import { wholeNumber } from './whole-numbers';
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
  animation?: boolean | null;
  speed?: number | null;
  maxDepth?: number | null;
  view?: OrbitViewMode | null;

  /** The app's own: `false` turns off the rings ideas with to-dos or goals show by themselves. Saved only while off. */
  progressRings?: boolean | null;

  /** The app's own: the map's tags, which Home groups maps by. None takes the key out. */
  tags?: string[] | null;
}

interface Media {
  src: string;
  alt: string | null;
  caption: string | null;
  provider: string | null;
  thumbnail: string | null;

  /** A picture's motion, when it's a live photo's: its video, and where in it the still was taken, in seconds. */
  motion: { src: string; still: number | null } | null;
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

  /** Its date, as Orbit writes it: `2026-11-20`, or `2026-11-20 09:30` with a time; one Orbit can't read stays in `attrs`, as written. */
  due: string | null;

  tags: string[];
  collapsed: boolean;
  type: string | null;

  /** The version of its type's fields it was written for (`goal@2`), when it says. */
  typeVersion: number | null;

  link: string | null;
  note: string | null;
  noteFormat: string | null;
  image: Media | null;
  video: Media | null;
  embed: Media | null;

  /** Attributes Orbit doesn't use itself, as they were read: a goal's numbers, or another app's. */
  attrs: Record<string, unknown> | null;

  /** Its sound: a voice memo, music or a recording. */
  audio: Media | null;

  /** The files it holds: PDFs, spreadsheets, any file. */
  files: FileInfo[];
}

/** One of an idea's files, as the app shows it: its size only as a whole number of bytes, which the app's number holds. */
export interface FileInfo {
  src: string;
  name: string | null;
  type: string | null;
  size: number | null;
}

/** The whole map for the app: every node, its options and the reader's place. */
export interface MapState {
  title: string;
  nodes: NodeInfo[];
  options: MapOptions;
  path: string[];
  mode: OrbitViewMode;

  /** The style the map picks, when Orbit knows it. */
  style: string | null;

  /** That style's background, for the screen around the map; null when it has none. */
  surface: string | null;

  /** Why the map can't be changed here, when it can't (see `Lock`). */
  readOnly: Lock | null;

  /** What Orbit couldn't read, in its words, when that's why. */
  problem: string | null;
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

  view?: OrbitViewMode | null;
  focus?: string | null;
  baseUrl?: string | null;
}

/**
 * The app's commands (`MapCommand` and the language command in the app's
 * page/). protocol-examples.ts has one of each, as the app writes it, which
 * the app's tests check against its own.
 */
export type Command =
  | { type: 'language'; words: Partial<PageWords> }
  | { type: 'load'; markdown: string; theme: AppTheme; settings: MapSettings; insets?: OrbitInsets | null; baseUrl?: string | null; focus?: string | null; view?: OrbitViewMode | null; mapId?: string | null; revision?: number }
  | { type: 'insets'; insets: OrbitInsets; glide?: boolean }
  | { type: 'unload' }
  | { type: 'theme'; theme: AppTheme }
  | { type: 'settings'; settings: MapSettings }
  | { type: 'edit'; id: string }
  | { type: 'editNew'; parentId: string; index?: number | null }
  | { type: 'stopEditing'; save: boolean }
  | { type: 'add'; parentId: string; node: Record<string, unknown>; index?: number | null }
  | { type: 'update'; id: string; changes: Record<string, unknown> }
  | { type: 'addToGoal'; id: string; amount: number }
  | { type: 'checkNote'; id: string; line: number; checked: boolean }
  | { type: 'addFiles'; id: string; files: FileInfo[] }
  | { type: 'removeFile'; id: string; src: string }
  | { type: 'remove'; id: string }
  | { type: 'move'; id: string; parentId: string; index?: number | null }
  | { type: 'duplicate'; id: string }
  | { type: 'focus'; id: string }
  | { type: 'view'; mode: OrbitViewMode }
  | { type: 'zoom'; factor: number }
  | { type: 'fit' }
  | { type: 'restore'; markdown: string; revision?: number }
  | { type: 'preview'; markdown: string; editable: boolean }
  | { type: 'source'; text: string }
  | { type: 'options'; options: MapOptions }
  | { type: 'present'; on: boolean }
  | { type: 'lock'; reason: Lock | null }
  | SnapshotJob
  | { type: 'convert'; job: number; text: string; to: 'markdown' | 'yaml' | 'opml' }
  | { type: 'check'; job: number; text: string }
  | { type: 'importFile'; job: number; data: string; name: string | null }
  | { type: 'addIdeas'; job: number; markdown: string; text: string; parentId: string | null }
  | { type: 'editMap'; job: number; markdown: string; title: string | null; tags: string[] | null }
  | { type: 'copy'; job: number; id: string }
  | { type: 'paste'; job: number; parentId: string; text: string }
  | { type: 'dropOver'; x: number; y: number }
  | { type: 'dropLeave' }
  | { type: 'dropAt'; job: number; x: number; y: number }
  | { type: 'addNodes'; job: number; parentId: string; nodes: Record<string, unknown>[] }
  | { type: 'mapFromText'; job: number; text: string; subject: string | null }
  | { type: 'cancel'; job: number }
  | { type: 'spotlight'; id: string; insets: OrbitInsets; share: number | null }
  | { type: 'endSpotlight' }
  | { type: 'pictureShown'; job: number; src: string }
  | StudyCommand;

type Job = Extract<Command, { job: number }>;

/** What the page tells the app (`MapMessage` in the app's page/): protocol-examples.ts has one of each, which the app's tests read. */
export type Message =
  | { type: 'hello' }
  | { type: 'ready'; state: MapState }
  | { type: 'saved'; kind: string; markdown: string; state: MapState; added: string | null; renamed: Renamed | null; removed: string | null; revision: number }
  | { type: 'state'; state: MapState }
  | { type: 'focus'; path: string[]; mode: OrbitViewMode }
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
  | { type: 'clipboard'; action: ClipboardAction; id: string }
  | { type: 'error'; message: string }
  | { type: 'declined'; change: 'add' | 'update' | 'options' | 'checkNote'; message: string }
  | { type: 'dropTarget'; id: string | null }
  | { type: 'dragOut'; id: string }
  | Spotlight;

/**
 * A node the app brought up close (`spotlight`, to sketch on it): where its
 * circle is on the page, in CSS px from the page's top left, as the flight
 * to it starts, once it rests there (again whenever it rests somewhere new,
 * as the page changes size), and once the map is back as it was (`end`,
 * at once when the node isn't drawn, so the app never waits). `scrim` is the
 * color the map dims with, which the app dims its own bars with too.
 */
interface Spotlight {
  type: 'spotlight';
  id: string;
  phase: 'start' | 'rest' | 'end';
  x: number;
  y: number;
  radius: number;
  scrim: string | null;
}

/** What Ctrl+C, Ctrl+X and Ctrl+V ask the app for: it has the clipboard. */
type ClipboardAction = 'copy' | 'cut' | 'paste';

declare global {
  interface Window {
    orbitNative?: AppChannel;
  }
}

/** The channel the app adds to its pages: messages to the app, and the app's commands as they come. */
interface AppChannel {
  postMessage(message: string): void;
  addEventListener(type: 'message', listener: (event: MessageEvent<string>) => void): void;
}

let map: OrbitSVGRenderer | null = null;
/** The app's id for the map on the page: every message about it says so, so none reaches another map's screen. */
let currentMap: string | null = null;
/** What reading the map on the page found. */
let reading: Reading = { lock: null, problem: null };
/** Why the app keeps the map from changes, if it does. */
let appLock: Lock | null = null;
/** An earlier version of the map, from its history, is on show: it's only looked at, never changed or saved. */
let showingVersion = false;
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
/** Which idea an edit gave a new id: the app's change, or text typed on the map. */
const renames = renamings();
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
/** The idea lit up for something dragged onto the map from another app, while there's one. */
let dropTarget: string | null = null;
/** The idea being dragged on the map, while one is, and whether it went out of the window to another app. */
let draggedIdea: string | null = null;
let draggedOut = false;

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

/** What a reading found wrong, in Orbit's words, or in the app's when Orbit found no ideas at all. */
const problemOf = (read: Reading): string | null => {
  return read.noIdeas ? pageWords().noIdeas : read.problem;
};

/** Why the map can't be changed now, if it's locked: what the app is told. */
const lock = (): Lock | null => {
  return appLock ?? reading.lock;
};

/** Whether the map on the page can be changed and saved now: not while it's locked, nor while an earlier version is on show. */
const canChange = (): boolean => {
  return changeable(lock(), showingVersion);
};

/** Whether ideas can be added, typed, moved and removed on the map now. */
const editable = (): boolean => {
  return !presenting && canChange();
};

// The map as the app sees it

const media = (value: OrbitNode['image'] | OrbitNode['video'] | OrbitNode['embed'] | OrbitNode['audio']): Media | null => {
  if (!value?.src) {
    return null;
  }

  const item = value as Partial<Omit<Media, 'motion'>> & { src: string; motion?: { src?: unknown; still?: unknown } };
  const motion = typeof item.motion?.src === 'string' && item.motion.src !== '' ? item.motion : null;
  return {
    src: item.src,
    alt: item.alt ?? null,
    caption: item.caption ?? null,
    provider: item.provider ?? null,
    thumbnail: item.thumbnail ?? null,
    motion: motion ? { src: motion.src as string, still: typeof motion.still === 'number' && Number.isFinite(motion.still) ? motion.still : null } : null,
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
    // Rounded, as the app shows it; a number past what the app holds, such as 1e12, is the nearest it does (whole-numbers.ts).
    priority: wholeNumber(node.priority, Math.round),
    due: node.due ?? null,
    tags: node.tags ?? [],
    collapsed: node.collapsed === true,
    type: node.type ?? null,
    // A version past what the app holds (goal@99999999999) is still newer than any it knows.
    typeVersion: wholeNumber(node.typeVersion, Math.ceil),
    link: node.link ?? null,
    note: node.note ?? null,
    noteFormat: node.noteFormat ?? null,
    image: media(node.image),
    video: media(node.video),
    embed: media(node.embed),
    attrs: node.attrs && Object.keys(node.attrs).length > 0 ? node.attrs : null,
    audio: media(node.audio),
    files: (node.files ?? []).filter((file) => typeof file.src === 'string' && file.src !== '').map(fileInfo),
  };
};

/** A file as the app has it: a size that isn't a whole number of bytes, or is past what's exact, said as none. */
const fileInfo = (file: OrbitFile): FileInfo => {
  return {
    src: file.src,
    name: file.name ?? null,
    type: file.type ?? null,
    size: Number.isSafeInteger(file.size) ? file.size! : null,
  };
};

/** A file the app sends, as Orbit takes it: without the fields it said nothing of. */
const orbitFile = (file: FileInfo): OrbitFile => {
  return {
    src: file.src,
    ...(file.name === null || file.name === undefined ? {} : { name: file.name }),
    ...(file.type === null || file.type === undefined ? {} : { type: file.type }),
    ...(file.size === null || file.size === undefined ? {} : { size: file.size }),
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
      animation: options.animation ?? null,
      speed: options.speed ?? null,
      // Orbit shows the levels up to maxDepth, so 1.5 shows what 2 does.
      maxDepth: wholeNumber(options.maxDepth, Math.ceil),
      view: options.view ?? null,
      progressRings: automaticRings(current) ? null : false,
      tags: tagsOf(current),
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

/**
 * The app's revision of the map's text the page last drew whole (`load`,
 * `restore`): each save says it, so a save made before a change from
 * elsewhere reached the page is never taken for one made after it.
 */
let revision = 0;

const postSaved = (kind: string, node: OrbitNode | null = null): void => {
  // Told of every edit, saved or not, so only the one right after the field for typing on the map closed is taken for its.
  const renamed = renames.edited(kind, node, updating);

  // A map that can't be changed is never saved, nor an earlier version on show: the map's file stays exactly as it is.
  if (!map || batching || !canChange()) {
    return;
  }

  const state = stateOf(map);

  if (state) {
    const added = kind === 'add' ? node?.id ?? null : null;
    const removed = kind === 'remove' && node && !removing ? node.text : null;
    post({ type: 'saved', kind, markdown: map.toMarkdown(), state, added, renamed, removed, revision });
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
  revision = command.revision ?? 0;
  appTheme = command.theme;
  settings = command.settings;
  insets = command.insets ?? {};
  baseUrl = command.baseUrl ?? undefined;
  currentMap = command.mapId ?? null;
  appLock = null;
  showingVersion = false;
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
    view: command.view ?? undefined,
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
        draggedIdea = node.id;
        draggedOut = false;
      } else if (phase === 'end') {
        draggedIdea = null;
      }

      post({ type: 'drag', phase, id: node.id, parentId: parent?.id ?? null, index });
    },
    onEdit: ({ kind, node }) => {
      refreshRings();
      postSaved(kind, node);
    },
    // Each frame of the flights is the page's own: the app hears where the node rests, and when the map is back.
    onSpotlight: ({ phase, node, x, y, radius }) => {
      if (phase !== 'move') {
        post({ type: 'spotlight', id: node.id, phase, x, y, radius, scrim: scrimColor() });
      }
    },
    onEditorOpen: ({ node, parent }) => post({ type: 'editorOpen', id: node?.id ?? null, parentId: parent?.id ?? null }),
    onEditorClose: ({ node }) => {
      // The idea as it was: what was typed is saved next, and may give it a new id.
      renames.closed(node);
      post({ type: 'editorClose' });
    },
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
  showingVersion = false;
  dropTarget = null;
  draggedIdea = null;
  document.getElementById('map')?.replaceChildren();
};

/** Changes the map's frontmatter options, as the style and layout sheet does. */
const setOptions = (changes: MapOptions): void => {
  const current = map?.getDocument();

  if (!map || !current) {
    return;
  }

  const { progressRings, tags, ...orbitChanges } = changes;
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

  if (tags !== undefined) {
    next = withTags(next, tags ?? []);
  }

  ringsOn = automaticRings(next);
  map.setOptions({ theme: themeOption(appTheme, knownStyle(next.options.theme)) });
  map.render(next);

  if (changes.view) {
    map.setViewMode(changes.view);
  }

  // Planets of a new style take new colors: rings take the ink that reads on them.
  requestAnimationFrame(() => refreshRings());
  postSaved('options');
};

/**
 * `null` in a change from the app removes that attribute. Custom attributes
 * (`attrs`) are merged into the node's own, so the app changes a goal's
 * numbers without knowing every other attribute; `null` there removes one.
 * A picture, video, sound or page for the same file is merged into the
 * node's own the same way (see `mergedMedia`), so a new caption keeps every
 * field the app doesn't know.
 */
const nodeChanges = (changes: Record<string, unknown>, node: OrbitNode | null = null): OrbitNodeInput => {
  const input: Record<string, unknown> = Object.fromEntries(Object.entries(changes).map(([key, value]) => [key, value === null ? undefined : value]));

  if (changes.attrs && typeof changes.attrs === 'object' && !Array.isArray(changes.attrs)) {
    input.attrs = mergeAttributes(node?.attrs, changes.attrs as Record<string, unknown>);
  }

  for (const key of MEDIA_KEYS) {
    const change = changes[key];

    if (change && typeof change === 'object' && !Array.isArray(change)) {
      const merged = mergedMedia(node?.[key as keyof OrbitNode], change as Record<string, unknown>);

      if (merged === undefined) {
        // A change to a file the node doesn't have changes nothing.
        delete input[key];
      } else {
        input[key] = merged;
      }
    }
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

/** An idea and everything under it, as `addNode` takes them. */
const newNode = (node: OrbitNode): OrbitNewNode => ({ ...nodeInput(node), children: node.children.map(newNode) });

const countIdeas = (node: OrbitNewNode): number => 1 + (node.children ?? []).reduce((total, child) => total + countIdeas(child), 0);

/** An idea and the ideas under it as plain text, for other apps: its name, then a list. */
const outline = (node: OrbitNode): string => {
  const name = (idea: OrbitNode): string => [idea.icon, idea.text].filter(Boolean).join(' ');
  const lines = [name(node)];

  const list = (parent: OrbitNode, depth: number): void => {
    for (const child of parent.children) {
      lines.push(`${'  '.repeat(depth)}- ${name(child)}`);
      list(child, depth + 1);
    }
  };

  list(node, 0);
  return lines.join('\n');
};

/** An idea and everything under it, as orbit-md for Orbit to paste, and as an outline for other apps. */
const copyIdea = (renderer: OrbitSVGRenderer, id: string): { markdown: string; text: string } => {
  const current = renderer.getDocument();
  const node = current ? findNode(current.root, id) : null;

  if (!node) {
    throw new Error(`No idea ${id} to copy`);
  }

  return { markdown: serializeOrbitMarkdown({ root: node, options: {}, warnings: [] }, { keepSource: false }), text: outline(node) };
};

/** Pasted text as ideas under `parentId`, after its last, as one change: one save, and one step to undo. */
const paste = (renderer: OrbitSVGRenderer, parentId: string, text: string): { count: number } => {
  const current = renderer.getDocument();

  if (!current || !findNode(current.root, parentId)) {
    throw new Error(`No idea ${parentId} to paste under`);
  }

  const { markdown, whole } = pastedText(text);
  const parsed = parse(markdown);
  const ideas = (whole ? [parsed.root] : parsed.root.children).map(newNode);

  batch('add', () => {
    let first: OrbitNode | null = null;

    for (const idea of ideas) {
      const added = renderer.addNode(parentId, idea);
      first ??= added;
    }

    return first;
  });

  return { count: ideas.reduce((total, idea) => total + countIdeas(idea), 0) };
};

/** Ideas the app made, such as pictures dropped from another app, under `parentId`, after its last, as one change: one save, and one step to undo. */
const addNodes = (renderer: OrbitSVGRenderer, parentId: string, nodes: Record<string, unknown>[]): { count: number } => {
  const current = renderer.getDocument();

  if (!current || !findNode(current.root, parentId)) {
    throw new Error(`No idea ${parentId} to add to`);
  }

  let count = 0;

  batch('add', () => {
    let first: OrbitNode | null = null;

    for (const node of nodes) {
      // One Orbit declines is left out, said back, and the others still come as one change.
      const input = { text: '', ...nodeChanges(node) } as OrbitNewNode;
      const added = changing('add', () => renderer.addNode(parentId, input));

      if (added) {
        count += 1;
        first ??= added;
      }
    }

    return first;
  });

  return { count };
};

// Drops from other apps

/**
 * Where something dragged onto the map from another app, at (`x`, `y`) on
 * the page, would go: the idea under it, or the one in focus where there's
 * none. Nowhere on a map that can't be changed now.
 */
const dropPlace = (x: number, y: number): OrbitNode | null => {
  if (!map || !editable()) {
    return null;
  }

  return map.getNodeAt(x, y) ?? map.getFocusedNode();
};

/** Lights up where a drag from another app would go, as the map's own drags light theirs, and tells the app when that changes. */
const showDropTarget = (id: string | null): void => {
  if (id === dropTarget) {
    return;
  }

  dropTarget = id;
  map?.setDropTarget(id);
  post({ type: 'dropTarget', id });
};

/** Answers a job with what `work` gives, or what went wrong. */
/** Makes a change the app asked for: one Orbit declines is said back, with why, and the map stays as it was. */
const changing = <Value>(change: 'add' | 'update' | 'options', work: () => Value): Value | null => {
  const tried = attempt(work);

  if (!tried.made) {
    post({ type: 'declined', change, message: tried.reason });
    return null;
  }

  return tried.value;
};

const answer = (job: number, work: () => unknown): void => {
  try {
    post({ type: 'result', job, value: work() });
  } catch (error) {
    post({ type: 'result', job, error: error instanceof Error ? error.message : String(error) });
  }
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

  if (current && canChange() && current.root.id === UNTITLED_MAP && current.root.text === before.untitledMap && now.untitledMap !== before.untitledMap) {
    // A map that's just a list, read again: its text is the same, only the made-up idea's name changes.
    map.render(parse(map.toMarkdown()));
  }

  postState();
};

// Pictures, files and conversions

/**
 * The jobs at work that take a while, pictures of maps, each with what
 * calls it off: the app sends `cancel` for a job no one waits for any more,
 * as a thumbnail that scrolled away or one it gave up on. Its pictures stop
 * loading, it stops at its next step, and it answers nothing.
 */
const working = new Map<number, AbortController>();

/** Goes on with a job, unless the app called it off: then it stops here. */
const goOn = (signal?: AbortSignal): void => {
  if (signal?.aborted) {
    throw new Error('Called off');
  }
};

// A node up close, to sketch on

/** The color the map dims with around a node up close, as the page resolves it from the map's style. */
const scrimColor = (): string | null => {
  const scrim = document.querySelector('.orbit__spotlight-scrim');
  return scrim ? getComputedStyle(scrim).fill || null : null;
};

/**
 * Brings a node up close for the app to sketch on: the app hears where it
 * starts from at once, then where it rests. A node the map doesn't draw now
 * ends there, so the app never waits for it.
 */
const spotlight = (command: Extract<Command, { type: 'spotlight' }>): void => {
  const brought = map?.spotlight(command.id, { insets: command.insets, share: command.share ?? undefined }) ?? false;
  const circle = brought ? map?.getNodeCircle(command.id) : null;

  if (circle) {
    post({ type: 'spotlight', id: command.id, phase: 'start', ...circle, scrim: scrimColor() });
  } else if (!brought) {
    post({ type: 'spotlight', id: command.id, phase: 'end', x: 0, y: 0, radius: 0, scrim: null });
  }
};

/**
 * Whether the picture at `src` (in the map's media) has loaded and been
 * drawn: the app waits for it before lifting its own canvas off a planet, so
 * the planet never shows without it. False when it can't be loaded.
 */
const pictureShown = async (src: string, signal?: AbortSignal): Promise<boolean> => {
  const picture = new Image();
  picture.src = new URL(src, baseUrl ?? location.href).href;

  try {
    await picture.decode();
  } catch {
    return false;
  }

  goOn(signal);
  // The map draws it on its next frame, and shows it on the one after.
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  goOn(signal);
  return true;
};

/** Pictures in an SVG drawn as an image can't load files: put them in the SVG itself. `signal` stops their loading. */
const inlinePictures = async (svg: string, signal?: AbortSignal): Promise<string> => {
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
      const response = await fetch(href, { signal });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();

      // Only a picture goes into the picture: whatever else an address answers with stays out of the export.
      if (!blob.type.startsWith('image/')) {
        throw new Error(`Not a picture: ${blob.type}`);
      }

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

const rasterize = async (svg: string, width: number, height: number, scale: number, type: string, signal?: AbortSignal): Promise<string> => {
  const inlined = await inlinePictures(svg, signal);
  goOn(signal);
  const url = URL.createObjectURL(new Blob([inlined], { type: 'image/svg+xml' }));

  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    // Called off while the picture was read: it isn't drawn.
    goOn(signal);
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

/** A picture of a map, as `job` asks; `signal` calls it off. */
const snapshot = async (job: SnapshotJob, signal?: AbortSignal): Promise<string> => {
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
      view: job.view ?? 'focus',
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
    return inlinePictures(svg, signal);
  }

  return rasterize(svg, width, height, job.scale ?? 2, job.format === 'png' ? 'image/png' : 'image/webp', signal);
};

/**
 * A map's text in the other flavor, or as OPML. Text Orbit couldn't read all
 * of, or that a newer Orbit wrote, has no conversion: it would lose what this
 * one didn't read or doesn't know.
 */
const convert = (text: string, to: 'markdown' | 'yaml' | 'opml'): string => {
  const parsed = parse(text);
  const read = readingOf(parsed, text);

  if (read.lock !== null) {
    throw new Error(problemOf(read) ?? pageWords().textNotRead);
  }

  if (to === 'opml') {
    return exportOPML(parsed);
  }

  return to === 'yaml' ? serializeOrbitYAML(parsed) : serializeOrbitMarkdown(parsed);
};

/**
 * A map's text with the ideas in `text` added under `parentId` (default:
 * its top), for ideas caught without the map open: a line each, nested as
 * pasting nests them (see paste.ts). Orbit adds them on a map no one sees,
 * so the text comes back the way Orbit writes it, ids and all.
 */
const addIdeas = (markdown: string, text: string, parentId: string | null): string => {
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

    const { markdown: caught, whole } = pastedText(text);
    const read = parse(caught);

    for (const idea of (whole ? [read.root] : read.root.children).map(newNode).map(caughtIdea)) {
      if (!capture.addNode(parent, idea)) {
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
  const calling = new AbortController();
  working.set(command.job, calling);

  try {
    let value: unknown;

    switch (command.type) {
      case 'snapshot':
        value = await snapshot(command, calling.signal);
        break;
      case 'convert':
        value = convert(command.text, command.to);
        break;
      case 'check':
        value = check(command.text);
        break;
      case 'importFile':
        value = importFile(command.data, command.name, pageWords().untitledMap);
        break;
      case 'addIdeas':
        value = addIdeas(command.markdown, command.text, command.parentId);
        break;
      case 'editMap':
        value = editedMap(command.markdown, { title: command.title, tags: command.tags }, pageWords().untitledMap);
        break;
      case 'mapFromText':
        value = mapFromText(command.text, command.subject);
        break;
      case 'pictureShown':
        value = await pictureShown(command.src, calling.signal);
        break;
    }

    // A job called off answers nothing: no one waits for it.
    if (!calling.signal.aborted) {
      post({ type: 'result', job: command.job, value });
    }
  } catch (error) {
    if (!calling.signal.aborted) {
      post({ type: 'result', job: command.job, error: error instanceof Error ? error.message : String(error) });
    }
  } finally {
    working.delete(command.job);
  }
};

// Commands from the app

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
    case 'importFile':
    case 'addIdeas':
    case 'editMap':
    case 'mapFromText':
    case 'pictureShown':
      void runJob(command);
      return;
    case 'cancel':
      working.get(command.job)?.abort();
      return;
    case 'spotlight':
      spotlight(command);
      return;
    case 'endSpotlight':
      map?.endSpotlight();
      return;
    case 'copy':
      answer(command.job, () => {
        if (!map) {
          throw new Error('No map to copy from');
        }

        return copyIdea(map, command.id);
      });

      return;
    case 'paste':
      answer(command.job, () => {
        // Nothing changes a map that can't be saved, as for the other changes, nor one presented or made into a path:
        // nothing pasted or dropped lands on it then, however late it comes.
        if (!map || !editable()) {
          throw new Error('This map can\'t be changed');
        }

        return paste(map, command.parentId, command.text);
      });

      return;
    case 'addNodes':
      answer(command.job, () => {
        if (!map || !editable()) {
          throw new Error('This map can\'t be changed');
        }

        return addNodes(map, command.parentId, command.nodes);
      });

      return;
    case 'dropAt':
      answer(command.job, () => {
        // Let go: the light goes out, and the app adds what was dropped where it says.
        const place = dropPlace(command.x, command.y);
        showDropTarget(null);
        return { id: place?.id ?? null };
      });

      return;
    case 'dropOver':
      showDropTarget(dropPlace(command.x, command.y)?.id ?? null);
      return;
    case 'dropLeave':
      showDropTarget(null);
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
      // A change made once (the talk panel coming or going) glides; one each frame (the keyboard) can't.
      map?.setOptions({ insets, insetsTransition: command.glide ? 'glide' : 'instant' });
      return;
  }

  const renderer = map;

  if (!renderer) {
    return;
  }

  if (refused(command.type, lock(), showingVersion)) {
    // Nothing changes a map that can't be saved, nor an earlier version on show: the map's file stays as it is.
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
    case 'add': {
      const input = { text: '', ...nodeChanges(command.node) } as OrbitNodeInput & { text: string };
      changing('add', () => renderer.addNode(command.parentId, input, command.index ?? undefined));
      break;
    }
    case 'update': {
      const tree = renderer.getDocument()?.root;
      const node = tree ? findNode(tree, command.id) : null;
      updating = command.id;

      try {
        const changes = nodeChanges(command.changes, node);
        changing('update', () => renderer.updateNode(command.id, changes));
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
    case 'addFiles': {
      // Added here, to the files the map has now, so files added one after another all stay; the ones it has are kept as
      // they are, with the fields Orbit doesn't read.
      const tree = renderer.getDocument()?.root;
      const node = tree ? findNode(tree, command.id) : null;

      if (!node) {
        break;
      }

      const files = node.files ?? [];
      const added = command.files.filter((file) => !files.some((there) => there.src === file.src)).map(orbitFile);

      if (added.length > 0) {
        changing('update', () => renderer.updateNode(command.id, { files: [...files, ...added] }));
      }

      break;
    }
    case 'removeFile': {
      const tree = renderer.getDocument()?.root;
      const node = tree ? findNode(tree, command.id) : null;
      const files = node?.files ?? [];
      const left = files.filter((file) => file.src !== command.src);

      if (node && left.length < files.length) {
        changing('update', () => renderer.updateNode(command.id, { files: left.length > 0 ? left : undefined }));
      }

      break;
    }
    case 'checkNote': {
      // Checked here, in the note the map has now, so quick presses all count however fast they come, and the file
      // changes just that box's line.
      const tree = renderer.getDocument()?.root;
      const node = tree ? findNode(tree, command.id) : null;
      const note = node?.note !== undefined && Number.isInteger(command.line) ? setNoteCheckbox(node.note, command.line, command.checked) : null;

      if (!node || note === null) {
        // The note changed since the app showed it: the app shows it as it is.
        post({ type: 'declined', change: 'checkNote', message: 'The note has no box on that line' });
      } else if (note !== node.note) {
        renderer.updateNode(command.id, { note });
      }

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
      // saved, since the app saves that very text itself, and Orbit writing it its own way could change it. A version
      // on show goes: what's drawn is the map as it is now, which takes changes again.
      showingVersion = false;
      revision = command.revision ?? revision;
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
      // A version to look at, not saved: one from the history, which nothing changes until the map as it is now
      // comes back, or the map as it is now again. What was being typed is kept first, in the map as it is now.
      const parsed = parse(command.markdown);
      renderer.stopEditing(true);
      showingVersion = !command.editable;

      if (command.editable) {
        reading = readingOf(parsed, command.markdown);
      }

      ringsOn = automaticRings(parsed);
      renderer.setOptions({ theme: themeOption(appTheme, knownStyle(parsed.options.theme)), editable: editable() });
      renderer.render(parsed);

      if (command.editable) {
        // The map as it is now is back: the app hears of it as it is, whatever it heard while a version was on show.
        postState();
      }

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
      changing('options', () => setOptions(command.options));
      break;
    case 'present':
      present(command.on);
      break;
    case 'hideChildren':
    case 'revealChildren':
      study(renderer, command);
      break;
  }
};

window.orbitNative?.addEventListener('message', (event) => {
  try {
    handle(JSON.parse(event.data) as Command);
  } catch (error) {
    post({ type: 'error', message: error instanceof Error ? error.message : String(error) });
  }
});

reportErrors(window, post);

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

/** How far past the window's edge (px) an idea dragged on the map goes before it leaves for another app: a drag along the edge stays on the map. */
const PAST_THE_EDGE = 24;

// An idea dragged out of the window, into another app beside Orbit, goes on as a drag that app can take: the app starts it.
window.addEventListener('pointermove', (event) => {
  if (draggedIdea === null || draggedOut) {
    return;
  }

  const { clientX: x, clientY: y } = event;

  if (x < -PAST_THE_EDGE || y < -PAST_THE_EDGE || x > innerWidth + PAST_THE_EDGE || y > innerHeight + PAST_THE_EDGE) {
    draggedOut = true;
    post({ type: 'dragOut', id: draggedIdea });
    // The press is the outside drag's now: the idea goes back to its place on the map, as when a press is taken away.
    event.target?.dispatchEvent(new PointerEvent('pointercancel', { pointerId: event.pointerId, pointerType: event.pointerType, isPrimary: event.isPrimary, clientX: x, clientY: y, bubbles: true }));
  }
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

/** Ctrl+C, Ctrl+X and Ctrl+V, by the letter typed or, on keyboards for other alphabets, where the letter is. */
const CLIPBOARD_LETTERS: Record<string, ClipboardAction> = { c: 'copy', x: 'cut', v: 'paste' };
const CLIPBOARD_CODES: Record<string, ClipboardAction> = { KeyC: 'copy', KeyX: 'cut', KeyV: 'paste' };

/** Whether text is being typed into `element`, which then has these keys to itself. */
const typing = (element: Element | null): boolean =>
  element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement || (element instanceof HTMLElement && element.isContentEditable);

// Copy, cut and paste work on the idea with the keyboard's focus, or the one in focus: the app has the clipboard.
// While the map is presented or made into a path, only copying does: nothing is cut from it or pasted onto it.
window.addEventListener('keydown', (event) => {
  const action = CLIPBOARD_LETTERS[event.key.toLowerCase()] ?? CLIPBOARD_CODES[event.code];
  const focused = document.activeElement;

  if (!map || !action || event.defaultPrevented || !(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey || typing(focused)) {
    return;
  }

  if (presenting && action !== 'copy') {
    return;
  }

  const id = focused?.closest('[data-orbit-node-id]')?.getAttribute('data-orbit-node-id') ?? map.getFocusPath().at(-1);

  if (!id) {
    return;
  }

  event.preventDefault();
  post({ type: 'clipboard', action, id });
}, true);

routeHaptics();
post({ type: 'hello' });
