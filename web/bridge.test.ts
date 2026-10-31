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
    expect(state?.options.maxDepth).toBe(2);
    expect(state?.nodes.map(({ id, priority, typeVersion }) => ({ id, priority, typeVersion }))).toEqual([
      { id: 'plans', priority: null, typeVersion: null },
      { id: 'savings', priority: null, typeVersion: LARGEST_WHOLE },
      { id: 'someday', priority: null, typeVersion: LARGEST_WHOLE },
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
