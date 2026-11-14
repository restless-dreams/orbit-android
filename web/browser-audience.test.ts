import { afterAll, afterEach, describe, expect, spyOn, test } from 'bun:test';
import { OrbitSVGRenderer } from '@restless-dreams/orbit';
import type { AudienceCommand } from './audience';
import { eventually, showPage, testClock } from './page-for-tests';

// The audience's page in a browser in the room, as the presenter's phone serves it, following the phone's events:
// what it does as the connection drops and comes back, as the phone sleeps and wakes, and as the room turns it away.
// Each test opens the page anew, as a browser joining.

/** The phone's words, in its language, as it writes them into the page it serves. */
const SPANISH = {
  language: 'es',
  waiting: 'Esperando a quien presenta',
  looking: 'Buscando a quien presenta…',
  removed: 'Quien presenta te quitó.',
  full: 'Esta presentación está llena.',
  closed: 'Quien presenta no deja entrar a nadie más.',
  ended: 'La presentación terminó.',
};

const streams: PhoneEvents[] = [];

/** An event stream from the phone, as the browser's EventSource is: the test says what the phone does with it. */
class PhoneEvents {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;

  readyState = PhoneEvents.CONNECTING;
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(readonly url: string) {
    streams.push(this);
  }

  close(): void {
    this.readyState = PhoneEvents.CLOSED;
    this.closed = true;
  }

  /** The phone answers: the stream is open. */
  open(): void {
    this.readyState = PhoneEvents.OPEN;
    this.onopen?.();
  }

  /** The phone sends a command. */
  say(command: AudienceCommand | { type: 'removed' }): void {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(command) }));
  }

  /** The connection drops, as when the phone's Wi-Fi blinks: the browser tries it again by itself. */
  drop(): void {
    this.readyState = PhoneEvents.CONNECTING;
    this.onerror?.();
  }

  /** The phone refuses the stream, as after it slept, or turned this browser away: the browser gives up on it. */
  refuse(): void {
    this.readyState = PhoneEvents.CLOSED;
    this.onerror?.();
  }
}

/** What /<code>/status answers, in order: a state, or an error for a phone out of reach. */
const answers: (string | Error)[] = [];
const asked: string[] = [];

(window as unknown as { EventSource: typeof PhoneEvents }).EventSource = PhoneEvents;

const fetching = spyOn(window, 'fetch').mockImplementation((async (address: string | URL | Request) => {
  asked.push(String(address));
  const answer = answers.shift() ?? new Error('No answer');

  if (answer instanceof Error) {
    throw answer;
  }

  return new Response(JSON.stringify({ state: answer }), { headers: { 'Content-Type': 'application/json' } });
}) as typeof fetch);

const reloading = spyOn(location, 'reload').mockImplementation(() => undefined);

// happy-dom lays nothing out, and a map with no size draws nothing: this one is a laptop's on a projector.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 1280, 720));
const flying = spyOn(OrbitSVGRenderer.prototype, 'flyTo');

// The room's page, at the presentation's code, with the phone's words, as the phone serves it.
history.replaceState(null, '', '/314159/');
showPage('audience.html', {
  '<!-- waiting -->': SPANISH.waiting,
  '<!-- scripts -->': `<script id="orbit-words" type="application/json">${JSON.stringify(SPANISH)}</script>`,
});
const clock = testClock();

afterEach(() => {
  // Each test ends with the page following or turned away for good: none of its waits is left for the next one.
  expect(clock.waiting).toBe(0);
  answers.length = 0;
  asked.length = 0;
  reloading.mockClear();
});

afterAll(() => {
  fetching.mockRestore();
  reloading.mockRestore();
  measuring.mockRestore();
  flying.mockRestore();
});

/** The map as the phone sends it, its picture at an address the phone gives no browser: the page asks the phone for it. */
const SHOW: AudienceCommand = {
  type: 'show',
  markdown: '# Lisbon\n\n## Alfama\n\nThe old town.\n\n## Belém\n![The tower](media/tower.jpg)\n\nBy the river.\n',
  theme: { dark: false, tokens: {} },
  baseUrl: 'https://appassets.androidplatform.net/maps/lisbon/',
  focus: 'alfama',
  captions: true,
};

const waitingSays = (): string => document.querySelector('.audience__waiting')?.textContent ?? '';

/** What the page says over the map about the connection, while it says anything: the waiting screen is under the map. */
const statusSays = (): string => {
  const status = document.querySelector<HTMLElement>('.audience__status');
  return status && !status.hidden ? status.textContent ?? '' : '';
};

const showing = (): boolean => document.body.classList.contains('audience--showing');

const caption = (): string => document.querySelector('.audience__title')?.textContent ?? '';

const latest = (): PhoneEvents => streams[streams.length - 1];

/** The phone opens a stream and catches the browser up: the map, and the idea the presenter is on. */
const catchUp = async (stream: PhoneEvents): Promise<void> => {
  stream.open();
  stream.say(SHOW);
  stream.say({ type: 'step', id: 'belem', index: 2, total: 3 });
  await eventually(() => caption() === 'Belém');
};

let opened = 0;

/** Opens the page anew, as a browser joining the room does, and follows the presentation. */
const join = async (): Promise<PhoneEvents> => {
  opened += 1;
  await import(`./browser-audience.ts?${opened}`);
  const stream = latest();
  await catchUp(stream);
  return stream;
};

describe('a browser in the room', () => {
  test('follows the presentation at its own address, where the map\'s pictures come from too', async () => {
    const stream = await join();

    expect(stream.url).toBe('/314159/events');
    expect(showing()).toBe(true);
    expect(caption()).toBe('Belém');
    expect(flying.mock.calls.at(-1)?.[0]).toBe('belem');
    expect([...document.querySelectorAll('#map image')].map((image) => image.getAttribute('href'))).toEqual(['http://localhost/314159/media/tower.jpg']);
    expect(waitingSays()).toBe(SPANISH.waiting);
  });

  test('says it\'s looking for the phone while the connection is down, and waits for the presenter once it\'s back', async () => {
    const stream = await join();
    expect(statusSays()).toBe('');
    stream.drop();
    expect(waitingSays()).toBe(SPANISH.looking);
    // The map stays on screen, over the waiting screen: the page says it over the map.
    expect(showing()).toBe(true);
    expect(statusSays()).toBe(SPANISH.looking);

    // The browser tries the same stream again by itself: no new one, and nothing asked of the phone.
    clock.advance(30_000);
    expect(latest()).toBe(stream);
    expect(asked).toEqual([]);

    stream.open();
    expect(waitingSays()).toBe(SPANISH.waiting);
    expect(statusSays()).toBe('');
    // What was on screen stayed while the phone was away.
    expect(caption()).toBe('Belém');
  });

  test('asks the phone why when it refuses the stream, and follows again a moment later while it\'s still in', async () => {
    const refused = await join();
    answers.push('joined');
    refused.refuse();
    await eventually(() => clock.waiting === 1);

    expect(asked).toEqual(['/314159/status']);
    clock.advance(2_999);
    expect(latest()).toBe(refused);
    clock.advance(1);

    const again = latest();
    expect(again).not.toBe(refused);
    expect(again.url).toBe('/314159/events');
    // The phone catches it up with where the presentation is now.
    await catchUp(again);
    expect(showing()).toBe(true);
  });

  test('comes back after the phone slept long enough to forget it, in again the way anyone joins', async () => {
    const refused = await join();
    answers.push('left');
    refused.refuse();
    await eventually(() => reloading.mock.calls.length === 1);

    expect(latest()).toBe(refused);
  });

  test('keeps looking while the phone is out of reach, and follows again once it answers', async () => {
    const refused = await join();
    answers.push(new Error('Network unreachable'), new Error('Network unreachable'), 'joined');
    refused.refuse();
    await eventually(() => clock.waiting === 1);
    expect(waitingSays()).toBe(SPANISH.looking);
    expect(statusSays()).toBe(SPANISH.looking);
    expect(asked.length).toBe(1);

    clock.advance(10_000);
    expect(asked.length).toBe(2);
    await eventually(() => clock.waiting === 1);
    expect(waitingSays()).toBe(SPANISH.looking);
    expect(latest()).toBe(refused);

    clock.advance(10_000);
    expect(asked.length).toBe(3);
    await eventually(() => clock.waiting === 1);
    clock.advance(3_000);
    expect(latest()).not.toBe(refused);
    await catchUp(latest());
    expect(waitingSays()).toBe(SPANISH.waiting);
    expect(statusSays()).toBe('');
  });

  test('stops at once when the presenter removes it, says so, and asks nothing more', async () => {
    const stream = await join();
    stream.say({ type: 'removed' });

    expect(stream.closed).toBe(true);
    expect(showing()).toBe(false);
    expect(waitingSays()).toBe(SPANISH.removed);
    clock.advance(60_000);
    expect(asked).toEqual([]);
    expect(latest()).toBe(stream);
  });

  for (const state of ['removed', 'ended'] as const) {
    test(`turned away for good (${state}) says why and leaves the map, and asks nothing more`, async () => {
      const refused = await join();
      answers.push(state);
      refused.refuse();
      await eventually(() => waitingSays() === SPANISH[state]);

      expect(showing()).toBe(false);
      // Said in the middle of the screen, where the map was: nothing over it says it's still looking.
      expect(statusSays()).toBe('');
      clock.advance(60_000);
      expect(asked).toEqual(['/314159/status']);
      expect(latest()).toBe(refused);
    });
  }

  for (const state of ['full', 'closed'] as const) {
    test(`turned away for now (${state}) says why, and asks again until it's let back in`, async () => {
      const refused = await join();
      answers.push(state, state, 'joined');
      refused.refuse();
      await eventually(() => clock.waiting === 1);

      expect(showing()).toBe(false);
      expect(waitingSays()).toBe(SPANISH[state]);
      clock.advance(10_000);
      await eventually(() => clock.waiting === 1);
      expect(waitingSays()).toBe(SPANISH[state]);
      expect(latest()).toBe(refused);

      clock.advance(10_000);
      await eventually(() => clock.waiting === 1);
      clock.advance(3_000);
      expect(asked.length).toBe(3);
      expect(latest()).not.toBe(refused);
      await catchUp(latest());
      expect(showing()).toBe(true);
    });
  }
});
