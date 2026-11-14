/**
 * What the tests of a whole page need, as the page has it in a browser: its
 * own elements, from its HTML, in the test's document, and a clock for the
 * page's long waits. Bun runs each test file on its own (`bun test
 * --isolate`), so a page's script runs once per file, on that file's
 * document.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Puts the body of `file` (in web/) into the document, as the page has it:
 * its elements, and the body's own classes. `fill` replaces the page's
 * placeholders (`<!-- data -->`, say), as the app or the build fills them.
 */
export const showPage = (file: string, fill: Record<string, string> = {}): void => {
  const html = readFileSync(join(import.meta.dir, file), 'utf8');
  const body = /<body([^>]*)>([\s\S]*)<\/body>/.exec(html);
  let content = body?.[2] ?? '';

  for (const [placeholder, value] of Object.entries(fill)) {
    content = content.replace(placeholder, () => value);
  }

  document.body.className = /class="([^"]*)"/.exec(body?.[1] ?? '')?.[1] ?? '';
  document.body.innerHTML = content;
};

/**
 * Puts the test's document at `url`, as a browser that opened the page
 * there has it: a page saved and opened from its file is at a file: URL,
 * whose origin is `null`, as in Firefox. The tests' pages are at
 * http://localhost otherwise.
 */
export const pageAt = (url: string): void => {
  (window as unknown as { happyDOM: { setURL(url: string): void } }).happyDOM.setURL(url);
};

/** A wait the page asked for on the test's clock. */
interface Wait {
  id: number;
  at: number;
  run: () => void;
}

/** The clock the page's long waits go by, which the test moves on. */
export interface TestClock {
  /** Moves the clock on by `ms`, running every wait that comes due on the way, in order. */
  advance(ms: number): void;

  /** How many waits are still to come. */
  readonly waiting: number;
}

/**
 * Puts the page's waits of `longest` ms and more on a clock the test moves
 * on (`advance`), so a test of what happens after 10 seconds takes none.
 * Shorter ones, such as a caption swapping as the map arrives or the
 * renderer's frames, run on the real clock, as they do in a browser: a test
 * waits for what they do with `eventually`.
 */
export const testClock = (longest = 1_000): TestClock => {
  const realSetTimeout = window.setTimeout.bind(window);
  const realClearTimeout = window.clearTimeout.bind(window);
  const waits: Wait[] = [];
  let now = 0;
  // Far from the ids the real clock gives, so the two never meet.
  let next = 1_000_000_000;

  window.setTimeout = ((handler: TimerHandler, delay = 0, ...rest: unknown[]): number => {
    if (typeof handler !== 'function' || delay < longest) {
      return realSetTimeout(handler, delay, ...rest);
    }

    next += 1;
    waits.push({ id: next, at: now + delay, run: () => handler(...rest) });
    return next;
  }) as typeof window.setTimeout;

  window.clearTimeout = ((id?: number): void => {
    const index = waits.findIndex((wait) => wait.id === id);

    if (index >= 0) {
      waits.splice(index, 1);
    } else {
      realClearTimeout(id);
    }
  }) as typeof window.clearTimeout;

  return {
    advance: (ms) => {
      const until = now + ms;
      const nextDue = (): Wait | undefined => waits.filter((wait) => wait.at <= until).sort((a, b) => a.at - b.at || a.id - b.id)[0];
      let due = nextDue();

      // In the order they come due, the ones that a wait asks for on the way included.
      while (due) {
        waits.splice(waits.indexOf(due), 1);
        now = due.at;
        due.run();
        due = nextDue();
      }

      now = until;
    },
    get waiting() {
      return waits.length;
    },
  };
};

/**
 * Waits until `condition` holds, as a page's short waits and promises come
 * true (a caption swapping in, an answer read): it checks every few
 * milliseconds, and fails only after `seconds`, however busy the machine.
 */
export const eventually = async (condition: () => boolean, seconds = 10): Promise<void> => {
  const deadline = performance.now() + seconds * 1_000;

  while (!condition()) {
    if (performance.now() > deadline) {
      throw new Error(`Still not so after ${seconds} seconds: ${condition.toString()}`);
    }

    await new Promise((resolve) => setTimeout(resolve, 5));
  }
};
