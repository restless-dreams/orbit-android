// Bundles the pages' scripts, with Orbit. Orbit comes from a local
// checkout, built first with `bun run build` there:
//
//   ORBIT_PATH=../orbit bun run build    (from web/; default: ../../orbit)
//
// Into the app's assets (app/src/main/assets/map/):
// - bridge.js, the map's own page (index.html);
// - audience.js and audience.html, what a second screen shows while presenting;
// - web-page.js and web-page.html, a map shared as one HTML file, which the
//   app fills in with the map's title, its data and the script;
// - browser.js and browser.html, the audience's page in any browser on the
//   local network, which the presenter's phone serves with its words, and
//   join.html, which asks for the presentation's code.
//
// And into dist/cast-receiver/, the same audience page as a Cast receiver,
// for a TV: a site to host over HTTPS and register with Google Cast.
//
// And one of each message between the app and the pages, from
// protocol-examples.ts, into the app's tests
// (app/src/test/resources/page-protocol.json).
import { join, resolve } from 'node:path';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { parse } from 'acorn';
import { protocolExamples } from './protocol-examples';
import { ENGLISH_WORDS } from './words';

const orbitPath = resolve(import.meta.dir, process.env.ORBIT_PATH ?? '../../orbit');
const orbitEntry = join(orbitPath, 'dist/index.js');

if (!existsSync(orbitEntry)) {
  console.error(`No Orbit build at ${orbitEntry}. Run \`bun run build\` in your Orbit checkout, or point ORBIT_PATH at it.`);
  process.exit(1);
}

const assets = join(import.meta.dir, '../app/src/main/assets/map');
const receiver = join(import.meta.dir, 'dist/cast-receiver');
// The Orbit the pages are built with: the about screen names it, and the receiver tells phones.
const { version } = JSON.parse(readFileSync(join(orbitPath, 'package.json'), 'utf8')) as { version: string };
const commit = Bun.spawnSync(['git', '-C', orbitPath, 'rev-parse', '--short', 'HEAD']).stdout.toString().trim();

/**
 * The newest JavaScript the bundles may be written in: WebViews from
 * Chromium 99 on draw maps (SystemWebView.MINIMUM_CHROMIUM in the app), and
 * read up to ES2022. Bun leaves syntax as it's written, the pages' and
 * Orbit's, so a bundle that needs a newer browser fails the build.
 */
const NEWEST_SYNTAX = 2022;

const bundle = async (entry: string, outdir: string, name: string, define: Record<string, string> = {}): Promise<number> => {
  const result = await Bun.build({
    entrypoints: [join(import.meta.dir, entry)],
    outdir,
    naming: name,
    target: 'browser',
    format: 'esm',
    minify: true,
    define,
    plugins: [{
      name: 'orbit',
      setup(build) {
        build.onResolve({ filter: /^@restless-dreams\/orbit$/ }, () => ({ path: orbitEntry }));
      },
    }],
  });

  if (!result.success) {
    for (const log of result.logs) {
      console.error(log);
    }

    process.exit(1);
  }

  const output = result.outputs[0];

  try {
    parse(await output.text(), { ecmaVersion: NEWEST_SYNTAX, sourceType: 'module' });
  } catch (error) {
    console.error(`${output.path} needs JavaScript newer than ES${NEWEST_SYNTAX}, which WebViews from Chromium 99 on don't all read: ${(error as Error).message}`);
    process.exit(1);
  }

  return output.size;
};

const sizes = {
  bridge: await bundle('bridge.ts', assets, 'bridge.js'),
  audience: await bundle('second-screen.ts', assets, 'audience.js'),
  webPage: await bundle('web-page.ts', assets, 'web-page.js'),
  browser: await bundle('browser-audience.ts', assets, 'browser.js'),
  receiver: await bundle('cast-receiver.ts', receiver, 'receiver.js', { ORBIT_COMMIT: JSON.stringify(commit) }),
};

// The audience's styles, shared by every page that shows a presentation.
const styles = readFileSync(join(import.meta.dir, 'audience.css'), 'utf8').trimEnd();
const withStyles = (file: string) => readFileSync(join(import.meta.dir, file), 'utf8').replace('<!-- styles -->', styles);

// One page, three ways in: the app's second screen, browsers, and a Cast receiver that loads Google's framework first.
// The second screen and the TV wait in English until they hear another language. The phone writes browsers' page
// in its own, its words and its waiting text, so it reads right before its script arrives.
const page = withStyles('audience.html');
const waitingInEnglish = page.replace('<!-- waiting -->', ENGLISH_WORDS.waiting);
writeFileSync(join(assets, 'audience.html'), waitingInEnglish.replace('<!-- scripts -->', '<script type="module" src="audience.js"></script>'));
writeFileSync(join(assets, 'web-page.html'), withStyles('web-page.html'));
writeFileSync(join(assets, 'browser.html'), page.replace('<!-- scripts -->', '<!-- words -->\n<script type="module" src="browser.js"></script>'));
writeFileSync(join(assets, 'join.html'), withStyles('join.html'));
mkdirSync(receiver, { recursive: true });
writeFileSync(join(receiver, 'index.html'), waitingInEnglish.replace(
  '<!-- scripts -->',
  '<script src="https://www.gstatic.com/cast/sdk/libs/caf_receiver/v3/cast_receiver_framework.js"></script>\n<script type="module" src="receiver.js"></script>',
));

writeFileSync(join(import.meta.dir, '../app/src/main/assets/map/orbit.json'), `${JSON.stringify({ version, commit })}\n`);
writeFileSync(join(import.meta.dir, '../app/src/test/resources/page-protocol.json'), protocolExamples());
const kilobytes = (size: number) => `${(size / 1024).toFixed(0)} KB`;
console.log(`From Orbit ${version} at ${orbitPath}:`);
console.log(`  app/src/main/assets/map/bridge.js (${kilobytes(sizes.bridge)}), audience.js (${kilobytes(sizes.audience)}) and audience.html`);
console.log(`  app/src/main/assets/map/web-page.js (${kilobytes(sizes.webPage)}) and web-page.html`);
console.log(`  app/src/main/assets/map/browser.js (${kilobytes(sizes.browser)}), browser.html and join.html`);
console.log(`  web/dist/cast-receiver/index.html and receiver.js (${kilobytes(sizes.receiver)})`);
console.log('  app/src/test/resources/page-protocol.json');
