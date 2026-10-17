import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import tseslint from 'typescript-eslint';

// The pages and the logo script follow the library's code style. Its rules
// come from the Orbit checkout the pages are built with (ORBIT_PATH, from
// web/; default ../../orbit), in scripts/eslint-style.js there.
// `bun run lint` checks web/ and art/ from the repository's root.
const orbitPath = resolve(import.meta.dirname, process.env.ORBIT_PATH ?? '../../orbit');
const { default: orbit } = await import(pathToFileURL(resolve(orbitPath, 'scripts/eslint-style.js')).href);

export default [
  {
    ignores: ['**/node_modules/**', '**/dist/**'],
  },
  {
    files: ['**/*.{ts,js}'],
    languageOptions: { parser: tseslint.parser },
    plugins: { orbit },
    rules: {
      curly: ['error', 'all'],
      'orbit/arrow-functions': 'error',
      'orbit/blank-lines-around-blocks': 'error',
      'orbit/blank-lines-around-documented-members': 'error',
    },
  },
];
