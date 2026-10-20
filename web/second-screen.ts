/**
 * The audience page on a second screen beside the phone, such as a TV on
 * HDMI or a wireless display. The app's WebView there sends commands as
 * messages on the `orbitNative` channel, the way the map's own page gets
 * them, once the page says hello, and hears how a video it plays is going.
 * Nothing there can be touched, so the app lets its videos play with sound
 * from the start.
 */
import { receive, reportMedia } from './audience';
import type { AudienceCommand } from './audience';

const host = window as unknown as {
  orbitNative?: {
    postMessage(message: string): void;
    addEventListener(type: 'message', listener: (event: MessageEvent<string>) => void): void;
  };
};

host.orbitNative?.addEventListener('message', (event) => receive(JSON.parse(event.data) as AudienceCommand));
reportMedia((report) => host.orbitNative?.postMessage(JSON.stringify(report)));
host.orbitNative?.postMessage(JSON.stringify({ type: 'hello' }));
