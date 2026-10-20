/**
 * The audience page on a TV: the Cast receiver a phone presenting a map casts
 * to. Commands come as JSON on the app's own Cast channel, and a map too big
 * for one Cast message (64 KB) arrives in numbered parts (receiver-protocol.ts,
 * which also says how the phone and the TV tell each other their versions).
 * How a video plays goes back to the phones on the same channel. TVs play
 * videos with their sound from the start.
 *
 * A TV can't read the app's resources: until a phone sends the words of its
 * presentation, in the phone's language, the TV speaks its own, English or
 * Spanish, by `navigator.language`.
 */
import { SUPPORTED_SCHEMA_VERSION } from '@restless-dreams/orbit';
import { hear, receive, reportMedia } from './audience';
import { RECEIVER_PROTOCOL, receiverChannel } from './receiver-protocol';
import { tvWords } from './words';

/** The Orbit the receiver is built with, by commit (build.ts). */
declare const ORBIT_COMMIT: string;

/** The app's Cast channel; the phone sends on the same one. */
const NAMESPACE = 'urn:x-cast:com.hyuchia.orbit.present';

/** The little of the Cast receiver framework this page uses. */
interface ReceiverContext {
  addCustomMessageListener(namespace: string, listener: (event: { data: unknown; senderId?: string }) => void): void;
  addEventListener(type: string, listener: (event: { senderId?: string }) => void): void;
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

const channel = receiverChannel({
  announcement: { type: 'receiver', protocol: RECEIVER_PROTOCOL, format: SUPPORTED_SCHEMA_VERSION, orbit: ORBIT_COMMIT },
  reply: (senderId, message) => {
    try {
      context.sendCustomMessage(NAMESPACE, senderId, message);
    } catch {
      // The phone left before the answer went.
    }
  },
  receive,
});

hear({ language: own.language, waiting: own.waiting });
context.addCustomMessageListener(NAMESPACE, ({ data, senderId }) => channel.hear(data, senderId));

// To every phone connected.
reportMedia((report) => {
  try {
    context.sendCustomMessage(NAMESPACE, undefined, report);
  } catch {
    // No phone to tell, between one leaving and the TV going back.
  }
});

// When the last phone lets go, the TV goes back to what it showed before.
context.addEventListener(cast.framework.system.EventType.SENDER_DISCONNECTED, ({ senderId }) => {
  if (senderId) {
    channel.forget(senderId);
  }

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
