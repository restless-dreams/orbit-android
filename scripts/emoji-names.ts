// The emoji the icon sheet's search finds, and the words it finds them by,
// from Unicode's list of emoji and the names and keywords CLDR gives them:
//
//   bun scripts/emoji-names.ts
//
// writes app/src/main/assets/emoji/:
// - emoji.txt, every emoji in Unicode's order, each with its place in Unicode's
//   ranking of how often emoji are used, and its skin tones;
// - en.txt and es.txt, each emoji's name and keywords in English and in
//   Spanish (CLDR's Latin American Spanish over its Spanish).
//
// Run it again after changing a version below. Emoji the phone can't draw
// are left out when the app reads the list.
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const EMOJI_VERSION = '17.0';
const CLDR_VERSION = '48.2';

const EMOJI_LIST = `https://unicode.org/Public/${EMOJI_VERSION}.0/emoji/emoji-test.txt`;
const FREQUENCY = 'https://home.unicode.org/emoji/emoji-frequency/';
const CLDR = `https://raw.githubusercontent.com/unicode-org/cldr/release-${CLDR_VERSION.replace('.', '-')}/common`;

// Each language's CLDR locales, each one's words replacing the one's before.
const LANGUAGES: Record<string, string[]> = {
  en: ['en'],
  es: ['es', 'es_419'],
};

const NOTICE = `# From Unicode's emoji ${EMOJI_VERSION}, emoji frequency and CLDR ${CLDR_VERSION}, © Unicode, Inc., under the Unicode License v3 (https://www.unicode.org/license.txt). Made by scripts/emoji-names.ts.`;

// CLDR's mark for a word a locale takes from the one it's based on.
const INHERITED = '↑↑↑';

const SKIN_TONES = /[\u{1F3FB}-\u{1F3FF}]/u;
const VARIATION_SELECTOR = /️/g;

interface Emoji {
  emoji: string;
  variants: string[];

  /** Its place in Unicode's ranking of how often emoji are used, from 1, when it's ranked. */
  rank?: number;

  /** Unicode's English name, for an emoji CLDR doesn't name yet. */
  name: string;
}

interface Words {
  name?: string;
  keywords?: string[];
}

const root = join(import.meta.dir, '..');
const output = join(root, 'app/src/main/assets/emoji');

const download = async (url: string): Promise<string> => {
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`${url}: ${response.status}`);
  }

  return response.text();
};

const unescape = (text: string): string =>
  text
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'")
    .replaceAll('&amp;', '&');

// Every fully qualified emoji but the parts others are made of (skin tones and hair), with each skin tone joining
// the emoji before it: Unicode lists an emoji's skin tones right after it.
const readEmoji = (list: string): Emoji[] => {
  const emoji: Emoji[] = [];
  let group = '';

  for (const line of list.split('\n')) {
    const groupLine = /^# group: (.+)$/.exec(line);

    if (groupLine) {
      group = groupLine[1].trim();
      continue;
    }

    const entry = /^[0-9A-F ]+;\s*fully-qualified\s*#\s*(\S+)\s+E[\d.]+\s+(.+)$/.exec(line);

    if (!entry || group === 'Component') {
      continue;
    }

    const [, character, name] = entry;

    if (SKIN_TONES.test(character) && emoji.length > 0) {
      emoji[emoji.length - 1].variants.push(character);
    } else {
      emoji.push({ emoji: character, variants: [], name });
    }
  }

  return emoji;
};

// Unicode's ranking of how often emoji are used: a table of groups, each about half as frequent as the one above
// it, with each group's emoji from the most used. Skin tones and genders are counted with one of them.
const readRanks = (page: string): Map<string, number> => {
  const ranks = new Map<string, number>();

  for (const row of page.matchAll(/<tr[^>]*>\s*<td[^>]*>\s*(\d+)\s*<\/td>\s*<td[^>]*>([\s\S]*?)<\/td>/g)) {
    for (const character of unescape(row[2].replace(/<[^>]+>/g, ' ')).split(/\s+/)) {
      if (character !== '' && !ranks.has(character.replace(VARIATION_SELECTOR, ''))) {
        ranks.set(character.replace(VARIATION_SELECTOR, ''), ranks.size + 1);
      }
    }
  }

  if (ranks.size < 1000) {
    throw new Error(`${FREQUENCY}: only ${ranks.size} ranked emoji; has the page changed?`);
  }

  return ranks;
};

// One CLDR file's names and keywords, by emoji without variation selectors, as CLDR writes them.
const readAnnotations = (xml: string, words: Map<string, Words>): void => {
  for (const match of xml.matchAll(/<annotation cp="([^"]+)"( type="tts")?>([^<]*)<\/annotation>/g)) {
    const [, character, tts, text] = match;
    const value = unescape(text).trim();

    if (value === INHERITED || value === '') {
      continue;
    }

    const key = unescape(character).replace(VARIATION_SELECTOR, '');
    const entry = words.get(key) ?? {};

    if (tts) {
      entry.name = value;
    } else {
      entry.keywords = value.split('|').map((keyword) => keyword.trim()).filter((keyword) => keyword !== '');
    }

    words.set(key, entry);
  }
};

const readLanguage = async (locales: string[]): Promise<Map<string, Words>> => {
  const words = new Map<string, Words>();

  for (const locale of locales) {
    for (const folder of ['annotations', 'annotationsDerived']) {
      readAnnotations(await download(`${CLDR}/${folder}/${locale}.xml`), words);
    }
  }

  return words;
};

// A line per emoji: the emoji, its name, and its keywords that aren't its name, apart with "|". Tabs and line breaks
// never appear in CLDR's words.
const languageFile = (emoji: Emoji[], words: Map<string, Words>, fallback: boolean): string => {
  const lines = [NOTICE];

  // The skin tones' names, which the app puts after an emoji's name for each of its skin tones.
  const tones = ['🏻', '🏼', '🏽', '🏾', '🏿'];

  for (const item of [...emoji, ...tones.map((tone) => ({ emoji: tone, variants: [], name: '' }))]) {
    const found = words.get(item.emoji.replace(VARIATION_SELECTOR, ''));
    const name = found?.name ?? (fallback ? item.name : undefined);

    if (!name) {
      continue;
    }

    const keywords = (found?.keywords ?? []).filter((keyword) => keyword.toLowerCase() !== name.toLowerCase());
    lines.push([item.emoji, name, keywords.join('|')].join('\t'));
  }

  return `${lines.join('\n')}\n`;
};

const emoji = readEmoji(await download(EMOJI_LIST));
const ranks = readRanks(await download(FREQUENCY));
mkdirSync(output, { recursive: true });

for (const item of emoji) {
  item.rank = ranks.get(item.emoji.replace(VARIATION_SELECTOR, ''));
}

// A line per emoji: the emoji, its rank (nothing for one Unicode hasn't ranked) and its skin tones apart with
// spaces, apart with tabs.
const emojiLines = emoji.map((item) => [item.emoji, item.rank ?? '', item.variants.join(' ')].join('\t'));
writeFileSync(join(output, 'emoji.txt'), `${[NOTICE, ...emojiLines].join('\n')}\n`);

for (const [language, locales] of Object.entries(LANGUAGES)) {
  const words = await readLanguage(locales);
  writeFileSync(join(output, `${language}.txt`), languageFile(emoji, words, language === 'en'));
}

const ranked = emoji.filter((item) => item.rank !== undefined).length;
console.log(`${emoji.length} emoji (${ranked} ranked), ${emoji.reduce((total, item) => total + item.variants.length, 0)} skin tones`);
