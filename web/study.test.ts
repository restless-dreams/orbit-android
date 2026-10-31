import { describe, expect, test } from 'bun:test';
import { study } from './study';

/** A map that keeps what it's asked, as Orbit's renderer would hide and reveal. */
const map = () => {
  const asked: string[] = [];
  return {
    asked,
    hideChildren: (ids: string | readonly string[]) => {
      asked.push(`hide ${[ids].flat().join(',')}`);
      return true;
    },
    revealChildren: (ids?: string | readonly string[]) => {
      asked.push(ids === undefined ? 'reveal all' : `reveal ${[ids].flat().join(',')}`);
      return true;
    },
  };
};

describe('study mode on the page', () => {
  test('hides the ideas under every card it names, and reveals one card at a time', () => {
    const page = map();
    study(page, { type: 'hideChildren', ids: ['ser', 'estar'] });
    study(page, { type: 'revealChildren', ids: ['ser'] });
    expect(page.asked).toEqual(['hide ser,estar', 'reveal ser']);
  });

  test('reveals every hidden idea when the app names none, as a session ends', () => {
    const page = map();
    study(page, { type: 'revealChildren', ids: null });
    expect(page.asked).toEqual(['reveal all']);
  });
});
