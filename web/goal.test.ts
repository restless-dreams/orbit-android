import { describe, expect, test } from 'bun:test';
import { parseOrbit, serializeOrbitMarkdown } from '@restless-dreams/orbit';
import type { OrbitNode } from '@restless-dreams/orbit';
import {
  addAmount,
  countsDown,
  currencyCode,
  fitSize,
  formatAmount,
  goalReached,
  goalShare,
  layoutRing,
  mergeAttributes,
  readGoal,
  readNumber,
  ringText,
  stepGoal,
} from './goal';
import type { Measure } from './goal';
import { goalRing, progressOf, progressRing } from './look';

const GROCERIES = `# Plans

- Groceries <!-- orbit type: progress -->
  - Apples <!-- orbit type: goal, target: 5, current: 2, unit: apples -->
    Green ones, if they have them.
  - Eggs <!-- orbit type: goal, target: 12, current: 12 -->
- Japan trip <!-- orbit type: goal, target: 3000, current: 1250, unit: USD -->
- Credit card <!-- orbit type: goal, start: 4800, current: 3100, target: 0, unit: USD -->
`;

const find = (node: OrbitNode, text: string): OrbitNode => {
  const found = node.text === text ? node : node.children.map((child) => findOrNull(child, text)).find(Boolean);

  if (!found) {
    throw new Error(`No ${text}`);
  }

  return found;
};

const findOrNull = (node: OrbitNode, text: string): OrbitNode | null => {
  try {
    return find(node, text);
  } catch {
    return null;
  }
};

const goalNode = (attrs: Record<string, unknown>): OrbitNode => {
  return { id: 'goal', text: 'Goal', level: 1, type: 'goal', attrs, children: [] };
};

/** Every character half an em wide: enough to see lines shrink as they grow. */
const halfEms: Measure = (text) => text.length * 0.5;

describe('reading goals', () => {
  test('numbers are plain numbers, or text that is one', () => {
    expect(readNumber(5)).toBe(5);
    expect(readNumber(1250.5)).toBe(1250.5);
    expect(readNumber(' 12 ')).toBe(12);
    expect(readNumber('-2.5')).toBe(-2.5);
    expect(readNumber('1,250')).toBeNull();
    expect(readNumber('five')).toBeNull();
    expect(readNumber('1e3')).toBeNull();
    expect(readNumber(true)).toBeNull();
    expect(readNumber(null)).toBeNull();
    expect(readNumber(1e15)).toBeNull();
  });

  test('current starts where the goal started, and the start at 0', () => {
    expect(readGoal(goalNode({ target: 5 }))).toEqual({ target: 5, current: 0, start: 0, unit: null, currency: null });
    expect(readGoal(goalNode({ start: 4800, target: 0 })).current).toBe(4800);
    expect(readGoal(goalNode({ target: 'lots', current: 'some' }))).toEqual({ target: null, current: 0, start: 0, unit: null, currency: null });
  });

  test('a unit is money when it is a currency code', () => {
    expect(currencyCode('USD')).toBe('USD');
    expect(currencyCode(' eur ')).toBe('EUR');
    expect(currencyCode('apples')).toBeNull();
    expect(currencyCode('km')).toBeNull();
    expect(currencyCode('XYZ')).toBeNull();
    expect(readGoal(goalNode({ target: 3000, unit: 'MXN' })).currency).toBe('MXN');
  });
});

describe('how far along a goal is', () => {
  test('counting up', () => {
    expect(goalShare(readGoal(goalNode({ target: 5, current: 2 })))).toBeCloseTo(0.4);
    expect(goalShare(readGoal(goalNode({ start: 10, target: 20, current: 15 })))).toBeCloseTo(0.5);
  });

  test('counting down is how much is paid', () => {
    const card = readGoal(goalNode({ start: 4800, current: 3100, target: 0 }));
    expect(countsDown(card)).toBe(true);
    expect(goalShare(card)).toBeCloseTo(1700 / 4800);
    expect(goalReached(readGoal(goalNode({ start: 4800, current: 0, target: 0 })))).toBe(true);
  });

  test('a target where the goal started is reached', () => {
    const goal = readGoal(goalNode({ start: 3, target: 3, current: 1 }));
    expect(goalReached(goal)).toBe(true);
    expect(goalShare(goal)).toBe(1);
  });

  test('past the target is reached, and below the start is not begun', () => {
    expect(goalShare(readGoal(goalNode({ target: 5, current: 7 })))).toBe(1);
    expect(goalReached(readGoal(goalNode({ target: 5, current: 7 })))).toBe(true);
    expect(goalShare(readGoal(goalNode({ target: 5, current: -2 })))).toBe(0);
    expect(goalShare(readGoal(goalNode({ start: 4800, target: 0, current: 5000 })))).toBe(0);
  });

  test('without a target, nothing is done', () => {
    const goal = readGoal(goalNode({ current: 4 }));
    expect(goalShare(goal)).toBe(0);
    expect(goalReached(goal)).toBe(false);
    expect(ringText(goal)).toEqual({ main: ['–'], detail: null, check: false });
  });

  test('a Progress idea counts each goal under it by how far along it is', () => {
    const groceries = find(parseOrbit(GROCERIES).root, 'Groceries');
    const { done, total } = progressOf(groceries);
    expect(total).toBe(2);
    expect(Math.round((done / total) * 100)).toBe(70);
  });
});

describe('changing goals', () => {
  test('amounts add as decimals do', () => {
    expect(addAmount(0.1, 0.2)).toBe(0.3);
    expect(addAmount(1250.5, 0.25)).toBe(1250.75);
    expect(addAmount(2, -1)).toBe(1);
    expect(addAmount(3100, -200.1)).toBe(2899.9);
    expect(Object.is(addAmount(1, -1), 0)).toBe(true);
  });

  test('steps down stop at the low end of the goal; steps up may pass the target', () => {
    expect(stepGoal(readGoal(goalNode({ target: 5, current: 1 })), -1)).toBe(0);
    expect(stepGoal(readGoal(goalNode({ target: 5, current: 0 })), -1)).toBe(0);
    expect(stepGoal(readGoal(goalNode({ target: 5, current: 5 })), 1)).toBe(6);
    expect(stepGoal(readGoal(goalNode({ start: 4800, current: 150, target: 0 })), -200)).toBe(0);
    expect(stepGoal(readGoal(goalNode({ start: 4800, current: 3100, target: 0 })), -200.5)).toBe(2899.5);
    // A number written below the goal by hand isn't lowered further, and steps up from where it is.
    expect(stepGoal(readGoal(goalNode({ target: 5, current: -2 })), -1)).toBe(-2);
    expect(stepGoal(readGoal(goalNode({ target: 5, current: -2 })), 1)).toBe(-1);
  });

  test('custom attributes merge, each keeping its place', () => {
    const merged = mergeAttributes({ start: 4800, current: 3100, target: 0, unit: 'USD' }, { current: 2900 });
    expect(Object.entries(merged ?? {})).toEqual([['start', 4800], ['current', 2900], ['target', 0], ['unit', 'USD']]);
    expect(mergeAttributes({ target: 5, owner: 'Ana' }, { target: null, current: null })).toEqual({ owner: 'Ana' });
    expect(mergeAttributes({ target: 5 }, { target: null })).toBeUndefined();
    expect(mergeAttributes(undefined, { unit: 'apples' })).toEqual({ unit: 'apples' });
  });

  test('an edited map saves, reads back and keeps every attribute, title and note', () => {
    const document = parseOrbit(GROCERIES);
    const apples = find(document.root, 'Apples');
    apples.attrs = mergeAttributes(apples.attrs, { current: addAmount(readGoal(apples).current, 1) });
    const card = find(document.root, 'Credit card');
    card.attrs = mergeAttributes(card.attrs, { current: addAmount(readGoal(card).current, -200.5) });
    const trip = find(document.root, 'Japan trip');
    trip.attrs = mergeAttributes(trip.attrs, { target: 3500 });

    const saved = serializeOrbitMarkdown(document);
    const read = parseOrbit(saved).root;
    expect(find(read, 'Apples').attrs).toEqual({ target: 5, current: 3, unit: 'apples' });
    expect(find(read, 'Apples').note).toBe('Green ones, if they have them.');
    expect(find(read, 'Eggs').attrs).toEqual({ target: 12, current: 12 });
    expect(find(read, 'Japan trip').attrs).toEqual({ target: 3500, current: 1250, unit: 'USD' });
    expect(find(read, 'Credit card').attrs).toEqual({ start: 4800, current: 2899.5, target: 0, unit: 'USD' });

    for (const text of ['Apples', 'Eggs', 'Japan trip', 'Credit card']) {
      expect(find(read, text).type).toBe('goal');
    }

    // Ideas no edit touched keep their lines exactly, edited goals keep their numbers on their line, and numbers are written plainly.
    expect(saved).toContain('- Groceries <!-- orbit type: progress -->\n');
    expect(saved).toContain('  - Eggs <!-- orbit type: goal, target: 12, current: 12 -->\n');
    expect(saved).toContain('  - Apples <!-- orbit type: goal, target: 5, current: 3, unit: apples -->\n');
    expect(saved).toContain('current: 2899.5');
    // Exported again as Orbit's own markdown, the goals are the same.
    const exported = parseOrbit(serializeOrbitMarkdown(parseOrbit(saved), { keepSource: false })).root;
    expect(find(exported, 'Credit card').attrs).toEqual({ start: 4800, current: 2899.5, target: 0, unit: 'USD' });
    expect(find(exported, 'Apples').attrs).toEqual({ target: 5, current: 3, unit: 'apples' });
  });
});

describe('the ring', () => {
  test('says the number, and the target under it', () => {
    expect(ringText(readGoal(goalNode({ target: 5, current: 2, unit: 'apples' })), 'en-US')).toEqual({ main: ['2'], detail: ['of 5'], check: false });
    expect(ringText(readGoal(goalNode({ target: 3000, current: 1250, unit: 'USD' })), 'en-US')).toEqual({
      main: ['$1,250', '$1.3K'],
      detail: ['of $3,000', 'of $3K'],
      check: false,
    });
  });

  test('says what is left when counting down, and checks a goal reached', () => {
    expect(ringText(readGoal(goalNode({ start: 4800, current: 3100, target: 0, unit: 'USD' })), 'en-US').detail).toEqual(['left']);
    expect(ringText(readGoal(goalNode({ target: 12, current: 12 })), 'en-US')).toEqual({ main: ['12'], detail: null, check: true });
  });

  test('writes numbers in the reader\'s language, as money in its currency', () => {
    expect(formatAmount(1250.5, 'USD', false, 'en-US')).toBe('$1,250.50');
    expect(formatAmount(1250, 'MXN', false, 'es-MX')).toBe('$1,250');
    expect(formatAmount(12345, 'EUR', false, 'de-DE')).toBe('12.345\u00a0€');
    expect(formatAmount(1250.5, null, false, 'de-DE')).toBe('1.250,5');
    expect(formatAmount(1250, 'USD', true, 'en-US')).toBe('$1.3K');
    expect(formatAmount(12345, null, true, 'en-US')).toBe('12K');
    expect(formatAmount(1234567, 'EUR', true, 'de-DE')).toBe('1,2\u00a0Mio.\u00a0€');
    expect(formatAmount(-0, null, false, 'en-US')).toBe('0');
  });

  test('a line fits inside the ring at any length', () => {
    const room = 27;

    for (const length of [1, 3, 6, 12, 40]) {
      const ems = length * 0.6;

      for (const offset of [0, -6, 12]) {
        const size = fitSize(ems, offset, 23);
        const half = (ems * size) / 2;
        const edge = Math.abs(offset) + (0.72 * size) / 2;
        expect(half * half + edge * edge).toBeLessThanOrEqual(room * room + 1e-6);
        expect(size).toBeGreaterThan(0);
      }
    }

    expect(fitSize(0.6, 0, 23)).toBe(23);
  });

  test('long numbers take their short form, and both lines stay inside', () => {
    const layout = layoutRing(ringText(readGoal(goalNode({ target: 300000, current: 125000.5, unit: 'USD' })), 'en-US'), halfEms);
    expect(layout.main.text).toBe('$125K');
    expect(layout.detail?.text).toBe('of $300K');
    expect(layout.main.y).toBeLessThan(layout.detail?.y ?? 0);
    const short = layoutRing(ringText(readGoal(goalNode({ target: 5, current: 2 })), 'en-US'), halfEms);
    expect(short.main).toMatchObject({ text: '2', size: 23 });
    expect(short.detail?.text).toBe('of 5');
  });

  test('a reached goal keeps its number, with the check under it', () => {
    const layout = layoutRing(ringText(readGoal(goalNode({ target: 12, current: 12 })), 'en-US'), halfEms);
    expect(layout.main.text).toBe('12');
    expect(layout.detail).toBeNull();
    expect(layout.check).toBeGreaterThan(layout.main.y);
  });
});

const PLANS = `# Plans <!-- orbit type: progress -->

- Trip <!-- orbit type: goal -->
  - Flights <!-- orbit type: goal, target: 1, current: 1 -->
  - Hotel <!-- orbit type: goal, target: 4, current: 2 -->
  - [x] Book the train
  - [ ] Renew the passport
- Groceries <!-- orbit type: progress -->
  - Apples <!-- orbit type: goal, target: 5, current: 2 -->
  - Pantry <!-- orbit type: goal -->
    - Rice <!-- orbit type: goal -->
      - [x] Brown rice
      - [ ] Basmati
- Savings <!-- orbit type: goal, target: 1000, current: 250, unit: USD -->
  - [x] Open the account
- Someday <!-- orbit type: goal -->
  - Read more
`;

describe('parent goals', () => {
  test('a goal without a target of its own shows the progress of the goals and to-dos under it', () => {
    const trip = find(parseOrbit(PLANS).root, 'Trip');
    // Flights reached, Hotel halfway, the train booked, the passport not yet: 2.5 of 4.
    expect(progressOf(trip)).toEqual({ done: 2.5, total: 4 });
    const ring = goalRing(trip, 'en-US');
    expect(ring.share).toBeCloseTo(0.625);
    expect(ring.text).toEqual({ main: ['63%'], detail: null, check: false });
    expect(goalRing(trip, 'en-US')).toEqual(progressRing(trip));
  });

  test('goals without a target are followed through, never counted themselves', () => {
    const root = parseOrbit(PLANS).root;
    // Pantry and Rice have no target: only Brown rice and Basmati count under them.
    expect(progressOf(find(root, 'Pantry'))).toEqual({ done: 1, total: 2 });
    expect(goalRing(find(root, 'Pantry'), 'en-US').text.main).toEqual(['50%']);
    // Groceries counts Apples and the two rices, not Pantry or Rice.
    expect(progressOf(find(root, 'Groceries'))).toEqual({ done: 1.4, total: 3 });
    // The whole map: the trip's 4, the groceries' 3, Savings and its account, never Trip, Pantry, Rice or Someday.
    const plans = progressOf(root);
    expect(plans.total).toBe(9);
    expect(plans.done).toBeCloseTo(2.5 + 1.4 + 0.25 + 1);
  });

  test('a goal with a target of its own shows its own numbers, whatever is under it', () => {
    const savings = find(parseOrbit(PLANS).root, 'Savings');
    const ring = goalRing(savings, 'en-US');
    expect(ring.share).toBeCloseTo(0.25);
    expect(ring.text).toEqual({ main: ['$250'], detail: ['of $1,000', 'of $1K'], check: false });
  });

  test('a goal without a target and nothing to follow has an empty ring', () => {
    const someday = find(parseOrbit(PLANS).root, 'Someday');
    expect(goalRing(someday, 'en-US')).toEqual({ share: 0, text: { main: ['–'], detail: null, check: false } });
  });
});
