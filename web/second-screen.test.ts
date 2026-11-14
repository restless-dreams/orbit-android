import { afterAll, describe, expect, spyOn, test } from 'bun:test';
import { OrbitSVGRenderer } from '@restless-dreams/orbit';
import type { AudienceCommand, MediaReport } from './audience';
import { eventually, showPage } from './page-for-tests';

// The audience's page on a second screen beside the phone, in the app's WebView there: it says hello on the app's
// channel, follows the presenter's commands, plays the presenter's videos big over the map from where the
// presenter's are, and tells the phone how each one goes (present/ShownMedia.kt corrects its reckoning with it).

const posted: unknown[] = [];
const listeners: ((event: MessageEvent<string>) => void)[] = [];

// The app's channel in its WebView, as it adds it to the page.
window.orbitNative = {
  postMessage: (message) => {
    posted.push(JSON.parse(message));
  },
  addEventListener: (_type, listener) => {
    listeners.push(listener);
  },
};

// happy-dom lays nothing out, and a map with no size draws nothing: this one is an HDMI screen's.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 1920, 1080));
const opening = spyOn(OrbitSVGRenderer.prototype, 'openMedia');

/** The clock the page's reports go by, which the test moves on: a playing video reports its time about once a second. */
let now = 1_000;
const timing = spyOn(performance, 'now').mockImplementation(() => now);

showPage('audience.html', { '<!-- waiting -->': 'Waiting for the presenter' });
await import('./second-screen');

afterAll(() => {
  measuring.mockRestore();
  opening.mockRestore();
  timing.mockRestore();
});

const send = (command: AudienceCommand): void => {
  for (const listener of listeners) {
    listener(new MessageEvent('message', { data: JSON.stringify(command) }));
  }
};

const reports = (): MediaReport[] => posted.filter((message): message is MediaReport => (message as { type?: string }).type === 'mediaState');

const caption = (): string => document.querySelector('.audience__title')?.textContent ?? '';

/** The video playing over the map, as the screen's WebView plays it: the test says where it is and how it goes. */
const playing = (): HTMLVideoElement => document.querySelector('dialog[open] video')!;

const video = (element: HTMLVideoElement, state: { time: number; paused?: boolean; ended?: boolean; muted?: boolean; duration?: number }, event: string): void => {
  Object.defineProperty(element, 'paused', { configurable: true, get: () => state.paused ?? false });
  Object.defineProperty(element, 'ended', { configurable: true, get: () => state.ended ?? false });
  Object.defineProperty(element, 'duration', { configurable: true, get: () => state.duration ?? 190 });
  Object.defineProperty(element, 'currentTime', { configurable: true, get: () => state.time, set: () => undefined });
  element.muted = state.muted ?? false;
  element.dispatchEvent(new Event(event));
};

const MAP: AudienceCommand = {
  type: 'show',
  markdown: '# Lisbon\n\n## Tram 28\n[![Tram 28](media/tram.jpg)](media/tram.mp4)\n\nUp the hill.\n\n## Belém\n\nBy the river.\n',
  theme: { dark: false, tokens: {} },
  baseUrl: 'https://appassets.androidplatform.net/maps/lisbon/',
  focus: 'lisbon',
  captions: true,
};

describe('a second screen', () => {
  test('says hello on the app\'s channel as it starts, so the app knows it can send', () => {
    expect(posted).toEqual([{ type: 'hello' }]);
  });

  test('speaks the phone\'s language and follows the presenter\'s steps', async () => {
    send({ type: 'language', words: { language: 'es', waiting: 'Esperando a quien presenta' } });
    expect(document.querySelector('.audience__waiting')?.textContent).toBe('Esperando a quien presenta');

    send(MAP);
    send({ type: 'step', id: 'tram-28', index: 1, total: 3 });
    await eventually(() => caption() === 'Tram 28');
  });

  test('plays the presenter\'s video from where the presenter\'s is, from the map\'s folder on the phone', () => {
    send({ type: 'media', id: 'tram-28', kind: 'video', playing: true, at: 12.5 });

    expect(opening).toHaveBeenLastCalledWith('tram-28', { kind: 'video', autoplay: true, startAt: 12.5 });
    expect(playing().getAttribute('src')).toBe('https://appassets.androidplatform.net/maps/lisbon/media/tram.mp4');
  });

  test('tells the phone how the video goes: at once as it changes, and its time about once a second while it plays', () => {
    const element = playing();
    video(element, { time: 12.5 }, 'playing');
    expect(reports()).toEqual([{ type: 'mediaState', id: 'tram-28', playing: true, time: 12.5, duration: 190, ended: false, muted: false }]);

    // Its time, where the phone reckons it is anyway: once a second at most.
    now += 400;
    video(element, { time: 12.9 }, 'timeupdate');
    now += 400;
    video(element, { time: 13.3 }, 'timeupdate');
    expect(reports().length).toBe(1);
    now += 400;
    video(element, { time: 13.7 }, 'timeupdate');
    expect(reports().length).toBe(2);
    expect(reports()[1].time).toBe(13.7);

    // A jump puts it away from where the phone reckons it: said at once.
    now += 300;
    video(element, { time: 14 }, 'timeupdate');
    expect(reports().length).toBe(2);
    now += 100;
    video(element, { time: 61 }, 'seeked');
    expect(reports().length).toBe(3);
    expect(reports()[2].time).toBe(61);

    // Paused, muted, and played to the end: each at once.
    video(element, { time: 61, paused: true }, 'pause');
    video(element, { time: 61, paused: true, muted: true }, 'volumechange');
    video(element, { time: 190, paused: true, ended: true, muted: true }, 'ended');
    expect(reports().slice(3).map(({ playing, muted, ended }) => ({ playing, muted, ended }))).toEqual([
      { playing: false, muted: false, ended: false },
      { playing: false, muted: true, ended: false },
      { playing: false, muted: true, ended: true },
    ]);
  });

  test('pauses, plays and moves the video as the presenter does', () => {
    const pausing = spyOn(OrbitSVGRenderer.prototype, 'pauseMedia');
    const resuming = spyOn(OrbitSVGRenderer.prototype, 'playMedia');
    const seeking = spyOn(OrbitSVGRenderer.prototype, 'seekMedia');

    send({ type: 'playback', playing: false });
    send({ type: 'seek', at: 0 });
    send({ type: 'playback', playing: true });

    expect(pausing).toHaveBeenCalledTimes(1);
    expect(seeking).toHaveBeenCalledWith(0);
    expect(resuming).toHaveBeenCalledTimes(1);
    pausing.mockRestore();
    resuming.mockRestore();
    seeking.mockRestore();
  });

  test('a video moved before it knows its length opens again from there, rather than go back to where it opened', () => {
    const seeking = spyOn(OrbitSVGRenderer.prototype, 'seekMedia');
    send({ type: 'media', id: 'tram-28', kind: 'video', playing: true, at: 40 });
    expect(opening).toHaveBeenLastCalledWith('tram-28', { kind: 'video', autoplay: true, startAt: 40 });

    // Started over at once, before the video has said anything of itself.
    send({ type: 'seek', at: 0 });
    expect(opening).toHaveBeenLastCalledWith('tram-28', { kind: 'video', autoplay: true, startAt: undefined });
    expect(seeking).not.toHaveBeenCalled();

    // Once it knows its length, a move moves it where it is.
    video(playing(), { time: 0 }, 'loadedmetadata');
    send({ type: 'seek', at: 20 });
    expect(seeking).toHaveBeenCalledWith(20);
    seeking.mockRestore();
  });

  test('goes back to the map as the presenter steps on, and says nothing more of that video', async () => {
    const element = playing();
    const before = reports().length;
    send({ type: 'step', id: 'belem', index: 2, total: 3 });

    expect(document.querySelector('dialog[open] video')).toBeNull();
    await eventually(() => caption() === 'Belém');
    now += 5_000;
    video(element, { time: 30 }, 'timeupdate');
    expect(reports().length).toBe(before);
  });
});
