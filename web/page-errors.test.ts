import { describe, expect, test } from 'bun:test';
import { pageError, reportErrors } from './page-errors';
import type { PageError } from './page-errors';

/** The errors the app hears of after `messages` are reported on a page, in order. */
const heard = (messages: string[]): PageError[] => {
  const page = new EventTarget();
  const posted: PageError[] = [];
  reportErrors(page, (error) => posted.push(error));

  for (const message of messages) {
    page.dispatchEvent(new ErrorEvent('error', { message }));
  }

  return posted;
};

describe('an error on the page', () => {
  test('reaches the app, which shows it on the map', () => {
    expect(heard(['Cannot read properties of undefined (reading \'id\')'])).toEqual([
      { type: 'error', message: 'Cannot read properties of undefined (reading \'id\')' },
    ]);
  });

  test('never Chromium\'s notice that a ResizeObserver goes on in the next frame, in either wording', () => {
    expect(heard(['ResizeObserver loop completed with undelivered notifications.', 'ResizeObserver loop limit exceeded'])).toEqual([]);
    expect(pageError('ResizeObserver loop completed with undelivered notifications.')).toBeNull();
    expect(pageError('ResizeObserver loop limit exceeded')).toBeNull();
  });

  test('still reaches the app after the notice, and among others', () => {
    expect(heard([
      'ResizeObserver loop completed with undelivered notifications.',
      'Orbit could not draw the map',
      'ResizeObserver loop limit exceeded',
      'TypeError: map is null',
    ])).toEqual([
      { type: 'error', message: 'Orbit could not draw the map' },
      { type: 'error', message: 'TypeError: map is null' },
    ]);
  });
});
