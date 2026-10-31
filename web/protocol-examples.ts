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
import { RECEIVER_PROTOCOL } from './receiver-protocol';
import { LARGEST_WHOLE, SMALLEST_WHOLE } from './whole-numbers';
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
      childCount: 2,
      icon: null,
      color: null,
      drawnColor: 'var(--_orbit-color-0)',
      status: null,
      priority: null,
      tags: [],
      collapsed: false,
      type: null,
      typeVersion: null,
      link: null,
      note: null,
      noteFormat: null,
      image: null,
      video: null,
      embed: null,
      attrs: null,
      audio: null,
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
      typeVersion: null,
      link: 'https://example.com/tokyo',
      note: 'Book the **ryokan**',
      noteFormat: 'markdown',
      image: { src: 'media/tokyo.jpg', alt: 'The tower at night', caption: 'Tokyo Tower', provider: null, thumbnail: null, motion: { src: 'media/tokyo-motion.mp4', still: 1.2 } },
      video: { src: 'https://www.youtube.com/watch?v=abc', alt: null, caption: null, provider: 'youtube', thumbnail: 'https://i.ytimg.com/vi/abc/hqdefault.jpg', motion: null },
      embed: null,
      attrs: { target: 3000, current: 1250, unit: 'USD' },
      audio: { src: 'media/memo-mf2k1x.mp4', alt: null, caption: 'Where to stay', provider: null, thumbnail: null, motion: null },
    },
    {
      // Numbers past what the app holds, as the page sends them (whole-numbers.ts): `type: goal@99999999999`, `priority: -1e21`.
      id: 'someday',
      text: 'Someday',
      parentId: 'trip-to-japan',
      depth: 1,
      index: 1,
      childCount: 0,
      icon: null,
      color: null,
      drawnColor: 'var(--_orbit-color-1)',
      status: null,
      priority: SMALLEST_WHOLE,
      tags: [],
      collapsed: false,
      type: 'goal',
      typeVersion: LARGEST_WHOLE,
      link: null,
      note: null,
      noteFormat: null,
      image: null,
      video: null,
      embed: null,
      attrs: null,
      audio: null,
    },
  ],
  options: { theme: 'night', palette: ['#e91e63', '#3f51b5'], showRings: true, animation: false, speed: 1.5, maxDepth: 3, view: 'focus', progressRings: false, tags: ['travel'] },
  path: ['trip-to-japan', 'tokyo'],
  mode: 'focus',
  style: 'night',
  surface: '#0b1020',
  readOnly: 'newer',
  problem: 'This map is written for version 2 of the format, and this version of Orbit reads up to 1.',
};

/** The map with more levels to show than the app holds (`maxDepth: 1e12`), as the page sends it (whole-numbers.ts). */
const DEEPEST_STATE: MapState = { ...STATE, options: { ...STATE.options, maxDepth: LARGEST_WHOLE } };

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
    revision: 2,
  },
  insets: { type: 'insets', insets: { top: 0, right: 80, bottom: 0, left: 0 }, glide: true },
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
  restore: { type: 'restore', markdown: '# Trip to Japan\n', revision: 2 },
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
    view: 'all',
    focus: null,
    baseUrl: 'https://appassets.androidplatform.net/maps/trip/',
  },
  convert: { type: 'convert', job: 4, text: '# Trip to Japan\n', to: 'opml' },
  check: { type: 'check', job: 5, text: 'nodes:\n  - text: Tokyo\n' },
  importFile: { type: 'importFile', job: 6, data: 'PG9wbWw+PC9vcG1sPg==', name: 'Trip.opml' },
  addIdeas: { type: 'addIdeas', job: 7, markdown: '# Trip to Japan\n', text: 'Call the hotel\nPack\n  - [ ] The adapter\n  - [x] Passports', parentId: null },
  copy: { type: 'copy', job: 8, id: 'tokyo' },
  paste: { type: 'paste', job: 9, parentId: 'tokyo', text: '- Shibuya\n- Asakusa' },
  dropOver: { type: 'dropOver', x: 182.5, y: 410 },
  dropLeave: { type: 'dropLeave' },
  dropAt: { type: 'dropAt', job: 10, x: 182.5, y: 412.25 },
  addNodes: {
    type: 'addNodes',
    job: 11,
    parentId: 'tokyo',
    nodes: [
      { text: 'Shibuya crossing', image: { src: 'media/shibuya.jpg' } },
      { text: 'Night walk', video: { src: 'media/night-walk.mp4', provider: 'native', thumbnail: 'media/night-walk-poster.jpg' } },
    ],
  },
  mapFromText: { type: 'mapFromText', job: 12, text: 'Trip to Japan\n\tTokyo\n\t\tShibuya\n\tKyoto', subject: null },
  editMap: { type: 'editMap', job: 13, markdown: '# Trip to Japan\n', title: 'Japan in spring', tags: ['travel', 'Plans'] },
  hideChildren: { type: 'hideChildren', ids: ['trip-to-japan', 'tokyo'] },
  revealChildren: { type: 'revealChildren', ids: null },
};

/** What the map's page tells the app. */
export const MAP_MESSAGES: OneOfEach<Message> = {
  hello: { type: 'hello' },
  ready: { type: 'ready', state: STATE },
  saved: { type: 'saved', kind: 'update', markdown: '# Trip to Japan\n- Tokyo\n', state: DEEPEST_STATE, added: null, renamed: { from: 'tokio', to: 'tokyo' }, removed: null, revision: 2 },
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
  declined: { type: 'declined', change: 'update', message: 'A node\'s image is its address, or { src, alt, caption, motion }, like { image: \'summit.jpg\' }, but got 42.' },
  dropTarget: { type: 'dropTarget', id: 'tokyo' },
  dragOut: { type: 'dragOut', id: 'tokyo' },
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
    // The protocol of TVs and browsers the audience's page speaks, which the app's own has to match.
    audience: { protocol: RECEIVER_PROTOCOL, commands: Object.values(AUDIENCE_COMMANDS), messages: Object.values(AUDIENCE_MESSAGES) },
  };

  return `${JSON.stringify(examples, null, 2)}\n`;
};
