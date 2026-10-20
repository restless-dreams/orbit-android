// Type-checks the pages (tsconfig.json) against the Orbit they're built
// with: its types at ORBIT_PATH (from web/; default: ../../orbit), built
// first with `bun run build` there. tsconfig.json's `paths` finds them in
// the default checkout; this finds them at ORBIT_PATH.
//
//   ORBIT_PATH=../orbit bun run typecheck
//
// tsconfig.json's `lib` is ES2022 with the DOM, so the pages can't use a
// newer part of JavaScript than WebViews from Chromium 99 on have; the build
// holds the bundles, Orbit's code included, to ES2022's syntax.
import { existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import ts from 'typescript';

const orbitPath = resolve(import.meta.dir, process.env.ORBIT_PATH ?? '../../orbit');
const orbitTypes = join(orbitPath, 'dist/index.d.ts');

if (!existsSync(orbitTypes)) {
  console.error(`No Orbit build at ${orbitTypes}. Run \`bun run build\` in your Orbit checkout, or point ORBIT_PATH at it.`);
  process.exit(1);
}

const host: ts.FormatDiagnosticsHost = {
  getCanonicalFileName: (name) => name,
  getCurrentDirectory: () => ts.sys.getCurrentDirectory(),
  getNewLine: () => ts.sys.newLine,
};

const report = (diagnostics: readonly ts.Diagnostic[]): void => {
  if (diagnostics.length > 0) {
    console.error(ts.formatDiagnosticsWithColorAndContext(diagnostics, host));
    process.exit(1);
  }
};

const config = ts.getParsedCommandLineOfConfigFile(
  join(import.meta.dir, 'tsconfig.json'),
  { paths: { '@restless-dreams/orbit': [orbitTypes] } },
  { ...ts.sys, onUnRecoverableConfigFileDiagnostic: (diagnostic) => report([diagnostic]) },
);

if (!config) {
  process.exit(1);
}

report(config.errors);
report(ts.getPreEmitDiagnostics(ts.createProgram({ rootNames: config.fileNames, options: config.options })));
console.log(`The pages' types check against Orbit ${orbitPath}.`);
