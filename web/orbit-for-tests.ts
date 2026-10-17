/**
 * Tests import Orbit the way the pages' bundles do: from a local checkout's
 * build, at ORBIT_PATH (from web/; default: ../../orbit).
 *
 *   ORBIT_PATH=../../orbit bun test
 */
import { plugin } from 'bun';
import { existsSync } from 'node:fs';
import { join, resolve } from 'node:path';

const orbitEntry = join(resolve(import.meta.dir, process.env.ORBIT_PATH ?? '../../orbit'), 'dist/index.js');

if (!existsSync(orbitEntry)) {
  console.error(`No Orbit build at ${orbitEntry}. Run \`bun run build\` in your Orbit checkout, or point ORBIT_PATH at it.`);
  process.exit(1);
}

// A module of its own, by name: Bun's test runner doesn't ask plugins to resolve package names.
plugin({
  name: 'orbit',
  setup(build) {
    build.module('@restless-dreams/orbit', async () => ({ exports: await import(orbitEntry), loader: 'object' }));
  },
});
