import { afterEach, describe, expect, test } from 'bun:test';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

// scripts/compare-pages.sh, which check-pages.sh and CI run once web/ has built the pages, in checkouts of its own:
// the pages as a commit has them, and then as a build left them.
const SCRIPT = join(import.meta.dir, '../scripts/compare-pages.sh');
const ORBIT_JSON = 'app/src/main/assets/map/orbit.json';
const BRIDGE = 'app/src/main/assets/map/bridge.js';
const PROTOCOL = 'app/src/test/resources/page-protocol.json';

/** What web/build.ts writes into orbit.json: the Orbit the pages are built with, and the newest map format it reads. */
interface OrbitBuild {
  version: string;
  commit: string;
  format: number;
}

const BUILT: OrbitBuild = { version: '0.4.0', commit: '34ca2c0', format: 1 };
const checkouts: string[] = [];

afterEach(() => {
  checkouts.splice(0).forEach((checkout) => rmSync(checkout, { recursive: true, force: true }));
});

const write = (checkout: string, file: string, text: string): void => {
  mkdirSync(dirname(join(checkout, file)), { recursive: true });
  writeFileSync(join(checkout, file), text);
};

/** orbit.json as web/build.ts writes it. */
const writeOrbitJson = (checkout: string, build: OrbitBuild): void => {
  write(checkout, ORBIT_JSON, `${JSON.stringify(build)}\n`);
};

const git = (checkout: string, ...args: string[]): void => {
  const run = Bun.spawnSync(['git', '-C', checkout, '-c', 'commit.gpgsign=false', ...args], {
    env: { ...process.env, GIT_AUTHOR_NAME: 'Orbit', GIT_AUTHOR_EMAIL: 'orbit@example.com', GIT_COMMITTER_NAME: 'Orbit', GIT_COMMITTER_EMAIL: 'orbit@example.com' },
  });

  if (run.exitCode !== 0) {
    throw new Error(`git ${args.join(' ')} failed: ${run.stderr.toString()}`);
  }
};

/** A checkout whose last commit has pages built with `BUILT`'s Orbit, as yet unchanged. */
const committed = (): string => {
  const checkout = mkdtempSync(join(tmpdir(), 'compare-pages-'));
  checkouts.push(checkout);
  writeOrbitJson(checkout, BUILT);
  write(checkout, BRIDGE, 'export const bridge = 1;\n');
  write(checkout, PROTOCOL, '[]\n');
  git(checkout, 'init', '--quiet');
  git(checkout, 'add', '.');
  git(checkout, 'commit', '--quiet', '--message', 'The pages');
  return checkout;
};

/** Whether the script says the checkout's pages are its commit's, and what it says when they aren't. */
const compare = (checkout: string): { same: boolean; said: string } => {
  const run = Bun.spawnSync(['bash', SCRIPT, checkout]);
  return { same: run.exitCode === 0, said: run.stderr.toString() };
};

describe('comparing the pages a build wrote with the commit\'s', () => {
  test('they match when the build wrote what the commit has, its Orbit commit named at a greater length', () => {
    const checkout = committed();
    writeOrbitJson(checkout, { ...BUILT, commit: '34ca2c0f1d' });

    expect(compare(checkout)).toEqual({ same: true, said: '' });
  });

  test('a page the build wrote differently differs', () => {
    const checkout = committed();
    write(checkout, BRIDGE, 'export const bridge = 2;\n');
    const { same, said } = compare(checkout);

    expect(same).toBe(false);
    expect(said).toContain(BRIDGE);
  });

  test('the protocol\'s examples written differently differ', () => {
    const checkout = committed();
    write(checkout, PROTOCOL, '[{}]\n');
    const { same, said } = compare(checkout);

    expect(same).toBe(false);
    expect(said).toContain(PROTOCOL);
  });

  test('an Orbit version other than the commit\'s differs', () => {
    const checkout = committed();
    writeOrbitJson(checkout, { ...BUILT, version: '0.4.1' });
    const { same, said } = compare(checkout);

    expect(same).toBe(false);
    expect(said).toContain('"version":"0.4.1"');
  });

  test('a map format other than the commit\'s differs, with the same Orbit version', () => {
    // The app's folder records say it reads the format orbit.json names (MAP_FORMAT, held to it by OrbitBuildTest).
    const checkout = committed();
    writeOrbitJson(checkout, { ...BUILT, format: 2 });
    const { same, said } = compare(checkout);

    expect(same).toBe(false);
    expect(said).toContain('"format":1');
    expect(said).toContain('"format":2');
  });
});
