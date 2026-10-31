// Checks the store listings in fastlane/metadata/android/ against what the
// stores take, before a release reaches them:
//
//   bun scripts/store-listing.ts
//
// Every language has a title, a short and a full description, and "What's
// new" for the version app/build.gradle.kts builds
// (changelogs/<versionCode>.txt, RELEASE.md, step 1), each within its store
// limit in characters. Google Play cuts nothing and refuses a text that's
// too long; F-Droid cuts it short. It prints what's wrong and exits with 1,
// or with 0 when every listing fits.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = join(import.meta.dirname, '..');
const LISTINGS = join(ROOT, 'fastlane/metadata/android');

// Google Play's limits, which are F-Droid's or tighter.
const LIMITS: Record<string, number> = {
  'title.txt': 30,
  'short_description.txt': 80,
  'full_description.txt': 4000,
};

const CHANGELOG_LIMIT = 500;

/** The versionCode app/build.gradle.kts gives the app. */
const versionCode = (): number => {
  const build = readFileSync(join(ROOT, 'app/build.gradle.kts'), 'utf8');
  const code = /^\s*versionCode\s*=\s*(\d+)\s*$/m.exec(build)?.[1];

  if (code === undefined) {
    throw new Error('app/build.gradle.kts has no versionCode.');
  }

  return Number(code);
};

/** A file's length as the stores count it: in characters, without the line break it ends with. */
const length = (path: string): number => [...readFileSync(path, 'utf8').replace(/\r?\n$/, '')].length;

const problems: string[] = [];
const code = versionCode();
const languages = readdirSync(LISTINGS, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name);

if (languages.length === 0) {
  problems.push(`${LISTINGS} has no languages.`);
}

for (const language of languages) {
  const files = { ...LIMITS, [`changelogs/${code}.txt`]: CHANGELOG_LIMIT };

  for (const [file, limit] of Object.entries(files)) {
    const path = join(LISTINGS, language, file);

    if (!existsSync(path)) {
      problems.push(`${language}/${file} is missing.`);
      continue;
    }

    const characters = length(path);

    if (characters === 0) {
      problems.push(`${language}/${file} is empty.`);
    } else if (characters > limit) {
      problems.push(`${language}/${file} has ${characters} characters, past the stores' ${limit}.`);
    }
  }
}

if (problems.length > 0) {
  console.error(`The store listings don't fit the stores (fastlane/metadata/android/, versionCode ${code}):`);

  for (const problem of problems) {
    console.error(`- ${problem}`);
  }

  process.exit(1);
}

console.log(`The store listings fit, in ${languages.join(', ')}, with "What's new" for versionCode ${code}.`);
