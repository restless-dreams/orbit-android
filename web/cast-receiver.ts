/**
 * The audience page on a TV: the Cast receiver a phone presenting a map casts
 * to. Commands come as JSON on the app's own Cast channel, and a map too big
 * for one Cast message (64 KB) arrives in numbered parts. How a video plays
 * goes back to the phones on the same channel. TVs play videos with their
 * sound from the start.
 *
 * A TV can't read the app's resources: until a phone sends the words of its
 * presentation, in the phone's language, the TV speaks its own, English or
 * Spanish, by `navigator.language`.
 */
import { hear, receive, reportMedia } from './audience';
import type { AudienceCommand } from './audience';
import { tvWords } from './words';

/** The app's Cast channel; the phone sends on the same one. */
const NAMESPACE = 'urn:x-cast:com.hyuchia.orbit.present';

interface Part {
  type: 'part';
  index: number;
  count: number;
  text: string;
}

/** The little of the Cast receiver framework this page uses. */
interface ReceiverContext {
  addCustomMessageListener(namespace: string, listener: (event: { data: AudienceCommand | Part }) => void): void;
  addEventListener(type: string, listener: () => void): void;
  getSenders(): unknown[];
  sendCustomMessage(namespace: string, senderId: string | undefined, message: unknown): void;
  start(options: { customNamespaces: Record<string, string>; disableIdleTimeout: boolean; statusText: string }): void;
  stop(): void;
}

declare const cast: {
  framework: {
    CastReceiverContext: { getInstance(): ReceiverContext };
    system: { MessageType: { JSON: string }; EventType: { SENDER_DISCONNECTED: string } };
  };
};

const context = cast.framework.CastReceiverContext.getInstance();
const own = tvWords(navigator.language);
let parts: string[] = [];
let partsReceived = 0;

hear({ language: own.language, waiting: own.waiting });

context.addCustomMessageListener(NAMESPACE, ({ data }) => {
  if (data.type !== 'part') {
    receive(data);
    return;
  }

  if (data.index === 0) {
    parts = [];
    partsReceived = 0;
  }

  if (parts[data.index] === undefined) {
    partsReceived += 1;
  }

  parts[data.index] = data.text;

  if (partsReceived === data.count) {
    const whole = parts.join('');
    parts = [];
    partsReceived = 0;
    receive(JSON.parse(whole) as AudienceCommand);
  }
});

// To every phone connected.
reportMedia((report) => {
  try {
    context.sendCustomMessage(NAMESPACE, undefined, report);
  } catch {
    // No phone to tell, between one leaving and the TV going back.
  }
});

// When the last phone lets go, the TV goes back to what it showed before.
context.addEventListener(cast.framework.system.EventType.SENDER_DISCONNECTED, () => {
  if (context.getSenders().length === 0) {
    receive({ type: 'end' });
    context.stop();
  }
});

context.start({
  customNamespaces: { [NAMESPACE]: cast.framework.system.MessageType.JSON },
  // A map on screen isn't media playing: without this the TV would close it after a while.
  disableIdleTimeout: true,
  statusText: own.status,
});
