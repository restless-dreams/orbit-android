/**
 * The words the app's pages say themselves, beyond Orbit's own: the kinds of
 * ideas, a goal ring's words, the map page's messages and the presentation
 * pages'. They come from the app's string resources, in the app's language
 * (page/PageWords.kt): the map's page and a second screen hear them as a
 * `language` command, a TV when a presentation starts on it, and the pages
 * the phone serves or exports carry them as JSON. Until then, and for any
 * word left out, pages speak English. The words the app writes into a page's
 * HTML itself, such as a web page's buttons, aren't here.
 */
import { ENGLISH_RING_WORDS } from './goal';
import type { RingWords } from './goal';

export interface PageWords extends RingWords {
  /** The language they're in, as a BCP 47 tag: the page's `lang`, which Orbit's own words follow. */
  language: string;

  /** What the app calls the kinds of ideas, by type: `milestone` is "Hito" in Spanish. look.ts names any left out in English. */
  kinds: Record<string, string>;

  /** The idea Orbit makes up at the top of a map that's just a list. */
  untitledMap: string;

  /** Why the app can't change a map, or bring text in: Orbit found no ideas in text that isn't empty. */
  noIdeas: string;

  /** Why text isn't written in the other flavor: Orbit couldn't read all of it. */
  textNotRead: string;

  /** Why ideas caught on the go weren't added to a map: Orbit couldn't read all of it. */
  mapNotAddedTo: string;

  /** Why ideas caught on the go weren't added to a map: a newer Orbit made it. */
  mapFromNewerOrbit: string;

  /** The audience's screen before a presentation starts, or after it ends. */
  waiting: string;

  /** A browser that lost the presenter's phone, while it looks for it again. */
  looking: string;

  /** Why a browser stopped following a presentation, or can't follow it now. */
  removed: string;

  full: string;
  closed: string;
  ended: string;

  /** The button over a video in a browser that plays its sound only once the page is touched. */
  tapForSound: string;
}

export const ENGLISH_WORDS: PageWords = {
  ...ENGLISH_RING_WORDS,
  language: 'en',
  kinds: {},
  untitledMap: 'Untitled map',
  noIdeas: 'Orbit found no ideas in this text.',
  textNotRead: 'Orbit couldn\'t read all of this text.',
  mapNotAddedTo: 'Orbit couldn\'t read all of this map, so it didn\'t add to it.',
  mapFromNewerOrbit: 'A newer version of Orbit made this map. Update Orbit to add to it.',
  waiting: 'Waiting for the presenter',
  looking: 'Looking for the presenter…',
  removed: 'The presenter removed you.',
  full: 'This presentation is full.',
  closed: 'The presenter isn\'t letting anyone new in.',
  ended: 'The presentation ended.',
  tapForSound: 'Tap for sound',
};

let current: PageWords = ENGLISH_WORDS;

/** The page's words now: the app's, once it gave them, else English. */
export const pageWords = (): PageWords => {
  return current;
};

/**
 * Takes the app's words for the page, English for any it leaves out, and
 * speaks their language: the page's `lang`, which Orbit's own words follow
 * on the maps drawn after, and on maps whose options are set again.
 */
export const speak = (words: Partial<PageWords> | null | undefined): PageWords => {
  const given: Record<string, unknown> = words && typeof words === 'object' ? words : {};
  const next: Record<string, unknown> = { ...ENGLISH_WORDS };

  for (const key of Object.keys(ENGLISH_WORDS)) {
    const value = given[key];

    if (typeof value === 'string' && value.trim() !== '') {
      next[key] = value;
    }
  }

  const kinds = given.kinds && typeof given.kinds === 'object' ? given.kinds as Record<string, unknown> : {};
  next.kinds = Object.fromEntries(Object.entries(kinds).filter((entry): entry is [string, string] => typeof entry[1] === 'string' && entry[1].trim() !== ''));
  current = next as unknown as PageWords;

  if (typeof given.language === 'string' && given.language.trim() !== '' && typeof document !== 'undefined') {
    document.documentElement.lang = given.language;
  }

  return current;
};

/** The words a page the phone serves carries in its #orbit-words script; none on a page without them. */
export const servedWords = (): Partial<PageWords> | null => {
  const text = document.getElementById('orbit-words')?.textContent;

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text) as Partial<PageWords>;
  } catch {
    return null;
  }
};

/** What a TV says before any phone has said anything: its status for the phones casting to it, and its waiting screen. */
export interface TvWords {
  language: string;
  status: string;
  waiting: string;
}

/**
 * The TV's own words, in each language the app speaks. A TV can't read the
 * app's resources, so a new language adds its words here too; the waiting
 * screen's are the app's `pages_waiting_for_the_presenter`.
 */
const TV_WORDS: Record<string, TvWords> = {
  en: { language: 'en', status: 'Presenting a map', waiting: 'Waiting for the presenter' },
  es: { language: 'es', status: 'Presentando un mapa', waiting: 'Esperando a quien presenta' },
};

/** The TV's words in its own language, `navigator.language`, when the app speaks it, and in English otherwise. */
export const tvWords = (language: string | undefined): TvWords => {
  const base = (language ?? '').trim().toLowerCase().split(/[-_]/)[0];
  return TV_WORDS[base] ?? TV_WORDS.en;
};
