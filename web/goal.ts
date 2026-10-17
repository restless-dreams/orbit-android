/**
 * Goal ideas track a number toward a target. A goal is an idea with
 * `type: goal` and these attributes:
 *
 * - `target`, the number to reach;
 * - `current`, where it is now (`start` until it's written);
 * - `start`, where it started (0 until it's written). Paying something off
 *   starts at what was owed, with 0 as its target;
 * - `unit`, a currency code (`USD`, `EUR`) for money, or any word for what's
 *   counted (`apples`, `km`).
 *
 * Numbers are plain numbers (`1250.5`). The ring a goal's planet shows, the
 * Progress idea above it and the app all read them the same way.
 */
import type { OrbitNode } from '@restless-dreams/orbit';

export interface Goal {
  /** The number to reach; null when it isn't written, or isn't a number. */
  target: number | null;

  current: number;
  start: number;

  /** The unit as written, if any. */
  unit: string | null;

  /** The unit as a currency code, when the goal counts money. */
  currency: string | null;
}

/** Numbers this large or larger aren't read: JavaScript's numbers stop holding every whole unit not far above it. */
const LARGEST = 1e15;

/** A number written as text, the way YAML would read it unquoted. */
const PLAIN_NUMBER = /^[+-]?(\d+(\.\d*)?|\.\d+)$/;

export const isGoal = (node: OrbitNode): boolean => {
  return node.type?.trim().toLowerCase() === 'goal';
};

/** A number in an attribute: a plain number, or text that is one. Anything else is no number. */
export const readNumber = (value: unknown): number | null => {
  let number: number;

  if (typeof value === 'number') {
    number = value;
  } else if (typeof value === 'string' && PLAIN_NUMBER.test(value.trim())) {
    number = Number(value.trim());
  } else {
    return null;
  }

  return Number.isFinite(number) && Math.abs(number) < LARGEST ? number + 0 : null;
};

let currencies: Set<string> | null = null;

/** The unit as a currency code, when it names one: `USD`, `eur`. */
export const currencyCode = (unit: string | null | undefined): string | null => {
  const code = unit?.trim().toUpperCase();

  if (!code || !/^[A-Z]{3}$/.test(code)) {
    return null;
  }

  if (!currencies) {
    const supported = (Intl as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf;
    currencies = new Set(typeof supported === 'function' ? supported('currency') : []);
  }

  // Without the list of currencies, any three capital letters are taken for one.
  return currencies.size === 0 || currencies.has(code) ? code : null;
};

export const readGoal = (node: OrbitNode): Goal => {
  const attributes = node.attrs ?? {};
  const start = readNumber(attributes.start) ?? 0;
  const unit = typeof attributes.unit === 'string' && attributes.unit.trim() !== '' ? attributes.unit.trim() : null;
  return {
    target: readNumber(attributes.target),
    current: readNumber(attributes.current) ?? start,
    start,
    unit,
    currency: currencyCode(unit),
  };
};

/** Whether the goal counts down, as paying something off does: it starts above its target. */
export const countsDown = (goal: Goal): boolean => {
  return goal.target !== null && goal.start > goal.target;
};

/** Whether the goal got where it was going: a target where it started is reached from the start. */
export const goalReached = (goal: Goal): boolean => {
  if (goal.target === null) {
    return false;
  }

  if (goal.target === goal.start) {
    return true;
  }

  return countsDown(goal) ? goal.current <= goal.target : goal.current >= goal.target;
};

/** How far along the goal is, from 0 to 1. A goal without a target hasn't begun. */
export const goalShare = (goal: Goal): number => {
  if (goal.target === null) {
    return 0;
  }

  if (goalReached(goal)) {
    return 1;
  }

  return Math.min(1, Math.max(0, (goal.current - goal.start) / (goal.target - goal.start)));
};

/** Decimal places a number is written with. */
const places = (value: number): number => {
  const written = String(value);
  const exponent = /e([+-]\d+)$/.exec(written);
  const mantissa = exponent ? written.slice(0, exponent.index) : written;
  const point = mantissa.indexOf('.');
  const fraction = point < 0 ? 0 : mantissa.length - point - 1;
  return Math.max(0, fraction - (exponent ? Number(exponent[1]) : 0));
};

/** `amount` added to `value` as decimals add, so 0.1 and 0.2 make 0.3. */
export const addAmount = (value: number, amount: number): number => {
  const scale = 10 ** Math.min(12, Math.max(places(value), places(amount)));
  return (Math.round(value * scale) + Math.round(amount * scale)) / scale + 0;
};

/**
 * The goal's number once `amount` is added. Steps down stop at the low end
 * of the goal, its start, or its target when it counts down, however fast
 * they come: a count doesn't go below nothing, nor a debt below paid off.
 * Steps up may go past the target, as buying one more than needed does.
 */
export const stepGoal = (goal: Goal, amount: number): number => {
  const stepped = addAmount(goal.current, amount);

  if (amount >= 0) {
    return stepped;
  }

  return Math.max(Math.min(goal.start, goal.target ?? goal.start, goal.current), stepped);
};

/**
 * Custom attributes with `changes` made: a value sets its key, keeping its
 * place among the others, and null removes it. Undefined when none are left.
 */
export const mergeAttributes = (attributes: Record<string, unknown> | undefined, changes: Record<string, unknown>): Record<string, unknown> | undefined => {
  const merged: Record<string, unknown> = { ...attributes };

  for (const [key, value] of Object.entries(changes)) {
    if (value === null || value === undefined) {
      delete merged[key];
    } else {
      merged[key] = value;
    }
  }

  return Object.keys(merged).length > 0 ? merged : undefined;
};

// Writing the numbers

const formats = new Map<string, Intl.NumberFormat>();

const numberFormat = (locale: string | undefined, options: Intl.NumberFormatOptions): Intl.NumberFormat => {
  const key = `${locale ?? ''} ${JSON.stringify(options)}`;
  let format = formats.get(key);

  if (!format) {
    format = new Intl.NumberFormat(locale, options);
    formats.set(key, format);
  }

  return format;
};

/** Decimal places a currency is written with: 2 for dollars, none for yen. */
const currencyPlaces = (currency: string): number => {
  return numberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
};

/**
 * A number as the reader's language writes it (no `locale`: the page's, which
 * follows the phone's), as money in its currency. `compact` shortens large
 * numbers: 1.3K, 12K, 1.2M.
 */
export const formatAmount = (value: number, currency: string | null, compact: boolean, locale?: string): string => {
  const options: Intl.NumberFormatOptions = currency ? { style: 'currency', currency } : {};

  if (compact) {
    options.notation = 'compact';
  } else {
    const whole = Number.isInteger(value);
    options.minimumFractionDigits = whole || !currency ? 0 : currencyPlaces(currency);
    options.maximumFractionDigits = whole ? 0 : currency ? currencyPlaces(currency) : 2;
  }

  return numberFormat(locale, options).format(value + 0);
};

/** A number's ways of being written in a ring, the full one first. */
const forms = (value: number, currency: string | null, locale?: string): string[] => {
  const full = formatAmount(value, currency, false, locale);
  const compact = formatAmount(value, currency, true, locale);
  return compact === full ? [full] : [full, compact];
};

/**
 * What a goal's ring says: its current number big, with the target under it
 * ("of 5", "of $3K"), or "left" when it counts down. Reached, a check takes
 * the line's place. Each line lists the ways it can be written, the full one
 * first; a ring picks whichever fits best.
 */
export interface RingText {
  main: string[];
  detail: string[] | null;
  check: boolean;
}

/** The words a ring says around its numbers, in the page's language (see words.ts). */
export interface RingWords {
  /** The target under the number, where `{target}` is: "of {target}". */
  goalOfTarget: string;

  /** Under the number of a goal counting down: what's left to pay. */
  goalLeft: string;
}

export const ENGLISH_RING_WORDS: RingWords = { goalOfTarget: 'of {target}', goalLeft: 'left' };

export const ringText = (goal: Goal, locale?: string, words: RingWords = ENGLISH_RING_WORDS): RingText => {
  if (goal.target === null) {
    return { main: ['–'], detail: null, check: false };
  }

  const main = forms(goal.current, goal.currency, locale);

  if (goalReached(goal)) {
    return { main, detail: null, check: true };
  }

  if (countsDown(goal)) {
    return { main, detail: [words.goalLeft], check: false };
  }

  return { main, detail: forms(goal.target, goal.currency, locale).map((target) => words.goalOfTarget.replace('{target}', () => target)), check: false };
};

// Fitting the text inside the ring

/** How wide `text` is in ems at a font weight. */
export type Measure = (text: string, weight: number) => number;

/** A line of text in the ring's 100 × 100 box: what it says, its font size, and the height of its middle. */
export interface RingLine {
  text: string;
  size: number;
  y: number;
}

export interface RingLayout {
  main: RingLine;
  detail: RingLine | null;

  /** Where the check's middle is, when there is one. */
  check: number | null;
}

/** The text keeps inside this circle: the ring's inner edge, 31 from the middle, less a margin. */
const ROOM = 27;
/** Digits and capitals stand this tall, in ems. */
export const CAP_HEIGHT = 0.72;
/** Between the number and the line under it. */
const GAP = 4;
export const MAIN_WEIGHT = 700;
export const DETAIL_WEIGHT = 600;
export const MAIN_SIZE = 23;
const DETAIL_SIZE = 11.5;
export const CHECK_RADIUS = 7.5;
/** The full form of a line wins while it's at least this share of the line's largest size; then the shortest wins. */
const MAIN_FULL = 0.7;
const DETAIL_FULL = 0.85;

/** The largest size, up to `largest`, at which a line `ems` wide with its middle `offset` from the ring's fits inside it. */
export const fitSize = (ems: number, offset: number, largest: number): number => {
  const fits = (size: number) => {
    const half = (ems * size) / 2;
    const edge = Math.abs(offset) + (CAP_HEIGHT * size) / 2;
    return half * half + edge * edge <= ROOM * ROOM;
  };

  if (fits(largest)) {
    return largest;
  }

  let low = 0;
  let high = largest;

  for (let step = 0; step < 24; step += 1) {
    const middle = (low + high) / 2;

    if (fits(middle)) {
      low = middle;
    } else {
      high = middle;
    }
  }

  return low;
};

const choose = (forms: string[], offset: number, largest: number, weight: number, full: number, measure: Measure): { text: string; size: number } => {
  const sized = forms.map((text) => ({ text, size: fitSize(measure(text, weight), offset, largest) }));

  if (sized[0].size >= largest * full) {
    return sized[0];
  }

  return sized.reduce((best, form) => (form.size > best.size ? form : best));
};

/** Where the lines' middles go: the block of them centered in the ring. */
const place = (main: number, below: number): { main: number; below: number } => {
  const total = main + (below > 0 ? GAP + below : 0);
  const top = 50 - total / 2;
  return { main: top + main / 2, below: top + main + GAP + below / 2 };
};

/**
 * The ring's text laid out to fit inside it, at any length: each line in the
 * form that fits best, as big as it can be. Lines are sized where the
 * largest text would put them, then centered; smaller text only moves
 * toward the middle, where there's more room.
 */
export const layoutRing = (text: RingText, measure: Measure, largest = MAIN_SIZE): RingLayout => {
  const belowAtMost = text.check ? CHECK_RADIUS * 2 : text.detail ? DETAIL_SIZE * CAP_HEIGHT : 0;
  const first = place(largest * CAP_HEIGHT, belowAtMost);
  const main = choose(text.main, first.main - 50, largest, MAIN_WEIGHT, MAIN_FULL, measure);
  const detail = text.detail && !text.check ? choose(text.detail, first.below - 50, DETAIL_SIZE, DETAIL_WEIGHT, DETAIL_FULL, measure) : null;
  const below = text.check ? CHECK_RADIUS * 2 : detail ? detail.size * CAP_HEIGHT : 0;
  const final = place(main.size * CAP_HEIGHT, below);
  return {
    main: { ...main, y: final.main },
    detail: detail ? { ...detail, y: final.below } : null,
    check: text.check ? final.below : null,
  };
};
