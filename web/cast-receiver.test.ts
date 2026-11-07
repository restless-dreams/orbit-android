import { afterAll, describe, expect, spyOn, test } from 'bun:test';
import { SUPPORTED_SCHEMA_VERSION } from '@restless-dreams/orbit';
import type { AudienceCommand, MediaReport } from './audience';
import { eventually, showPage } from './page-for-tests';
import { RECEIVER_PROTOCOL } from './receiver-protocol';

// The audience's page on a TV, as the Cast receiver phones cast to: it answers each phone's hello with its versions,
// follows commands that come whole or in parts, shows the poster of a video kept on the phone, which it can't reach,
// tells every phone how a video from the web goes (GoogleCast.kt hands that to the presentation), and goes back to
// what the TV showed before when the last phone lets go.

const NAMESPACE = 'urn:x-cast:com.hyuchia.orbit.present';

/** What the receiver sent, to which phone (none: every phone), in order. */
const sent: { to: string | undefined; message: unknown }[] = [];
let messages: ((event: { data: unknown; senderId?: string }) => void) | null = null;
let disconnected: ((event: { senderId?: string }) => void) | null = null;
let started: { customNamespaces: Record<string, string>; disableIdleTimeout: boolean; statusText: string } | null = null;
let stopped = 0;
let senders = ['phone', 'tablet'];

/** The little of Google's Cast receiver framework the page uses, as the TV runs it. */
const context = {
  addCustomMessageListener: (namespace: string, listener: typeof messages) => {
    expect(namespace).toBe(NAMESPACE);
    messages = listener;
  },
  addEventListener: (type: string, listener: typeof disconnected) => {
    expect(type).toBe('senderdisconnected');
    disconnected = listener;
  },
  getSenders: () => senders,
  sendCustomMessage: (namespace: string, to: string | undefined, message: unknown) => {
    expect(namespace).toBe(NAMESPACE);
    sent.push({ to, message });
  },
  start: (options: NonNullable<typeof started>) => {
    started = options;
  },
  stop: () => {
    stopped += 1;
  },
};

Object.assign(globalThis, {
  cast: {
    framework: {
      CastReceiverContext: { getInstance: () => context },
      system: { MessageType: { JSON: 'JSON' }, EventType: { SENDER_DISCONNECTED: 'senderdisconnected' } },
    },
  },
  // The build puts in the commit of the Orbit it bundles.
  ORBIT_COMMIT: 'abc1234',
});

// A TV set to Spanish, which waits in its own language until a phone says its own.
Object.defineProperty(navigator, 'language', { configurable: true, get: () => 'es-MX' });
// happy-dom lays nothing out, and a map with no size draws nothing: this one is a TV's.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 1920, 1080));

showPage('audience.html', { '<!-- waiting -->': 'Waiting for the presenter' });
await import('./cast-receiver');

afterAll(() => {
  measuring.mockRestore();
});

/** A message from a phone, as the framework hands it over. */
const from = (senderId: string, data: unknown): void => {
  messages?.({ data, senderId });
};

const reports = (): { to: string | undefined; message: MediaReport }[] => {
  return sent.filter((item): item is { to: string | undefined; message: MediaReport } => (item.message as { type?: string }).type === 'mediaState');
};

const caption = (): string => document.querySelector('.audience__title')?.textContent ?? '';

const showing = (): boolean => document.body.classList.contains('audience--showing');

const MAP: AudienceCommand = {
  type: 'show',
  markdown: [
    '# Lisbon',
    '',
    '## Tram 28',
    '[![Tram 28](data:image/png;base64,iVBORw0KGgo=)](media/tram.mp4)',
    '<!-- orbit caption: Up the hill -->',
    '',
    '## Fado',
    '[![Fado](https://example.org/fado.jpg)](https://example.org/fado.mp4)',
    '',
  ].join('\n'),
  theme: { dark: true, tokens: {} },
  focus: 'lisbon',
  captions: true,
};

describe('a TV', () => {
  test('starts in its own language, and stays on while a map shows', () => {
    expect(started).toEqual({ customNamespaces: { [NAMESPACE]: 'JSON' }, disableIdleTimeout: true, statusText: 'Presentando un mapa' });
    expect(document.querySelector('.audience__waiting')?.textContent).toBe('Esperando a quien presenta');
  });

  test('answers a phone\'s hello with the protocol it speaks, the newest map format it reads and its Orbit', () => {
    from('phone', { type: 'hello', protocol: 2, app: '0.4.0' });

    expect(sent).toEqual([{ to: 'phone', message: { type: 'receiver', protocol: RECEIVER_PROTOCOL, format: SUPPORTED_SCHEMA_VERSION, orbit: 'abc1234' } }]);
  });

  test('follows a phone\'s commands, a map too big for one message joined from its parts', async () => {
    const text = JSON.stringify(MAP);
    const cut = [text.slice(0, 40), text.slice(40, 90), text.slice(90)];
    from('phone', { type: 'part', index: 2, count: 3, text: cut[2] });
    from('phone', { type: 'part', index: 0, count: 3, text: cut[0] });
    expect(showing()).toBe(false);
    from('phone', { type: 'part', index: 1, count: 3, text: cut[1] });
    expect(showing()).toBe(true);

    from('phone', { type: 'step', id: 'tram-28', index: 1, total: 3 });
    await eventually(() => caption() === 'Tram 28');
  });

  test('shows the poster and caption of a video kept on the phone, which it can\'t reach, and says nothing of it', () => {
    from('phone', { type: 'media', id: 'tram-28', kind: 'video', playing: true, at: 3 });

    expect(document.body.classList.contains('audience--still')).toBe(true);
    expect(document.querySelector('.audience__still-title')?.textContent).toBe('Tram 28');
    expect(document.querySelector('.audience__still-text')?.textContent).toBe('Up the hill');
    expect(document.querySelector<HTMLImageElement>('.audience__still-picture')?.getAttribute('src')).toBe('data:image/png;base64,iVBORw0KGgo=');
    expect(document.querySelector('dialog[open] video')).toBeNull();
    expect(reports()).toEqual([]);
  });

  test('plays a video from the web, and tells every phone how it goes', async () => {
    from('phone', { type: 'step', id: 'fado', index: 2, total: 3 });
    await eventually(() => caption() === 'Fado');
    expect(document.body.classList.contains('audience--still')).toBe(false);

    from('phone', { type: 'media', id: 'fado', kind: 'video', playing: true, at: 0 });
    const video = document.querySelector<HTMLVideoElement>('dialog[open] video')!;
    expect(video.getAttribute('src')).toBe('https://example.org/fado.mp4');
    Object.defineProperty(video, 'paused', { configurable: true, get: () => false });
    Object.defineProperty(video, 'duration', { configurable: true, get: () => 241 });
    video.dispatchEvent(new Event('playing'));

    expect(reports()).toEqual([{ to: undefined, message: { type: 'mediaState', id: 'fado', playing: true, time: 0, duration: 241, ended: false, muted: false } }]);
  });

  test('goes on while a phone is still connected, and goes back to what it showed before when the last one lets go', () => {
    senders = ['phone'];
    disconnected?.({ senderId: 'tablet' });
    expect(showing()).toBe(true);
    expect(stopped).toBe(0);

    senders = [];
    disconnected?.({ senderId: 'phone' });
    expect(showing()).toBe(false);
    expect(stopped).toBe(1);
  });
});
