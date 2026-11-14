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

/** Waits until `ready` says so, a frame at a time, for as long as a flight takes and then some. */
const until = async (ready: () => boolean): Promise<void> => {
  for (let waited = 0; waited < 3000 && !ready(); waited += 16) {
    await Bun.sleep(16);
  }
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

describe("a live photo's motion", () => {
  test('goes only onto the picture it was taken with, never onto one that replaced it meanwhile', () => {
    load('# Trip\n## Lake\n\n![The lake](media/lake.jpg)\n');
    const from = posted.length;

    // The motion was copied in for the lake's picture, which the shore's took the place of meanwhile.
    send({ type: 'update', id: 'lake', changes: { image: { src: 'media/shore.jpg' } } });
    send({ type: 'update', id: 'lake', changes: { image: { motion: { src: 'media/lake-motion.mp4' } } }, onto: { image: 'media/lake.jpg' } });
    expect(postedOf('saved', from).map((saved) => saved.markdown)).toEqual(['# Trip\n## Lake\n\n![](media/shore.jpg)\n']);

    // Onto the picture it was taken with, it goes.
    const after = posted.length;
    send({ type: 'update', id: 'lake', changes: { image: { motion: { src: 'media/shore-motion.mp4' } } }, onto: { image: 'media/shore.jpg' } });
    expect(postedOf('saved', after).at(-1)?.markdown).toContain('media/shore-motion.mp4');
  });
});

describe('a change the app waits to hear about', () => {
  /** What the page posted after the first `from` messages, as the app hears it: saves and job `job`'s answer, in order. */
  const heard = (from: number, job: number): (string | unknown)[] => {
    return posted.slice(from).flatMap((message) => {
      if (message.type === 'saved') {
        return [message.markdown];
      }

      return message.type === 'result' && message.job === job ? [message.error ?? message.value] : [];
    });
  };

  test('is answered once the idea took it, after its save', () => {
    load('# Trip\n## Lake\n');
    const from = posted.length;
    send({ type: 'update', id: 'lake', changes: { image: { src: 'media/sketch-a.svg', alt: 'A line' } }, job: 41 });

    expect(heard(from, 41)).toEqual(['# Trip\n## Lake\n![A line](media/sketch-a.svg)\n', true]);
  });

  test("is answered false for an idea the map doesn't have now, renamed or removed meanwhile, and saves nothing", () => {
    load('# Trip\n- Tokio\n- Kyoto\n');
    send({ type: 'update', id: 'tokio', changes: { text: 'Tokyo' } });
    const from = posted.length;
    send({ type: 'update', id: 'tokio', changes: { image: { src: 'media/late.jpg' } }, job: 42 });
    send({ type: 'remove', id: 'kyoto' });
    const removed = posted.length;
    send({ type: 'update', id: 'kyoto', changes: { image: { src: 'media/late.jpg' } }, job: 43 });

    expect(heard(from, 42)).toEqual([false, '# Trip\n- Tokyo\n']);
    expect(heard(removed, 43)).toEqual([false]);
    expect(postedOf('declined', from)).toEqual([]);
  });

  test('is answered false when the file it was for is no longer the idea\'s', () => {
    load('# Trip\n## Lake\n\n![](media/shore.jpg)\n');
    const from = posted.length;
    send({ type: 'update', id: 'lake', changes: { image: { motion: { src: 'media/lake-motion.mp4' } } }, onto: { image: 'media/lake.jpg' }, job: 44 });

    expect(heard(from, 44)).toEqual([false]);
  });

  test('is answered with why Orbit declined it, rather than said as declined', () => {
    load('# Trip\n## Lake\n');
    const from = posted.length;
    send({ type: 'update', id: 'lake', changes: { image: 42 }, job: 45 });

    expect(heard(from, 45)).toEqual([expect.stringContaining('image')]);
    expect(postedOf('declined', from)).toEqual([]);
  });

  test('adding files is answered too: once they are the idea\'s, and false for an idea gone meanwhile', () => {
    load('# Trip\n## Tickets\n## Hotel\n');
    const file = { src: 'media/file-a.pdf', name: 'A.pdf', type: 'application/pdf', size: 4 };
    const from = posted.length;
    send({ type: 'addFiles', id: 'tickets', files: [file], job: 47 });
    // Again: the idea has it already, which is what was asked.
    send({ type: 'addFiles', id: 'tickets', files: [file], job: 48 });
    send({ type: 'remove', id: 'hotel' });
    send({ type: 'addFiles', id: 'hotel', files: [file], job: 49 });

    expect(heard(from, 47).slice(0, 2)).toEqual([expect.stringContaining('media/file-a.pdf'), true]);
    expect(postedOf('result', from).map((result) => [result.job, result.value])).toEqual([[47, true], [48, true], [49, false]]);
  });

  test("is answered false on a map that can't be changed, never left to wait", () => {
    load('# Trip\n## Lake\n');
    send({ type: 'lock', reason: 'deleted' });
    const from = posted.length;
    send({ type: 'update', id: 'lake', changes: { image: { src: 'media/sketch-a.svg' } }, job: 46 });
    send({ type: 'lock', reason: null });

    expect(heard(from, 46)).toEqual([false]);
  });
});

describe('ideas caught on the go', () => {
  test("go into a map drawn out of sight that asks for its pictures where the map keeps them, not beside the page", async () => {
    const asked: string[] = [];
    const original = Element.prototype.setAttribute;

    const setting = spyOn(Element.prototype, 'setAttribute').mockImplementation(function (this: Element, name: string, value: string) {
      if (this.tagName.toLowerCase() === 'image' && name === 'href') {
        asked.push(value);
      }

      original.call(this, name, value);
    });

    const from = posted.length;
    const markdown = '# Trip\n## Lake\n\n![The lake](media/lake.jpg)\n## Shore\n\n![](media/shore.jpg)\n';

    try {
      send({ type: 'addIdeas', job: 81, markdown, text: 'Call the hotel', parentId: null, baseUrl: 'https://appassets.androidplatform.net/maps/trip/' });

      for (let turn = 0; turn < 20 && postedOf('result', from).length === 0; turn += 1) {
        await new Promise((resolve) => setTimeout(resolve, 5));
      }
    } finally {
      setting.mockRestore();
    }

    expect(postedOf('result', from).find((result) => result.job === 81)?.value).toContain('Call the hotel');
    // Each picture there is, from the map's own folder, and none beside the page.
    expect([...new Set(asked)].sort()).toEqual(['https://appassets.androidplatform.net/maps/trip/media/lake.jpg', 'https://appassets.androidplatform.net/maps/trip/media/shore.jpg']);
  });

  test('that hold no idea Orbit reads add none, and give the map\'s text back as it was, for the app to say so', async () => {
    const from = posted.length;
    const markdown = '# Trip <!-- orbit id: trip -->\n\n- Lisbon\n';
    send({ type: 'addIdeas', job: 82, markdown, text: '---\ntags: [home]\n---\n', parentId: null, baseUrl: null });

    for (let turn = 0; turn < 20 && postedOf('result', from).length === 0; turn += 1) {
      await new Promise((resolve) => setTimeout(resolve, 5));
    }

    const answer = postedOf('result', from).find((result) => result.job === 82);
    expect(answer?.error).toBeUndefined();
    expect(answer?.value).toBe(markdown);
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

describe('a picture of a map', () => {
  // Each picture, as the map's own text points at it, or another map's, from wherever the page is.
  const TRIP = [
    '# Trip',
    '## Own',
    '![](media/own.png)',
    '## Next door',
    '![](../other/media/next-door.png)',
    '## Another map',
    '![](../../maps/other/media/another.png)',
    '## By its address',
    `![](${location.origin}/maps/plans/media/shown.png)`,
    '## From the web',
    '![](https://example.com/web.png)',
    '',
  ].join('\n');

  test("carries its own map's pictures and the web's, and never another map's, whatever the page shows", async () => {
    // The page shows another map meanwhile, whose pictures it may load.
    load('# Plans\n- One\n');
    const fetched: string[] = [];

    const fetching = spyOn(globalThis, 'fetch').mockImplementation(async (input: string | URL | Request) => {
      fetched.push(input instanceof Request ? input.url : String(input));
      return new Response(new Blob([new Uint8Array([137, 80, 78, 71])], { type: 'image/png' }));
    });

    const from = posted.length;
    send({
      type: 'snapshot',
      job: 61,
      format: 'svg',
      markdown: TRIP,
      width: 800,
      height: 600,
      scale: 1,
      theme: { dark: false, tokens: {} },
      transparent: false,
      style: null,
      view: 'all',
      focus: null,
      baseUrl: `${location.origin}/jobs/0123abcd/`,
    });
    await until(() => postedOf('result', from).some((result) => result.job === 61));
    fetching.mockRestore();

    const svg = postedOf('result', from).find((result) => result.job === 61)?.value;
    expect(typeof svg).toBe('string');
    expect(fetched.sort()).toEqual([`${location.origin}/jobs/0123abcd/media/own.png`, 'https://example.com/web.png']);
    expect(String(svg)).not.toContain('/maps/');
    expect(String(svg)).not.toContain('next-door');
  });
});

describe('a drawing of a map, its pictures inside it', () => {
  test('reads its pictures one at a time, each once however many planets show it', async () => {
    let inFlight = 0;
    let most = 0;
    const asked: string[] = [];

    const fetching = spyOn(globalThis, 'fetch').mockImplementation((async (input: RequestInfo | URL) => {
      asked.push(String(input));
      inFlight += 1;
      most = Math.max(most, inFlight);
      await new Promise((resolve) => setTimeout(resolve, 5));
      inFlight -= 1;
      return new Response(new Blob([new Uint8Array([137, 80, 78, 71])], { type: 'image/png' }));
    }) as typeof fetch);

    const from = posted.length;

    send({
      type: 'snapshot',
      job: 61,
      format: 'svg',
      markdown: '# Trip\n## Tram\n![](media/tram.png)\n## Tram again\n![](media/tram.png)\n## Belem\n![](media/belem.png)\n## Sintra\n![](media/sintra.png)\n',
      width: 800,
      height: 600,
      scale: 1,
      theme: { dark: false, tokens: {} },
      transparent: false,
      style: null,
      view: 'overview',
      focus: null,
      baseUrl: 'https://appassets.androidplatform.net/maps/trip/',
    });

    for (let turn = 0; turn < 100 && postedOf('result', from).length === 0; turn += 1) {
      await new Promise((resolve) => setTimeout(resolve, 5));
    }

    fetching.mockRestore();
    const svg = String(postedOf('result', from).find((result) => result.job === 61)?.value ?? '');

    expect(most).toBe(1);
    expect(asked.map((url) => url.replace(/^.*\//, '')).sort()).toEqual(['belem.png', 'sintra.png', 'tram.png']);
    expect(svg).not.toContain('media/');
    expect((svg.match(/href="data:image\/png;base64,/g) ?? []).length).toBe(4);
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

describe('text typed in the source editor', () => {
  test('is the map as it is now, even over an earlier version on show, and takes changes again', () => {
    load('# Now\n- Today\n');
    send({ type: 'preview', markdown: '---\nschemaVersion: 999\n---\n# Then\n- Before\n', editable: false });
    const from = posted.length;

    send({ type: 'source', text: '# Now\n- Typed\n' });

    expect(postedOf('state', from).at(-1)?.state.title).toBe('Now');
    expect(postedOf('state', from).at(-1)?.state.readOnly).toBeNull();
    send({ type: 'update', id: 'typed', changes: { note: 'Saved' } });
    expect(postedOf('saved', from).map((saved) => saved.markdown)).toEqual(['# Now\n- Typed\n  Saved\n']);
  });
});

describe('an idea being typed on the map as a change from elsewhere arrives', () => {
  const TRIP = '# Trip\n- Tokio\n';

  /** The map as the app draws it, at the app's `revision` of its text. */
  const loadAt = (markdown: string, revision: number): void => {
    send({ type: 'load', markdown, theme: { dark: false, tokens: {} }, settings: { haptics: false, snap: false, longPressDelay: 400 }, mapId: 'trip', revision });
  };

  /** Types `text` in the field open on the map, and leaves it open. */
  const typeOnTheMap = (text: string): void => {
    const field = document.querySelector('textarea');

    if (!field) {
      throw new Error('No field open on the map');
    }

    field.value = text;
  };

  /** The ideas the map on the page shows now, as the app last heard of them. */
  const ideasSince = (from: number): string[] | undefined => {
    return postedOf('state', from).at(-1)?.state.nodes.map((node) => node.text);
  };

  const typings = [
    { idea: 'a new idea', open: { type: 'editNew', parentId: 'trip' } as Command, typed: 'Kyoto draft', saved: '# Trip\n- Tokio\n- Kyoto draft\n' },
    { idea: 'an idea renamed', open: { type: 'edit', id: 'tokio' } as Command, typed: 'Tokyo draft', saved: '# Trip\n- Tokyo draft\n' },
  ];

  const changes = [
    { change: 'a newer Orbit wrote', markdown: '---\nschemaVersion: 999\n---\n# Trip\n- Kyoto\n', lock: 'newer' },
    { change: 'Orbit can\'t read all of', markdown: '---\ntheme: [night\n---\n# Trip\n- Kyoto\n', lock: 'unreadable' },
  ];

  for (const { idea, open, typed, saved } of typings) {
    for (const { change, markdown, lock } of changes) {
      test(`keeps ${idea}, saved on the map as it was, before drawing a change ${change}`, () => {
        loadAt(TRIP, 1);
        send(open);
        typeOnTheMap(typed);
        const from = posted.length;

        send({ type: 'restore', markdown, revision: 2 });

        // Saved at the revision it was typed on, which the app keeps in the map's history, never over the change.
        const saves = postedOf('saved', from);
        expect(saves.map((save) => ({ markdown: save.markdown, revision: save.revision, readOnly: save.state.readOnly }))).toEqual([{ markdown: saved, revision: 1, readOnly: null }]);
        // Before the map can only be shown: the app takes no save from a map that can't be changed.
        const order = posted.slice(from).map((message) => (message.type === 'state' ? `state ${message.state.readOnly}` : message.type));
        expect(order.indexOf('saved')).toBeLessThan(order.indexOf(`state ${lock}`));
        expect(order).toContain('editorClose');
        // The change is what's drawn, and only shown.
        expect(postedOf('state', from).at(-1)?.state.readOnly).toBe(lock as MapState['readOnly']);
        expect(ideasSince(from)).toEqual(['Trip', 'Kyoto']);
      });
    }
  }

  test('keeps what was typed into an idea the change took away, saved on the map as it was', () => {
    loadAt(TRIP, 1);
    send({ type: 'edit', id: 'tokio' });
    typeOnTheMap('Tokyo');
    const from = posted.length;

    send({ type: 'restore', markdown: '# Trip\n- Kyoto\n', revision: 2 });

    expect(postedOf('saved', from).map((save) => ({ markdown: save.markdown, revision: save.revision }))).toEqual([{ markdown: '# Trip\n- Tokyo\n', revision: 1 }]);
    expect(ideasSince(from)).toEqual(['Trip', 'Kyoto']);
  });

  test('keeps a new idea typed under an idea the change moved, saved on the map as it was', () => {
    loadAt('# Trip\n- Tokio\n- Japan\n', 1);
    send({ type: 'editNew', parentId: 'tokio' });
    typeOnTheMap('Shibuya');
    const from = posted.length;

    send({ type: 'restore', markdown: '# Trip\n- Japan\n  - Tokio\n', revision: 2 });

    expect(postedOf('saved', from).map((save) => ({ markdown: save.markdown, revision: save.revision }))).toEqual([{ markdown: '# Trip\n- Tokio\n  - Shibuya\n- Japan\n', revision: 1 }]);
    expect(ideasSince(from)).toEqual(['Trip', 'Japan', 'Tokio']);
  });

  for (const { idea, open, typed, saved } of typings) {
    for (const reason of ['deleted', 'storage'] as const) {
      test(`keeps ${idea}, saved before the map is locked as ${reason}`, () => {
        loadAt(TRIP, 1);
        send(open);
        typeOnTheMap(typed);
        const from = posted.length;

        send({ type: 'lock', reason });

        // Saved on the map as it is, which the app keeps where it can; before the map can only be shown.
        const saves = postedOf('saved', from);
        expect(saves.map((save) => ({ markdown: save.markdown, revision: save.revision, readOnly: save.state.readOnly }))).toEqual([{ markdown: saved, revision: 1, readOnly: null }]);
        const order = posted.slice(from).map((message) => (message.type === 'state' ? `state ${message.state.readOnly}` : message.type));
        expect(order.indexOf('saved')).toBeLessThan(order.indexOf(`state ${reason}`));
        expect(order).toContain('editorClose');
        send({ type: 'lock', reason: null });
      });
    }
  }

  test('goes on being typed into a change that leaves its idea where it was, and is saved on that change', () => {
    loadAt(TRIP, 1);
    send({ type: 'edit', id: 'tokio' });
    typeOnTheMap('Tokyo');
    const from = posted.length;

    send({ type: 'restore', markdown: '# Trip\n- Tokio\n- Kyoto\n', revision: 2 });

    // The field stays open on the change, with what was typed in it.
    expect(postedOf('saved', from)).toEqual([]);
    expect(postedOf('editorClose', from)).toEqual([]);
    expect(document.querySelector('textarea')?.value).toBe('Tokyo');

    send({ type: 'stopEditing', save: true });

    expect(postedOf('saved', from).map((save) => ({ markdown: save.markdown, revision: save.revision }))).toEqual([{ markdown: '# Trip\n- Tokyo\n- Kyoto\n', revision: 2 }]);
  });
});

describe('pasting', () => {
  test('pastes markdown written elsewhere whole, even with a heading-like line in its note\'s code', () => {
    load('# Trip\n- Lisbon\n');
    const from = posted.length;

    send({ type: 'paste', job: 81, parentId: 'trip', text: '# Flights\nBook early.\n\n```\n# not a heading\n```\n\n## Tokyo\n' });

    expect(postedOf('result', from).find((result) => result.job === 81)?.value).toEqual({ count: 2 });
    const saved = postedOf('saved', from).at(-1)?.markdown ?? '';
    expect(saved).toContain('Flights');
    expect(saved).toContain('# not a heading');
    expect(saved).toContain('Tokyo');
  });

  test('saves nothing when nothing was added: no change to keep, nor to undo', () => {
    load('# Trip\n- Lisbon\n');
    const from = posted.length;

    // Markdown with no ideas in it, a drop that brought none, and the map's center duplicated, which has nowhere to go.
    send({ type: 'paste', job: 82, parentId: 'trip', text: '---\ntags: [home]\n---\n' });
    send({ type: 'addNodes', job: 83, parentId: 'trip', nodes: [] });
    send({ type: 'duplicate', id: 'trip' });

    expect(postedOf('result', from).map((result) => [result.job, result.value])).toEqual([[82, { count: 0 }], [83, { count: 0 }]]);
    expect(postedOf('saved', from)).toEqual([]);

    send({ type: 'addNodes', job: 84, parentId: 'trip', nodes: [{ text: 'Porto' }] });
    expect(postedOf('saved', from).map((saved) => saved.added)).toEqual(['porto']);
  });
});

describe('copying', () => {
  test('copies a deep branch as an outline nested as deep as an outline goes, deeper ideas listed there', () => {
    // A branch forty ideas deep, each under the one before.
    load(['# Top', ...Array.from({ length: 40 }, (_, step) => `${'  '.repeat(step)}- Step ${step + 1}`), ''].join('\n'));
    const from = posted.length;

    send({ type: 'copy', job: 91, id: 'top' });

    const copied = postedOf('result', from).find((result) => result.job === 91)?.value as { text: string };
    const lines = copied.text.split('\n');
    expect(lines).toHaveLength(41);
    expect(lines.slice(1, 34)).toEqual(Array.from({ length: 33 }, (_, step) => `${'  '.repeat(step)}- Step ${step + 1}`));
    expect(lines.slice(34)).toEqual(Array.from({ length: 7 }, (_, step) => `${'  '.repeat(32)}- Step ${step + 34}`));
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
