/**
 * The audience's page in any browser on the local network: a laptop on a
 * projector, or phones in the room following along. The presenter's phone
 * serves it, at /<code>/, to those who joined, and sends each command as a
 * server-sent event; the browser reconnects by itself if the connection
 * drops. When the phone turns the page away, the page says why.
 *
 * Browsers play a video's sound only once someone has touched the page: a
 * video that should play but is muted, or didn't start, gets a "Tap for
 * sound" button over it. There can be dozens of browsers, so they tell the
 * phone nothing of how their videos go.
 *
 * The page speaks the presenter's phone's language: the phone serves it
 * with its words (#orbit-words) and its `lang`.
 */
import { hear, playWithSound, receive, watchMedia } from './audience';
import type { AudienceCommand, Shown } from './audience';
import { pageWords, servedWords } from './words';
import type { OrbitMediaState } from '@restless-dreams/orbit';

/** Where this browser stands with the presentation, as /<code>/status says. */
type RoomState = 'joined' | 'removed' | 'full' | 'closed' | 'ended' | 'left';
type TurnedAway = 'removed' | 'full' | 'closed' | 'ended';

/** How long a page that was turned away waits before asking again, in ms. */
const ASK_AGAIN = 10_000;
/** How long a page that's still in waits before following again, in ms. */
const FOLLOW_AGAIN = 3_000;
/** How long a video that should play may take to start before the page offers its sound, in ms. */
const START_GRACE = 1_500;

// The phone's language and words, before the page says anything.
hear(servedWords());

const code = location.pathname.split('/').filter(Boolean)[0] ?? '';
// Pictures come from the phone, next to this page.
const base = new URL(`/${code}/`, location.href).href;
const waiting = document.querySelector<HTMLElement>('.audience__waiting');

const say = (text: string): void => {
  if (waiting) {
    waiting.textContent = text;
  }
};

const follow = (): void => {
  const events = new EventSource(`/${code}/events`);

  events.onmessage = (event) => {
    const message = JSON.parse(event.data) as AudienceCommand | { type: 'removed' };

    if (message.type === 'removed') {
      events.close();
      turnAway('removed');
      return;
    }

    receive(message.type === 'show' ? { ...message, baseUrl: base } : message);
  };

  events.onopen = () => say(pageWords().waiting);

  events.onerror = () => {
    // The browser tries a dropped connection again by itself, but not one the phone refused: ask the phone why.
    if (events.readyState === EventSource.CLOSED) {
      void explain();
    } else {
      say(pageWords().looking);
    }
  };
};

const where = async (): Promise<RoomState | null> => {
  try {
    const response = await fetch(`/${code}/status`, { cache: 'no-store' });
    const body = (await response.json()) as { state?: RoomState };
    return body.state ?? null;
  } catch {
    return null;
  }
};

const explain = async (): Promise<void> => {
  const state = await where();

  switch (state) {
    case 'joined':
      window.setTimeout(follow, FOLLOW_AGAIN);
      return;
    case 'left':
      // In again the way anyone joins: at once, or through the form.
      location.reload();
      return;
    case 'removed':
    case 'ended':
      turnAway(state);
      return;
    case 'full':
    case 'closed':
      turnAway(state);
      window.setTimeout(() => void explain(), ASK_AGAIN);
      return;
    default:
      say(pageWords().looking);
      window.setTimeout(() => void explain(), ASK_AGAIN);
  }
};

const turnAway = (why: TurnedAway): void => {
  receive({ type: 'end' });
  // Why, in the words the phone's join page uses for the same reasons.
  say(pageWords()[why]);
};

/**
 * The button for a video's sound. The viewer is a modal dialog in the top
 * layer, which makes the rest of the page inert, popovers included: the
 * button is a modal dialog of its own, shown after the viewer, so it sits
 * above it and takes the tap.
 */
const sound = document.createElement('dialog');
sound.className = 'audience__sound';
sound.tabIndex = -1;
const soundButton = document.createElement('button');
soundButton.type = 'button';
soundButton.className = 'audience__sound-button';
soundButton.innerHTML = '<svg class="audience__sound-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.47 4.47 0 0 0 16.5 12zM14 3.23v2.06a7 7 0 0 1 0 13.42v2.06A9 9 0 0 0 14 3.23z"/></svg>';
soundButton.append(pageWords().tapForSound);
sound.append(soundButton);
document.body.append(sound);

const soundOn = (): void => {
  playWithSound();
  sound.close();
};

// It stays until it's tapped, anywhere on it, or the video plays with sound.
sound.addEventListener('cancel', (event) => event.preventDefault());
sound.addEventListener('click', soundOn);

sound.addEventListener('keydown', (event) => {
  if (event.target === sound && (event.key === 'Enter' || event.key === ' ')) {
    event.preventDefault();
    soundOn();
  }
});

let startWait = 0;
let shownBefore: Shown | null = null;

watchMedia((shown, state) => offerSound(shown, state));

/** Shows the button while a video that should play is muted, or hasn't started after a moment; hides it otherwise. */
const offerSound = (shown: Shown | null, state: OrbitMediaState | null): void => {
  // Something new over the map: the viewer may have opened again, above the button.
  if (shown?.id !== shownBefore?.id || shown?.kind !== shownBefore?.kind) {
    sound.close();
  }

  shownBefore = shown;
  window.clearTimeout(startWait);
  const wanted = shown?.kind === 'video' && shown.playing;

  if (!wanted || state?.ended) {
    sound.close();
    return;
  }

  if (state?.muted) {
    offer();
  } else if (state?.playing) {
    sound.close();
  } else {
    startWait = window.setTimeout(offer, START_GRACE);
  }
};

const offer = (): void => {
  if (sound.open) {
    return;
  }

  try {
    sound.showModal();
  } catch {
    sound.setAttribute('open', '');
  }

  // The dialog holds the focus rather than its button: no focus ring on a screen for a room, and Enter or Space still turn the sound on.
  sound.focus({ preventScroll: true });
};

follow();
