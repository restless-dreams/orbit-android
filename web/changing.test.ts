import { describe, expect, test } from 'bun:test';
import { attempt, CHANGES, changeable, refused } from './changing';

/** Commands that look around the map, or show it in another way, without changing it. */
const LOOKING = ['focus', 'view', 'zoom', 'fit', 'present', 'preview', 'restore', 'lock', 'stopEditing', 'hideChildren', 'revealChildren'];

describe('an earlier version on show', () => {
  test('takes no change, and is never saved', () => {
    expect(changeable(null, true)).toBe(false);

    for (const type of CHANGES) {
      expect(refused(type, null, true)).toBe(true);
    }
  });

  test('refuses new tags, a renamed map and a goal step: the changes that wrote an old version over the map', () => {
    expect(refused('options', null, true)).toBe(true);
    expect(refused('update', null, true)).toBe(true);
    expect(refused('addToGoal', null, true)).toBe(true);
  });

  test('can still be looked around', () => {
    for (const type of LOOKING) {
      expect(refused(type, null, true)).toBe(false);
    }
  });
});

describe('the map as it is now', () => {
  test('takes every change, and is saved', () => {
    expect(changeable(null, false)).toBe(true);

    for (const type of CHANGES) {
      expect(refused(type, null, false)).toBe(false);
    }
  });

  test('takes none while it\'s locked, whatever is on show', () => {
    for (const lock of ['unreadable', 'newer', 'deleted', 'storage'] as const) {
      expect(changeable(lock, false)).toBe(false);
      expect(refused('update', lock, false)).toBe(true);
      expect(refused('options', lock, true)).toBe(true);
      expect(refused('focus', lock, false)).toBe(false);
    }
  });
});

describe('a change Orbit declines', () => {
  test('is said back with Orbit\'s reason, and only a TypeError, Orbit\'s way of declining, is', () => {
    expect(attempt(() => 'tokyo')).toEqual({ made: true, value: 'tokyo' });

    const declined = attempt(() => {
      throw new TypeError('A node\'s image is its address, or { src, alt, caption, motion }, like { image: \'summit.jpg\' }, but got 42.');
    });

    expect(declined).toEqual({ made: false, reason: 'A node\'s image is its address, or { src, alt, caption, motion }, like { image: \'summit.jpg\' }, but got 42.' });

    expect(() => attempt(() => {
      throw new Error('The page broke');
    })).toThrow('The page broke');
  });
});
