import { describe, expect, spyOn, test } from 'bun:test';
import type { AudienceCommand } from './audience';
import { MOST_COMMAND_LENGTH, MOST_PARTS, RECEIVER_PROTOCOL, SILENT_PROTOCOL, receiverChannel } from './receiver-protocol';
import type { ReceiverAnnouncement } from './receiver-protocol';

const ANNOUNCEMENT: ReceiverAnnouncement = { type: 'receiver', protocol: RECEIVER_PROTOCOL, format: 1, orbit: 'f62a16c' };

/** A receiver's channel, with what it followed and what it answered. */
const tv = () => {
  const followed: AudienceCommand[] = [];
  const answered: { senderId: string | undefined; message: ReceiverAnnouncement }[] = [];
  const channel = receiverChannel({
    announcement: ANNOUNCEMENT,
    reply: (senderId, message) => answered.push({ senderId, message }),
    receive: (command) => followed.push(command),
  });
  return { channel, followed, answered };
};

/** A command in parts, the way the phone sends one too big for a Cast message. */
const inParts = (command: AudienceCommand, length: number) => {
  const text = JSON.stringify(command);
  const parts: string[] = [];

  for (let start = 0; start < text.length; start += length) {
    parts.push(text.slice(start, start + length));
  }

  return parts.map((part, index) => ({ type: 'part', index, count: parts.length, text: part }));
};

const SHOW: AudienceCommand = { type: 'show', markdown: '# Trip\n## Food\n## Museums\n', theme: { dark: false, seed: '#0B57D0', tokens: {} }, captions: true };

describe('a phone from before versions were said (Orbit for Android 0.1.1 and 0.2.0)', () => {
  test('its commands are followed as they come, and it counts as protocol 1', () => {
    const { channel, followed, answered } = tv();
    channel.hear({ type: 'language', words: { language: 'es' } }, 'old-phone');
    channel.hear(SHOW, 'old-phone');
    channel.hear({ type: 'step', id: 'food', index: 0, total: 2 }, 'old-phone');
    expect(followed.map((command) => command.type)).toEqual(['language', 'show', 'step']);
    expect(answered).toEqual([]);
    expect(channel.protocolOf('old-phone')).toBe(SILENT_PROTOCOL);
  });

  test('a map too big for one message is joined from its parts and followed once', () => {
    const { channel, followed } = tv();

    for (const part of inParts(SHOW, 10)) {
      channel.hear(part, 'old-phone');
    }

    expect(followed).toEqual([SHOW]);
  });
});

describe('a phone that says hello', () => {
  test('hears which protocol the receiver speaks, the newest map format its Orbit reads, and its Orbit', () => {
    const { channel, followed, answered } = tv();
    channel.hear({ type: 'hello', protocol: 2, app: '0.3.0' }, 'phone');
    expect(answered).toEqual([{ senderId: 'phone', message: ANNOUNCEMENT }]);
    expect(channel.protocolOf('phone')).toBe(2);
    // A hello is answered, never followed as a command.
    expect(followed).toEqual([]);
  });

  test('a newer phone is answered the same way, and its commands still followed', () => {
    const { channel, followed, answered } = tv();
    channel.hear({ type: 'hello', protocol: RECEIVER_PROTOCOL + 3, app: '2.0.0', shiny: true }, 'newer-phone');
    channel.hear({ ...SHOW, somethingNew: 'kept for later' }, 'newer-phone');
    expect(answered[0].message.protocol).toBe(RECEIVER_PROTOCOL);
    expect(channel.protocolOf('newer-phone')).toBe(RECEIVER_PROTOCOL + 3);
    expect(followed).toHaveLength(1);
    expect(followed[0].type).toBe('show');
  });

  test('a hello without a readable protocol counts as protocol 1', () => {
    const { channel } = tv();
    channel.hear({ type: 'hello', protocol: 'two' }, 'phone');
    expect(channel.protocolOf('phone')).toBe(SILENT_PROTOCOL);
  });

  test('each phone is kept apart, and forgotten when it leaves', () => {
    const { channel, followed } = tv();
    const first = inParts(SHOW, 12);
    const second = inParts({ ...SHOW, focus: 'food' }, 12);
    channel.hear({ type: 'hello', protocol: 2 }, 'phone');

    // Parts from two phones at once, interleaved, still come together as each one's own command.
    for (let index = 0; index < Math.max(first.length, second.length); index += 1) {
      if (first[index]) {
        channel.hear(first[index], 'phone');
      }

      if (second[index]) {
        channel.hear(second[index], 'tablet');
      }
    }

    expect(followed).toEqual([SHOW, { ...SHOW, focus: 'food' }]);
    channel.forget('phone');
    expect(channel.protocolOf('phone')).toBe(SILENT_PROTOCOL);
  });
});

test('anything that isn\'t a message is left alone', () => {
  const { channel, followed, answered } = tv();
  channel.hear('a stray string', 'phone');
  channel.hear(null, 'phone');
  expect(followed).toEqual([]);
  expect(answered).toEqual([]);
});

describe('parts', () => {
  test('come together whatever order they arrive in', () => {
    const { channel, followed } = tv();
    const parts = inParts(SHOW, 10);

    for (const part of [...parts].reverse()) {
      channel.hear(part, 'phone');
    }

    expect(followed).toEqual([SHOW]);
  });

  test('of a command left unfinished give way to the next command\'s', () => {
    const { channel, followed } = tv();
    const unfinished = inParts({ ...SHOW, focus: 'food' }, 10);
    channel.hear(unfinished[0], 'phone');
    channel.hear(unfinished[1], 'phone');

    for (const part of inParts(SHOW, 10)) {
      channel.hear(part, 'phone');
    }

    expect(followed).toEqual([SHOW]);
  });

  test('that aren\'t parts drop the command they were in, and the next one still comes', () => {
    const { channel, followed } = tv();
    const parts = inParts(SHOW, 10);
    const broken = [
      { ...parts[1], index: parts[1].count },
      { ...parts[1], index: -1 },
      { ...parts[1], index: 1.5 },
      { ...parts[1], count: 0 },
      { ...parts[1], count: 'many' },
      { ...parts[1], text: 42 },
      { type: 'part' },
    ];

    for (const part of broken) {
      channel.hear(parts[0], 'phone');
      channel.hear(part, 'phone');

      for (const rest of parts.slice(2)) {
        channel.hear(rest, 'phone');
      }
    }

    expect(followed).toEqual([]);

    for (const part of parts) {
      channel.hear(part, 'phone');
    }

    expect(followed).toEqual([SHOW]);
  });

  test('that don\'t make a command are dropped without a throw', () => {
    const { channel, followed } = tv();
    const reported = spyOn(console, 'error').mockImplementation(() => undefined);
    channel.hear({ type: 'part', index: 0, count: 2, text: '{"type":"sh' }, 'phone');
    expect(() => channel.hear({ type: 'part', index: 1, count: 2, text: 'ow",' }, 'phone')).not.toThrow();
    expect(followed).toEqual([]);
    expect(reported).toHaveBeenCalledTimes(1);
    reported.mockRestore();
  });

  test('can\'t add up to more than a command may hold', () => {
    const { channel, followed } = tv();
    // One string for every part, so the test holds it once.
    const third = 'x'.repeat(Math.floor(MOST_COMMAND_LENGTH / 3) + 1);

    for (let index = 0; index < 3; index += 1) {
      channel.hear({ type: 'part', index, count: 4, text: third }, 'phone');
    }

    channel.hear({ type: 'part', index: 3, count: 4, text: '' }, 'phone');
    expect(followed).toEqual([]);

    // The room it took is let go: the next command comes as usual.
    for (const part of inParts(SHOW, 10)) {
      channel.hear(part, 'phone');
    }

    expect(followed).toEqual([SHOW]);
  });

  test('can\'t be counted past the most a command may come in', () => {
    const { channel, followed } = tv();
    channel.hear({ type: 'part', index: 0, count: MOST_PARTS + 1, text: JSON.stringify(SHOW) }, 'phone');
    expect(followed).toEqual([]);
  });
});
