import { afterAll, beforeEach, describe, expect, spyOn, test } from 'bun:test';
import { OrbitSVGRenderer } from '@restless-dreams/orbit';
import type { AudienceCommand } from './audience';
import { showPage } from './page-for-tests';

// The presenter's video on the audience's page in a browser in the room, which plays muted until someone there taps
// for its sound (browser-audience.ts): the sound comes on where the presenter's video is, as the page reckons it from
// the presenter's commands, the same way the phone does (present/ShownMedia.kt).

/** The page's clock, which the test moves on. */
let now = 1_000;
const timing = spyOn(performance, 'now').mockImplementation(() => now);
// happy-dom lays nothing out, and a map with no size draws nothing: this one is a laptop's on a projector.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 1280, 720));
const seeking = spyOn(OrbitSVGRenderer.prototype, 'seekMedia').mockImplementation(() => true);

showPage('audience.html', { '<!-- waiting -->': 'Waiting for the presenter' });
const { playWithSound, receive } = await import('./audience');

afterAll(() => {
  receive({ type: 'end' });
  seeking.mockRestore();
  measuring.mockRestore();
  timing.mockRestore();
});

const MAP: AudienceCommand = {
  type: 'show',
  markdown: '# Lisbon\n\n## Tram 28\n[![Tram 28](media/tram.jpg)](media/tram.mp4)\n\nUp the hill.\n',
  theme: { dark: false, tokens: {} },
  baseUrl: 'http://localhost/314159/',
  focus: 'tram-28',
};

/** The video over the map, as the browser plays it: ten seconds long, where the test says it is. */
const playing = (): { time: number; paused: boolean; ended: boolean } => {
  const video = document.querySelector<HTMLVideoElement>('dialog[open] video')!;
  const state = { time: 0, paused: false, ended: false };
  Object.defineProperty(video, 'currentTime', { configurable: true, get: () => state.time, set: () => undefined });
  Object.defineProperty(video, 'paused', { configurable: true, get: () => state.paused });
  Object.defineProperty(video, 'ended', { configurable: true, get: () => state.ended });
  Object.defineProperty(video, 'duration', { configurable: true, get: () => 10 });
  video.muted = true;
  video.dispatchEvent(new Event('loadedmetadata'));
  video.dispatchEvent(new Event('playing'));
  return state;
};

/** The video says how it goes now, as the browser does with `event`. */
const says = (event: string): void => {
  document.querySelector<HTMLVideoElement>('dialog[open] video')!.dispatchEvent(new Event(event));
};

beforeEach(() => {
  now = 1_000;
  seeking.mockClear();
  receive(MAP);
  receive({ type: 'media', id: 'tram-28', kind: 'video', playing: true, at: 0 });
});

describe('the sound tapped for in a browser', () => {
  test('comes on where the presenter\'s video is, played again after its end', () => {
    const video = playing();

    // It ends after its ten seconds, and five seconds later the presenter plays it again: it starts over.
    now = 11_000;
    video.time = 10;
    video.paused = true;
    video.ended = true;
    says('ended');
    now = 16_000;
    receive({ type: 'playback', playing: true });

    // The browser's own video started over too, and stalled at its start; four seconds on, someone taps for sound.
    video.time = 0;
    video.paused = false;
    video.ended = false;
    says('playing');
    now = 20_000;
    playWithSound();

    // Four seconds in, not the 19 the clock would say from the first start, past the video's end.
    expect(seeking.mock.calls.map(([seconds]) => seconds)).toEqual([4]);
  });

  test('comes on where the presenter\'s video stopped, never past its end', () => {
    const video = playing();

    // It ends, and a few seconds later the presenter pauses it there: half a minute on, someone taps for sound.
    now = 11_000;
    video.time = 10;
    video.paused = true;
    video.ended = true;
    says('ended');
    now = 15_000;
    receive({ type: 'playback', playing: false });
    now = 45_000;
    playWithSound();

    // The browser's video is where the presenter's is, at its end: nothing to move.
    expect(seeking).not.toHaveBeenCalled();
  });
});
