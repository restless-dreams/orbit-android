// One of each message the app and its pages send each other, written the
// way the sender writes it: the app's commands with every field, nulls
// included, as kotlinx.serialization writes them, and the pages' messages
// as the pages post them. Each table has a key for every kind its type
// has, so a new kind needs an example before the pages type-check.
//
// build.ts writes them into the app's test resources
// (app/src/test/resources/page-protocol.json), where ProtocolTest checks
// that the app writes each command exactly so, and reads each message
// with all it says.

import type { AudienceCommand, MediaReport } from './audience';
import type { Command, MapState, Message } from './bridge';
import { ENGLISH_WORDS } from './words';

/** An example of each kind of message in `Union`, by its `type`. */
type OneOfEach<Union extends { type: string }> = { [Type in Union['type']]: Extract<Union, { type: Type }> };

const THEME = { dark: false, tokens: { background: '#fff8f6', 'node-text-dark': '#201a19' }, seed: '#8f4c38' };

const STATE: MapState = {
  title: 'Trip to Japan',
  nodes: [
    {
      id: 'trip-to-japan',
      text: 'Trip to Japan',
      parentId: null,
      depth: 0,
      index: 0,
      childCount: 1,
      icon: null,
      color: null,
      drawnColor: 'var(--_orbit-color-0)',
      status: null,
      priority: null,
      tags: [],
      collapsed: false,
      type: null,
      link: null,
      note: null,
      noteFormat: null,
      image: null,
      video: null,
      embed: null,
      attrs: null,
    },
    {
      id: 'tokyo',
      text: 'Tokyo',
      parentId: 'trip-to-japan',
      depth: 1,
      index: 0,
      childCount: 0,
      icon: '🗼',
      color: '#e91e63',
      drawnColor: '#e91e63',
      status: 'todo',
      priority: 2,
      tags: ['city'],
      collapsed: true,
      type: 'goal',
      link: 'https://example.com/tokyo',
      note: 'Book the **ryokan**',
      noteFormat: 'markdown',
      image: { src: 'media/tokyo.jpg', alt: 'The tower at night', caption: 'Tokyo Tower', provider: null, thumbnail: null },
      video: { src: 'https://www.youtube.com/watch?v=abc', alt: null, caption: null, provider: 'youtube', thumbnail: 'https://i.ytimg.com/vi/abc/hqdefault.jpg' },
      embed: null,
      attrs: { target: 3000, current: 1250, unit: 'USD' },
    },
  ],
  options: { theme: 'night', palette: ['#e91e63', '#3f51b5'], showRings: true, orbitAnimation: false, orbitSpeed: 1.5, maxDepth: 3, viewMode: 'focus', progressRings: false, tags: ['travel'] },
  path: ['trip-to-japan', 'tokyo'],
  mode: 'focus',
  style: 'night',
  surface: '#0b1020',
  readOnly: 'newer',
  problem: 'This map is written for version 2 of the format, and this version of Orbit reads up to 1.',
};

/** What the app tells the map's page. */
export const MAP_COMMANDS: OneOfEach<Command> = {
  language: { type: 'language', words: ENGLISH_WORDS },
  load: {
    type: 'load',
    markdown: '# Trip to Japan\n- Tokyo\n',
    theme: THEME,
    settings: { haptics: true, snap: false, longPressDelay: 400 },
    insets: { top: 56, right: 0, bottom: 96.5, left: 0 },
    baseUrl: 'https://appassets.androidplatform.net/maps/trip/',
    focus: 'tokyo',
    view: 'all',
    mapId: 'trip',
  },
  insets: { type: 'insets', insets: { top: 0, right: 80, bottom: 0, left: 0 } },
  unload: { type: 'unload' },
  theme: { type: 'theme', theme: { dark: true, tokens: {}, seed: null } },
  settings: { type: 'settings', settings: { haptics: false, snap: true, longPressDelay: 500 } },
  edit: { type: 'edit', id: 'tokyo' },
  editNew: { type: 'editNew', parentId: 'trip-to-japan', index: 1 },
  stopEditing: { type: 'stopEditing', save: true },
  add: { type: 'add', parentId: 'trip-to-japan', node: { text: 'Kyoto', status: 'todo' }, index: null },
  update: { type: 'update', id: 'tokyo', changes: { color: '#3f51b5', note: null, attrs: { current: 1300 } } },
  addToGoal: { type: 'addToGoal', id: 'tokyo', amount: 25.5 },
  remove: { type: 'remove', id: 'tokyo' },
  move: { type: 'move', id: 'tokyo', parentId: 'trip-to-japan', index: 0 },
  duplicate: { type: 'duplicate', id: 'tokyo' },
  focus: { type: 'focus', id: 'tokyo' },
  view: { type: 'view', mode: 'all' },
  zoom: { type: 'zoom', factor: 1.25 },
  fit: { type: 'fit' },
  restore: { type: 'restore', markdown: '# Trip to Japan\n' },
  preview: { type: 'preview', markdown: '# Trip to Japan\n- Osaka\n', editable: false },
  source: { type: 'source', text: 'title: Trip\nnodes:\n  - text: Tokyo\n' },
  options: { type: 'options', options: { theme: 'cosmos', palette: null, progressRings: null, tags: ['travel', 'family'] } },
  present: { type: 'present', on: true },
  lock: { type: 'lock', reason: 'deleted' },
  snapshot: {
    type: 'snapshot',
    job: 3,
    format: 'png',
    markdown: null,
    width: 1600,
    height: 1100,
    scale: 2,
    theme: THEME,
    transparent: true,
    style: '',
    viewMode: 'all',
    focus: null,
    baseUrl: 'https://appassets.androidplatform.net/maps/trip/',
  },
  convert: { type: 'convert', job: 4, text: '# Trip to Japan\n', to: 'opml' },
  check: { type: 'check', job: 5, text: 'nodes:\n  - text: Tokyo\n' },
  importFile: { type: 'importFile', job: 6, data: 'PG9wbWw+PC9vcG1sPg==', name: 'Trip.opml' },
  addIdeas: { type: 'addIdeas', job: 7, markdown: '# Trip to Japan\n', ideas: [{ text: 'Call the hotel', note: null }, { text: 'Pack the adapter', note: 'Type A, two of them' }], parentId: null },
  copy: { type: 'copy', job: 8, id: 'tokyo' },
  paste: { type: 'paste', job: 9, parentId: 'tokyo', text: '- Shibuya\n- Asakusa' },
};

/** What the map's page tells the app. */
export const MAP_MESSAGES: OneOfEach<Message> = {
  hello: { type: 'hello' },
  ready: { type: 'ready', state: STATE },
  saved: { type: 'saved', kind: 'update', markdown: '# Trip to Japan\n- Tokyo\n', state: STATE, added: null, renamed: { from: 'tokio', to: 'tokyo' }, removed: null },
  state: { type: 'state', state: STATE },
  focus: { type: 'focus', path: ['trip-to-japan', 'tokyo'], mode: 'all' },
  context: { type: 'context', id: 'tokyo', x: 120.5, y: 340 },
  held: { type: 'held' },
  drag: { type: 'drag', phase: 'end', id: 'tokyo', parentId: 'trip-to-japan', index: 2 },
  details: { type: 'details', id: 'tokyo' },
  editorOpen: { type: 'editorOpen', id: null, parentId: 'trip-to-japan' },
  editorClose: { type: 'editorClose' },
  source: { type: 'source', diagnostics: [{ message: 'Unknown attribute "colour".', line: 3, severity: 'warning' }], drawn: true },
  open: { type: 'open', url: 'https://example.com/tokyo' },
  haptic: { type: 'haptic' },
  result: { type: 'result', job: 4, value: '<opml version="2.0"></opml>' },
  clipboard: { type: 'clipboard', action: 'paste', id: 'tokyo' },
  error: { type: 'error', message: 'Orbit couldn\'t draw this map.' },
};

/** What the app tells an audience's page, on a second screen or a TV. */
export const AUDIENCE_COMMANDS: OneOfEach<AudienceCommand> = {
  language: { type: 'language', words: ENGLISH_WORDS },
  show: { type: 'show', markdown: '# Trip to Japan\n- Tokyo\n', theme: THEME, baseUrl: null, focus: 'tokyo', captions: false },
  step: { type: 'step', id: 'tokyo', index: 1, total: 4 },
  captions: { type: 'captions', on: true },
  media: { type: 'media', id: 'tokyo', kind: 'video', playing: true, at: 42.5 },
  playback: { type: 'playback', playing: false },
  seek: { type: 'seek', at: 90 },
  closeMedia: { type: 'closeMedia' },
  end: { type: 'end' },
};

/** What an audience's page on a second screen tells the app. */
export const AUDIENCE_MESSAGES: { hello: { type: 'hello' }; mediaState: MediaReport } = {
  hello: { type: 'hello' },
  mediaState: { type: 'mediaState', id: 'tokyo', playing: true, time: 42.5, duration: 190, ended: false, muted: true },
};

/** All of them, as the app's tests read them. */
export const protocolExamples = (): string => {
  const examples = {
    map: { commands: Object.values(MAP_COMMANDS), messages: Object.values(MAP_MESSAGES) },
    audience: { commands: Object.values(AUDIENCE_COMMANDS), messages: Object.values(AUDIENCE_MESSAGES) },
  };

  return `${JSON.stringify(examples, null, 2)}\n`;
};
