/**
 * Tests import Orbit the way the pages' bundles do: from a local checkout's
 * build, at ORBIT_PATH (from web/; default: ../../orbit), as long as it's as
 * new as the checkout's sources (orbit-build.ts).
 *
 *   ORBIT_PATH=../../orbit bun run test
 */
import { plugin } from 'bun';
import { orbitBuild } from './orbit-build';

const orbitEntry = orbitBuild('dist/index.js');

// A module of its own, by name: Bun's test runner doesn't ask plugins to resolve package names.
plugin({
  name: 'orbit',
  setup(build) {
    build.module('@restless-dreams/orbit', async () => ({ exports: await import(orbitEntry), loader: 'object' }));
  },
});
