import { afterEach, describe, expect, test } from 'bun:test';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { parseOrbit, serializeOrbitMarkdown } from '@restless-dreams/orbit';
import type { OrbitNode } from '@restless-dreams/orbit';
import { readGoal, ringText } from './goal';
import { KIND_NAMES, goalRing, nodeTypes } from './look';
import { ENGLISH_WORDS, pageWords, speak, tvWords } from './words';
import type { PageWords } from './words';

const RESOURCES = join(import.meta.dir, '../app/src/main/res');

/** XML's own escapes, as a resource file writes them. */
const ENTITIES: Record<string, string> = { amp: '&', lt: '<', gt: '>', quot: '"', apos: '\'' };

/** A string resource's text as Android reads it: entities and escapes undone, and spaces run together. */
const androidText = (raw: string): string => {
  const xml = raw.replace(/&(#x[0-9a-f]+|#[0-9]+|[a-z]+);/gi, (entity, name: string) => {
    if (name.startsWith('#x') || name.startsWith('#X')) {
      return String.fromCodePoint(parseInt(name.slice(2), 16));
    }

    if (name.startsWith('#')) {
      return String.fromCodePoint(Number(name.slice(1)));
    }

    return ENTITIES[name] ?? entity;
  });

  let text = '';

  for (let index = 0; index < xml.length; index += 1) {
    const character = xml[index];

    if (character === '\\' && index + 1 < xml.length) {
      const escaped = xml[index + 1];
      text += escaped === 'n' ? '\n' : escaped === 't' ? '\t' : escaped;
      index += 1;
    } else {
      text += character;
    }
  }

  return text.trim().replace(/[ \t\r\n]+/g, ' ');
};

/** The app's strings in one of its resource folders (`values`, `values-es`), by name. */
const resources = (folder: string): Record<string, string> => {
  const found: Record<string, string> = {};
  const directory = join(RESOURCES, folder);

  for (const file of readdirSync(directory).filter((name) => name.startsWith('strings') && name.endsWith('.xml'))) {
    const xml = readFileSync(join(directory, file), 'utf8');

    for (const match of xml.matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
      found[match[1]] = androidText(match[2]);
    }
  }

  return found;
};

/** Where each of the pages' words comes from in the app's resources. A goal's target goes where `%1$s` is. */
const WHERE: Record<Exclude<keyof PageWords, 'language' | 'kinds'>, string> = {
  goalOfTarget: 'pages_goal_of_target',
  goalLeft: 'pages_goal_left',
  untitledMap: 'common_untitled_map',
  noIdeas: 'pages_no_ideas',
  textNotRead: 'pages_text_not_read',
  mapNotAddedTo: 'pages_map_not_added_to',
  mapFromNewerOrbit: 'pages_map_from_a_newer_orbit',
  waiting: 'pages_waiting_for_the_presenter',
  looking: 'pages_looking_for_the_presenter',
  removed: 'nearby_presenter_removed_you',
  full: 'nearby_presentation_is_full',
  closed: 'nearby_presenter_is_not_letting_anyone_new_in',
  ended: 'nearby_presentation_ended',
  tapForSound: 'pages_tap_for_sound',
};

const english = resources('values');
const spanish = resources('values-es');

const goalNode = (attrs: Record<string, unknown>): OrbitNode => {
  return { id: 'goal', text: 'Goal', level: 1, type: 'goal', attrs, children: [] };
};

afterEach(() => {
  speak(null);
});

describe('the pages\' English', () => {
  test('is the app\'s, word for word', () => {
    expect(ENGLISH_WORDS.language).toBe(english.pages_language);

    for (const [key, name] of Object.entries(WHERE)) {
      expect(`${key}: ${ENGLISH_WORDS[key as keyof typeof WHERE]}`).toBe(`${key}: ${english[name].replace('%1$s', '{target}')}`);
    }

    for (const [kind, name] of Object.entries(KIND_NAMES)) {
      expect(name).toBe(english[`map_kind_${kind}`]);
    }
  });

  test('names every kind of idea until the app names them', () => {
    const types = nodeTypes();
    expect(Object.fromEntries(Object.entries(types).map(([kind, type]) => [kind, type.label]))).toEqual(KIND_NAMES);
  });
});

describe('the app\'s words', () => {
  test('replace the English, which stays for any left out or empty', () => {
    const words = speak({
      language: 'es-MX',
      goalOfTarget: 'de {target}',
      noIdeas: '  ',
      waiting: 3 as unknown as string,
      kinds: { milestone: 'Hito', goal: 7 as unknown as string },
    });
    expect(words).toBe(pageWords());
    expect(words.language).toBe('es-MX');
    expect(words.goalOfTarget).toBe('de {target}');
    expect(words.noIdeas).toBe(ENGLISH_WORDS.noIdeas);
    expect(words.waiting).toBe(ENGLISH_WORDS.waiting);
    expect(nodeTypes().milestone.label).toBe('Hito');
    expect(nodeTypes().goal.label).toBe('Goal');
    // Words the page heard before don't linger: each language comes whole.
    expect(speak({ language: 'en' }).goalOfTarget).toBe('of {target}');
    expect(nodeTypes().milestone.label).toBe('Milestone');
  });

  test('set the page\'s lang, which Orbit\'s own words follow, when they name one', () => {
    const page = globalThis as { document?: unknown };
    const root = { lang: 'en' };
    page.document = { documentElement: root };

    try {
      speak({ language: 'es-MX' });
      expect(root.lang).toBe('es-MX');
      speak({ waiting: 'Esperando' });
      expect(root.lang).toBe('es-MX');
    } finally {
      delete page.document;
    }
  });

  test('come in Spanish from the app\'s Spanish', () => {
    expect(spanish.pages_language).toBe('es');

    for (const name of [...Object.values(WHERE), ...Object.keys(KIND_NAMES).map((kind) => `map_kind_${kind}`)]) {
      expect(`${name}: ${spanish[name] ?? 'missing'}`).not.toBe(`${name}: missing`);
    }

    expect(spanish.pages_goal_of_target).toBe('de %1$s');
  });
});

describe('a goal\'s ring', () => {
  const words = { goalOfTarget: spanish.pages_goal_of_target.replace('%1$s', '{target}'), goalLeft: spanish.pages_goal_left };

  test('says its words in the page\'s language, and its numbers as the reader\'s language writes them', () => {
    expect(ringText(readGoal(goalNode({ target: 12, current: 5 })), 'es-MX', words)).toEqual({ main: ['5'], detail: ['de 12'], check: false });
    expect(ringText(readGoal(goalNode({ start: 4800, current: 3100, target: 0, unit: 'USD' })), 'en-US', words).detail).toEqual(['por pagar']);
    // Amounts go in as they're written, dollar signs and all.
    expect(ringText(readGoal(goalNode({ target: 3000, current: 1250, unit: 'USD' })), 'en-US', words).detail).toEqual(['de $3,000', 'de $3K']);
    expect(goalRing(goalNode({ target: 5, current: 2 }), 'en-US', words).text.detail).toEqual(['de 5']);
  });

  test('says them in English without the app\'s', () => {
    expect(goalRing(goalNode({ target: 5, current: 2 }), 'en-US').text.detail).toEqual(['of 5']);
    expect(goalRing(goalNode({ target: 5, current: 2 }), 'en-US', pageWords()).text.detail).toEqual(['of 5']);
  });
});

describe('a TV', () => {
  test('waits in its own language when the app speaks it, and in English otherwise', () => {
    expect(tvWords('es-MX')).toEqual({ language: 'es', status: 'Presentando un mapa', waiting: spanish.pages_waiting_for_the_presenter });
    expect(tvWords('es')).toEqual(tvWords('es-ES'));
    expect(tvWords('en-US')).toEqual({ language: 'en', status: 'Presenting a map', waiting: english.pages_waiting_for_the_presenter });
    expect(tvWords('fr-FR')).toEqual(tvWords('en'));
    expect(tvWords(undefined)).toEqual(tvWords('en'));
  });
});

describe('a map that\'s just a list', () => {
  test('gets its made-up top idea named in the app\'s language, and saves as the list it was', () => {
    const list = '- Groceries\n  - Apples\n- Chores\n';

    for (const name of [english.common_untitled_map, spanish.common_untitled_map]) {
      const parsed = parseOrbit(list, { untitled: name });
      expect(parsed.root.text).toBe(name);
      expect(parsed.root.id).toBe('untitled-map');
      expect(serializeOrbitMarkdown(parsed)).toBe(list);
    }
  });
});
