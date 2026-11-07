import { afterAll, describe, expect, spyOn, test } from 'bun:test';
import type { Command, MapState, Message } from './bridge';
import { LARGEST_WHOLE, SMALLEST_WHOLE } from './whole-numbers';

// The map's page as the app drives it: the app's commands go in on the channel it adds to the page, and what the
// page posts on it comes out, as the app hears it.

const posted: Message[] = [];
const listeners: ((event: MessageEvent<string>) => void)[] = [];

window.orbitNative = {
  postMessage: (message) => {
    posted.push(JSON.parse(message) as Message);
  },
  addEventListener: (_type, listener) => {
    listeners.push(listener);
  },
};

// happy-dom lays nothing out, and a map with no size draws nothing: this one is a phone's.
const measuring = spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(() => new DOMRect(0, 0, 412, 700));
document.body.appendChild(document.createElement('div')).id = 'map';
await import('./bridge');

afterAll(() => {
  send({ type: 'unload' });
  measuring.mockRestore();
});

const send = (command: Command): void => {
  for (const listener of listeners) {
    listener(new MessageEvent('message', { data: JSON.stringify(command) }));
  }
};

/** What the page posted of `type` after the first `from` messages. */
const postedOf = <Type extends Message['type']>(type: Type, from: number): Extract<Message, { type: Type }>[] => {
  return posted.slice(from).filter((message): message is Extract<Message, { type: Type }> => message.type === type);
};

const load = (markdown: string): void => {
  send({ type: 'load', markdown, theme: { dark: false, tokens: {} }, settings: { haptics: false, snap: false, longPressDelay: 400 }, mapId: 'plans' });
};

/** The fields of a node the app reads as whole numbers, in its Int. */
const WHOLE_FIELDS = ['depth', 'index', 'childCount', 'priority', 'typeVersion'] as const;

/** The numbers in `state` the app reads as whole ones but couldn't hold, each as `where: number`. */
const unheld = (state: MapState): string[] => {
  const numbers = [
    ...state.nodes.flatMap((node) => WHOLE_FIELDS.map((field) => ({ where: `${node.id}.${field}`, number: node[field] }))),
    { where: 'options.maxDepth', number: state.options.maxDepth ?? null },
  ];

  return numbers
    .filter(({ number }) => number !== null && !(Number.isInteger(number) && number >= SMALLEST_WHOLE && number <= LARGEST_WHOLE))
    .map(({ where, number }) => `${where}: ${number}`);
};

describe("a map's numbers", () => {
  // Numbers Orbit reads that the app's Int can't hold: levels between whole ones, versions and priorities past its range.
  const PLANS = [
    '---',
    'maxDepth: 1.5',
    '---',
    '# Plans',
    '- Savings <!-- orbit type: goal@99999999999, target: 5, current: 2 -->',
    `- Someday <!-- orbit type: goal@${'9'.repeat(400)} -->`,
    '- Errands <!-- orbit priority: 1000000000000 -->',
    '- Chores <!-- orbit priority: -1e21 -->',
    '',
  ].join('\n');

  test('reach the app as whole numbers it holds, the nearest to what they mean', () => {
    const from = posted.length;
    load(PLANS);
    const state = postedOf('ready', from)[0]?.state;

    expect(state && unheld(state)).toEqual([]);
    // Orbit shows the levels up to maxDepth, so 1.5 shows what 2 does; a version past what the app holds is newer than any it knows.
    // One past what a number holds exactly Orbit keeps as written, unread, and its idea has no type.
    expect(state?.options.maxDepth).toBe(2);
    expect(state?.nodes.map(({ id, priority, typeVersion }) => ({ id, priority, typeVersion }))).toEqual([
      { id: 'plans', priority: null, typeVersion: null },
      { id: 'savings', priority: null, typeVersion: LARGEST_WHOLE },
      { id: 'someday', priority: null, typeVersion: null },
      { id: 'errands', priority: LARGEST_WHOLE, typeVersion: null },
      { id: 'chores', priority: SMALLEST_WHOLE, typeVersion: null },
    ]);
  });

  test('never keep a change or a save from the app, and stay in the map as they were written', () => {
    load(PLANS);
    const from = posted.length;
    send({ type: 'update', id: 'errands', changes: { note: 'Before noon' } });
    send({ type: 'theme', theme: { dark: true, tokens: {} } });
    const saved = postedOf('saved', from)[0];
    const state = postedOf('state', from)[0]?.state;

    expect(saved && unheld(saved.state)).toEqual([]);
    expect(state && unheld(state)).toEqual([]);
    expect(saved?.markdown).toContain('Before noon');

    for (const written of ['maxDepth: 1.5', 'type: goal@99999999999', `type: goal@${'9'.repeat(400)}`, 'priority: 1000000000000', 'priority: -1e21']) {
      expect(saved?.markdown).toContain(written);
    }
  });
});

describe("an idea's date", () => {
  test('reaches the app as Orbit writes it, and the app sets one or takes it off', () => {
    const from = posted.length;
    load('# Trip\n## Flights <!-- orbit due: 2026-11-20T09:30 -->\n## Hotel\n');
    const state = postedOf('ready', from)[0]?.state;
    expect(state?.nodes.map(({ id, due }) => ({ id, due }))).toEqual([{ id: 'trip', due: null }, { id: 'flights', due: '2026-11-20 09:30' }, { id: 'hotel', due: null }]);

    const after = posted.length;
    send({ type: 'update', id: 'hotel', changes: { due: '2026-11-21' } });
    send({ type: 'update', id: 'flights', changes: { due: null } });
    const saved = postedOf('saved', after).at(-1);
    expect(saved?.markdown).toBe('# Trip\n## Flights\n## Hotel <!-- orbit due: "2026-11-21" -->\n');
  });
});

describe('a picture of a map the app calls off', () => {
  /** A thumbnail's job, as the app asks for one. */
  const thumbnail = (job: number): Command => ({
    type: 'snapshot',
    job,
    format: 'webp',
    markdown: '# Trip to Japan\n- Tokyo\n- Kyoto\n',
    width: 480,
    height: 300,
    scale: 2,
    theme: { dark: false, tokens: {} },
    transparent: true,
    style: null,
    view: null,
    focus: null,
    baseUrl: null,
  });

  /** Lets the page's work go on, a few turns of its event loop, as long as a picture takes in happy-dom and more. */
  const settle = async (): Promise<void> => {
    for (let turn = 0; turn < 20; turn += 1) {
      await new Promise((resolve) => setTimeout(resolve, 5));
    }
  };

  const answers = (job: number, from: number) => postedOf('result', from).filter((result) => result.job === job);

  test('answers, when it isn\'t called off', async () => {
    const from = posted.length;
    send(thumbnail(41));
    await settle();

    expect(answers(41, from)).toHaveLength(1);
  });

  test('stops before it draws the picture, and answers nothing', async () => {
    const making = spyOn(document, 'createElement');
    const from = posted.length;
    // Called off as soon as it starts: the thumbnail scrolled away.
    send(thumbnail(42));
    send({ type: 'cancel', job: 42 });
    await settle();

    expect(answers(42, from)).toEqual([]);
    expect(making.mock.calls.filter(([tag]) => tag === 'canvas')).toEqual([]);
    making.mockRestore();
  });

  test('called off once it answered, or never asked, changes nothing', async () => {
    const from = posted.length;
    send(thumbnail(43));
    await settle();
    send({ type: 'cancel', job: 43 });
    send({ type: 'cancel', job: 4040 });
    send(thumbnail(44));
    await settle();

    expect(answers(43, from)).toHaveLength(1);
    expect(answers(44, from)).toHaveLength(1);
    expect(postedOf('error', from)).toEqual([]);
  });
});

describe('an idea renamed', () => {
  test('by typing on the map says its new id in the save, as a change from the app does', () => {
    load('# Trip\n- Tokio\n- Kyoto\n');
    const from = posted.length;
    // Rename, from the idea's menu or F2: the field opens on the idea, and leaving it keeps what was typed.
    send({ type: 'edit', id: 'tokio' });
    const field = document.querySelector('textarea');

    if (!field) {
      throw new Error('No field open on the map');
    }

    field.value = 'Tokyo';
    send({ type: 'stopEditing', save: true });
    send({ type: 'update', id: 'kyoto', changes: { text: 'Kioto' } });

    expect(postedOf('saved', from).map((saved) => saved.renamed)).toEqual([{ from: 'tokio', to: 'tokyo' }, { from: 'kyoto', to: 'kioto' }]);
  });
});

describe('a map presented, or made into a path', () => {
  /** Ctrl and a letter, typed on the map, with no field open for typing. */
  const typed = (letter: string): void => {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: letter, code: `Key${letter.toUpperCase()}`, ctrlKey: true, bubbles: true, cancelable: true }));
  };

  test('takes nothing pasted or dropped, however late it comes, and takes it again once it\'s back', () => {
    load('# Trip\n- Tokyo\n');
    send({ type: 'present', on: true });
    const from = posted.length;

    // A paste, or ideas a drop brings, that the app sent just as the presentation, or the path, began.
    send({ type: 'paste', job: 71, parentId: 'trip', text: 'Kyoto\nOsaka' });
    send({ type: 'addNodes', job: 72, parentId: 'trip', nodes: [{ text: 'Nara' }] });

    expect(postedOf('result', from).map((result) => [result.job, result.error])).toEqual([[71, 'This map can\'t be changed'], [72, 'This map can\'t be changed']]);
    expect(postedOf('saved', from)).toEqual([]);

    send({ type: 'present', on: false });
    send({ type: 'paste', job: 73, parentId: 'trip', text: 'Kyoto' });

    expect(postedOf('result', from).find((result) => result.job === 73)?.value).toEqual({ count: 1 });
    expect(postedOf('saved', from).at(-1)?.markdown).toContain('Kyoto');
  });

  test('copies its ideas with Ctrl+C, but neither cuts nor pastes', () => {
    load('# Trip\n- Tokyo\n');
    send({ type: 'present', on: true });
    const from = posted.length;

    typed('x');
    typed('v');
    typed('c');

    expect(postedOf('clipboard', from).map((clipboard) => clipboard.action)).toEqual(['copy']);

    send({ type: 'present', on: false });
    typed('v');

    expect(postedOf('clipboard', from).map((clipboard) => clipboard.action)).toEqual(['copy', 'paste']);
  });
});

describe('an idea brought up close, to sketch on', () => {
  /** Waits until `ready` says so, a frame at a time, for as long as a flight takes and then some. */
  const until = async (ready: () => boolean): Promise<void> => {
    for (let waited = 0; waited < 3000 && !ready(); waited += 16) {
      await Bun.sleep(16);
    }
  };

  test('says where it starts, where it rests in the room the app leaves, and when the map is back', async () => {
    load('# Plans\n- Savings\n- Errands\n- Chores\n');
    const from = posted.length;

    send({ type: 'spotlight', id: 'savings', insets: { top: 0, right: 0, bottom: 100, left: 0 }, share: null });
    expect(postedOf('spotlight', from).map((message) => message.phase)).toEqual(['start']);
    await until(() => postedOf('spotlight', from).some((message) => message.phase === 'rest'));

    // The phone's 412 by 700, less the app's 100 at the bottom: the idea's middle in the middle of that, 86% of 412 across.
    const rest = postedOf('spotlight', from).find((message) => message.phase === 'rest');
    expect(rest).toMatchObject({ id: 'savings', x: 206, y: 300 });
    expect(rest?.radius).toBeCloseTo((412 * 0.86) / 2, 1);

    send({ type: 'endSpotlight' });
    await until(() => postedOf('spotlight', from).some((message) => message.phase === 'end'));
    expect(postedOf('spotlight', from).map((message) => message.phase).filter((phase) => phase !== 'rest')).toEqual(['start', 'end']);
  });

  test('ends at once for an idea the map doesn’t draw, so the app never waits', () => {
    load('# Plans\n- Savings\n');
    const from = posted.length;
    send({ type: 'spotlight', id: 'nowhere', insets: { top: 0, right: 0, bottom: 0, left: 0 }, share: null });
    expect(postedOf('spotlight', from)).toEqual([{ type: 'spotlight', id: 'nowhere', phase: 'end', x: 0, y: 0, radius: 0, scrim: null, map: 'plans' } as Message]);
  });
});

describe('a box of a note\'s checklist checked from the app', () => {
  const TRIP = '# Trip\n## Packing\nBefore we go:\n> - [ ] Boots\n> - [x] Hat\n\n| Item | Cost |\n| :--- | ---: |\n| Hotel | 300 |\n- Day one\n';

  test('changes just that line of the file, as an edit, in the note the map has now', () => {
    load(TRIP);
    const from = posted.length;
    // Two presses in a row: the second is made on the note the first left, so neither undoes the other.
    send({ type: 'checkNote', id: 'packing', line: 2, checked: true });
    send({ type: 'checkNote', id: 'packing', line: 3, checked: false });
    const saves = postedOf('saved', from);

    expect(saves.map((saved) => saved.kind)).toEqual(['update', 'update']);
    expect(saves[0].markdown).toBe(TRIP.replace('> - [ ] Boots', '> - [x] Boots'));
    expect(saves[1].markdown).toBe(TRIP.replace('> - [ ] Boots', '> - [x] Boots').replace('> - [x] Hat', '> - [ ] Hat'));
    expect(saves[1].state.nodes.find((node) => node.id === 'packing')?.note).toBe('Before we go:\n\n- [x] Boots\n- [ ] Hat\n\n| Item | Cost |\n| :--- | ---: |\n| Hotel | 300 |');
  });

  test('saves nothing when the box already is that way, and says so when the line has no box', () => {
    load(TRIP);
    const from = posted.length;
    send({ type: 'checkNote', id: 'packing', line: 3, checked: true });
    send({ type: 'checkNote', id: 'packing', line: 0, checked: true });
    send({ type: 'checkNote', id: 'nowhere', line: 2, checked: true });

    expect(postedOf('saved', from)).toEqual([]);
    expect(postedOf('declined', from).map((declined) => declined.change)).toEqual(['checkNote', 'checkNote']);
  });

  test('changes nothing on a map that can\'t be changed', () => {
    load(TRIP);
    send({ type: 'lock', reason: 'deleted' });
    const from = posted.length;
    send({ type: 'checkNote', id: 'packing', line: 2, checked: true });
    send({ type: 'lock', reason: null });

    expect(postedOf('saved', from)).toEqual([]);
  });
});

describe("an idea's files, added and taken off from the app", () => {
  const TRIP = '# Trip\n## Tickets\n<!-- orbit\nfiles:\n  - src: media/file-a.pdf\n    name: A.pdf\n    pages: 3\n-->\n## Hotel\n';
  const B = { src: 'media/file-b.xlsx', name: 'Budget: May.xlsx', type: 'application/vnd.ms-excel', size: 48213 };

  test('each idea says its files, with its size only as a whole number of bytes', () => {
    const from = posted.length;
    load('# Trip\n## Tickets <!-- orbit files: [media/a.pdf, { src: media/b.pdf, size: 1.5 }, { src: media/c.pdf, size: 9007199254740993 }] -->\n');
    const state = postedOf('ready', from)[0]?.state;
    const tickets = state?.nodes.find((node) => node.id === 'tickets');
    expect(tickets?.files).toEqual([
      { src: 'media/a.pdf', name: null, type: null, size: null },
      { src: 'media/b.pdf', name: null, type: null, size: null },
      { src: 'media/c.pdf', name: null, type: null, size: null },
    ]);
  });

  test('files added one after another all stay, after the ones the idea has, which keep what Orbit doesn\'t read', () => {
    load(TRIP);
    const from = posted.length;
    send({ type: 'addFiles', id: 'tickets', files: [B] });
    send({ type: 'addFiles', id: 'tickets', files: [{ src: 'media/file-c.zip', name: null, type: null, size: null }, B] });
    const saves = postedOf('saved', from);

    expect(saves.map((saved) => saved.kind)).toEqual(['update', 'update']);
    expect(saves[1].markdown).toBe('# Trip\n## Tickets\n<!-- orbit\nfiles:\n  - src: media/file-a.pdf\n    name: A.pdf\n    pages: 3\n  - src: media/file-b.xlsx\n    name: "Budget: May.xlsx"\n    type: application/vnd.ms-excel\n    size: 48213\n  - src: media/file-c.zip\n-->\n## Hotel\n');
    expect(saves[1].state.nodes.find((node) => node.id === 'tickets')?.files.map((file) => file.src)).toEqual(['media/file-a.pdf', 'media/file-b.xlsx', 'media/file-c.zip']);
  });

  test('a file taken off leaves the others as they are, and the last one takes the attribute with it', () => {
    load(TRIP);
    const from = posted.length;
    send({ type: 'addFiles', id: 'hotel', files: [B] });
    send({ type: 'removeFile', id: 'hotel', src: 'media/file-b.xlsx' });
    send({ type: 'removeFile', id: 'tickets', src: 'media/nowhere.pdf' });
    send({ type: 'removeFile', id: 'tickets', src: 'media/file-a.pdf' });
    const saves = postedOf('saved', from);

    expect(saves.length).toBe(3);
    expect(saves.at(-1)?.markdown).toBe('# Trip\n## Tickets\n## Hotel\n');
  });

  test('changes nothing on a map that can\'t be changed', () => {
    load(TRIP);
    send({ type: 'lock', reason: 'deleted' });
    const from = posted.length;
    send({ type: 'addFiles', id: 'tickets', files: [B] });
    send({ type: 'removeFile', id: 'tickets', src: 'media/file-a.pdf' });
    send({ type: 'lock', reason: null });

    expect(postedOf('saved', from)).toEqual([]);
  });
});
