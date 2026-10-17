/**
 * The audience page on a second screen beside the phone, such as a TV on
 * HDMI or a wireless display. The app's WebView there sends commands through
 * `window.orbitApp.receive()`, the way the map's own page gets them, once the
 * page says hello, and hears how a video it plays is going. Nothing there can
 * be touched, so the app lets its videos play with sound from the start.
 */
import { receive, reportMedia } from './audience';
import type { AudienceCommand } from './audience';

const host = window as unknown as {
  orbitApp?: { receive(command: AudienceCommand): void };
  orbitNative?: { postMessage(message: string): void };
};

host.orbitApp = { receive };
reportMedia((report) => host.orbitNative?.postMessage(JSON.stringify(report)));
host.orbitNative?.postMessage(JSON.stringify({ type: 'hello' }));
