// The Orbit build the pages are built, tested and type-checked with: a
// local checkout's dist/, at ORBIT_PATH (from web/; default: ../../orbit),
// built there with `bun run build`. A build older than Orbit's sources is
// from an earlier Orbit, as after pulling or checking out another commit
// without building again: the pages would be built, tested or checked
// against what Orbit was, and fail, or pass, for reasons that aren't
// theirs. So the build, the tests and the type check stop and say so first.
import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

/** The Orbit checkout: ORBIT_PATH, from web/, or the one beside this repository. */
export const orbitPath = resolve(import.meta.dir, process.env.ORBIT_PATH ?? '../../orbit');

/** What Orbit's build is made from: its code, and its package.json, which names its version and what it bundles. */
const SOURCES = ['src', 'package.json'];

/** A file or a folder, and when it last changed. */
interface Changed {
  path: string;
  time: number;
}

/**
 * What under `path` changed last, a folder read all the way down: a file,
 * or a folder, which changes as a file in it comes or goes. Null when
 * there's nothing there.
 */
const lastChanged = (path: string): Changed | null => {
  if (!existsSync(path)) {
    return null;
  }

  const stats = statSync(path);
  const itself = { path, time: stats.mtimeMs };

  if (!stats.isDirectory()) {
    return itself;
  }

  return readdirSync(path).reduce<Changed>((latest, name) => {
    const changed = lastChanged(join(path, name));
    return changed && changed.time > latest.time ? changed : latest;
  }, itself);
};

/**
 * Why the Orbit checkout at `orbit` can't give its build's `file`
 * (`dist/index.js`, `dist/index.d.ts`), or null when it can: it isn't
 * built, or one of its sources changed after it was.
 */
export const orbitBuildProblem = (orbit: string, file: string): string | null => {
  const built = join(orbit, file);

  if (!existsSync(built)) {
    return `No Orbit build at ${built}. Run \`bun run build\` in your Orbit checkout, or point ORBIT_PATH at it.`;
  }

  const time = statSync(built).mtimeMs;

  for (const source of SOURCES) {
    const changed = lastChanged(join(orbit, source));

    if (changed && changed.time > time) {
      return `Orbit's build at ${built} is older than its ${relative(orbit, changed.path)}: it's from an earlier Orbit than `
        + `the checkout has. Run \`bun run build\` in ${orbit} again, with the Bun its package.json names.`;
    }
  }

  return null;
};

/** The path of the Orbit build's `file`, at ORBIT_PATH; when it isn't there, or is older than Orbit's sources, says why and stops. */
export const orbitBuild = (file: string): string => {
  const problem = orbitBuildProblem(orbitPath, file);

  if (problem) {
    console.error(problem);
    process.exit(1);
  }

  return join(orbitPath, file);
};
