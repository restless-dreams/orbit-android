import { afterEach, describe, expect, test } from 'bun:test';
import { mkdirSync, mkdtempSync, rmSync, utimesSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { orbitBuildProblem } from './orbit-build';

const checkouts: string[] = [];

afterEach(() => {
  checkouts.splice(0).forEach((checkout) => rmSync(checkout, { recursive: true, force: true }));
});

/** Writes `file` in `checkout`, last changed `minutes` after a fixed moment. */
const write = (checkout: string, file: string, minutes: number): void => {
  const path = join(checkout, file);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, `${file}\n`);
  const time = new Date(Date.UTC(2026, 0, 1, 12, minutes));
  utimesSync(path, time, time);
};

/** An Orbit checkout whose sources last changed at minute 0, and folders before that, built at minute `built`. */
const checkout = (built: number): string => {
  const orbit = mkdtempSync(join(tmpdir(), 'orbit-build-'));
  checkouts.push(orbit);
  write(orbit, 'package.json', 0);
  write(orbit, 'src/index.ts', 0);
  write(orbit, 'src/core/layout.ts', 0);
  const before = new Date(Date.UTC(2026, 0, 1, 11, 0));
  [join(orbit, 'src/core'), join(orbit, 'src')].forEach((folder) => utimesSync(folder, before, before));
  write(orbit, 'dist/index.js', built);
  write(orbit, 'dist/index.d.ts', built);
  return orbit;
};

describe('the Orbit build the pages use', () => {
  test('is used when it was built after its sources last changed', () => {
    const orbit = checkout(5);

    expect(orbitBuildProblem(orbit, 'dist/index.js')).toBeNull();
    expect(orbitBuildProblem(orbit, 'dist/index.d.ts')).toBeNull();
  });

  test('says to build Orbit when there is no build', () => {
    const orbit = checkout(5);
    rmSync(join(orbit, 'dist'), { recursive: true });

    expect(orbitBuildProblem(orbit, 'dist/index.js')).toContain('No Orbit build at');
  });

  test('says to build Orbit again when a source changed after the build, however deep', () => {
    const orbit = checkout(5);
    write(orbit, 'src/core/layout.ts', 9);

    const problem = orbitBuildProblem(orbit, 'dist/index.js');
    expect(problem).toContain(`older than its ${join('src', 'core', 'layout.ts')}`);
    expect(problem).toContain('bun run build');
  });

  test('says so when the version changed, or a source came or went', () => {
    const changed = checkout(5);
    write(changed, 'package.json', 9);
    expect(orbitBuildProblem(changed, 'dist/index.d.ts')).toContain('older than its package.json');

    const gone = checkout(5);
    rmSync(join(gone, 'src/index.ts'));
    expect(orbitBuildProblem(gone, 'dist/index.js')).toContain('older than its src');
  });
});
