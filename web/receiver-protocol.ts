/**
 * How a TV's receiver (cast-receiver.ts) and the presenter's phone tell
 * each other which version of their messages they speak, and how a command
 * too big for one Cast message (64 KB) comes in numbered parts.
 *
 * Protocol 1 is every app from before versions were said (Orbit for
 * Android 0.1.1 and 0.2.0): it sends no hello, and the receiver keeps every
 * command it sends working. Protocol 2 says hello first, and hears the
 * receiver's answer: the receiver's protocol, the newest map format its
 * Orbit reads, and its Orbit. A receiver only ever adds to what it
 * understands, so it keeps working with every app that has shipped.
 */
import type { AudienceCommand } from './audience';

/** The protocol this receiver speaks. */
export const RECEIVER_PROTOCOL = 2;

/** The protocol of an app that says nothing of its own. */
export const SILENT_PROTOCOL = 1;

/** The most parts a command may come in. */
export const MOST_PARTS = 65_536;

/**
 * The most text a command's parts may add up to, in characters: twice a map
 * with every picture a map holds, shrunk for a TV, and its text. Past it, a
 * command is dropped, so no one on the network can fill the TV's memory.
 */
export const MOST_COMMAND_LENGTH = 128 * 1024 * 1024;

/** What the receiver answers a phone's hello with. */
export interface ReceiverAnnouncement {
  type: 'receiver';
  protocol: number;

  /** The newest map format (`schemaVersion`) the receiver's Orbit reads. */
  format: number;

  /** The Orbit the receiver draws maps with, by commit. */
  orbit: string;
}

/** What a phone that speaks protocol 2 or later says first. */
export interface SenderHello {
  type: 'hello';
  protocol?: number;

  /** The app's own version, for the receiver's console. */
  app?: string;
}

/** A piece of a command too big for one Cast message. */
export interface Part {
  type: 'part';
  index: number;
  count: number;
  text: string;
}

export interface ReceiverChannel {
  /** Takes one message from the phone `senderId`. */
  hear(data: unknown, senderId?: string): void;

  /** The protocol the phone `senderId` speaks: what its hello said, or `SILENT_PROTOCOL` for one that said none. */
  protocolOf(senderId?: string): number;

  /** Forgets a phone that left. */
  forget(senderId: string): void;
}

export interface ReceiverChannelOptions {
  announcement: ReceiverAnnouncement;

  /** Sends a message to the phone `senderId`. */
  reply(senderId: string | undefined, message: ReceiverAnnouncement): void;

  /** Follows a whole command from a phone. */
  receive(command: AudienceCommand): void;
}

const isObject = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null;

/** Whether a message is a part a command could come in: its place among a count of parts, and its text. */
const isPart = (data: Record<string, unknown>): data is Record<string, unknown> & Part => {
  const { type, index, count, text } = data;

  if (type !== 'part' || typeof index !== 'number' || typeof count !== 'number' || typeof text !== 'string') {
    return false;
  }

  return Number.isInteger(count) && count >= 1 && count <= MOST_PARTS && Number.isInteger(index) && index >= 0 && index < count;
};

/** A command coming in parts from one phone: its parts so far, how many, and their length together. */
interface Joining {
  count: number;
  parts: (string | undefined)[];
  received: number;
  length: number;
}

/**
 * The receiver's side of the channel. A hello is answered and kept, never
 * followed. Parts are joined per phone, and the whole command followed once
 * every part came, whatever order they came in; a part where the command
 * already has one starts the next command. A part that isn't one, a command
 * longer than `MOST_COMMAND_LENGTH` and parts that don't make JSON are
 * dropped. Anything else goes to `receive` as it is: the audience's page
 * follows what it knows and leaves the rest.
 */
export const receiverChannel = (options: ReceiverChannelOptions): ReceiverChannel => {
  const protocols = new Map<string, number>();
  const pieces = new Map<string, Joining>();
  const keyOf = (senderId?: string) => senderId ?? '';

  const join = (data: Record<string, unknown>, senderId?: string): void => {
    const key = keyOf(senderId);

    if (!isPart(data)) {
      pieces.delete(key);
      return;
    }

    let joining = pieces.get(key);

    if (!joining || joining.count !== data.count || joining.parts[data.index] !== undefined) {
      joining = { count: data.count, parts: new Array<string | undefined>(data.count), received: 0, length: 0 };
      pieces.set(key, joining);
    }

    joining.parts[data.index] = data.text;
    joining.received += 1;
    joining.length += data.text.length;

    if (joining.length > MOST_COMMAND_LENGTH) {
      pieces.delete(key);
      return;
    }

    if (joining.received < joining.count) {
      return;
    }

    pieces.delete(key);
    let command: unknown;

    try {
      command = JSON.parse(joining.parts.join(''));
    } catch (error) {
      console.error('Orbit couldn\'t read a command the phone sent in parts:', error);
      return;
    }

    options.receive(command as AudienceCommand);
  };

  return {
    hear(data, senderId) {
      if (!isObject(data)) {
        return;
      }

      if (data.type === 'hello') {
        const protocol = typeof data.protocol === 'number' && Number.isInteger(data.protocol) && data.protocol > 0 ? data.protocol : SILENT_PROTOCOL;
        protocols.set(keyOf(senderId), protocol);
        options.reply(senderId, options.announcement);
        return;
      }

      if (data.type === 'part') {
        join(data, senderId);
        return;
      }

      options.receive(data as unknown as AudienceCommand);
    },

    protocolOf(senderId) {
      return protocols.get(keyOf(senderId)) ?? SILENT_PROTOCOL;
    },

    forget(senderId) {
      protocols.delete(senderId);
      pieces.delete(senderId);
    },
  };
};
