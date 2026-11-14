import { describe, expect, test } from 'bun:test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const page = readFileSync(join(import.meta.dir, 'join.html'), 'utf8');
const script = page.slice(page.lastIndexOf('<script>') + '<script>'.length, page.lastIndexOf('</script>'));
const onward = page.slice(page.indexOf('<!-- onward -->'), page.indexOf('<!-- /onward -->'));

/** What a tab's storage keeps, or one that refuses, as a browser with site data blocked does. */
const storage = (refuses = false): Pick<Storage, 'getItem' | 'setItem'> => {
  const kept = new Map<string, string>();

  return {
    getItem: (key) => {
      if (refuses) {
        throw new DOMException('Blocked', 'SecurityError');
      }

      return kept.get(key) ?? null;
    },
    setItem: (key, value) => {
      kept.set(key, value);
    },
  };
};

/** Runs the join page's script on the page a room's address gets from another site: where it went on to, if anywhere. */
const opened = (tab: Pick<Storage, 'getItem' | 'setItem'>): string[] => {
  document.body.innerHTML = onward.replace('<!-- onward label -->', 'Open');
  const went: string[] = [];
  const location = { replace: (to: string) => went.push(to) };
  new Function('location', 'sessionStorage', script)(location, tab);
  return went;
};

describe('a room asked for from another site', () => {
  test('opens again from its own address at once, as a link from a chat on the web is followed', () => {
    expect(opened(storage())).toEqual([document.querySelector<HTMLAnchorElement>('.join__onward')!.href]);
  });

  test('goes on by itself only once in a tab, and after that waits for a tap on its button', () => {
    // A site that sends a window it opened to one code after another gets one try from it, however long it waits.
    const tab = storage();
    expect(opened(tab)).toHaveLength(1);
    expect(opened(tab)).toEqual([]);
    expect(document.querySelector('.join__onward')?.textContent).toBe('Open');
    tab.setItem('orbit-onward', String(Date.now() - 60 * 60 * 1000));
    expect(opened(tab)).toEqual([]);

    // Another tab goes on by itself once too.
    expect(opened(storage())).toHaveLength(1);
  });

  test('waits for a tap where the tab keeps nothing', () => {
    expect(opened(storage(true))).toEqual([]);
  });
});
